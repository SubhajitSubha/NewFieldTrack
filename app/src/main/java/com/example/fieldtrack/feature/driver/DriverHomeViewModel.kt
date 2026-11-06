package com.example.fieldtrack.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.feature.plan.PlanSummary
import com.example.fieldtrack.feature.plan.observePlanSummary
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.feature.home.HomeAlert
import com.example.fieldtrack.feature.home.OpenJob
import com.example.fieldtrack.feature.home.dealsWithCustomers
import com.example.fieldtrack.feature.home.lastSevenDays
import com.example.fieldtrack.feature.home.landWorked
import com.example.fieldtrack.feature.home.machineAlerts
import com.example.fieldtrack.feature.home.uploadAlert
import com.example.fieldtrack.data.sync.SyncStage
import com.example.fieldtrack.feature.startOfToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** A job this driver did today. */
data class DriverJobRow(
    val jobId: String,
    val customerName: String,
    val workType: String,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val machineKind: MachineKind,
    val live: Boolean,
    /** Reached the server. Shown only when it has not, so a quiet row means safe. */
    val sent: Boolean = true,
)

data class DriverHomeState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val businessName: String = "",
    /** Seconds on a machine today, across every job. */
    val secondsToday: Long = 0,
    val jobsToday: Int = 0,
    /** Ground covered today, when today's work was measured by area. */
    val areaToday: Double = 0.0,
    val areaUnit: MeasureUnit = MeasureUnit.Acre,
    /** Hours on a machine for each of the last seven days, today last. */
    val weekHours: List<Double> = List(7) { 0.0 },
    val machineName: String? = null,
    val machineId: String? = null,
    val machineKind: MachineKind = MachineKind.Tractor,
    val today: List<DriverJobRow> = emptyList(),
    val openJob: OpenJob? = null,
    val pendingUploads: Int = 0,
    val uploadsStuck: Boolean = false,
    /** Their machine's breakdown or service, and anything still to send. */
    val alerts: List<HomeAlert> = emptyList(),
    /**
     * Cash this person took at farmers' gates today. Null for someone who does
     * not take payments, so the card is absent rather than reading ₹0.
     */
    val cashWithYouRupees: Long? = null,
    val plan: PlanSummary = PlanSummary(),
)

/**
 * A field worker's own screen.
 *
 * The owner's home answers "what did the business make today". This one answers
 * "what am I doing, and what have I done", because those are different questions
 * and a driver given the first is being shown somebody else's business.
 *
 * There are no earnings on it — not hidden behind a capability, simply absent.
 * The one rupee figure a senior driver sees is the cash in their own pocket,
 * because handing that over at the end of the day is their job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverHomeViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private data class Basics(
        val jobs: List<JobEntity>,
        val live: JobEntity?,
        val names: Map<String, String>,
        val machines: List<com.example.fieldtrack.data.db.entity.MachineEntity>,
        val pending: Int,
    )

    private val core: Flow<DriverHomeState> = app.sessionStore.session.flatMapLatest { s ->
        val now = System.currentTimeMillis()
        val dayStart = startOfToday(now)
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, -6)
        }.timeInMillis

        val basics = combine(
            app.workRepository.observeForOperator(s.orgId, s.personId, weekStart, Long.MAX_VALUE),
            app.workRepository.observeLiveFor(s.orgId, s.personId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.db.outboxDao().observePendingCount(),
        ) { jobs, live, customers, machines, pending ->
            Basics(jobs, live, customers.associate { it.id to it.name }, machines, pending)
        }
        val cash = if (Capability.TakePayment in s.can) {
            app.ledgerRepository.observeCashTakenBy(s.orgId, s.personId, dayStart, Long.MAX_VALUE)
        } else {
            flowOf(null)
        }

        combine(
            basics,
            app.db.machineDao().observeOpenBreakdowns(s.orgId),
            cash,
            app.syncEngine.status,
            app.clockConfirmations.changes,
        ) { b, breakdowns, cashTaken, sync, _ ->
            val stuck = sync.stage == SyncStage.Blocked
            val todays = b.jobs.filter { it.workedAt >= dayStart }
            val rows = todays.map { job ->
                DriverJobRow(
                    jobId = job.id,
                    customerName = b.names[job.customerId].orEmpty(),
                    workType = job.workType,
                    amountDone = job.quantity,
                    unit = job.unit,
                    elapsedSeconds = app.workRepository.elapsedSeconds(job),
                    machineKind = b.machines.firstOrNull { it.id == job.machineId }?.kind
                        ?: MachineKind.Other,
                    live = job.status == JobStatus.Running || job.status == JobStatus.Paused,
                    sent = job.syncState == SyncState.Synced,
                )
            }
            // The machine they are on now, or the last one they were on today —
            // which is the one they will reach for next.
            val machineId = (b.live ?: todays.firstOrNull { it.machineId != null })?.machineId
            val machine = b.machines.firstOrNull { it.id == machineId }

            val alerts = buildList {
                // Their own machine only. The yard's other machines are not
                // theirs to fix, and listing them is noise on the wrong phone.
                addAll(machineAlerts(listOfNotNull(machine), breakdowns))
                uploadAlert(b.pending, stuck)?.let(::add)
            }

            DriverHomeState(
                loaded = true,
                displayName = s.displayName,
                businessName = s.businessName,
                secondsToday = rows.sumOf { it.elapsedSeconds },
                jobsToday = rows.count { !it.live },
                // Only area work is added up. Totalling acres with trips and
                // quintals would produce a number that means nothing.
                areaToday = landWorked(todays, s.landUnit).first,
                areaUnit = landWorked(todays, s.landUnit).second,
                weekHours = lastSevenDays(b.jobs, now, { it.workedAt }) {
                    app.workRepository.elapsedSeconds(it) / 3600.0
                },
                machineName = machine?.name,
                machineId = machine?.id,
                machineKind = machine?.kind ?: MachineKind.Tractor,
                today = rows.filterNot { it.live },
                openJob = b.live?.let { job ->
                    val jobMachine = b.machines.firstOrNull { it.id == job.machineId }
                    OpenJob(
                        jobId = job.id,
                        customerName = b.names[job.customerId].orEmpty(),
                        workType = job.workType,
                        machineName = jobMachine?.name,
                        machineKind = jobMachine?.kind ?: MachineKind.Other,
                        amountDone = job.quantity,
                        unit = job.unit,
                        elapsedSeconds = job.elapsedSeconds,
                        runningSince = job.runningSince,
                        paused = job.status == JobStatus.Paused,
                        clockConfirmed = app.clockConfirmations.isConfirmed(job.id, job.runningSince),
                    )
                },
                pendingUploads = b.pending,
                uploadsStuck = stuck,
                alerts = alerts,
                cashWithYouRupees = cashTaken,
            )
        }
    }

    /** The plan in a line: what is on today and tomorrow, and what was missed. */
    val state: StateFlow<DriverHomeState> = combine(
        core,
        app.sessionStore.session.flatMapLatest { app.observePlanSummary(it) },
    ) { st, plan -> st.copy(plan = plan) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverHomeState())

    /** Whether this person's bar carries Customers rather than Machines. */
    val dealsWithCustomers: Boolean
        get() = app.sessionStore.current()?.can?.let(::dealsWithCustomers) ?: false

    fun pause(jobId: String) = act { orgId -> app.workRepository.pause(orgId, jobId) }

    fun resume(jobId: String) = act { orgId -> app.workRepository.resume(orgId, jobId) }

    /**
     * Pause and resume from the home are the same calls the job screen makes. A
     * tap racing another device's change is dropped quietly: the job row will
     * redraw with whatever actually happened, which is the honest answer.
     */
    private fun act(block: suspend (String) -> Unit) {
        val orgId = app.sessionStore.current()?.orgId ?: return
        viewModelScope.launch { runCatching { block(orgId) } }
    }

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}
