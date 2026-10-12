package com.example.fieldtrack.feature.machine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Correcting a machine.
 *
 * Shares [AddMachineState] with the add flow so there is one form rather than two
 * that drift apart. Unlike adding, this one writes the hour meter back: a reading
 * typed in a hurry at the headland is the thing most often wrong, and a wrong meter
 * silently moves every service reminder after it.
 */
class EditMachineViewModel(
    private val app: FieldFlowApplication,
    private val machineId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AddMachineState())
    val state: StateFlow<AddMachineState> = _state.asStateFlow()

    private val _allowed = MutableStateFlow(true)
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            _allowed.value = Capability.EditMachines in s.can
            val machine = app.directoryRepository.findMachine(s.orgId, machineId)
                ?: return@launch
            _state.update {
                it.copy(
                    kind = machine.kind,
                    name = machine.name,
                    plate = machine.plate.orEmpty(),
                    hoursText = machine.hoursUsed.toLong().toString(),
                    serviceEveryText = machine.serviceIntervalHours?.toLong()?.toString().orEmpty(),
                )
            }
        }
    }

    fun setKind(kind: MachineKind) = _state.update { it.copy(kind = kind) }
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPlate(v: String) = _state.update { it.copy(plate = v) }
    fun setHours(v: String) = _state.update { it.copy(hoursText = v.filter { c -> c.isDigit() }) }
    fun setServiceEvery(v: String) =
        _state.update { it.copy(serviceEveryText = v.filter { c -> c.isDigit() }) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        runCatching {
            app.directoryRepository.updateMachine(
                orgId = s.orgId,
                can = s.can,
                machineId = machineId,
                name = current.name,
                kind = current.kind,
                plate = current.plate,
                hoursUsed = current.hoursText.toDoubleOrNull(),
                // Zero means "do not remind me", which is a real preference for an old
                // machine that gets serviced when it complains rather than on a schedule.
                serviceIntervalHours = current.serviceEveryText.toDoubleOrNull()?.takeIf { it > 0 },
            )
        }.onFailure { _allowed.value = false }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}
