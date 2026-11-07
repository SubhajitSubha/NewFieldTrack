package com.example.fieldtrack.feature.plan

import com.example.fieldtrack.data.billing.CreditCheck
import com.example.fieldtrack.data.billing.CreditWarning
import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.MoneyInput
import com.example.fieldtrack.data.db.PaymentMethod
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.CancelReason
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.workTypeOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingFormState(
    val loaded: Boolean = false,
    val canPlan: Boolean = false,
    /** The booking was not found, or belongs to another business. */
    val notFound: Boolean = false,
    val today: Long = 0,
    val id: String? = null,
    val status: BookingStatus = BookingStatus.Planned,
    val cancelReason: CancelReason? = null,
    /** Somebody has started the work, so the booking is what it was and is no longer to be edited. */
    val started: Boolean = false,
    val query: String = "",
    val customers: List<CustomerEntity> = emptyList(),
    val customerNames: Map<String, String> = emptyMap(),
    val customer: CustomerEntity? = null,
    val fields: List<FieldEntity> = emptyList(),
    val fieldId: String? = null,
    val workType: WorkTypeOption = workTypeOptions.first(),
    val day: Long = 0,
    val part: DayPart = DayPart.Morning,
    val startMinute: Int? = null,
    val machines: List<MachineEntity> = emptyList(),
    val machineId: String? = null,
    val team: List<MembershipEntity> = emptyList(),
    val driverId: String? = null,
    val note: String = "",
    /** A token taken with this booking, as typed. Only asked when somebody may take money. */
    /** What this farmer already owes, said before more work is booked for them. */
    val creditWarning: CreditWarning? = null,
    val advanceText: String = "",
    val advanceMethod: PaymentMethod = PaymentMethod.Cash,
    val canTakeMoney: Boolean = false,
    /** What was taken when the booking was made, shown once it exists. */
    val advanceTakenMinor: Long? = null,
    /** Everything else booked that day, for saying what this one would double-book. */
    val sameDay: List<BookingRow> = emptyList(),
    val saving: Boolean = false,
) {
    /** Open to change: still a plan, and nobody has started it. */
    val editable: Boolean get() = canPlan && status == BookingStatus.Planned && !started

    val canSave: Boolean get() = editable && !saving && customer != null && day >= today

    private val clashes: List<BookingRow>
        get() = PlanDay.clashesFor(day, part, machineId, driverId, id, sameDay)

    val machineClash: BookingRow?
        get() = machineId?.let { m -> clashes.firstOrNull { it.booking.machineId == m } }

    val driverClash: BookingRow?
        get() = driverId?.let { d -> clashes.firstOrNull { it.booking.operatorPersonId == d } }

    private val machine: MachineEntity? get() = machines.firstOrNull { it.id == machineId }

    val machineDown: Boolean get() = machine?.status == MachineStatus.Down && day >= today

    val machineGone: Boolean get() = machineId != null && machine == null

    /** The person it was given to has since left the team. */
    val driverGone: Boolean get() = driverId != null && team.firstOrNull { it.personId == driverId }?.active != true

    val hasWarning: Boolean
        get() = machineClash != null || driverClash != null || machineDown || machineGone || driverGone
}

/**
 * Booking work: who it is for, what, when, on which machine and with which driver.
 *
 * Everything but the farmer is optional, because a booking is made on a phone call
 * and the call rarely settles the driver. Warnings about a machine that is down or
 * a driver already booked are said as they arise and never block a save: the day
 * is run by people who swap machines at the gate, and a plan that refused to
 * change would not be used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingFormViewModel(
    private val app: FieldFlowApplication,
    /** The booking being edited, or null for a new one. */
    private val bookingId: String?,
    /** The day being looked at on the plan, so a new booking starts there. */
    private val forDay: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingFormState())
    val state: StateFlow<BookingFormState> = _state.asStateFlow()

    private var orgId = ""

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            orgId = s.orgId
            val (settings, today) = app.observePlanToday(s.orgId).first()
            val existing = bookingId?.let { app.planRepository.observe(s.orgId, it).first() }
            if (bookingId != null && existing == null) {
                _state.update { it.copy(loaded = true, notFound = true) }
                return@launch
            }
            _state.update { st ->
                st.copy(
                    loaded = true,
                    canPlan = Capability.PlanWork in s.can,
                    canTakeMoney = Capability.TakePayment in s.can,
                    today = today,
                    day = existing?.booking?.day ?: forDay?.takeIf { it >= today } ?: today,
                ).let { base ->
                    val b = existing?.booking ?: return@let base
                    base.copy(
                        id = b.id,
                        status = b.status,
                        cancelReason = b.cancelReason,
                        started = existing.jobStatus != null,
                        fieldId = b.fieldId,
                        workType = workTypeOptions.firstOrNull { it.key == b.workType } ?: workTypeOptions.last(),
                        part = b.part,
                        startMinute = b.startMinute,
                        machineId = b.machineId,
                        driverId = b.operatorPersonId,
                        note = b.note.orEmpty(),
                        advanceTakenMinor = b.advanceMinor,
                    )
                }
            }
            existing?.booking?.customerId?.let { id ->
                app.directoryRepository.findCustomer(s.orgId, id)?.let { c -> _state.update { it.copy(customer = c) } }
            }
            // The day's bookings follow the day being chosen, so a clash is said the
            // moment the day, part, machine or driver changes.
            _state.map { it.day }.distinctUntilChanged()
                .flatMapLatest { app.planRepository.observeDays(s.orgId, it, it) }
                .collect { rows -> _state.update { it.copy(sameDay = rows) } }
        }
        // What can change under a form left open: somebody starts the work, or another
        // planner cancels it, and the day itself turns over at the roll hour. Read once
        // at the start, these let a booking be cancelled with a job already running, or
        // saved for a day that has already gone.
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val id = bookingId ?: return@launch
            app.planRepository.observe(s.orgId, id).collect { row ->
                if (row == null) return@collect
                _state.update {
                    it.copy(
                        status = row.booking.status,
                        cancelReason = row.booking.cancelReason,
                        started = row.jobStatus != null,
                    )
                }
            }
        }
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            app.observePlanToday(s.orgId).collect { (_, today) -> _state.update { it.copy(today = today) } }
        }
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            kotlinx.coroutines.flow.combine(
                app.directoryRepository.observeMachines(s.orgId),
                app.db.orgDao().observeEveryone(s.orgId),
                app.directoryRepository.observeCustomers(s.orgId),
            ) { machines, team, customers -> Triple(machines, team, customers) }
                .collect { (machines, team, customers) ->
                    _state.update {
                        it.copy(machines = machines, team = team, customerNames = customers.associate { c -> c.id to c.name })
                    }
                }
        }
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            _state.map { it.query }.distinctUntilChanged()
                .flatMapLatest { app.directoryRepository.searchCustomers(s.orgId, it) }
                .collect { found -> _state.update { it.copy(customers = found) } }
        }
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            _state.map { it.customer?.id }.distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else app.directoryRepository.observeFields(s.orgId, id)
                }
                .collect { fields -> _state.update { it.copy(fields = fields) } }
        }
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun selectCustomer(customer: CustomerEntity) {
        _state.update {
            // The plot belongs to whoever was chosen before; changing the farmer drops it.
            if (it.customer?.id == customer.id) it.copy(customer = customer, query = "")
            else it.copy(customer = customer, fieldId = null, query = "", creditWarning = null)
        }
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            if (!s.canSeeMoney) return@launch
            val entries = app.ledgerRepository.observeLedger(s.orgId, customer.id).first()
            val warning = CreditCheck.warning(entries, customer.creditLimitMinor, System.currentTimeMillis())
            _state.update { if (it.customer?.id == customer.id) it.copy(creditWarning = warning) else it }
        }
    }

    fun selectField(id: String?) = _state.update { it.copy(fieldId = id) }
    fun selectWorkType(option: WorkTypeOption) = _state.update { it.copy(workType = option) }
    fun setDay(day: Long) = _state.update { it.copy(day = day) }
    fun setPart(part: DayPart) = _state.update { it.copy(part = part) }
    fun setStartMinute(minute: Int?) = _state.update { it.copy(startMinute = minute) }
    fun selectMachine(id: String?) = _state.update { it.copy(machineId = id) }
    fun selectDriver(id: String?) = _state.update { it.copy(driverId = id) }
    fun setNote(text: String) = _state.update { it.copy(note = text.take(MAX_NOTE)) }

    fun setAdvance(text: String) = _state.update { it.copy(advanceText = MoneyInput.clean(text)) }

    fun setAdvanceMethod(method: PaymentMethod) = _state.update { it.copy(advanceMethod = method) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        val customer = current.customer ?: return@launch
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: return@launch
        runCatching {
            app.planRepository.save(
                can = s.can, orgId = s.orgId, id = current.id, customerId = customer.id,
                fieldId = current.fieldId, workType = current.workType.key, day = current.day,
                part = current.part, startMinute = current.startMinute, machineId = current.machineId,
                operatorPersonId = current.driverId, note = current.note,
                advanceMinor = if (current.id == null && current.canTakeMoney) MoneyInput.parse(current.advanceText) else null,
                advanceMethod = current.advanceMethod,
                byPersonId = s.personId,
            )
        }.onSuccess { _state.update { it.copy(saving = false) }; onSaved() }
            // Refused: the permission was taken away while this was open.
            .onFailure { _state.update { it.copy(saving = false, canPlan = false) } }
    }

    fun cancel(reason: CancelReason, onDone: () -> Unit) = change(onDone) { s, id ->
        app.planRepository.cancel(s.can, s.orgId, id, reason)
    }

    fun markDone(onDone: () -> Unit) = change(onDone) { s, id -> app.planRepository.markDone(s.can, s.orgId, id) }

    fun reopen(onDone: () -> Unit) = change(onDone) { s, id -> app.planRepository.reopen(s.can, s.orgId, id) }

    private fun change(
        onDone: () -> Unit,
        block: suspend (com.example.fieldtrack.data.session.Session, String) -> Unit,
    ) = viewModelScope.launch {
        val id = _state.value.id ?: return@launch
        val s = app.sessionStore.load() ?: return@launch
        runCatching { block(s, id) }.onSuccess { onDone() }
    }

    private companion object {
        const val MAX_NOTE = 300
    }
}
