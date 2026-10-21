package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.formatBillNumber
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.session.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How much of the bill the customer is settling right now. */
enum class PaymentChoice { All, Some, Nothing }

data class FinishState(
    val loaded: Boolean = false,
    val customerName: String = "",
    val customerPhone: String? = null,
    val customerVillage: String? = null,
    val machineName: String? = null,
    val businessName: String = "",
    /** Letterhead, for the receipt heading. Blank lines are simply not drawn. */
    val businessPhone: String? = null,
    val businessAddress: String? = null,
    val businessUpiId: String? = null,
    val businessGstin: String? = null,
    /** "2026-27/0241". Null until the job is billed. */
    val billNumber: String? = null,
    val billedAtMillis: Long? = null,
    val workType: String = "",
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val quantity: Double = 0.0,
    val rateRupees: Long = 0,
    val elapsedSeconds: Long = 0,
    /** What this job comes to on its own. */
    val jobAmountRupees: Long = 0,
    /**
     * True when the trips on this job were not all charged the same.
     *
     * A single "rate" is then a lie: the bill has to say the trips varied rather
     * than name a figure none of them was.
     */
    val ratesVaried: Boolean = false,
    /** What the customer owed before this job. */
    val previousBalanceRupees: Long = 0,
) {
    val totalDueRupees: Long get() = jobAmountRupees + previousBalanceRupees
}

/**
 * Who else was on the job, asked while everyone is still standing in the field.
 *
 * Headcount only, for everybody, owner included. The alternative — asking the
 * owner for a figure here and the driver only for a number — is two different
 * finish flows depending on who you are, and the amount belongs where it can
 * be thought about rather than at the end of a nine-hour day. The job sheet
 * says out loud that nobody has priced it yet.
 */
data class FinishLabour(
    /** Off to begin with. A pre-ticked box invents labourers nobody hired. */
    val any: Boolean = false,
    /** Null once somebody says nobody counted. */
    val headcount: Int? = DEFAULT_HEADS,
) {
    companion object {
        const val DEFAULT_HEADS = 2
    }
}

/** What the user has chosen on the payment step. */
data class PaymentState(
    val choice: PaymentChoice = PaymentChoice.Nothing,
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.Cash,
    val saving: Boolean = false,
) {
    val amountRupees: Long get() = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
}

@OptIn(ExperimentalCoroutinesApi::class)
class FinishViewModel(
    private val app: FieldFlowApplication,
    private val jobId: String,
) : ViewModel() {


    private val _payment = MutableStateFlow(PaymentState())
    val payment: StateFlow<PaymentState> = _payment.asStateFlow()

    private val _labour = MutableStateFlow(FinishLabour())
    val labour: StateFlow<FinishLabour> = _labour.asStateFlow()

    /** Step 0 review, 1 bill, 2 saved. One screen, three stages. */
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _receivedRupees = MutableStateFlow(0L)
    val receivedRupees: StateFlow<Long> = _receivedRupees.asStateFlow()

    /**
     * What the customer owed *before* this job, captured once when the screen opens.
     *
     * Deliberately a snapshot rather than a live read. Completing the job appends its
     * charge to the ledger, so a live balance would start including this job's own
     * amount and the bill would show it twice — once as the job and again as an old
     * balance. The number only has meaning as of the moment before we bill.
     */
    private val priorBalance = MutableStateFlow<Long?>(null)

    /** The customer's real balance once the charge and any payment have landed. */
    private val _finalBalance = MutableStateFlow<Long?>(null)
    val finalBalance: StateFlow<Long?> = _finalBalance.asStateFlow()

    val state: StateFlow<FinishState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.workRepository.observeJob(s.orgId, jobId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.db.orgDao().observeOrg(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            priorBalance,
        ) { job, customers, org, machines, prior ->
            if (job == null) return@combine FinishState()
            val customer = customers.firstOrNull { it.id == job.customerId }
            val elapsed = app.workRepository.elapsedSeconds(job)
            val quantity = if (job.billingMode == BillingMode.PerHour) elapsed / 3600.0
            else job.quantity
            val amount = if (job.billingMode == BillingMode.PerHour) {
                Math.round(quantity * job.rateRupees)
            } else {
                job.amountRupees
            }
            val varied = job.billingMode == BillingMode.PerTrip &&
                amount != Math.round(quantity * job.rateRupees)

            FinishState(
                loaded = true,
                customerName = customer?.name.orEmpty(),
                customerPhone = customer?.phone,
                customerVillage = customer?.village,
                machineName = machines.firstOrNull { it.id == job.machineId }?.name,
                businessName = org?.name.orEmpty(),
                businessPhone = org?.phone,
                businessAddress = org?.addressLine,
                businessUpiId = org?.upiId,
                businessGstin = org?.gstin,
                billNumber = job.billFy?.let { fy ->
                    job.billSeq?.let { seq -> formatBillNumber(fy, seq) }
                },
                billedAtMillis = job.completedAt,
                workType = job.workType,
                billingMode = job.billingMode,
                unit = job.unit,
                quantity = quantity,
                rateRupees = job.rateRupees,
                elapsedSeconds = elapsed,
                jobAmountRupees = amount,
                ratesVaried = varied,
                previousBalanceRupees = prior ?: 0L,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinishState())

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            // Snapshot the old balance before anything is billed.
            val job = app.workRepository.observeJob(s.orgId, jobId).first()
            if (job != null) {
                priorBalance.value = app.ledgerRepository
                    .observeBalance(s.orgId, job.customerId).first()
            }
        }
    }

    fun toBill() {
        _step.value = 1
        // Default to settling everything: it is the common case, and a wrong default
        // here is visible on the next line rather than hidden.
        val total = state.value.totalDueRupees
        _payment.value = _payment.value.copy(
            choice = PaymentChoice.All,
            amountText = total.toString(),
        )
    }

    fun back(): Boolean {
        if (_step.value == 0) return true
        _step.value = _step.value - 1
        return false
    }

    fun chooseAll() {
        _payment.value = _payment.value.copy(
            choice = PaymentChoice.All,
            amountText = state.value.totalDueRupees.toString(),
        )
    }

    fun chooseSome() {
        _payment.value = _payment.value.copy(choice = PaymentChoice.Some, amountText = "")
    }

    fun chooseNothing() {
        _payment.value = _payment.value.copy(choice = PaymentChoice.Nothing, amountText = "")
    }

    fun setAmount(value: String) {
        _payment.value = _payment.value.copy(
            choice = PaymentChoice.Some,
            amountText = value.filter { it.isDigit() },
        )
    }

    fun setMethod(method: PaymentMethod) {
        _payment.value = _payment.value.copy(method = method)
    }

    /**
     * Completes the job and records whatever was paid.
     *
     * Completing writes the charge to the ledger inside its own transaction, so the
     * two steps here can never leave work billed but unrecorded — the worst they can
     * do is leave a payment unrecorded, which the customer's khata makes obvious.
     */
    fun setAnyLabour(any: Boolean) = _labour.update { it.copy(any = any) }

    fun setLabourHeadcount(value: Int) = _labour.update {
        it.copy(headcount = value.coerceIn(1, 200))
    }

    /** Says nobody counted, rather than leaving a guess that reads as a count. */
    fun setLabourUncounted() = _labour.update { it.copy(headcount = null) }

    fun setLabourCounted() = _labour.update {
        it.copy(headcount = FinishLabour.DEFAULT_HEADS)
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val current = state.value
        val pay = _payment.value
        if (pay.saving) return@launch
        _payment.value = pay.copy(saving = true)

        app.workRepository.complete(s.orgId, jobId, byPersonId = s.personId)

        // Written with no figure on it whoever is saving, so one flow serves
        // the driver and the owner alike. A failure here must not cost the
        // job: the work is already completed above, and a crew nobody logged
        // is a smaller loss than a day's work rolled back.
        val crew = _labour.value
        if (crew.any) {
            runCatching {
                app.ledgerRepository.recordJobCost(
                    orgId = s.orgId,
                    can = s.can,
                    jobId = jobId,
                    kind = JobCostKind.Labour,
                    headcount = crew.headcount,
                    byPersonId = s.personId,
                    atMillis = System.currentTimeMillis(),
                )
            }
        }

        val paid = pay.amountRupees.coerceAtMost(current.totalDueRupees)
        val customerId = app.workRepository.observeJob(s.orgId, jobId).first()?.customerId
        if (pay.choice != PaymentChoice.Nothing && paid > 0 && customerId != null) {
            app.ledgerRepository.recordPayment(
                orgId = s.orgId,
                customerId = customerId,
                amountRupees = paid,
                method = pay.method,
                byPersonId = s.personId,
                jobId = jobId,
            )
        }
        _receivedRupees.value = paid
        // Read the balance back from the ledger rather than arithmetic on screen
        // state: the khata is the authority on what is owed, and it now holds both
        // this job's charge and whatever was just paid.
        if (customerId != null) {
            _finalBalance.value = app.ledgerRepository.observeBalance(s.orgId, customerId).first()
        }
        _payment.value = _payment.value.copy(saving = false)
        _step.value = 2
        onDone()
    }
}
