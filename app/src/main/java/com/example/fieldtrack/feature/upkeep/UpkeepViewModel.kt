package com.example.fieldtrack.feature.upkeep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A machine that is not working, and what is wrong with it. */
data class BrokenMachine(
    val breakdownId: String,
    val machineId: String,
    val machineName: String,
    val kind: MachineKind,
    val category: BreakdownCategory,
    val note: String?,
    val reportedAtMillis: Long,
    /** True when a job is still open on it, waiting to be carried on. */
    val jobWaiting: Boolean,
)

/** A machine coming up for service. */
data class ServiceDue(
    val machineId: String,
    val machineName: String,
    val kind: MachineKind,
    val hoursToGo: Double,
    val lastServiceAtHours: Double,
    val dueAtHours: Double,
    val overdue: Boolean,
) {
    /** How far through the interval this machine is, for the bar. */
    val progress: Float
        get() {
            val interval = dueAtHours - lastServiceAtHours
            if (interval <= 0.0) return 1f
            return ((interval - hoursToGo) / interval).coerceIn(0.0, 1.0).toFloat()
        }
}

/** A service or repair that has been done and paid for. */
data class ServiceDone(
    val id: String,
    val machineName: String,
    val description: String,
    val vendor: String?,
    val costRupees: Long?,
    val atMillis: Long,
)

data class UpkeepState(
    val loaded: Boolean = false,
    val broken: List<BrokenMachine> = emptyList(),
    val dueSoon: List<ServiceDue> = emptyList(),
    val done: List<ServiceDone> = emptyList(),
    val machines: List<MachineEntity> = emptyList(),
    val canEdit: Boolean = true,
)

/** What is being typed into the "log a service" sheet. */
data class ServiceDraft(
    val machineId: String? = null,
    val description: String = "",
    val costText: String = "",
    val vendor: String = "",
    /** Set when this repair closes a specific breakdown. */
    val closesBreakdownId: String? = null,
) {
    val costRupees: Long? get() = costText.filter { it.isDigit() }.toLongOrNull()
    val canSave: Boolean get() = machineId != null && description.isNotBlank()
}

/**
 * Keeping the fleet running.
 *
 * The screen exists because reporting a breakdown used to be a one-way door: the
 * machine went Down and the only way out was to discard the job it happened on. An
 * owner could tell the truth about a seized pump or keep his record of the day's
 * work, not both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpkeepViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _draft = MutableStateFlow<ServiceDraft?>(null)
    val draft: StateFlow<ServiceDraft?> = _draft.asStateFlow()

    val state: StateFlow<UpkeepState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.workRepository.observeOpenBreakdowns(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.workRepository.observeServiceDue(s.orgId, SERVICE_HORIZON_HOURS),
            app.workRepository.observeRecentService(s.orgId),
            app.workRepository.observeMachinesWithLiveJobs(s.orgId),
        ) { breakdowns, machines, due, done, busyMachineIds ->
            val byId = machines.associateBy { it.id }
            UpkeepState(
                loaded = true,
                broken = breakdowns.mapNotNull { breakdown ->
                    val machine = byId[breakdown.machineId] ?: return@mapNotNull null
                    BrokenMachine(
                        breakdownId = breakdown.id,
                        machineId = machine.id,
                        machineName = machine.name,
                        kind = machine.kind,
                        category = breakdown.category,
                        note = breakdown.note,
                        reportedAtMillis = breakdown.reportedAt,
                        // Whether work is genuinely still open on it, not merely
                        // whether the breakdown happened during a job. That job may
                        // have been finished weeks ago, and telling somebody their
                        // work is waiting when it is not is worse than saying nothing.
                        jobWaiting = machine.id in busyMachineIds,
                    )
                },
                dueSoon = due.map { machine ->
                    val last = machine.lastServiceAtHours ?: 0.0
                    val dueAt = last + (machine.serviceIntervalHours ?: 0.0)
                    ServiceDue(
                        machineId = machine.id,
                        machineName = machine.name,
                        kind = machine.kind,
                        hoursToGo = dueAt - machine.hoursUsed,
                        lastServiceAtHours = last,
                        dueAtHours = dueAt,
                        overdue = machine.hoursUsed >= dueAt,
                    )
                },
                done = done.map { record ->
                    ServiceDone(
                        id = record.id,
                        machineName = byId[record.machineId]?.name.orEmpty(),
                        description = record.description,
                        vendor = record.vendor,
                        costRupees = record.costRupees,
                        atMillis = record.atMillis,
                    )
                },
                machines = machines,
                canEdit = Capability.EditBusiness in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpkeepState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }


    /** Fixed, with nothing to record about it. */
    fun markRepaired(breakdownId: String) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        app.workRepository.resolveBreakdown(s.orgId, breakdownId)
    }

    fun logService(machineId: String? = null) {
        _draft.value = ServiceDraft(machineId = machineId)
    }

    /** Log the repair that fixed a specific breakdown, closing it in the same act. */
    fun logRepair(broken: BrokenMachine) {
        _draft.value = ServiceDraft(
            machineId = broken.machineId,
            closesBreakdownId = broken.breakdownId,
        )
    }

    fun setDraftMachine(machineId: String) = _draft.update { it?.copy(machineId = machineId) }
    fun setDraftDescription(value: String) = _draft.update { it?.copy(description = value) }
    fun setDraftCost(value: String) = _draft.update {
        it?.copy(costText = value.filter { c -> c.isDigit() })
    }
    fun setDraftVendor(value: String) = _draft.update { it?.copy(vendor = value) }
    fun dismissDraft() { _draft.value = null }

    fun saveDraft() = viewModelScope.launch {
        val draft = _draft.value ?: return@launch
        if (!draft.canSave) return@launch
        val s = app.sessionStore.load() ?: return@launch
        app.workRepository.recordService(
            orgId = s.orgId,
            machineId = draft.machineId!!,
            description = draft.description,
            costRupees = draft.costRupees,
            vendor = draft.vendor,
            closesBreakdownId = draft.closesBreakdownId,
        )
        _draft.value = null
    }

    private companion object {
        /** Show a service as coming up once it is this close. */
        const val SERVICE_HORIZON_HOURS = 200.0
    }
}
