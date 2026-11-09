package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.billing.Quote
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
    val businessPayDetails: String? = null,
    val businessTaxName: String? = null,
    val businessTaxBps: Int? = null,
    val businessGstin: String? = null,
    /** "2026-27/0241". Null until the job is billed. */
    val billNumber: String? = null,
    val billedAtMillis: Long? = null,
    val workType: String = "",
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val quantity: Double = 0.0,
    val rateMinor: Long = 0,
    /** Who pays for the diesel, so the bill can say when the customer does. */
    val fuelTerms: com.example.fieldtrack.data.db.FuelTerms = com.example.fieldtrack.data.db.FuelTerms.Included,
    val elapsedSeconds: Long = 0,
    /** What this job comes to on its own, extras and discount included. */
    val jobAmountMinor: Long = 0,
    /** The bill line by line, for saying where the figure comes from. */
    val quote: Quote? = null,
    /** What the customer is being let off, as typed on this screen. */
    val discountMinor: Long = 0,
    /**
     * True when the trips on this job were not all charged the same.
     *
     * A single "rate" is then a lie: the bill has to say the trips varied rather
     * than name a figure none of them was.
     */
    val ratesVaried: Boolean = false,
    /** What the customer owed before this job. */
    val previousBalanceMinor: Long = 0,
) {
    val totalDueMinor: Long get() = jobAmountMinor + previousBalanceMinor
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
    /** The payer's own name for an "Other" way of paying. */
    val methodLabel: String = "",
    val saving: Boolean = false,
) {
    val amountMinor: Long get() = MoneyInput.parse(amountText) ?: 0L
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

    private val _receivedMinor = MutableStateFlow(0L)
    val receivedMinor: StateFlow<Long> = _receivedMinor.asStateFlow()

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

    /** A discount or round-off the customer is given at settlement, as typed. */
    private val _discountText = MutableStateFlow("")
    val discountText: StateFlow<String> = _discountText.asStateFlow()

    fun setDiscount(value: String) {
        _discountText.value = MoneyInput.clean(value)
        // The amount to be paid moves with it: a customer settling in full settles the smaller bill.
        if (_payment.value.choice == PaymentChoice.All) chooseAll()
    }

    private val tick = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val state: StateFlow<FinishState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            // Looked at again every half minute: an hourly job's clock keeps running
            // while somebody reads the bill and chooses how to pay, and a figure
            // frozen at the moment the screen opened drifts from what is charged.
            combine(app.workRepository.observeJob(s.orgId, jobId), tick) { job, _ -> job },
            app.directoryRepository.observeCustomers(s.orgId),
            app.db.orgDao().observeOrg(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            priorBalance.combine(_discountText) { prior, discount -> prior to discount },
        ) { job, customers, org, machines, (prior, discountText) ->
            if (job == null) return@combine FinishState()
            val customer = customers.firstOrNull { it.id == job.customerId }
            val elapsed = app.workRepository.elapsedSeconds(job)
            val quantity = app.workRepository.billableQuantity(job)
            val discount = MoneyInput.parse(discountText) ?: 0L
            val quote = app.workRepository.quote(s.orgId, jobId, discountMinor = discount)
            val amount = quote?.totalMinor ?: app.workRepository.billableMinor(job)
            val varied = job.billingMode == BillingMode.PerTrip &&
                quote?.workMinor != Math.round(quantity * job.rateMinor)

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
                businessPayDetails = org?.payDetails,
                businessTaxName = org?.taxName,
                businessTaxBps = org?.taxPercentBps,
                businessGstin = org?.gstin,
                billNumber = job.billFy?.let { fy ->
                    job.billSeq?.let { seq -> formatBillNumber(fy, seq) }
                },
                billedAtMillis = job.completedAt,
                workType = job.workType,
                billingMode = job.billingMode,
                unit = job.unit,
                quantity = quantity,
                rateMinor = job.rateMinor,
                fuelTerms = job.fuelTerms,
                elapsedSeconds = elapsed,
                jobAmountMinor = amount,
                quote = quote,
                discountMinor = if (job.status == com.example.fieldtrack.data.db.JobStatus.Completed) job.discountMinor else discount,
                ratesVaried = varied,
                previousBalanceMinor = prior ?: 0L,
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
        val total = state.value.totalDueMinor
        _payment.value = _payment.value.copy(
            choice = PaymentChoice.All,
            amountText = MoneyInput.text(total),
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
            amountText = MoneyInput.text(state.value.totalDueMinor),
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
            amountText = MoneyInput.clean(value),
        )
    }

    fun setMethod(method: PaymentMethod) {
        _payment.value = _payment.value.copy(method = method)
    }

    fun setMethodLabel(text: String) {
        _payment.value = _payment.value.copy(methodLabel = text)
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

        // What the job really came to, read when it was billed. The figure on screen
        // is from the last time the clock was looked at, and an hourly job goes on
        // running while somebody is choosing how to pay.
        val charged = app.workRepository.complete(
            s.orgId, jobId, byPersonId = s.personId, discountMinor = current.discountMinor,
        )
        val due = current.previousBalanceMinor + charged

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

        val paid = when (pay.choice) {
            PaymentChoice.All -> due
            else -> pay.amountMinor.coerceAtMost(due)
        }
        val customerId = app.workRepository.observeJob(s.orgId, jobId).first()?.customerId
        if (pay.choice != PaymentChoice.Nothing && paid > 0 && customerId != null) {
            app.ledgerRepository.recordPayment(
                orgId = s.orgId,
                customerId = customerId,
                amountMinor = paid,
                method = pay.method,
                methodLabel = pay.methodLabel,
                byPersonId = s.personId,
                jobId = jobId,
            )
        }
        _receivedMinor.value = paid
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
