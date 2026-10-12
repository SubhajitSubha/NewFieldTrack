package com.example.fieldtrack.feature.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One job a customer could be billed for, as the picker lists it. */
data class PickableJob(
    val entryId: String,
    val jobId: String?,
    val title: String,
    val atMillis: Long,
    val amountRupees: Long,
    val outstandingRupees: Long,
) {
    val settled: Boolean get() = outstandingRupees <= 0L
}

data class StatementState(
    val loaded: Boolean = false,
    val customerName: String = "",
    val period: StatementPeriod = StatementPeriod.FinancialYear,
    val content: StatementContent = StatementContent.UnpaidOnly,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    /** The days the chosen period resolves to, for the line under the chips. */
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    /** True when this account has never been squared, so "since we settled" is empty. */
    val neverSettled: Boolean = true,
    val jobs: List<PickableJob> = emptyList(),
    val pickedEntryIds: Set<String> = emptySet(),
    /** What the chosen options add up to, shown before anything is sent. */
    val lineCount: Int = 0,
    val totalRupees: Long = 0,
    val oldestUnpaidMillis: Long? = null,
    val working: Boolean = false,
) {
    val canSend: Boolean get() = loaded && !working && lineCount > 0

    /** True when the summary is money owed rather than money received. */
    val showsDebt: Boolean
        get() = content != StatementContent.PaymentsOnly
}

/**
 * Choosing what a statement covers, and then making it.
 *
 * The live summary is the point of the screen. A farmer about to send a customer a
 * demand for money wants to know what is on it before it leaves the phone, so every
 * option change recomputes the count and the total rather than waiting for a preview.
 */
class StatementViewModel(
    private val app: FieldFlowApplication,
    private val customerId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(StatementState())
    val state: StateFlow<StatementState> = _state.asStateFlow()

    private var entries: List<LedgerEntryEntity> = emptyList()
    private var jobsById: Map<String, JobEntity> = emptyMap()
    private var org: OrgEntity? = null
    private var customer: CustomerEntity? = null
    private var orgId: String = ""

    init {
        viewModelScope.launch {
            val session = app.sessionStore.load() ?: return@launch
            orgId = session.orgId
            org = app.db.orgDao().observeOrg(session.orgId).first()
            customer = app.directoryRepository.findCustomer(session.orgId, customerId)
            entries = app.ledgerRepository.observeLedger(session.orgId, customerId).first()
            // The ledger note is only the work type. A dues list that says
            // "Rotavator ₹3,500" four times convinces nobody; the measure and the
            // rate are what make a line arguable, and those live on the job.
            jobsById = app.workRepository.observeForCustomer(session.orgId, customerId)
                .first().associateBy { it.id }
            _state.update {
                it.copy(
                    loaded = true,
                    customerName = customer?.name.orEmpty(),
                    neverSettled = lastSettledAt(entries) == null,
                )
            }
            recompute()
        }
    }

    fun setPeriod(period: StatementPeriod) {
        _state.update { it.copy(period = period) }
        recompute()
    }

    fun setCustomRange(startMillis: Long, endMillis: Long) {
        _state.update {
            it.copy(
                period = StatementPeriod.Custom,
                customStartMillis = startMillis,
                customEndMillis = endMillis,
            )
        }
        recompute()
    }

    fun setContent(content: StatementContent) {
        _state.update { current ->
            current.copy(
                content = content,
                // Entering the picker starts from what is unpaid, which is what
                // someone reaching for it is nearly always arguing about.
                pickedEntryIds = if (content == StatementContent.PickedJobs &&
                    current.pickedEntryIds.isEmpty()
                ) {
                    current.jobs.filterNot { it.settled }.map { it.entryId }.toSet()
                } else {
                    current.pickedEntryIds
                },
            )
        }
        recompute()
    }

    fun togglePicked(entryId: String) {
        _state.update {
            it.copy(
                pickedEntryIds = if (entryId in it.pickedEntryIds) {
                    it.pickedEntryIds - entryId
                } else {
                    it.pickedEntryIds + entryId
                },
            )
        }
        recompute()
    }

    fun clearPicked() {
        _state.update { it.copy(pickedEntryIds = emptySet()) }
        recompute()
    }

    /**
     * Works out the days, the lines and the total for whatever is currently chosen.
     *
     * Pure arithmetic over entries already in memory, so it can run on every tap
     * without a round trip to the database.
     */
    private fun recompute() {
        val now = System.currentTimeMillis()
        val current = _state.value
        val start = periodStart(
            period = current.period,
            now = now,
            entries = entries,
            customStart = current.customStartMillis,
        )
        val end = current.customEndMillis.takeIf { current.period == StatementPeriod.Custom }

        val outstanding = outstandingCharges(entries)
            .filter { start == null || it.entry.atMillis >= start }
            .filter { end == null || it.entry.atMillis <= end }

        val jobs = outstandingByEntry()
            .filter { start == null || it.atMillis >= start }
            .filter { end == null || it.atMillis <= end }

        val (count, total) = when (current.content) {
            StatementContent.UnpaidOnly ->
                outstanding.size to outstanding.sumOf { it.outstandingRupees }

            StatementContent.PickedJobs -> {
                val picked = jobs.filter { it.entryId in current.pickedEntryIds }
                picked.size to picked.sumOf { it.amountRupees }
            }

            StatementContent.PaymentsOnly -> {
                val payments = entriesFor(entries, StatementContent.PaymentsOnly, start, end)
                payments.size to payments.sumOf { it.amountRupees }
            }

            StatementContent.Everything -> {
                val shown = entriesFor(entries, StatementContent.Everything, start, end)
                shown.size to (balanceBefore(entries, start) + shown.sumOf {
                    when (it.kind) {
                        LedgerKind.Charge, LedgerKind.Opening -> it.amountRupees
                        else -> -it.amountRupees
                    }
                })
            }
        }

        _state.update {
            it.copy(
                startMillis = start,
                endMillis = end,
                jobs = jobs,
                lineCount = count,
                totalRupees = total,
                oldestUnpaidMillis = outstanding.firstOrNull()?.entry?.atMillis,
            )
        }
    }

    /** Every charge on the account, with how much of it is still owed. */
    private fun outstandingByEntry(): List<PickableJob> {
        val stillOwed = outstandingCharges(entries).associate { it.entry.id to it.outstandingRupees }
        return entries
            .filter { it.kind == LedgerKind.Charge || it.kind == LedgerKind.Opening }
            .sortedByDescending { it.atMillis }
            .map { entry ->
                PickableJob(
                    entryId = entry.id,
                    jobId = entry.jobId,
                    title = entry.note.orEmpty(),
                    atMillis = entry.atMillis,
                    amountRupees = entry.amountRupees,
                    outstandingRupees = stillOwed[entry.id] ?: 0L,
                )
            }
    }

    /** Everything the document builder needs, gathered once. */
    fun sources() = StatementSources(
        org = org,
        customer = customer,
        entries = entries,
        jobsById = jobsById,
        state = _state.value,
    )

    fun setWorking(working: Boolean) = _state.update { it.copy(working = working) }
}

/** What the screen hands to the document builder. Plain data, no view model. */
data class StatementSources(
    val org: OrgEntity?,
    val customer: CustomerEntity?,
    val entries: List<LedgerEntryEntity>,
    /** The jobs behind the charges, for the measure and the rate on each line. */
    val jobsById: Map<String, JobEntity>,
    val state: StatementState,
)
