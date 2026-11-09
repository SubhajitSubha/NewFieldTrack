package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.billing.Billing
import com.example.fieldtrack.feature.calculator.cleanQuantity
import com.example.fieldtrack.feature.calculator.countsWhole
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.repository.OverlapWarning
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.workTypeOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class PastWorkState(
    val loaded: Boolean = false,
    /** Midnight of the day the work was done. */
    val dayMillis: Long = 0,
    val customer: CustomerEntity? = null,
    val customers: List<CustomerEntity> = emptyList(),
    val query: String = "",
    val machines: List<MachineEntity> = emptyList(),
    /** The machine, what went out with it, and the work. The day is past, so a busy machine is no reason to skip it. */
    val pick: WorkPick = WorkPick(now = false),
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val quantityText: String = "",
    val rateText: String = "",
    /** What is agreed beyond the rate. Folded away until somebody opens it. */
    val terms: TermsDraft = TermsDraft(),
    val crew: List<Crew> = emptyList(),
    /** Minutes from midnight. Null when they do not remember the times. */
    val fromMinutes: Int? = null,
    val toMinutes: Int? = null,
    /** Jobs already on this machine at the same time. A warning, never a refusal. */
    val overlaps: List<OverlapWarning> = emptyList(),
    val saving: Boolean = false,
) {
    val quantity: Double get() = quantityText.toDoubleOrNull() ?: 0.0
    val machineId: String? get() = pick.machineId
    val workType: WorkTypeOption get() = pick.work
    val rateMinor: Long get() = MoneyInput.parse(rateText) ?: 0L

    val amountMinor: Long
        get() {
            val work = Billing.amount(billingMode, quantity, rateMinor, Money.style.stepMinor)
            val t = terms.toTerms()
            // Work typed in afterwards has no clock to wait on and no diesel log to pass on.
            return (maxOf(work, t.minimumMinor) + t.arrivalMinor).coerceAtLeast(0)
        }

    /** Minutes between the two times, when both are given and make sense. */
    val workedMinutes: Int?
        get() {
            val from = fromMinutes ?: return null
            val to = toMinutes ?: return null
            return (to - from).takeIf { it > 0 }
        }

    val hasTimes: Boolean get() = fromMinutes != null || toMinutes != null

    /**
     * Hourly work where the times and the hours typed do not agree.
     *
     * The bill uses the hours typed — that is the figure the screen shows — and the
     * times are a note of when. Said out loud, so nobody is surprised which one won.
     * A quarter of an hour either way is rounding, not a disagreement.
     */
    val hoursDisagreeWithTimes: Boolean
        get() = billingMode == BillingMode.PerHour && workedMinutes != null && quantity > 0 &&
            kotlin.math.abs(workedMinutes!! - quantity * 60) > HOURS_TOLERANCE_MINUTES

    /** True when the times are the wrong way round, which is worth saying. */
    val timesBackwards: Boolean
        get() = fromMinutes != null && toMinutes != null && workedMinutes == null

    val canSave: Boolean
        get() = !saving && customer != null && rateMinor > 0 && !timesBackwards &&
            (billingMode == BillingMode.Fixed || quantity > 0)
}

/**
 * Work that happened before anybody pressed start.
 *
 * A phone goes flat in a field, or a driver simply forgets, and the job still has to
 * reach the khata. This records it with the day it was actually done rather than
 * today, and marks it as entered by hand so neither side later mistakes it for a
 * timed record.
 */
class PastWorkViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(PastWorkState())
    val state: StateFlow<PastWorkState> = _state.asStateFlow()

    private var orgId: String = ""
    private var landUnit: MeasureUnit = MeasureUnit.Acre
    private var overlapCheck: Job? = null

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            orgId = s.orgId
            landUnit = s.landUnit
            val machines = app.directoryRepository.observeMachines(s.orgId).first()
            _state.update {
                it.copy(
                    loaded = true,
                    // Yesterday to begin with: today's work would have been timed.
                    dayMillis = startOfDay(System.currentTimeMillis()) - DAY_MILLIS,
                    customers = app.directoryRepository.observeCustomers(s.orgId).first(),
                    machines = machines,
                    unit = resolveUnit(it.billingMode, s.landUnit),
                )
            }
            val history = app.directoryRepository.observeWorkHistory(s.orgId).first()
            changePick(recharge = true) { it.withMachines(machines).withHistory(history) }
        }
    }

    /**
     * Moves the machine, partner and work together, and when that changes the work, charges it
     * the way this business last charged that work.
     */
    private fun changePick(recharge: Boolean = false, change: (WorkPick) -> WorkPick) {
        val before = _state.value.pick.work.key
        _state.update { it.copy(pick = change(it.pick)) }
        val after = _state.value.pick
        if (recharge || after.work.key != before) {
            val (mode, unit) = chargeFor(after.work, after.history, landUnit)
            _state.update {
                it.copy(
                    billingMode = mode,
                    unit = unit,
                    quantityText = if (unit.countsWhole) it.quantityText.substringBefore('.') else it.quantityText,
                    rateText = "",
                )
            }
        }
        suggestRate()
        checkOverlaps()
    }

    fun selectPartner(id: String?) = changePick { it.pickPartner(id) }

    fun nameOther(text: String) = _state.update { it.copy(pick = it.pick.nameOther(text)) }

    fun setDay(millis: Long) {
        _state.update { it.copy(dayMillis = startOfDay(millis)) }
        checkOverlaps()
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    /**
     * Fills the form from what was said. Only what was understood; the rest stays as it was,
     * and nothing is saved. The person looks at the boxes and fixes what is off.
     */
    fun applySpoken(said: SpokenWork) {
        said.workType?.let { selectWorkType(it) }
        said.customer?.let { selectCustomer(it) }
        said.day?.let { day ->
            val today = startOfDay(System.currentTimeMillis())
            setDay(if (day == SpokenDay.Today) today else today - DAY_MILLIS)
        }
        said.unit?.takeIf { it in unitsFor(_state.value.billingMode) }?.let { setUnit(it) }
        said.quantity?.let { setQuantity(it.toString().removeSuffix(".0")) }
    }

    /** What was said, understood against this business's own farmers and the words for work and units. */
    fun understand(text: String, workWords: Map<String, WorkTypeOption>, unitWords: Map<String, MeasureUnit>): SpokenWork =
        SpokenWorkParser.parse(text, _state.value.customers, workWords, unitWords)

    fun selectCustomer(customer: CustomerEntity) {
        _state.update { it.copy(customer = customer, query = "") }
        suggestRate()
    }

    fun selectMachine(id: String?) = changePick { it.pickMachine(id) }

    fun selectWorkType(option: WorkTypeOption) = changePick { it.pickWork(option) }

    fun setBillingMode(mode: BillingMode) {
        if (mode !in _state.value.workType.billingModes) return
        _state.update {
            val unit = resolveUnit(mode, landUnit)
            it.copy(
                billingMode = mode,
                unit = unit,
                // Trips are counted whole. "2.5" typed under acres and then switched to
                // trips would otherwise be priced as two and a half loads.
                quantityText = if (unit.countsWhole) {
                    it.quantityText.substringBefore('.')
                } else it.quantityText,
                // A rate typed for one way of charging is not the rate for another:
                // 1,200 an acre must not become 1,200 an hour without anybody saying so.
                rateText = if (mode != it.billingMode) "" else it.rateText,
            )
        }
        suggestRate()
    }

    fun setUnit(unit: MeasureUnit) = _state.update { it.copy(unit = unit) }

    fun setQuantity(value: String) =
        _state.update { it.copy(quantityText = cleanQuantity(value, whole = it.unit.countsWhole)) }

    fun setRate(value: String) =
        _state.update { it.copy(rateText = MoneyInput.clean(value)) }

    fun setTerms(draft: TermsDraft) = _state.update { it.copy(terms = draft) }

    fun setFrom(minutes: Int?) {
        _state.update { it.copy(fromMinutes = minutes) }
        checkOverlaps()
    }

    fun setTo(minutes: Int?) {
        _state.update { it.copy(toMinutes = minutes) }
        checkOverlaps()
    }

    /** Clears both times, for the common "I don't remember" case. */
    fun forgetTimes() {
        _state.update { it.copy(fromMinutes = null, toMinutes = null, overlaps = emptyList()) }
    }

    /**
     * Warns when this machine was already on another job at that time.
     *
     * A warning rather than a block: two jobs can genuinely overlap when a driver
     * hands a machine over mid-field, and the person entering this knows what
     * happened better than the app does.
     */
    private fun checkOverlaps() {
        overlapCheck?.cancel()
        val current = _state.value
        // The tractor's time, which is what two jobs cannot both have had.
        val machineId = current.pick.jobMachineId
        val from = current.fromMinutes
        val to = current.toMinutes
        if (machineId == null || from == null || to == null || to <= from) {
            _state.update { it.copy(overlaps = emptyList()) }
            return
        }
        overlapCheck = viewModelScope.launch {
            val found = app.workRepository.findOverlaps(
                orgId = orgId,
                machineId = machineId,
                startsAt = current.dayMillis + from * MINUTE_MILLIS,
                endsAt = current.dayMillis + to * MINUTE_MILLIS,
            )
            _state.update { it.copy(overlaps = found) }
        }
    }

    /** What this customer was charged last time for this work, on this machine first, if anything. */
    private fun suggestRate() = viewModelScope.launch {
        val current = _state.value
        if (current.rateText.isNotBlank() || orgId.isEmpty()) return@launch
        val customerId = current.customer?.id.takeIf { app.sessionStore.rememberPerCustomer.value }
        val candidates = app.directoryRepository.ratesFor(
            orgId, current.workType.key, customerId, billingMode = current.billingMode, unit = current.unit,
        )
        val machineId = current.pick.jobMachineId
        val onMachine = machineId?.let {
            app.directoryRepository.lastRateOn(orgId, it, current.workType.key, current.billingMode, current.unit, customerId)
        }
        val best = SuggestedRates.order(candidates, onMachine?.rateMinor, onMachine?.sameCustomer == true, null)
            .firstOrNull() ?: return@launch
        _state.update {
            val same = it.workType.key == current.workType.key && it.billingMode == current.billingMode &&
                it.unit == current.unit && it.pick.jobMachineId == machineId
            if (same && it.rateText.isBlank()) it.copy(rateText = MoneyInput.text(best.amountMinor)) else it
        }
    }

    /** Creates the job and hands back its id so the caller can go and bill it. */
    fun save(onSaved: (String) -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        val startedAt = current.fromMinutes?.let { current.dayMillis + it * MINUTE_MILLIS }
        val completedAt = current.toMinutes?.let { current.dayMillis + it * MINUTE_MILLIS }

        val workKey = current.pick.workKey { app.getString(it.labelRes) }
        val jobId = app.workRepository.recordPastWork(
            orgId = s.orgId,
            customerId = current.customer!!.id,
            workType = workKey,
            billingMode = current.billingMode,
            unit = current.unit,
            rateMinor = current.rateMinor,
            quantity = if (current.billingMode == BillingMode.Fixed) 1.0 else current.quantity,
            // Noon rather than midnight, so the day it belongs to survives a device
            // whose clock sits in a different zone from the one it was typed in.
            workedAt = current.dayMillis + MIDDAY_MILLIS,
            startedAt = startedAt,
            completedAt = completedAt,
            // The tractor, with the tool behind it, whichever of the two was picked.
            machineId = current.pick.jobMachineId,
            operatorPersonId = s.personId,
            terms = current.terms.toTerms().copy(implementMachineId = current.pick.implementId),
        )
        app.directoryRepository.rememberRate(
            orgId = s.orgId,
            workType = workKey,
            billingMode = current.billingMode,
            unit = current.unit,
            amountMinor = current.rateMinor,
            customerId = current.customer.id.takeIf {
                app.sessionStore.rememberPerCustomer.value
            },
            terms = current.terms.toTerms(),
        )
        _state.update { it.copy(saving = false) }
        onSaved(jobId)
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance(BusinessClock.timeZone).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private const val MINUTE_MILLIS = 60_000L
private const val DAY_MILLIS = 24 * 60 * MINUTE_MILLIS
private const val MIDDAY_MILLIS = 12 * 60 * MINUTE_MILLIS

/** Eight digits is a rate of ten crore a unit; more is a slip of the thumb. */
private const val MAX_RATE_DIGITS = 8

/** How far the hours typed may be from the times before it is worth saying. */
private const val HOURS_TOLERANCE_MINUTES = 15
