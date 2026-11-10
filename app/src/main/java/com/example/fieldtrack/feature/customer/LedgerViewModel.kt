package com.example.fieldtrack.feature.customer

import com.example.fieldtrack.data.db.entity.MembershipEntity
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One line of the khata as it is shown.
 *
 * [balanceAfterMinor] is the running total as of this line. A contractor arguing a
 * figure with a farmer reads down the column; without it the khata is a list of
 * numbers that happen to add up somewhere off screen.
 */
data class LedgerRow(
    val id: String,
    val kind: LedgerKind,
    val label: String,
    val method: PaymentMethod?,
    val amountMinor: Long,
    val atMillis: Long,
    val balanceAfterMinor: Long,
    val isReversal: Boolean,
    /** The job this line came from, when it came from one. */
    val jobId: String? = null,
    /** Who wrote this line, by name; only filled in where more than one person writes here. */
    val byName: String? = null,
    val byPersonId: String? = null,
    /** The payer's own name for an "Other" way of paying. */
    val methodLabel: String? = null,
) {
    /** True when this line increases what the customer owes. */
    val isDebit: Boolean get() = kind == LedgerKind.Charge || kind == LedgerKind.Opening
}

/** Which face of a customer's record is showing. */
enum class CustomerTab { Khata, Jobs, Payments }

/** One job done for this customer, as the Jobs tab lists it. */
data class CustomerJobRow(
    val id: String,
    val jobId: String?,
    val workType: String,
    val quantity: Double,
    val unit: MeasureUnit,
    val billingMode: BillingMode,
    val machineName: String?,
    val atMillis: Long,
    val amountMinor: Long,
    val outstandingMinor: Long,
) {
    val settled: Boolean get() = outstandingMinor <= 0L
}

/** One payment this customer has made. */
data class CustomerPaymentRow(
    val id: String,
    val method: PaymentMethod?,
    val jobId: String?,
    val atMillis: Long,
    val amountMinor: Long,
    /** The payer's own name for an "Other" way of paying. */
    val methodLabel: String? = null,
)

data class LedgerState(
    /** How many plots this farmer has written down, for the row that opens them. */
    val fieldCount: Int = 0,
    val loaded: Boolean = false,
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String? = null,
    val village: String? = null,
    val businessName: String = "",
    /** Where customers pay this business, for the reminder: bank or mobile-money details and a UPI id. */
    val payDetails: String? = null,
    val upiId: String? = null,
    /** The language this customer reads, so a reminder to them is written in it. */
    val customerLanguage: String? = null,
    val creditLimitMinor: Long? = null,
    val chaseOnDay: Long? = null,
    val balanceMinor: Long = 0,
    /** When the oldest unsettled charge was raised, for the "oldest N days" line. */
    val oldestDebtMillis: Long? = null,
    val rows: List<LedgerRow> = emptyList(),
    val jobs: List<CustomerJobRow> = emptyList(),
    val payments: List<CustomerPaymentRow> = emptyList(),
    /** Headline figures for the Jobs tab. */
    val billedMinor: Long = 0,
    val unpaidJobCount: Int = 0,
    /** Headline figures for the Payments tab. */
    val receivedMinor: Long = 0,
    val lastPaymentMillis: Long? = null,
    /** Whether this person may correct a job behind one of these lines. */
    val canAmend: Boolean = false,
    /** False for a driver the owner did not trust with money. */
    val canSeeMoney: Boolean = true,
    /** False for somebody who may only look: no money to take, no job to start. */
    val canRecord: Boolean = true,
)

/**
 * Turns raw ledger entries into khata lines carrying a running balance.
 *
 * Entries arrive newest first, because that is the order the screen shows them. The
 * balance has to accumulate oldest first, so this walks them backwards and flips the
 * result. Pure, and tested directly — the signs and the ordering here are exactly the
 * kind of thing that looks right and is off by one entry.
 */
fun buildKhata(entries: List<LedgerEntryEntity>): List<LedgerRow> {
    val reversedIds = entries.mapNotNull { it.reversesEntryId }.toSet()
    var running = 0L
    return entries.asReversed().map { entry ->
        running += when (entry.kind) {
            LedgerKind.Charge, LedgerKind.Opening -> entry.amountMinor
            LedgerKind.Payment, LedgerKind.Adjustment -> -entry.amountMinor
        }
        LedgerRow(
            id = entry.id,
            kind = entry.kind,
            label = entry.note.orEmpty(),
            method = entry.method,
            amountMinor = entry.amountMinor,
            atMillis = entry.atMillis,
            balanceAfterMinor = running,
            isReversal = entry.reversesEntryId != null || entry.id in reversedIds,
            jobId = entry.jobId,
            byPersonId = entry.byPersonId,
            methodLabel = entry.methodLabel,
        )
    }.asReversed()
}

/** A charge with money still owed on it, and how much of it is left. */
data class Due(
    val entry: LedgerEntryEntity,
    val outstandingMinor: Long,
)

/**
 * Which charges a customer has not paid for yet.
 *
 * Money arrives on account, not against a job — a farmer handing over ₹5,000 at the
 * gate is not allocating it to anything. So "unpaid" has to be worked out, and the
 * rule is the one every khata has always used: what comes in clears the oldest debt
 * first. A charge can end up half covered, and it stays on this list for the
 * remainder.
 *
 * A correction to a job (an Adjustment carrying its jobId) comes off that job's own
 * charge before any payment is applied, because it was never really owed. An
 * adjustment with no job behind it is a general credit like a payment.
 */
fun outstandingCharges(entries: List<LedgerEntryEntity>): List<Due> {
    val charges = entries
        .filter { it.kind == LedgerKind.Charge || it.kind == LedgerKind.Opening }
        // Tie-broken past the timestamp: two jobs finished in the same millisecond
        // would otherwise settle in whatever order the query happened to return, so
        // the same khata could report different jobs unpaid on two devices.
        .sortedWith(compareBy({ it.atMillis }, { it.createdAt }, { it.id }))

    val perJobCredit = entries
        .filter { it.kind == LedgerKind.Adjustment && it.jobId != null }
        .groupBy { it.jobId }
        .mapValues { (_, lines) -> lines.sumOf { it.amountMinor } }
        .toMutableMap()

    var pool = entries.sumOf {
        when {
            it.kind == LedgerKind.Payment -> it.amountMinor
            it.kind == LedgerKind.Adjustment && it.jobId == null -> it.amountMinor
            else -> 0L
        }
    }

    return charges.mapNotNull { charge ->
        // The job's own corrections first; they reduce what was ever owed.
        val corrected = charge.jobId
            ?.let { id -> perJobCredit.remove(id)?.let { charge.amountMinor - it } }
            ?: charge.amountMinor
        var left = corrected.coerceAtLeast(0L)

        val applied = minOf(pool, left)
        pool -= applied
        left -= applied

        if (left > 0) Due(charge, left) else null
    }
}

/** A charge a payment would settle, and by how much. */
data class Cleared(
    val entry: LedgerEntryEntity,
    val clearedMinor: Long,
    val fully: Boolean,
)

/**
 * What a payment of this size would settle, oldest debt first.
 *
 * Farmers here mostly pay once the crop is sold: one lump sum against a season of
 * jobs, often not the whole amount, and the rest later. Both people want to know
 * which jobs that money just closed — "the levelling and the rotavator, and half the
 * harvesting" — and until now the app worked it out and told nobody.
 *
 * Returns the charges in the order the money reaches them. Anything beyond what is
 * owed is simply not allocated; an overpayment sits on the account as credit.
 */
fun clearedBy(entries: List<LedgerEntryEntity>, paymentMinor: Long): List<Cleared> {
    var left = paymentMinor
    val cleared = mutableListOf<Cleared>()
    for (due in outstandingCharges(entries)) {
        if (left <= 0L) break
        val applied = minOf(left, due.outstandingMinor)
        left -= applied
        cleared += Cleared(
            entry = due.entry,
            clearedMinor = applied,
            fully = applied >= due.outstandingMinor,
        )
    }
    return cleared
}

/**
 * When the oldest debt a customer has still not cleared was raised.
 *
 * Not the oldest charge on the account: someone who has traded for two seasons and
 * owes for one job from last week is five days late, not two years, and the
 * difference decides whether they get a phone call.
 */
fun oldestUnpaidAt(entries: List<LedgerEntryEntity>): Long? =
    outstandingCharges(entries).firstOrNull()?.entry?.atMillis

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val app: FieldFlowApplication,
    private val customerId: String,
) : ViewModel() {


    val state: StateFlow<LedgerState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeCustomer(s.orgId, customerId),
            app.ledgerRepository.observeLedger(s.orgId, customerId),
            app.ledgerRepository.observeBalance(s.orgId, customerId),
            app.db.orgDao().observeOrg(s.orgId),
            app.workRepository.observeForCustomer(s.orgId, customerId),
            app.directoryRepository.observeMachines(s.orgId),
            app.directoryRepository.observeFields(s.orgId, customerId),
            app.teamRepository.observeEveryone(s.orgId),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val customer = values[0] as CustomerEntity?
            @Suppress("UNCHECKED_CAST")
            val entries = values[1] as List<LedgerEntryEntity>
            val balance = values[2] as Long
            val org = values[3] as OrgEntity?
            @Suppress("UNCHECKED_CAST")
            val jobEntities = values[4] as List<JobEntity>
            @Suppress("UNCHECKED_CAST")
            val machines = values[5] as List<MachineEntity>
            @Suppress("UNCHECKED_CAST")
            val fields = values[6] as List<FieldEntity>

            @Suppress("UNCHECKED_CAST")
            val team = values[7] as List<MembershipEntity>
            // Names only where more than one person writes in this business: "by you" on
            // every line of a one-person khata is noise, and "by Gurmeet" is the answer
            // an owner with a crew is looking for when a figure surprises them.
            val names = if (team.count { it.active } > 1) team.associate { it.personId to it.displayName } else emptyMap()
            val rows = buildKhata(entries).map { it.copy(byName = it.byPersonId?.let { id -> names[id] }) }
            val stillOwed = outstandingCharges(entries)
                .associate { it.entry.id to it.outstandingMinor }
            val jobsById = jobEntities.associateBy { it.id }
            val machineNames = machines.associate { it.id to it.name }

            val jobRows = entries
                .filter { it.kind == LedgerKind.Charge || it.kind == LedgerKind.Opening }
                .sortedByDescending { it.atMillis }
                .map { entry ->
                    val job = entry.jobId?.let { jobsById[it] }
                    CustomerJobRow(
                        id = entry.id,
                        jobId = entry.jobId,
                        workType = entry.note.orEmpty(),
                        quantity = job?.quantity ?: 0.0,
                        unit = job?.unit ?: MeasureUnit.Acre,
                        billingMode = job?.billingMode ?: BillingMode.Fixed,
                        machineName = job?.machineId?.let { machineNames[it] },
                        atMillis = entry.atMillis,
                        amountMinor = entry.amountMinor,
                        outstandingMinor = stillOwed[entry.id] ?: 0L,
                    )
                }

            val paymentRows = entries
                .filter { it.kind == LedgerKind.Payment }
                .sortedByDescending { it.atMillis }
                .map {
                    CustomerPaymentRow(
                        id = it.id,
                        method = it.method,
                        jobId = it.jobId,
                        atMillis = it.atMillis,
                        amountMinor = it.amountMinor,
                        methodLabel = it.methodLabel,
                    )
                }

            LedgerState(
                fieldCount = fields.size,
                loaded = true,
                canAmend = Capability.AmendBilledWork in s.can,
                canSeeMoney = s.canSeeMoney,
                canRecord = Capability.RecordWork in s.can,
                customerId = customerId,
                customerName = customer?.name.orEmpty(),
                customerPhone = customer?.phone,
                village = customer?.village,
                businessName = org?.name.orEmpty(),
                payDetails = org?.payDetails,
                upiId = org?.upiId,
                customerLanguage = customer?.language,
                creditLimitMinor = customer?.creditLimitMinor,
                chaseOnDay = customer?.chaseOnDay,
                balanceMinor = balance,
                oldestDebtMillis = oldestUnpaidAt(entries),
                rows = rows,
                jobs = jobRows,
                payments = paymentRows,
                billedMinor = jobRows.sumOf { it.amountMinor },
                unpaidJobCount = jobRows.count { !it.settled },
                receivedMinor = paymentRows.sumOf { it.amountMinor },
                lastPaymentMillis = paymentRows.firstOrNull()?.atMillis,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    /** Remind the owner to chase this customer in [days] days, or not at all with null. */
    fun chaseIn(days: Int?) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val day = days?.let { java.time.LocalDate.now(com.example.fieldtrack.data.region.BusinessClock.zone).toEpochDay() + it }
        app.directoryRepository.setChaseDay(s.orgId, customerId, day)
    }

    /**
     * The receipt for one payment: who paid, how much, how, and what they owe after it.
     *
     * "After it" is read off the same running balance the khata shows, so the receipt
     * and the khata can never give two figures for the same moment.
     */
    suspend fun receiptFor(paymentId: String, methodLabel: String, dateLabel: String): com.example.fieldtrack.document.Document.Receipt? {
        val s = app.sessionStore.load() ?: return null
        val org = app.db.orgDao().observeOrg(s.orgId).first()
        val customer = app.directoryRepository.observeCustomer(s.orgId, customerId).first()
        val entries = app.ledgerRepository.observeLedger(s.orgId, customerId).first()
        val entry = entries.firstOrNull { it.id == paymentId && it.kind == LedgerKind.Payment } ?: return null
        val after = buildKhata(entries).firstOrNull { it.id == paymentId }?.balanceAfterMinor ?: return null
        return com.example.fieldtrack.document.Documents.receipt(org, customer, entry, methodLabel, dateLabel, after)
    }
}
