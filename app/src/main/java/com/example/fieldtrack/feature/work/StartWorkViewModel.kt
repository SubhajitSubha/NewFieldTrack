package com.example.fieldtrack.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.session.Session
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.workTypeOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The unit to offer for a billing mode, preferring the one this business actually
 * uses.
 *
 * Land is measured differently from one district to the next, so a work type's
 * built-in default must never override what the contractor told us during setup.
 * Modes that have nothing to do with land — trips, hours, kilometres — ignore it.
 */
fun resolveUnit(mode: BillingMode, orgLandUnit: MeasureUnit): MeasureUnit {
    val allowed = unitsFor(mode)
    return if (orgLandUnit in allowed) orgLandUnit else allowed.first()
}

/**
 * A rate the contractor has used before, offered as a chip.
 *
 * [label] is what tells two rates for the same work apart — "wheat" against "paddy" —
 * and is the only thing that makes a row of several chips readable.
 */
data class RateSuggestion(val amountRupees: Long, val label: String? = null)

data class StartWorkState(
    val step: Int = 0,
    val query: String = "",
    val customers: List<CustomerEntity> = emptyList(),
    val machines: List<MachineEntity> = emptyList(),
    val customer: CustomerEntity? = null,
    /**
     * This customer's plots, for the "which field?" question.
     *
     * Empty for a farmer nobody has written fields down for, and the question
     * is then not asked at all — a step offering one option and "do not say"
     * is a step that only ever wastes a tap.
     */
    val fields: List<FieldEntity> = emptyList(),
    val fieldId: String? = null,
    val machineId: String? = null,
    val workType: WorkTypeOption = workTypeOptions.first(),
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    /** What this business measures land in, which names the area billing mode. */
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** False for a driver, who picks from the fleet but may not add to it. */
    val canAddMachines: Boolean = false,
    val rateText: String = "",
    /** A rate this contractor has used before for this work — offered as one tap. */
    val suggestedRates: List<RateSuggestion> = emptyList(),
    val starting: Boolean = false,
) {
    val rateRupees: Long get() = rateText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    val canGoNext: Boolean
        get() = when (step) {
            0 -> customer != null
            1 -> true
            else -> rateRupees > 0
        }
}

/**
 * Setting up a job: who it is for, what is being done, and what it costs.
 *
 * One ViewModel across all three steps rather than one per screen — the steps are a
 * single decision made in sequence, and splitting them would mean passing half-built
 * jobs through navigation arguments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartWorkViewModel(
    private val app: FieldFlowApplication,
    /**
     * Set when the job was started from a customer's own screen.
     *
     * Skips straight past "who is this for?", because they just told us by standing
     * on that customer's page and tapping the plus.
     */
    private val forCustomerId: String? = null,
    /**
     * Set when the job was started from a field's own screen.
     *
     * Carried through rather than asked again: somebody standing on the
     * canal-side plot who taps "start work here" has already answered both
     * who it is for and which field.
     */
    private val forFieldId: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(StartWorkState())
    val state: StateFlow<StartWorkState> = _state.asStateFlow()


    /**
     * What this business measures land in. Held separately because the work-type and
     * billing-mode pickers both have to defer to it — a contractor who told us he
     * works in bigha should never be shown acres again.
     */
    private var orgLandUnit: MeasureUnit = MeasureUnit.Acre

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            orgLandUnit = s.landUnit
            _state.update {
                it.copy(
                    landUnit = s.landUnit,
                    unit = resolveUnit(it.billingMode, s.landUnit),
                    canAddMachines = Capability.EditMachines in s.can,
                )
            }
            // A field names its own customer, so starting from a field
            // answers step one too.
            val fromField = forFieldId?.let {
                app.directoryRepository.observeField(s.orgId, it).first()
            }
            val customerId = fromField?.customerId ?: forCustomerId
            customerId?.let { id ->
                app.directoryRepository.findCustomer(s.orgId, id)?.let { customer ->
                    selectCustomer(customer)
                    _state.update { state ->
                        state.copy(step = 1, fieldId = fromField?.id)
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                app.sessionStore.session,
                _state.map { it.customer?.id }.distinctUntilChanged(),
            ) { s, id -> s to id }
                .flatMapLatest { (s, id) ->
                    if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
                    else app.directoryRepository.observeFields(s.orgId, id)
                }
                .collect { fields -> _state.update { state -> state.copy(fields = fields) } }
        }
        viewModelScope.launch {
            app.sessionStore.session
                .flatMapLatest { app.directoryRepository.observeMachines(it.orgId) }
                .collect { machines -> _state.update { state -> state.copy(machines = machines) } }
        }
        viewModelScope.launch {
            combine(
                app.sessionStore.session,
                _state.map { it.query }.distinctUntilChanged(),
            ) { s, query -> s to query }
                .flatMapLatest { (s, query) -> app.directoryRepository.searchCustomers(s.orgId, query) }
                .collect { found -> _state.update { state -> state.copy(customers = found) } }
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun selectCustomer(customer: CustomerEntity) {
        // The field belongs to whoever was chosen before; changing the farmer
        // has to drop it, or the job is filed against a plot that is not theirs.
        _state.update {
            if (it.customer?.id == customer.id) it.copy(customer = customer)
            else it.copy(customer = customer, fieldId = null)
        }
        refreshSuggestedRate()
    }

    fun selectField(id: String?) = _state.update { it.copy(fieldId = id) }

    fun selectMachine(id: String?) = _state.update { it.copy(machineId = id) }

    /**
     * Picking the work also sets a sensible billing mode, which the next step can
     * still override. Most contractors charge harvesting by the acre and hauling by
     * the trip; making them say so every time is friction for nothing.
     */
    fun selectWorkType(option: WorkTypeOption) {
        _state.update {
            it.copy(
                workType = option,
                billingMode = option.defaultMode,
                unit = resolveUnit(option.defaultMode, orgLandUnit),
            )
        }
        refreshSuggestedRate()
    }

    fun setBillingMode(mode: BillingMode) {
        // Refuse a mode this work is not sold by, rather than trusting the caller.
        // The picker already hides them; this is what keeps a stale recomposition or
        // a future caller from writing a job measured in bags at a per-acre rate.
        if (mode !in _state.value.workType.billingModes) return
        _state.update { it.copy(billingMode = mode, unit = resolveUnit(mode, orgLandUnit)) }
        refreshSuggestedRate()
    }

    fun setUnit(unit: MeasureUnit) = _state.update { it.copy(unit = unit) }

    fun setRate(value: String) = _state.update { it.copy(rateText = value.filter { c -> c.isDigit() }) }

    fun next() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }

    /**
     * Returns true when the caller should leave the flow rather than step back.
     *
     * A flow opened from a customer's own page has no step one to go back to — the
     * customer was chosen by standing on their screen — so backing out of step two
     * leaves rather than dropping the farmer on an empty search they never asked for.
     */
    fun back(): Boolean {
        val step = _state.value.step
        if (step == 0) return true
        if (step == 1 && forCustomerId != null) return true
        _state.update { it.copy(step = step - 1) }
        return false
    }

    /** Adds a machine mid-flow and selects it, so the job can carry on. */
    fun createMachine(name: String, kind: MachineKind) = viewModelScope.launch {
        if (name.isNotBlank()) addMachine(name.trim(), kind)
    }

    suspend fun addMachine(name: String, kind: MachineKind) {
        val s = app.sessionStore.load() ?: return
        selectMachine(app.directoryRepository.addMachine(s.orgId, name, kind))
    }

    /** Creates the job and returns its id, or null if the session is not ready. */
    suspend fun start(): String? {
        val s = app.sessionStore.load() ?: return null
        val current = _state.value
        val customer = current.customer ?: return null
        _state.update { it.copy(starting = true) }

        val jobId = app.workRepository.startJob(
            orgId = s.orgId,
            customerId = customer.id,
            workType = current.workType.key,
            billingMode = current.billingMode,
            unit = current.unit,
            rateRupees = current.rateRupees,
            machineId = current.machineId,
            operatorPersonId = s.personId,
            // The whole reason fields exist. Without this the plot screens
            // would show nought earned on every field, for ever.
            fieldId = current.fieldId,
        )
        // Remember what was charged, against this customer, so "what I charged Ramesh
        // last time" is the number offered first next time. Switched off on the rate
        // card, the rate is kept for the business as a whole instead.
        app.directoryRepository.rememberRate(
            orgId = s.orgId,
            workType = current.workType.key,
            billingMode = current.billingMode,
            unit = current.unit,
            amountRupees = current.rateRupees,
            customerId = customer.id.takeIf { app.sessionStore.rememberPerCustomer.value },
        )
        return jobId
    }

    private fun refreshSuggestedRate() = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val current = _state.value
        // Every rate that could apply, not just the first — one kind of work can
        // carry several, and picking one of them silently is how a contractor ends
        // up billing paddy at the wheat rate.
        val candidates = app.directoryRepository.ratesFor(
            orgId = s.orgId,
            workType = current.workType.key,
            customerId = current.customer?.id.takeIf {
                app.sessionStore.rememberPerCustomer.value
            },
        )
        if (candidates.isEmpty()) return@launch

        _state.update {
            it.copy(
                suggestedRates = candidates.map { rate ->
                    RateSuggestion(rate.amountRupees, rate.label)
                },
                // Prefill only when the person has not typed anything yet, and only
                // from the best match — offering a choice does not mean guessing.
                rateText = if (it.rateText.isBlank()) candidates.first().amountRupees.toString()
                else it.rateText,
            )
        }
    }

    private companion object {
        const val LAST_STEP = 2
    }
}
