package com.example.fieldtrack.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.formatBillNumber
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.dao.JobCostWithPaid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One thing that happened on a job, ready to be drawn as a timeline entry. */
data class JobEventRow(
    val id: String,
    val type: JobEventType,
    val atMillis: Long,
    val byName: String?,
    val numericValue: Double?,
    val textValue: String?,
    /** How long a pause lasted, worked out from when work carried on. */
    val pausedSeconds: Long? = null,
)

data class JobDetailState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val customerId: String = "",
    val customerName: String = "",
    val fieldLabel: String? = null,
    val workType: String = "",
    val status: JobStatus = JobStatus.Running,
    /** False while the job's latest version is still only on this phone. */
    val sent: Boolean = true,
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val quantity: Double = 0.0,
    val rateMinor: Long = 0,
    val amountMinor: Long = 0,
    /** The bill line by line, so a job with a minimum, an arrival charge or a discount says where its figure comes from. */
    val quote: com.example.fieldtrack.data.billing.Quote? = null,
    val elapsedSeconds: Long = 0,
    val machineName: String? = null,
    val operatorName: String? = null,
    val operatorPhone: String? = null,
    val billNumber: String? = null,
    val workedAtMillis: Long = 0,
    /** True when the trips on this job were not all charged at the same rate. */
    val ratesVaried: Boolean = false,
    val events: List<JobEventRow> = emptyList(),
    val canAmend: Boolean = false,
    val canSeeMoney: Boolean = true,
    /** What this job took beyond the machine, each with what has been paid. */
    val costs: List<JobCostWithPaid> = emptyList(),
    val fuelMinor: Long = 0,
    val fuelLitres: Double = 0.0,
    /** False when this person may not see what people are paid. */
    val seesPeopleCost: Boolean = false,
    val canRecordCost: Boolean = false,
    val canPriceCost: Boolean = false,
    /**
     * This job's share of the machine's own repairs and loan, for the hours it ran.
     * Null where there is nothing honest to share yet, or this reader may not see
     * what the business pays. An estimate, kept apart from the figures above it.
     */
    val machineShareMinor: Long? = null,
) {
    val live: Boolean get() = status == JobStatus.Running || status == JobStatus.Paused
    val finished: Boolean get() = status == JobStatus.Completed

    /** Hands on this job, counted. Null where nobody counted is folded away. */
    val labourHeads: Int?
        get() = costs
            .filter { it.cost.kind == JobCostKind.Labour }
            .mapNotNull { it.cost.headcount }
            .takeIf { it.isNotEmpty() }
            ?.sum()

    /** True when somebody worked but nobody said how many. */
    val labourUncounted: Boolean
        get() = costs.any { it.cost.kind == JobCostKind.Labour && it.cost.headcount == null }

    val labourMinor: Long
        get() = costs.filter { it.cost.kind == JobCostKind.Labour }
            .sumOf { it.cost.amountMinor ?: 0 }

    val otherMinor: Long
        get() = costs.filter { it.cost.kind == JobCostKind.Other }
            .sumOf { it.cost.amountMinor ?: 0 }

    /** What is still owed on this job. Shown beside the total, never taken off it. */
    val owedMinor: Long get() = costs.sumOf { it.owedMinor ?: 0 }

    /** Recorded by somebody who could not put a figure on it. */
    val unpricedCount: Int get() = costs.count { it.cost.amountMinor == null }

    /**
     * Whether a total can honestly be shown.
     *
     * Every component has to be visible to this reader and every cost priced.
     * A total that silently omits a wage reads as profit, and a total that
     * omits a cost nobody has priced reads as a finished answer when it is
     * half a question.
     */
    val canTotal: Boolean
        get() = canSeeMoney && seesPeopleCost && unpricedCount == 0

    val costsMinor: Long get() = fuelMinor + labourMinor + otherMinor
    val leftMinor: Long get() = amountMinor - costsMinor

    /** What was left once the machine's share is counted too. */
    val leftAfterMachineMinor: Long? get() = machineShareMinor?.let { leftMinor - it }

    /** How much this job lost, when it did and every cost is priced. */
    val lostMinor: Long? get() = if (canTotal && leftMinor < 0) -leftMinor else null
}

/**
 * Everything that happened on one job.
 *
 * The job row says what was billed; the event trail says how it got there — when it
 * started, who paused it for lunch, when the machine broke, what the rate was
 * changed to and why. All of it has been recorded since the work loop was built and
 * none of it has ever been shown to anybody.
 */
/** The four extra flows, bundled so the outer combine stays within its arity. */
private data class Extras(
    val machines: List<com.example.fieldtrack.data.db.entity.MachineEntity>,
    val costs: List<JobCostWithPaid>,
    val fuelMinor: Long,
    val fuelLitres: Double,
    val running: Map<String, com.example.fieldtrack.data.costing.Costing.RunningCost>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JobDetailViewModel(
    private val app: FieldFlowApplication,
    private val jobId: String,
) : ViewModel() {

    val state: StateFlow<JobDetailState> = app.sessionStore.session.flatMapLatest { s ->
        val seesPeople = Capability.SeePeopleCost in s.can
        combine(
            app.workRepository.observeJob(s.orgId, jobId),
            app.workRepository.observeEvents(jobId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.teamRepository.observeEveryone(s.orgId),
            combine(
                app.directoryRepository.observeMachines(s.orgId),
                app.ledgerRepository.observeJobCosts(s.orgId, jobId),
                app.db.machineDao().observeFuelSpendOnJob(s.orgId, jobId),
                app.db.machineDao().observeFuelLitresOnJob(s.orgId, jobId),
                app.directoryRepository.observeRunningCosts(s.orgId),
            ) { machines, costs, fuelMinor, fuelLitres, running ->
                Extras(machines, costs, fuelMinor, fuelLitres, running)
            },
        ) { job, events, customers, team, extra ->
            val machines = extra.machines
            if (job == null) return@combine JobDetailState(loaded = true, exists = false)
            val names = team.associate { it.personId to it.displayName }
            val operator = team.firstOrNull { it.personId == job.operatorPersonId }

            JobDetailState(
                loaded = true,
                exists = true,
                customerId = job.customerId,
                customerName = customers.firstOrNull { it.id == job.customerId }?.name.orEmpty(),
                fieldLabel = customers.firstOrNull { it.id == job.customerId }?.village,
                workType = job.workType,
                status = job.status,
                sent = job.syncState == com.example.fieldtrack.data.db.SyncState.Synced,
                billingMode = job.billingMode,
                unit = job.unit,
                quantity = app.workRepository.billableQuantity(job),
                rateMinor = job.rateMinor,
                amountMinor = app.workRepository.billableMinor(job),
                elapsedSeconds = app.workRepository.elapsedSeconds(job),
                machineName = machines.firstOrNull { it.id == job.machineId }?.name,
                operatorName = operator?.displayName,
                operatorPhone = operator?.phone,
                billNumber = job.billFy?.let { fy ->
                    job.billSeq?.let { seq -> formatBillNumber(fy, seq) }
                },
                workedAtMillis = job.workedAt,
                ratesVaried = job.billingMode == BillingMode.PerTrip &&
                    job.workAmountMinor != Math.round(job.quantity * job.rateMinor),
                quote = app.workRepository.quote(job.orgId, job.id),
                events = timelineOf(events, names),
                canAmend = Capability.AmendBilledWork in s.can,
                canSeeMoney = s.canSeeMoney,
                // The rupees on a cost are stripped out here rather than hidden
                // in the view, so a figure this reader may not see never
                // reaches the screen at all.
                costs = if (seesPeople) extra.costs else extra.costs.map { row ->
                    row.copy(cost = row.cost.copy(amountMinor = null), paidMinor = 0)
                },
                machineShareMinor = if (seesPeople && s.canSeeMoney && job.status == JobStatus.Completed) {
                    job.machineId?.let { id ->
                        extra.running[id]?.let { Costing.machineShare(it, job.elapsedSeconds) }
                    }
                } else null,
                fuelMinor = if (s.canSeeMoney) extra.fuelMinor else 0,
                fuelLitres = extra.fuelLitres,
                seesPeopleCost = seesPeople,
                canRecordCost = Capability.RecordWork in s.can,
                canPriceCost = Capability.PayOut in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobDetailState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    /**
     * The events in the order they happened, with each pause measured.
     *
     * A pause on its own says nothing useful — "paused at 12:40" leaves the reader
     * counting. Pairing it with the moment work carried on turns it into "35
     * minutes", which is the thing being asked about when a day's hours are queried.
     */
    private fun timelineOf(
        events: List<com.example.fieldtrack.data.db.entity.JobEventEntity>,
        names: Map<String, String>,
    ): List<JobEventRow> {
        val ordered = events.sortedBy { it.atMillis }
        return ordered.mapIndexed { index, event ->
            val resumedAt = if (event.type == JobEventType.Paused) {
                ordered.drop(index + 1)
                    .firstOrNull { it.type == JobEventType.Resumed }?.atMillis
            } else null
            JobEventRow(
                id = event.id,
                type = event.type,
                atMillis = event.atMillis,
                byName = event.byPersonId?.let { names[it] },
                numericValue = event.numericValue,
                // A hand-over records the person's id; the reader wants their name.
                textValue = if (event.type == JobEventType.HandedOver) {
                    event.textValue?.let { names[it] } ?: event.textValue
                } else {
                    event.textValue
                },
                pausedSeconds = resumedAt?.let { (it - event.atMillis) / 1000 },
            )
        }
        // Oldest first. This is a story about a day's work, and it is read from the
        // moment the engine started, not backwards from the bill.
    }
}
