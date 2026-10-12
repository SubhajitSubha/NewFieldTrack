package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.repository.OverlapWarning
import com.example.fieldtrack.feature.WorkTypeOption
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
    val machineId: String? = null,
    val machines: List<MachineEntity> = emptyList(),
    val workType: WorkTypeOption = workTypeOptions.first(),
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val quantityText: String = "",
    val rateText: String = "",
    /** Minutes from midnight. Null when they do not remember the times. */
    val fromMinutes: Int? = null,
    val toMinutes: Int? = null,
    /** Jobs already on this machine at the same time. A warning, never a refusal. */
    val overlaps: List<OverlapWarning> = emptyList(),
    val saving: Boolean = false,
) {
    val quantity: Double get() = quantityText.toDoubleOrNull() ?: 0.0
    val rateRupees: Long get() = rateText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    val amountRupees: Long
        get() = if (billingMode == BillingMode.Fixed) rateRupees
        else Math.round(quantity * rateRupees)

    /** Minutes between the two times, when both are given and make sense. */
    val workedMinutes: Int?
        get() {
            val from = fromMinutes ?: return null
            val to = toMinutes ?: return null
            return (to - from).takeIf { it > 0 }
        }

    val hasTimes: Boolean get() = fromMinutes != null || toMinutes != null

    /** True when the times are the wrong way round, which is worth saying. */
    val timesBackwards: Boolean
        get() = fromMinutes != null && toMinutes != null && workedMinutes == null

    val canSave: Boolean
        get() = !saving && customer != null && rateRupees > 0 && !timesBackwards &&
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
            _state.update {
                it.copy(
                    loaded = true,
                    // Yesterday to begin with: today's work would have been timed.
                    dayMillis = startOfDay(System.currentTimeMillis()) - DAY_MILLIS,
                    customers = app.directoryRepository.observeCustomers(s.orgId).first(),
                    machines = app.directoryRepository.observeMachines(s.orgId).first(),
                    unit = resolveUnit(it.billingMode, s.landUnit),
                )
            }
        }
    }

    fun setDay(millis: Long) {
        _state.update { it.copy(dayMillis = startOfDay(millis)) }
        checkOverlaps()
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun selectCustomer(customer: CustomerEntity) {
        _state.update { it.copy(customer = customer, query = "") }
        suggestRate()
    }

    fun selectMachine(id: String?) {
        _state.update { it.copy(machineId = id) }
        checkOverlaps()
    }

    fun selectWorkType(option: WorkTypeOption) {
        _state.update {
            it.copy(
                workType = option,
                billingMode = option.defaultMode,
                unit = resolveUnit(option.defaultMode, landUnit),
            )
        }
        suggestRate()
    }

    fun setBillingMode(mode: BillingMode) {
        if (mode !in _state.value.workType.billingModes) return
        _state.update { it.copy(billingMode = mode, unit = resolveUnit(mode, landUnit)) }
        suggestRate()
    }

    fun setUnit(unit: MeasureUnit) = _state.update { it.copy(unit = unit) }

    fun setQuantity(value: String) =
        _state.update { it.copy(quantityText = value.filter { c -> c.isDigit() || c == '.' }) }

    fun setRate(value: String) =
        _state.update { it.copy(rateText = value.filter { c -> c.isDigit() }) }

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
        val machineId = current.machineId
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

    /** What this customer was charged last time for this work, if anything. */
    private fun suggestRate() = viewModelScope.launch {
        val current = _state.value
        if (current.rateText.isNotBlank()) return@launch
        val remembered = app.directoryRepository.suggestedRate(
            orgId,
            current.workType.key,
            current.customer?.id.takeIf { app.sessionStore.rememberPerCustomer.value },
        ) ?: return@launch
        _state.update {
            if (it.rateText.isBlank()) it.copy(rateText = remembered.amountRupees.toString())
            else it
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

        val jobId = app.workRepository.recordPastWork(
            orgId = s.orgId,
            customerId = current.customer!!.id,
            workType = current.workType.key,
            billingMode = current.billingMode,
            unit = current.unit,
            rateRupees = current.rateRupees,
            quantity = if (current.billingMode == BillingMode.Fixed) 1.0 else current.quantity,
            // Noon rather than midnight, so the day it belongs to survives a device
            // whose clock sits in a different zone from the one it was typed in.
            workedAt = current.dayMillis + MIDDAY_MILLIS,
            startedAt = startedAt,
            completedAt = completedAt,
            machineId = current.machineId,
            operatorPersonId = s.personId,
        )
        app.directoryRepository.rememberRate(
            orgId = s.orgId,
            workType = current.workType.key,
            billingMode = current.billingMode,
            unit = current.unit,
            amountRupees = current.rateRupees,
            customerId = current.customer.id.takeIf {
                app.sessionStore.rememberPerCustomer.value
            },
        )
        _state.update { it.copy(saving = false) }
        onSaved(jobId)
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
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
