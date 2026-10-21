package com.example.fieldtrack.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.feature.customer.previousSeasonStart
import com.example.fieldtrack.feature.customer.seasonStart
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
) {
    val peopleRupees: Long get() = jobPeopleRupees + loosePeopleRupees

    val costsRupees: Long get() = fuelRupees + serviceRupees + peopleRupees

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
 * Seasons rather than months because that is the unit the work comes in:
 * kharif from June, rabi from November, and a contractor's book effectively
 * starts again at each. A calendar month cuts a harvest in half.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeasonViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val window = MutableStateFlow(SeasonWindow.Now)

    fun setWindow(value: SeasonWindow) = window.update { value }

    val state: StateFlow<SeasonState> = combine(
        app.sessionStore.session,
        window,
    ) { session, chosen -> session to chosen }
        .flatMapLatest { (s, chosen) ->
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val from = when (chosen) {
                SeasonWindow.Now -> seasonStart(now, zone)
                SeasonWindow.Last -> previousSeasonStart(now, zone)
            }.atStartOfDay(zone).toInstant().toEpochMilli()
            // The season running now has not ended, so it runs to this moment.
            // The one before ends where this one starts, a millisecond short of
            // it so a job worked on the first morning of the new season is
            // counted once and in the right place.
            val to = when (chosen) {
                SeasonWindow.Now -> now
                SeasonWindow.Last -> seasonStart(now, zone)
                    .atStartOfDay(zone).toInstant().toEpochMilli() - 1
            }
            val seesPeople = Capability.SeePeopleCost in s.can
            val dao = app.db.orgDao()

            combine(
                dao.observeEarnedBetween(s.orgId, from, to),
                dao.observeFuelSpendBetween(s.orgId, from, to),
                dao.observeServiceSpendBetween(s.orgId, from, to),
                combine(
                    dao.observeJobCostBetween(s.orgId, from, to),
                    dao.observeLooseWagesBetween(s.orgId, from, to),
                    dao.observeUnpricedBetween(s.orgId, from, to),
                ) { onJobs, loose, unpriced -> Triple(onJobs, loose, unpriced) },
                app.ledgerRepository.observeBalances(s.orgId),
            ) { earned, fuel, service, people, balances ->
                SeasonState(
                    loaded = true,
                    window = chosen,
                    fromMillis = from,
                    toMillis = to,
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
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeasonState())
}
