package com.example.fieldtrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.dao.CustomerBalance
import com.example.fieldtrack.data.db.dao.JobMargin
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.session.Session
import com.example.fieldtrack.feature.startOfToday
import com.example.fieldtrack.data.sync.SyncStage
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
import java.util.Calendar

/** One finished job as the home list shows it. */
data class TodayRow(
    val jobId: String,
    val customerName: String,
    val workType: String,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val amountRupees: Long,
    val machineKind: MachineKind,
    /** Who did it, on a home that watches more than one person. */
    val byName: String? = null,
    /** Reached the server. Shown only when it has not. */
    val sent: Boolean = true,
    /** How much this job lost, when it did and every cost is priced. */
    val lostRupees: Long? = null,
)

data class HomeState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val businessName: String = "",
    /** Whether this person may correct a job after it has been billed. */
    val canAmend: Boolean = false,
    val canSeeMoney: Boolean = true,
    val earnedTodayRupees: Long = 0,
    val jobsToday: Int = 0,
    val areaToday: Double = 0.0,
    val areaUnit: MeasureUnit = MeasureUnit.Acre,
    /** Earned on each of the last seven days, today last. */
    val weekEarned: List<Double> = List(7) { 0.0 },
    val owedRupees: Long = 0,
    val owingCustomers: Int = 0,
    val seasonEarnedRupees: Long = 0,
    val seasonFromMillis: Long = 0,
    /** What the business calls this season, if it has named them. */
    val seasonName: String? = null,
    val today: List<TodayRow> = emptyList(),
    /**
     * The job to go back to, running or paused.
     *
     * A paused job counts. A driver whose machine stopped pauses and walks away from
     * the screen; if the way back disappeared with it, the job would be stranded —
     * there is no jobs list on a solo owner's phone to find it in.
     */
    val openJob: OpenJob? = null,
    val alerts: List<HomeAlert> = emptyList(),
    val pendingUploads: Int = 0,
    val uploadsStuck: Boolean = false,
)

/** Everything an owner's home reads, gathered so the combines stay under five. */
internal data class Books(
    val customers: List<CustomerEntity>,
    val balances: List<CustomerBalance>,
    val entries: List<LedgerEntryEntity>,
    val seasonEarned: Long,
    /** The week's finished jobs and what is known to have gone into each. */
    val margins: List<JobMargin> = emptyList(),
) {
    /** The jobs that lost money, and by how much — only where every cost is priced. */
    val lostByJob: Map<String, Long>
        get() = margins.mapNotNull { m -> m.lostRupees?.let { m.jobId to it } }.toMap()
}

internal data class Yard(
    val machines: List<MachineEntity>,
    val breakdowns: List<BreakdownEntity>,
    val unpriced: Int,
    val pending: Int,
    /** The server refused a change and the queue is held up behind it. */
    val stuck: Boolean,
    /** Machines drinking clearly more diesel an hour than they were. */
    val fuelRises: List<HomeAlert.FuelHigh> = emptyList(),
)

/** Shared by the solo and yard homes: the money side of the business. */
internal fun FieldFlowApplication.observeBooks(s: Session, seasonFrom: Long): Flow<Books> =
    if (Capability.SeeMoney !in s.can) {
        combine(directoryRepository.observeCustomers(s.orgId), flowOf(Unit)) { customers, _ ->
            Books(customers, emptyList(), emptyList(), 0)
        }
    } else {
        combine(
            directoryRepository.observeCustomers(s.orgId),
            ledgerRepository.observeBalances(s.orgId),
            ledgerRepository.observeAllEntries(s.orgId),
            db.orgDao().observeEarnedBetween(s.orgId, seasonFrom, Long.MAX_VALUE),
            // Losses are said only to somebody who sees what the business pays: a
            // loss means counting the crew, and a loss worked out without them
            // would be a different number from the one on the job's own page.
            if (Capability.SeePeopleCost in s.can) {
                db.orgDao().observeJobMargins(s.orgId, sevenDaysFrom(System.currentTimeMillis()), Long.MAX_VALUE)
            } else {
                flowOf(emptyList())
            },
        ) { customers, balances, entries, season, margins ->
            Books(customers, balances, entries, season, margins)
        }
    }

/** Shared by the solo and yard homes: machines and the loose ends. */
internal fun FieldFlowApplication.observeYard(s: Session): Flow<Yard> = combine(
    combine(
        directoryRepository.observeMachines(s.orgId),
        db.machineDao().observeOpenBreakdowns(s.orgId),
        ::Pair,
    ),
    if (Capability.SeePeopleCost in s.can) db.orgDao().observeUnpricedJobCostCount(s.orgId)
    else flowOf(0),
    db.outboxDao().observePendingCount(),
    syncEngine.status,
    // This week's diesel per hour against the four weeks before it, for each
    // machine. The edge between the two is a whole day, so a job is in exactly one.
    run {
        val now = System.currentTimeMillis()
        val week = 7L * 24 * 60 * 60 * 1000
        combine(
            db.machineDao().observeFuelOnJobs(s.orgId, now - week, Long.MAX_VALUE),
            db.machineDao().observeFuelOnJobs(s.orgId, now - 5 * week, now - week),
            ::Pair,
        )
    },
) { (machines, breakdowns), unpriced, pending, status, (recent, before) ->
    val recentBy = recent.associateBy { it.machineId }
    val beforeBy = before.associateBy { it.machineId }
    Yard(
        machines, breakdowns, unpriced, pending,
        stuck = status.stage == SyncStage.Blocked,
        fuelRises = machines.mapNotNull { machine ->
            Costing.fuelRise(recentBy[machine.id], beforeBy[machine.id])?.let { rise ->
                HomeAlert.FuelHigh(
                    machineId = machine.id,
                    machineName = machine.name,
                    recentLitresPerHour = rise.recentLitresPerHour,
                    beforeLitresPerHour = rise.beforeLitresPerHour,
                    percent = rise.percent,
                )
            }
        },
    )
}

/** Every alert an owner's home raises, from what the two flows above carry. */
internal fun ownerAlerts(books: Books, yard: Yard, canSeeMoney: Boolean, now: Long): List<HomeAlert> =
    buildList {
        addAll(machineAlerts(yard.machines, yard.breakdowns))
        // A machine already broken down has its own, louder row; two for one
        // machine would be the same problem twice.
        val brokenDown = yard.breakdowns.map { it.machineId }.toSet()
        addAll(yard.fuelRises.filter { it.machineId !in brokenDown })
        if (canSeeMoney) {
            overdueAlert(
                debtsByAge(books.entries),
                books.customers.associate { it.id to it.name },
                now,
            )?.let(::add)
        }
        if (yard.unpriced > 0) add(HomeAlert.Unpriced(yard.unpriced))
        uploadAlert(yard.pending, yard.stuck)?.let(::add)
    }

/** Midnight six days ago: the first of the seven days a home charts. */
internal fun sevenDaysFrom(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfToday(now)
    add(Calendar.DAY_OF_YEAR, -6)
}.timeInMillis

internal fun JobEntity.toOpenJob(
    customerName: String,
    machine: MachineEntity?,
    clockConfirmed: Boolean,
) = OpenJob(
    jobId = id,
    customerName = customerName,
    workType = workType,
    machineName = machine?.name,
    machineKind = machine?.kind ?: MachineKind.Other,
    amountDone = quantity,
    unit = unit,
    elapsedSeconds = elapsedSeconds,
    runningSince = runningSince,
    paused = status == JobStatus.Paused,
    clockConfirmed = clockConfirmed,
)

/**
 * A one-person business's home.
 *
 * The same person drives, bills and collects, so the screen holds all three:
 * what today made, the job in hand or the button to start one, and who still
 * owes. Nothing about a crew, because there is none.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<HomeState> = app.seasonedSession.flatMapLatest { (s, seasons) ->
        val now = System.currentTimeMillis()
        val dayStart = startOfToday(now)
        val zone = ZoneId.systemDefault()
        val season = seasons.current(now, zone)
        val seasonFrom = season.startMillis(zone)

        combine(
            app.workRepository.observeCompletedToday(s.orgId, sevenDaysFrom(now)),
            app.workRepository.observeLiveJob(s.orgId, s.personId),
            app.observeBooks(s, seasonFrom),
            app.observeYard(s),
            app.clockConfirmations.changes,
        ) { week, live, books, yard, _ ->
            val names = books.customers.associate { it.id to it.name }
            val kindById = yard.machines.associate { it.id to it.kind }
            val jobs = week.filter { it.workedAt >= dayStart }
            val owed = books.balances.filter { it.balanceRupees > 0 }

            HomeState(
                loaded = true,
                displayName = s.displayName,
                businessName = s.businessName,
                canAmend = Capability.AmendBilledWork in s.can,
                canSeeMoney = s.canSeeMoney,
                earnedTodayRupees = jobs.sumOf { it.amountRupees },
                jobsToday = jobs.size,
                areaToday = jobs.filter { it.unit.isLand }.sumOf { it.quantity },
                areaUnit = jobs.firstOrNull { it.unit.isLand }?.unit ?: s.landUnit,
                weekEarned = lastSevenDays(week, now, { it.workedAt }) { it.amountRupees.toDouble() },
                owedRupees = owed.sumOf { it.balanceRupees },
                owingCustomers = owed.size,
                seasonEarnedRupees = books.seasonEarned,
                seasonFromMillis = seasonFrom,
                seasonName = season.name,
                today = jobs.map { job ->
                    TodayRow(
                        jobId = job.id,
                        customerName = names[job.customerId].orEmpty(),
                        workType = job.workType,
                        amountDone = job.quantity,
                        unit = job.unit,
                        elapsedSeconds = job.elapsedSeconds,
                        amountRupees = job.amountRupees,
                        machineKind = job.machineId?.let { kindById[it] } ?: MachineKind.Other,
                        sent = job.syncState == SyncState.Synced,
                        lostRupees = books.lostByJob[job.id],
                    )
                },
                openJob = live?.toOpenJob(
                    names[live.customerId].orEmpty(),
                    yard.machines.firstOrNull { it.id == live.machineId },
                    app.clockConfirmations.isConfirmed(live.id, live.runningSince),
                ),
                alerts = ownerAlerts(books, yard, s.canSeeMoney, now),
                pendingUploads = yard.pending,
                uploadsStuck = yard.stuck,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

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
