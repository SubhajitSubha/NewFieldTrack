package com.example.fieldtrack.feature.work

import com.example.fieldtrack.data.billing.CreditCheck
import com.example.fieldtrack.data.billing.CreditWarning
import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.db.dao.WorkKindFacts
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.db.entity.BookingEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.session.Session
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.plan.observePlanToday
import com.example.fieldtrack.feature.plan.visibleTo
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
data class RateSuggestion(
    val amountMinor: Long,
    val label: String? = null,
    /** The terms this rate usually came with, offered along with it. */
    val rate: RateEntity? = null,
)

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
    /** The machine, what goes out with it, and the work, which move together. */
    val pick: WorkPick = WorkPick(),
    val billingMode: BillingMode = BillingMode.PerAcre,
    val unit: MeasureUnit = MeasureUnit.Acre,
    /** What this business measures land in, which names the area billing mode. */
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    /** False for a driver, who picks from the fleet but may not add to it. */
    val canAddMachines: Boolean = false,
    val rateText: String = "",
    /** What is agreed beyond the rate; folded away until somebody opens it. */
    val terms: TermsDraft = TermsDraft(),
    /** People who can be put on the job besides whoever starts it. */
    val crew: List<Crew> = emptyList(),
    /** A rate this contractor has used before for this work — offered as one tap. */
    val suggestedRates: List<RateSuggestion> = emptyList(),
    val starting: Boolean = false,
    /**
     * What an hour on the chosen machine costs, and how its work has gone.
     * Null for somebody who may not see what the business pays, for a job with
     * no machine, or while it loads — the hint is then simply not there.
     */
    val machineCost: Costing.RunningCost? = null,
    val machineKinds: List<WorkKindFacts> = emptyList(),
    /** Something to say about what this customer already owes, or null. Informs, never blocks. */
    val creditWarning: CreditWarning? = null,
    /** The booking this work is being started from, which is what makes that booking started. */
    val bookingId: String? = null,
    /** What this customer has booked and not started, offered as "is this that work?". */
    val openBookings: List<BookingRow> = emptyList(),
    /** The plan's today, for saying which day an offered booking was for. */
    val planToday: Long = 0,
) {
    val rateMinor: Long get() = MoneyInput.parse(rateText) ?: 0L

    /** The machine picked, as the person picked it: a tractor, or a tool. */
    val machineId: String? get() = pick.machineId
    val workType: WorkTypeOption get() = pick.work

    /**
     * What this machine costs for the work being set up, if enough is known.
     *
     * By the unit when the machine has done enough of this kind of work to say
     * how fast it goes; by the hour when the job is itself paid by the hour; and
     * otherwise only the hourly figure, because dividing it by a speed nobody has
     * measured would be a guess dressed as a number.
     */
    val costHint: CostHint?
        get() {
            val cost = machineCost?.takeIf { it.enough } ?: return null
            val perHour = cost.rounded().total
            if (unit == MeasureUnit.Hour && billingMode == BillingMode.PerHour) {
                return CostHint.PerUnit(perHour)
            }
            val kind = machineKinds.firstOrNull { it.workType == workType.key && it.unit == unit }
            val perUnit = kind?.let { Costing.perUnit(cost, it) }
            return perUnit?.let { CostHint.PerUnit(it.costMinor) } ?: CostHint.OnlyHourly(perHour)
        }

    val canGoNext: Boolean
        get() = when (step) {
            0 -> customer != null
            1 -> true
            else -> rateMinor > 0
        }
}

/** What the machine chosen for a job costs, in the terms the rate is being set in. */
sealed interface CostHint {
    /** What one unit of this work costs on this machine. */
    data class PerUnit(val costMinor: Long) : CostHint

    /** Only what an hour costs; there is not enough of this work to say per unit. */
    data class OnlyHourly(val perHourMinor: Long) : CostHint
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
    /**
     * Set when the job was started from a booking on the plan.
     *
     * Everything the booking says is carried in, and the job it becomes is linked to
     * it. Like [forCustomerId] it answers step one, so backing out leaves.
     */
    private val forBookingId: String? = null,
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
            val team = app.teamRepository.observeTeam(s.orgId).first()
            _state.update {
                it.copy(
                    crew = team.filter { m -> m.personId != s.personId }.map { m -> Crew(m.personId, m.displayName) },
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
            val booking = forBookingId?.let { app.planRepository.observe(s.orgId, it).first() }?.booking
            booking?.let { b ->
                app.directoryRepository.findCustomer(s.orgId, b.customerId)?.let { customer ->
                    selectCustomer(customer)
                    _state.update { it.copy(step = 1) }
                    applyBooking(b, app.directoryRepository.observeMachines(s.orgId).first())
                }
            }
        }
        // Whatever this customer has booked and not yet started, for "is this that work?".
        // Not asked once the work is already tied to a booking.
        viewModelScope.launch {
            combine(
                app.sessionStore.session,
                _state.map { it.customer?.id }.distinctUntilChanged(),
            ) { s, id -> s to id }
                .flatMapLatest { (s, id) ->
                    if (id == null) kotlinx.coroutines.flow.flowOf(0L to emptyList<BookingRow>())
                    else app.observePlanToday(s.orgId).flatMapLatest { (settings, today) ->
                        // Only what this person may see of the plan. A driver starting
                        // ad hoc work for a farmer was offered other drivers' bookings,
                        // machine included, whether or not the owner let them see them.
                        app.planRepository.observeOpenFor(s.orgId, id, today)
                            .map { today to it.visibleTo(s, settings) }
                    }
                }
                .collect { (today, rows) -> _state.update { it.copy(planToday = today, openBookings = rows) } }
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
        // What the machines have done and how it was charged: the order of the work list, and
        // the way a work is charged when it is picked. The first time it comes, the work already
        // showing is charged that way too.
        viewModelScope.launch {
            var first = true
            app.sessionStore.session
                .flatMapLatest { app.directoryRepository.observeWorkHistory(it.orgId) }
                .collect { history ->
                    changePick(recharge = first) { it.withHistory(history) }
                    first = false
                }
        }
        viewModelScope.launch {
            app.sessionStore.session
                .flatMapLatest { app.directoryRepository.observeMachines(it.orgId) }
                .collect { machines ->
                    changePick { it.withMachines(machines) }
                    _state.update { state -> state.copy(machines = machines) }
                }
        }
        // What the chosen machine costs to run, for the hint under the rate. Only
        // for somebody who sees what the business pays: the loan is part of it, and
        // the person setting a price is exactly the one who must not be shown a cost
        // missing it.
        viewModelScope.launch {
            combine(
                app.sessionStore.session,
                _state.map { it.pick.jobMachineId }.distinctUntilChanged(),
            ) { s, machineId -> s to machineId }
                .flatMapLatest { (s, machineId) ->
                    val allowed = Capability.SeeMoney in s.can && Capability.SeePeopleCost in s.can
                    if (machineId == null || !allowed) {
                        kotlinx.coroutines.flow.flowOf<Pair<Costing.RunningCost?, List<WorkKindFacts>>>(
                            null to emptyList()
                        )
                    } else {
                        combine(
                            app.directoryRepository.observeRunningCosts(s.orgId).map { it[machineId] },
                            app.directoryRepository.observeWorkKinds(s.orgId, machineId),
                        ) { running, kinds -> running to kinds }
                    }
                }
                .collect { (running, kinds) ->
                    _state.update { it.copy(machineCost = running, machineKinds = kinds) }
                }
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
            // Nor does a booking made for them.
            else it.copy(customer = customer, fieldId = null, bookingId = null, creditWarning = null)
        }
        refreshSuggestedRate()
        warnAboutDebt(customer)
    }

    fun selectField(id: String?) = _state.update { it.copy(fieldId = id) }

    /** What they already owe, said before the work starts, for somebody who may see money. */
    private fun warnAboutDebt(customer: CustomerEntity) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        if (!s.canSeeMoney) return@launch
        val entries = app.ledgerRepository.observeLedger(s.orgId, customer.id).first()
        val warning = CreditCheck.warning(entries, customer.creditLimitMinor, System.currentTimeMillis())
        _state.update { if (it.customer?.id == customer.id) it.copy(creditWarning = warning) else it }
    }

    /** Fills the job in from a booking and ties the two together. */
    fun useBooking(row: BookingRow) = applyBooking(row.booking, _state.value.machines)

    /** "Not this one": keeps what was filled in, and lets go of the booking. */
    fun dropBooking() = _state.update { it.copy(bookingId = null) }

    private fun applyBooking(b: BookingEntity, machines: List<MachineEntity>) {
        _state.update { it.copy(bookingId = b.id, fieldId = b.fieldId) }
        // A machine since removed is not carried into a job that could not name it. The work is
        // the booking's, own name and all.
        changePick {
            it.withMachines(machines.ifEmpty { it.machines })
                .fromBooking(b.machineId?.takeIf { id -> machines.any { m -> m.id == id } }, b.workType)
        }
    }

    fun selectMachine(id: String?) = changePick { it.pickMachine(id) }

    fun selectPartner(id: String?) = changePick { it.pickPartner(id) }

    fun nameOther(text: String) = _state.update { it.copy(pick = it.pick.nameOther(text)) }

    /**
     * Moves the machine, partner and work together, and when that changes the work, charges it
     * the way this business last charged that work.
     */
    private fun changePick(recharge: Boolean = false, change: (WorkPick) -> WorkPick) {
        val before = _state.value.pick.work.key
        _state.update { it.copy(pick = change(it.pick)) }
        val after = _state.value.pick
        if (recharge || after.work.key != before) {
            val (mode, unit) = chargeFor(after.work, after.history, orgLandUnit)
            _state.update { it.copy(billingMode = mode, unit = unit, rateText = "") }
        }
        refreshSuggestedRate()
    }

    /**
     * Picking the work also sets a sensible billing mode, which the next step can
     * still override. Most contractors charge harvesting by the acre and hauling by
     * the trip; making them say so every time is friction for nothing.
     */
    fun selectWorkType(option: WorkTypeOption) = changePick { it.pickWork(option) }

    fun setBillingMode(mode: BillingMode) {
        // Refuse a mode this work is not sold by, rather than trusting the caller.
        // The picker already hides them; this is what keeps a stale recomposition or
        // a future caller from writing a job measured in bags at a per-acre rate.
        if (mode !in _state.value.workType.billingModes) return
        _state.update {
            it.copy(
                billingMode = mode,
                unit = resolveUnit(mode, orgLandUnit),
                // A rate typed for one way of charging is not the rate for another.
                rateText = if (mode != it.billingMode) "" else it.rateText,
            )
        }
        refreshSuggestedRate()
    }

    fun setUnit(unit: MeasureUnit) = _state.update { it.copy(unit = unit) }

    fun setRate(value: String) =
        _state.update { it.copy(rateText = MoneyInput.clean(value)) }

    fun setTerms(draft: TermsDraft) = _state.update { it.copy(terms = draft) }

    /** A usual rate tapped: its amount, and the terms it usually comes with. */
    fun applySuggestion(suggestion: RateSuggestion) = _state.update {
        it.copy(
            rateText = MoneyInput.text(suggestion.amountMinor),
            terms = TermsDraft.of(suggestion.rate, keep = it.terms),
        )
    }

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
        if (step == 1 && (forCustomerId != null || forBookingId != null)) return true
        _state.update { it.copy(step = step - 1) }
        return false
    }

    /** Adds a machine mid-flow and selects it, so the job can carry on. */
    fun createMachine(name: String, kind: MachineKind, typeLabel: String? = null) = viewModelScope.launch {
        if (name.isNotBlank()) addMachine(name.trim(), kind, typeLabel)
    }

    suspend fun addMachine(name: String, kind: MachineKind, typeLabel: String? = null) {
        val s = app.sessionStore.load() ?: return
        selectMachine(app.directoryRepository.addMachine(s.orgId, name, kind, typeLabel = typeLabel))
    }

    /** Creates the job and returns its id, or null if the session is not ready. */
    suspend fun start(): String? {
        val s = app.sessionStore.load() ?: return null
        val current = _state.value
        val customer = current.customer ?: return null
        _state.update { it.copy(starting = true) }

        val workKey = current.pick.workKey { app.getString(it.labelRes) }
        val jobId = app.workRepository.startJob(
            orgId = s.orgId,
            customerId = customer.id,
            workType = workKey,
            billingMode = current.billingMode,
            unit = current.unit,
            rateMinor = current.rateMinor,
            // The tractor, with the tool behind it, whichever of the two was picked.
            machineId = current.pick.jobMachineId,
            operatorPersonId = s.personId,
            // The whole reason fields exist. Without this the plot screens
            // would show nought earned on every field, for ever. Not a plot that has
            // since been deleted: a booking made last week can name one.
            fieldId = current.fieldId?.takeIf { app.directoryRepository.observeField(s.orgId, it).first() != null },
            bookingId = current.bookingId,
            terms = current.terms.toTerms().copy(implementMachineId = current.pick.implementId),
        )
        // Remember what was charged, against this customer, so "what I charged Ramesh
        // last time" is the number offered first next time. Switched off on the rate
        // card, the rate is kept for the business as a whole instead.
        app.directoryRepository.rememberRate(
            orgId = s.orgId,
            workType = workKey,
            billingMode = current.billingMode,
            unit = current.unit,
            amountMinor = current.rateMinor,
            customerId = customer.id.takeIf { app.sessionStore.rememberPerCustomer.value },
            terms = current.terms.toTerms(),
        )
        return jobId
    }

    private fun refreshSuggestedRate() = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val current = _state.value
        val perCustomer = app.sessionStore.rememberPerCustomer.value
        // Every rate that could apply, not just the first — one kind of work can
        // carry several, and picking one of them silently is how a contractor ends
        // up billing paddy at the wheat rate.
        val candidates = app.directoryRepository.ratesFor(
            orgId = s.orgId,
            workType = current.workType.key,
            customerId = current.customer?.id.takeIf { perCustomer },
            // Only rates for how this work is being charged, in the unit it is
            // counted in: a per-hour rate is no answer to "per acre".
            billingMode = current.billingMode,
            unit = current.unit,
        )
        // What this machine was last charged out at for this work. Two combines are two prices.
        val machineId = current.pick.jobMachineId
        val onMachine = machineId?.let {
            app.directoryRepository.lastRateOn(
                s.orgId, it, current.workType.key, current.billingMode, current.unit,
                current.customer?.id.takeIf { perCustomer },
            )
        }
        val suggestions = SuggestedRates.order(
            candidates = candidates,
            machineRate = onMachine?.rateMinor,
            machineRateForCustomer = onMachine?.sameCustomer == true,
            machineName = current.pick.machines.firstOrNull { it.id == machineId }?.name,
        )
        _state.update {
            // Only the answer to what is on screen now: a slower lookup for a work since changed is dropped.
            if (it.workType.key != current.workType.key || it.billingMode != current.billingMode ||
                it.unit != current.unit || it.pick.jobMachineId != machineId
            ) return@update it
            if (suggestions.isEmpty()) return@update it.copy(suggestedRates = emptyList())
            val best = suggestions.first()
            it.copy(
                suggestedRates = suggestions,
                // Prefill only when the person has not typed anything yet, and only
                // from the best match — offering a choice does not mean guessing.
                rateText = if (it.rateText.isBlank()) MoneyInput.text(best.amountMinor) else it.rateText,
                // The terms that usually come with that rate, unless some were typed already.
                terms = if (it.rateText.isBlank() && it.terms.isPlain) {
                    TermsDraft.of(best.rate, keep = it.terms)
                } else it.terms,
            )
        }
    }

    private companion object {
        const val LAST_STEP = 2
    }
}

/** Eight digits is a rate of ten crore a unit; more is a slip of the thumb. */
private const val MAX_RATE_DIGITS = 8
