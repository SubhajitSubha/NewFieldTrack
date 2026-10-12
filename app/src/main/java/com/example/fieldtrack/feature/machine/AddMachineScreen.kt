package com.example.fieldtrack.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.iconFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddMachineState(
    val kind: MachineKind = MachineKind.Tractor,
    val name: String = "",
    val plate: String = "",
    val hoursText: String = "",
    val serviceEveryText: String = "250",
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

class AddMachineViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(AddMachineState())
    val state: StateFlow<AddMachineState> = _state.asStateFlow()

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
        val s = app.sessionStore.load() ?: return@launch

        app.directoryRepository.addMachine(
            orgId = s.orgId,
            name = current.name,
            kind = current.kind,
            plate = current.plate.ifBlank { null },
            // Zero means "do not remind me", which is a real preference for an old
            // machine that gets serviced when it complains rather than on a schedule.
            serviceIntervalHours = current.serviceEveryText.toDoubleOrNull()?.takeIf { it > 0 },
        )
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/**
 * The machine form, used both to add and to correct.
 *
 * Hour meters drift and number plates get typed wrong, so the same fields that set a
 * machine up are the ones used to fix it later.
 */
@Composable
fun AddMachineScreen(
    state: AddMachineState,
    onBack: () -> Unit,
    onKind: (MachineKind) -> Unit,
    onName: (String) -> Unit,
    onPlate: (String) -> Unit,
    onHours: (String) -> Unit,
    onServiceEvery: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    titleRes: Int = R.string.machines_new_title,
    saveLabelRes: Int = R.string.machines_save,
    readOnlyNotice: String? = null,
) {
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(FieldTrackTheme.colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(titleRes),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_close_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                FtSectionLabel(
                    stringResource(R.string.machines_kind),
                    Modifier.padding(bottom = 10.dp),
                )
                // Icons first, because a driver picking a machine type recognises the
                // shape long before they read the word under it.
                MachineKindGrid(state.kind, onKind)
            }

            FtTextField(
                label = stringResource(R.string.machines_name),
                value = state.name,
                onValueChange = onName,
                placeholder = stringResource(R.string.machines_name_hint),
                focused = true,
            )
            FtTextField(
                label = stringResource(R.string.machines_plate),
                value = state.plate,
                onValueChange = onPlate,
                placeholder = stringResource(R.string.machines_plate_hint),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    FtTextField(
                        label = stringResource(R.string.machines_hours_used),
                        value = state.hoursText,
                        onValueChange = onHours,
                        keyboardType = KeyboardType.Number,
                    )
                }
                Box(Modifier.weight(1f)) {
                    FtTextField(
                        label = stringResource(R.string.machines_service_every),
                        value = state.serviceEveryText,
                        onValueChange = onServiceEvery,
                        suffix = stringResource(R.string.machines_service_unit),
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
            if (readOnlyNotice != null) {
                Text(
                    text = readOnlyNotice,
                    style = FieldTrackTheme.type.bodySmall,
                    color = FieldTrackTheme.colors.dueInk,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }

        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(
                text = stringResource(saveLabelRes),
                onClick = onSave,
                enabled = state.canSave && readOnlyNotice == null,
            )
        }
    }
}

@Composable
private fun MachineKindGrid(selected: MachineKind, onKind: (MachineKind) -> Unit) {
    val kinds = listOf(
        MachineKind.Tractor, MachineKind.Harvester, MachineKind.Rotavator,
        MachineKind.Cultivator, MachineKind.Trolley, MachineKind.Thresher,
        MachineKind.Sprayer, MachineKind.Transplanter, MachineKind.Other,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kinds.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { kind ->
                    KindTile(
                        kind = kind,
                        selected = kind == selected,
                        onClick = { onKind(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KindTile(
    kind: MachineKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier
            .clip(FieldTrackTheme.shapes.medium)
            .background(colors.surface)
            .border(
                width = if (selected) FieldTrackTheme.dimens.borderSelected
                else FieldTrackTheme.dimens.border,
                color = if (selected) colors.primary else colors.line,
                shape = FieldTrackTheme.shapes.medium,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = iconFor(kind),
            contentDescription = null,
            tint = if (selected) colors.primary else colors.inkSecondary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(kind.labelRes()),
            style = FieldTrackTheme.type.caption,
            color = if (selected) colors.primaryPressed else colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

fun MachineKind.labelRes(): Int = when (this) {
    MachineKind.Tractor -> R.string.machine_tractor
    MachineKind.Harvester -> R.string.machine_harvester
    MachineKind.Rotavator -> R.string.machine_rotavator
    MachineKind.Cultivator -> R.string.machine_cultivator
    MachineKind.Trolley -> R.string.machine_trolley
    MachineKind.Thresher -> R.string.machine_thresher
    MachineKind.Sprayer -> R.string.machine_sprayer
    MachineKind.Transplanter -> R.string.machine_transplanter
    MachineKind.PowerTiller -> R.string.machine_power_tiller
    MachineKind.Baler -> R.string.machine_baler
    MachineKind.Leveller -> R.string.machine_leveller
    MachineKind.Other -> R.string.machine_other
}
