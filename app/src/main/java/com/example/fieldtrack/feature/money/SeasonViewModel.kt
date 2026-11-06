package com.example.fieldtrack.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.season.SeasonStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/** Which stretch of the year is being totalled. */
enum class SeasonWindow { Now, Last }

data class SeasonState(
    val loaded: Boolean = false,
    val window: SeasonWindow = SeasonWindow.Now,
    val fromMillis: Long = 0,
    val toMillis: Long = 0,
    /** What the business calls the season shown, if it has named them. */
    val seasonName: String? = null,
    /** The days the business's seasons begin, for saying what a season is here. */
    val seasonStarts: List<SeasonStart> = emptyList(),
    /** Whether this reader may change when the seasons begin: the owner. */
    val canChangeSeasons: Boolean = false,
    val earnedRupees: Long = 0,
    val fuelRupees: Long = 0,
    val serviceRupees: Long = 0,
    /** Wages laid against jobs, paid or not. */
    val jobPeopleRupees: Long = 0,
    /** Wages that belong to no job — a month's pay, a day's hire, an advance. */
    val loosePeopleRupees: Long = 0,
    /** Costs somebody recorded and nobody has priced. */
    val unpricedCount: Int = 0,
    /** What customers still owe, across all time, not only this season. */
    val owedByCustomersRupees: Long = 0,
    val canSeeMoney: Boolean = false,
    val seesPeopleCost: Boolean = false,
    /**
     * Loan instalments that fell in these months, about — what the owner said
     * each machine costs a month, over the months in the season.
     *
     * Counted in the total because it is a cost: a season "left" figure that
     * ignores a tractor's instalments overstates the year for exactly the owners
     * who have one. Said to be approximate because it is a monthly sum spread
     * over a stretch of days, not a record of payments made.
     */
    val loanRupees: Long = 0,
    /** Jobs that cost more than they were billed for, and how much between them. */
    val lostJobs: Int = 0,
    val lostRupees: Long = 0,
) {
    val peopleRupees: Long get() = jobPeopleRupees + loosePeopleRupees

    val costsRupees: Long get() = fuelRupees + serviceRupees + peopleRupees + loanRupees

    val leftRupees: Long get() = earnedRupees - costsRupees

    /**
     * Whether a total can honestly be shown.
     *
     * Every part of it has to be visible to this reader and actually priced.
     * A season total missing the wages reads as profit and is wrong by the
     * largest single cost most contractors have.
     */
    val canTotal: Boolean get() = canSeeMoney && seesPeopleCost && unpricedCount == 0
}

/**
 * What the business made over a season.
 *
 * The level where everything finally lands. A machine's own screen has to
 * admit that a month's wages and a season advance belong to no machine and
 * leave them out; here there is nothing to leave out, because the season is
 * the stretch of time those wages were for.
 *
 * Seasons rather than months because that is the unit the work comes in: a
 * contractor's book effectively starts again at each, and a calendar month cuts
 * a harvest in half. When they begin is the business's own to say — see
 * [com.example.fieldtrack.data.season.SeasonCalendar].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val window = MutableStateFlow(SeasonWindow.Now)

    fun setWindow(value: SeasonWindow) = window.update { value }

    val state: StateFlow<SeasonState> = combine(
        app.seasonedSession,
        window,
    ) { sessionAndSeasons, chosen -> sessionAndSeasons to chosen }
        .flatMapLatest { (sessionAndSeasons, chosen) ->
            val (s, seasons) = sessionAndSeasons
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val thisSeason = seasons.current(now, zone)
            val shown = when (chosen) {
                SeasonWindow.Now -> thisSeason
                SeasonWindow.Last -> seasons.previous(now, zone)
            }
            val from = shown.startMillis(zone)
            // The season running now has not ended, so it runs to this moment.
            // The one before ends where this one starts, a millisecond short of
            // it so a job worked on the first morning of the new season is
            // counted once and in the right place.
            val to = when (chosen) {
                SeasonWindow.Now -> now
                SeasonWindow.Last -> thisSeason.startMillis(zone) - 1
            }
            // The season running now has no end yet, so what is asked for has none.
            // Ending it at the moment the screen opened left out everything that
            // arrived after — a pull bringing a driver's day in, say — while the home
            // screen, which does not stop at a moment, counted it.
            val asked = if (chosen == SeasonWindow.Now) Long.MAX_VALUE else to
            val seesPeople = Capability.SeePeopleCost in s.can
            val dao = app.db.orgDao()

            // The estimate and the losses, for somebody who sees what the business
            // pays. Kept in their own pair so the main combine stays within its arity.
            val extras = if (seesPeople) {
                combine(
                    app.db.machineDao().observeMonthlyLoans(s.orgId),
                    dao.observeJobMargins(s.orgId, from, asked),
                ) { monthly, margins ->
                    val lost = margins.mapNotNull { it.lostRupees }
                    Triple(
                        Costing.instalmentsBetween(monthly, from, to),
                        lost.size,
                        lost.sum(),
                    )
                }
            } else {
                kotlinx.coroutines.flow.flowOf(Triple(0L, 0, 0L))
            }

            combine(
                combine(
                dao.observeEarnedBetween(s.orgId, from, asked),
                dao.observeFuelSpendBetween(s.orgId, from, asked),
                dao.observeServiceSpendBetween(s.orgId, from, asked),
                combine(
                    dao.observeJobCostBetween(s.orgId, from, asked),
                    dao.observeLooseWagesBetween(s.orgId, from, asked),
                    dao.observeUnpricedBetween(s.orgId, from, asked),
                ) { onJobs, loose, unpriced -> Triple(onJobs, loose, unpriced) },
                app.ledgerRepository.observeBalances(s.orgId),
            ) { earned, fuel, service, people, balances ->
                SeasonState(
                    loaded = true,
                    window = chosen,
                    fromMillis = from,
                    toMillis = to,
                    seasonName = shown.name,
                    seasonStarts = seasons.starts,
                    canChangeSeasons = Capability.EditBusiness in s.can,
                    earnedRupees = if (s.canSeeMoney) earned else 0,
                    fuelRupees = if (s.canSeeMoney) fuel else 0,
                    serviceRupees = if (s.canSeeMoney) service else 0,
                    // Stripped here rather than hidden in the view, so a figure
                    // this reader may not see never reaches the screen at all.
                    jobPeopleRupees = if (seesPeople) people.first else 0,
                    loosePeopleRupees = if (seesPeople) people.second else 0,
                    unpricedCount = if (seesPeople) people.third else 0,
                    owedByCustomersRupees = if (s.canSeeMoney) {
                        balances.filter { it.balanceRupees > 0 }.sumOf { it.balanceRupees }
                    } else 0,
                    canSeeMoney = s.canSeeMoney,
                    seesPeopleCost = seesPeople,
                )
            }, extras) { base, (loan, lostJobs, lostRupees) ->
                base.copy(loanRupees = loan, lostJobs = lostJobs, lostRupees = lostRupees)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeasonState())
}
