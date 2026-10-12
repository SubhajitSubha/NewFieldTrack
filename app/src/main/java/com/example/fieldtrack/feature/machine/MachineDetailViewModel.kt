package com.example.fieldtrack.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BreakdownCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Logging a service from the machine's own screen, where the machine is a given. */
data class MachineServiceDraft(
    val description: String = "",
    val costText: String = "",
    val vendor: String = "",
) {
    val costRupees: Long? get() = costText.filter { it.isDigit() }.toLongOrNull()
    val canSave: Boolean get() = description.isNotBlank()
}

/** One job this machine did, as the detail screen lists it. */
data class MachineJobRow(
    val id: String,
    val customerName: String,
    val workType: String,
    val quantityLabel: String,
    val amountRupees: Long,
    val workedAtMillis: Long,
    val live: Boolean,
)

data class MachineDetailState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val name: String = "",
    val kind: MachineKind = MachineKind.Tractor,
    val plate: String? = null,
    val status: MachineStatus = MachineStatus.Idle,
    val hoursUsed: Double = 0.0,
    val earnedRupees: Long = 0,
    val dieselRupees: Long = 0,
    /** Who is on it right now, when anybody is. */
    val driverName: String? = null,
    /** Hours until the next service; null when this machine is not on a schedule. */
    val serviceInHours: Double? = null,
    val lastServiceAtHours: Double? = null,
    val openBreakdowns: Int = 0,
    val recentJobs: List<MachineJobRow> = emptyList(),
    val canEdit: Boolean = false,
    val canSeeMoney: Boolean = true,
) {
    val serviceOverdue: Boolean get() = serviceInHours != null && serviceInHours <= 0
}

/**
 * Everything about one machine in one place.
 *
 * The fuel, service and breakdown records have been written since the upkeep work
 * landed and had nowhere to be read. This is where an owner finds out what a machine
 * has actually cost him against what it has brought in — which is the question
 * behind owning one at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MachineDetailViewModel(
    private val app: FieldFlowApplication,
    private val machineId: String,
) : ViewModel() {

    val state: StateFlow<MachineDetailState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeMachine(s.orgId, machineId),
            app.workRepository.observeForMachine(s.orgId, machineId),
            app.workRepository.observeEarnedOnMachine(s.orgId, machineId),
            app.workRepository.observeFuelSpendOnMachine(s.orgId, machineId),
            combine(
                app.directoryRepository.observeCustomers(s.orgId),
                app.teamRepository.observeEveryone(s.orgId),
                app.workRepository.observeBreakdowns(s.orgId, machineId),
            ) { customers, team, breakdowns -> Triple(customers, team, breakdowns) },
        ) { machine, jobs, earned, diesel, (customers, team, breakdowns) ->
            if (machine == null) return@combine MachineDetailState(loaded = true, exists = false)
            val names = customers.associate { it.id to it.name }
            val live = jobs.firstOrNull { it.status.name == "Running" || it.status.name == "Paused" }

            MachineDetailState(
                loaded = true,
                exists = true,
                name = machine.name,
                kind = machine.kind,
                plate = machine.plate,
                status = machine.status,
                hoursUsed = machine.hoursUsed,
                earnedRupees = earned,
                dieselRupees = diesel,
                driverName = live?.operatorPersonId
                    ?.let { id -> team.firstOrNull { it.personId == id }?.displayName },
                serviceInHours = machine.serviceIntervalHours?.let { interval ->
                    (machine.lastServiceAtHours ?: 0.0) + interval - machine.hoursUsed
                },
                lastServiceAtHours = machine.lastServiceAtHours,
                openBreakdowns = breakdowns.count { it.resolvedAt == null },
                recentJobs = jobs.map { it.asRow(names[it.customerId].orEmpty()) },
                canEdit = Capability.EditMachines in s.can,
                canSeeMoney = s.canSeeMoney,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineDetailState())

    /** Open when the user is reporting a fault on this machine. */
    private val _reporting = MutableStateFlow(false)
    val reporting: StateFlow<Boolean> = _reporting.asStateFlow()

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    /** What is being typed into the service sheet, if anything. */
    private val _service = MutableStateFlow<MachineServiceDraft?>(null)
    val service: StateFlow<MachineServiceDraft?> = _service.asStateFlow()

    fun reportBreakdown() { _reporting.value = true }
    fun dismissReport() { _reporting.value = false }

    fun logService() { _service.value = MachineServiceDraft() }
    fun dismissService() { _service.value = null }
    fun setServiceDescription(v: String) = _service.update { it?.copy(description = v) }
    fun setServiceCost(v: String) = _service.update {
        it?.copy(costText = v.filter { c -> c.isDigit() })
    }
    fun setServiceVendor(v: String) = _service.update { it?.copy(vendor = v) }

    /**
     * Logs work done on this machine, without asking which machine.
     *
     * There is no picker because there is no choice: the answer is the screen the
     * user is standing on. Sending them to the fleet-wide list to pick it out of a
     * row of chips was asking a question that had already been answered, and landing
     * them somewhere they did not ask to go.
     */
    fun saveService() = viewModelScope.launch {
        val draft = _service.value ?: return@launch
        if (!draft.canSave) return@launch
        val s = app.sessionStore.load() ?: return@launch
        app.workRepository.recordService(
            orgId = s.orgId,
            machineId = machineId,
            description = draft.description,
            costRupees = draft.costRupees,
            vendor = draft.vendor,
        )
        _service.value = null
    }

    /**
     * Records a fault on this machine, with no job involved.
     *
     * The job-side report pauses the work it interrupted. This one has no work to
     * pause: the machine broke in the yard, or on the road, or was found broken this
     * morning — and until now there was nowhere to say so.
     */
    fun saveBreakdown(category: BreakdownCategory, note: String?) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        app.workRepository.reportBreakdownForMachine(
            orgId = s.orgId,
            machineId = machineId,
            category = category,
            note = note,
            byPersonId = s.personId,
        )
        _reporting.value = false
    }

    private fun JobEntity.asRow(customerName: String) = MachineJobRow(
        id = id,
        customerName = customerName,
        workType = workType,
        quantityLabel = quantity.toString(),
        amountRupees = amountRupees,
        workedAtMillis = workedAt,
        live = status.name == "Running" || status.name == "Paused",
    )
}
