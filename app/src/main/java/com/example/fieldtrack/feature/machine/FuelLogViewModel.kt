package com.example.fieldtrack.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
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

/** One tank-fill. */
data class FuelRow(
    val id: String,
    val litres: Double,
    val costRupees: Long,
    val atMillis: Long,
    val byName: String?,
    val onJob: Boolean,
)

data class FuelLogState(
    val loaded: Boolean = false,
    val machineName: String = "",
    val totalRupees: Long = 0,
    val totalLitres: Double = 0.0,
    val hoursUsed: Double = 0.0,
    val entries: List<FuelRow> = emptyList(),
    val canRecord: Boolean = true,
) {
    /**
     * Litres per hour, or null when there is not enough to say.
     *
     * Shown only once the machine has run long enough for the figure to mean
     * something. A tractor filled once after twenty minutes would otherwise read as
     * drinking a hundred litres an hour, which is the sort of number that gets
     * believed and then worried about.
     */
    val litresPerHour: Double?
        get() = if (hoursUsed >= MIN_HOURS && totalLitres > 0) totalLitres / hoursUsed else null

    private companion object {
        const val MIN_HOURS = 10.0
    }
}

data class FuelDraft(val litresText: String = "", val costText: String = "") {
    val litres: Double get() = litresText.toDoubleOrNull() ?: 0.0
    val costRupees: Long get() = costText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val canSave: Boolean get() = litres > 0 && costRupees > 0
}

/** What a machine has drunk, and what that cost. */
@OptIn(ExperimentalCoroutinesApi::class)
class FuelLogViewModel(
    private val app: FieldFlowApplication,
    private val machineId: String,
) : ViewModel() {

    private val _draft = MutableStateFlow<FuelDraft?>(null)
    val draft: StateFlow<FuelDraft?> = _draft.asStateFlow()

    val state: StateFlow<FuelLogState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeMachine(s.orgId, machineId),
            app.workRepository.observeFuel(s.orgId, machineId),
            app.teamRepository.observeEveryone(s.orgId),
        ) { machine, entries, team ->
            val names = team.associate { it.personId to it.displayName }
            FuelLogState(
                loaded = true,
                machineName = machine?.name.orEmpty(),
                totalRupees = entries.sumOf { it.costRupees },
                totalLitres = entries.sumOf { it.litres },
                hoursUsed = machine?.hoursUsed ?: 0.0,
                entries = entries.map { entry ->
                    FuelRow(
                        id = entry.id,
                        litres = entry.litres,
                        costRupees = entry.costRupees,
                        atMillis = entry.atMillis,
                        byName = entry.byPersonId?.let { names[it] },
                        onJob = entry.jobId != null,
                    )
                },
                canRecord = Capability.RecordWork in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelLogState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun add() { _draft.value = FuelDraft() }
    fun dismiss() { _draft.value = null }
    fun setLitres(v: String) = _draft.update {
        it?.copy(litresText = v.filter { c -> c.isDigit() || c == '.' })
    }
    fun setCost(v: String) = _draft.update {
        it?.copy(costText = v.filter { c -> c.isDigit() })
    }

    fun save() = viewModelScope.launch {
        val draft = _draft.value ?: return@launch
        if (!draft.canSave) return@launch
        val s = app.sessionStore.load() ?: return@launch
        app.workRepository.addFuelForMachine(
            orgId = s.orgId,
            machineId = machineId,
            litres = draft.litres,
            costRupees = draft.costRupees,
            byPersonId = s.personId,
        )
        _draft.value = null
    }
}
