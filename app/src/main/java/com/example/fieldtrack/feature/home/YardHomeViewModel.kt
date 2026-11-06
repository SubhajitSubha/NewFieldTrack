package com.example.fieldtrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.feature.plan.PlanSummary
import com.example.fieldtrack.feature.plan.observePlanSummary
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.feature.startOfToday
import com.example.fieldtrack.data.repository.ClockCheck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Somebody out on a job right now. */
data class CrewRow(
    val jobId: String,
    val personName: String,
    val customerName: String,
    val workType: String,
    val machineName: String?,
    val machineKind: MachineKind,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val runningSince: Long?,
    val paused: Boolean,
    /** Somebody on this phone already said this long stretch is real work. */
    val clockConfirmed: Boolean = false,
)

data class YardHomeState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val businessName: String = "",
    val canSeeMoney: Boolean = false,
    val canAmend: Boolean = false,
    /** Everybody else's open jobs. This person's own is [ownJob]. */
    val crew: List<CrewRow> = emptyList(),
    val machinesTotal: Int = 0,
    val machinesWorking: Int = 0,
    val machinesDown: Int = 0,
    val earnedTodayRupees: Long = 0,
    val collectedTodayRupees: Long = 0,
    val jobsToday: Int = 0,
    val areaToday: Double = 0.0,
    val areaUnit: MeasureUnit = MeasureUnit.Acre,
    /** Rupees earned per day when money may be seen; machine hours per day when not. */
    val week: List<Double> = List(7) { 0.0 },
    val owedRupees: Long = 0,
    val owingCustomers: Int = 0,
    val seasonEarnedRupees: Long = 0,
    val seasonFromMillis: Long = 0,
    /** What the business calls this season, if it has named them. */
    val seasonName: String? = null,
    val doneToday: List<TodayRow> = emptyList(),
    val ownJob: OpenJob? = null,
    val alerts: List<HomeAlert> = emptyList(),
    val pendingUploads: Int = 0,
    val uploadsStuck: Boolean = false,
    val plan: PlanSummary = PlanSummary(),
)

/**
 * The home of somebody watching other people work — an owner with drivers, or
 * the manager they put in charge.
 *
 * Its question is "where is everyone, and is anything wrong". So the deep card
 * is the yard right now: how many are out, how many machines are turning, and
 * what the day has made. Below it the crew, by name, with their clocks running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YardHomeViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private data class Work(
        val week: List<JobEntity>,
        val live: List<JobEntity>,
        val team: List<MembershipEntity>,
    )

    private val core: Flow<YardHomeState> = app.seasonedSession.flatMapLatest { (s, seasons) ->
        val now = System.currentTimeMillis()
        val dayStart = startOfToday(now)
        val zone = ZoneId.systemDefault()
        val season = seasons.current(now, zone)
        val seasonFrom = season.startMillis(zone)

        val work = combine(
            app.workRepository.observeCompletedToday(s.orgId, sevenDaysFrom(now)),
            app.workRepository.observeLive(s.orgId),
            app.db.orgDao().observeTeam(s.orgId),
        ) { week, live, team -> Work(week, live, team) }

        val collected = if (s.canSeeMoney) {
            app.ledgerRepository.observeCollectedBetween(s.orgId, dayStart, Long.MAX_VALUE)
        } else {
            flowOf(0L)
        }

        combine(
            work,
            app.observeBooks(s, seasonFrom),
            app.observeYard(s),
            collected,
            app.clockConfirmations.changes,
        ) { w, books, yard, collectedToday, _ ->
            val customers = books.customers.associate { it.id to it.name }
            val people = w.team.associate { it.personId to it.displayName }
            val machines = yard.machines.associateBy { it.id }
            val today = w.week.filter { it.workedAt >= dayStart }
            val owed = books.balances.filter { it.balanceRupees > 0 }
            val mine = w.live.firstOrNull { it.operatorPersonId == s.personId }

            YardHomeState(
                loaded = true,
                displayName = s.displayName,
                businessName = s.businessName,
                canSeeMoney = s.canSeeMoney,
                canAmend = Capability.AmendBilledWork in s.can,
                crew = w.live
                    .filter { it.id != mine?.id }
                    // Running before paused, then longest out first: the one who has
                    // been at it since six is the one to ask about.
                    .sortedWith(compareBy({ it.status == JobStatus.Paused }, { it.startedAt ?: it.workedAt }))
                    .map { job ->
                        val machine = machines[job.machineId]
                        CrewRow(
                            jobId = job.id,
                            personName = people[job.operatorPersonId].orEmpty(),
                            customerName = customers[job.customerId].orEmpty(),
                            workType = job.workType,
                            machineName = machine?.name,
                            machineKind = machine?.kind ?: MachineKind.Other,
                            amountDone = job.quantity,
                            unit = job.unit,
                            elapsedSeconds = job.elapsedSeconds,
                            runningSince = job.runningSince,
                            paused = job.status == JobStatus.Paused,
                            clockConfirmed = app.clockConfirmations.isConfirmed(job.id, job.runningSince),
                        )
                    },
                machinesTotal = yard.machines.size,
                machinesWorking = yard.machines.count { it.status == MachineStatus.Working },
                machinesDown = yard.machines.count { it.status == MachineStatus.Down },
                earnedTodayRupees = if (s.canSeeMoney) today.sumOf { it.amountRupees } else 0,
                collectedTodayRupees = collectedToday,
                jobsToday = today.size,
                areaToday = landWorked(today, s.landUnit).first,
                areaUnit = landWorked(today, s.landUnit).second,
                week = if (s.canSeeMoney) {
                    lastSevenDays(w.week, now, { it.workedAt }) { it.amountRupees.toDouble() }
                } else {
                    lastSevenDays(w.week, now, { it.workedAt }) { it.elapsedSeconds / 3600.0 }
                },
                owedRupees = owed.sumOf { it.balanceRupees },
                owingCustomers = owed.size,
                seasonEarnedRupees = books.seasonEarned,
                seasonFromMillis = seasonFrom,
                seasonName = season.name,
                doneToday = today.map { job ->
                    TodayRow(
                        jobId = job.id,
                        customerName = customers[job.customerId].orEmpty(),
                        workType = job.workType,
                        amountDone = job.quantity,
                        unit = job.unit,
                        elapsedSeconds = job.elapsedSeconds,
                        amountRupees = job.amountRupees,
                        machineKind = machines[job.machineId]?.kind ?: MachineKind.Other,
                        byName = people[job.operatorPersonId],
                        sent = job.syncState == com.example.fieldtrack.data.db.SyncState.Synced,
                        lostRupees = books.lostByJob[job.id],
                    )
                },
                ownJob = mine?.toOpenJob(
                    customers[mine.customerId].orEmpty(),
                    machines[mine.machineId],
                    app.clockConfirmations.isConfirmed(mine.id, mine.runningSince),
                ),
                alerts = ownerAlerts(books, yard, s.canSeeMoney, now) +
                    // Somebody else's clock left on. Theirs to answer if they open
                    // the app; the owner's to catch if they do not.
                    w.live
                        .filter { it.id != mine?.id && it.status == JobStatus.Running }
                        .filter { ClockCheck.looksForgotten(it.runningSince, now) }
                        // Answered already on this phone — the job screen will not
                        // ask again, so the home must not keep raising it either.
                        .filterNot { app.clockConfirmations.isConfirmed(it.id, it.runningSince) }
                        .map { job ->
                            HomeAlert.ClockLeftOn(
                                jobId = job.id,
                                personName = people[job.operatorPersonId].orEmpty(),
                                customerName = customers[job.customerId].orEmpty(),
                                sinceMillis = job.runningSince ?: now,
                            )
                        },
                pendingUploads = yard.pending,
                uploadsStuck = yard.stuck,
            )
        }
    }

    /** The plan in a line: what is on today and tomorrow, and what was missed. */
    val state: StateFlow<YardHomeState> = combine(
        core,
        app.sessionStore.session.flatMapLatest { app.observePlanSummary(it) },
    ) { st, plan -> st.copy(plan = plan) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YardHomeState())

    fun pause(jobId: String) = act { orgId -> app.workRepository.pause(orgId, jobId) }

    fun resume(jobId: String) = act { orgId -> app.workRepository.resume(orgId, jobId) }

    /** A tap racing another device's change is dropped; the row redraws with what happened. */
    private fun act(block: suspend (String) -> Unit) {
        val orgId = app.sessionStore.current()?.orgId ?: return
        viewModelScope.launch { runCatching { block(orgId) } }
    }

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}
