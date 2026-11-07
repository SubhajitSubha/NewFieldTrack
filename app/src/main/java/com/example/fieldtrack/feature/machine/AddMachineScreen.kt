package com.example.fieldtrack.feature.machine

import com.example.fieldtrack.designsystem.format.Money
import androidx.compose.foundation.background
import com.example.fieldtrack.designsystem.format.MoneyInput
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
import com.example.fieldtrack.designsystem.component.FtDropdown
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
    /** What the owner calls a machine the list has no name for, asked only when the kind is Other. */
    val typeLabel: String = "",
    val name: String = "",
    val plate: String = "",
    val hoursText: String = "",
    val serviceEveryText: String = "250",
    /**
     * What the form was opened with, so a save can tell a meter reading somebody
     * typed from today's hours simply shown back to them.
     */
    val initialHoursText: String = "",
    /** Loan instalment, insurance and the like, each month. */
    val monthlyText: String = "",
    val initialMonthlyText: String = "",
    /** Hours the machine works in a year, if the owner knows. */
    val hoursPerYearText: String = "",
    val initialHoursPerYearText: String = "",
    /** Whether this person may enter and see what a machine costs to own. */
    val canSeeFinance: Boolean = false,
    val saving: Boolean = false,
) {
    /** A year has 8,760 hours; nothing works more than that, so it is a typo. */
    val hoursPerYearTooMany: Boolean
        get() = (hoursPerYearText.toIntOrNull() ?: 0) > MAX_HOURS_PER_YEAR

    val hoursChanged: Boolean get() = hoursText != initialHoursText

    val financeChanged: Boolean
        get() = monthlyText != initialMonthlyText || hoursPerYearText != initialHoursPerYearText

    val canSave: Boolean get() = name.isNotBlank() && !saving && !hoursPerYearTooMany

    companion object {
        const val MAX_HOURS_PER_YEAR = 8_760
    }
}

class AddMachineViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(AddMachineState())
    val state: StateFlow<AddMachineState> = _state.asStateFlow()

    fun setKind(kind: MachineKind) = _state.update { it.copy(kind = kind) }
    fun setTypeLabel(v: String) = _state.update { it.copy(typeLabel = v.take(40)) }
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setPlate(v: String) = _state.update { it.copy(plate = v) }
    fun setHours(v: String) = _state.update { it.copy(hoursText = v.filter { c -> c.isDigit() }) }
    fun setServiceEvery(v: String) =
        _state.update { it.copy(serviceEveryText = v.filter { c -> c.isDigit() }) }
    fun setMonthly(v: String) =
        _state.update { it.copy(monthlyText = MoneyInput.clean(v)) }
    fun setHoursPerYear(v: String) =
        _state.update { it.copy(hoursPerYearText = v.filter { c -> c.isDigit() }.take(4)) }

    init {
        viewModelScope.launch {
            val can = app.sessionStore.load()?.can ?: return@launch
            _state.update { it.copy(canSeeFinance = canEnterFinance(can)) }
        }
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }

        val machineId = app.directoryRepository.addMachine(
            orgId = s.orgId,
            name = current.name,
            kind = current.kind,
            typeLabel = current.typeLabel.takeIf { current.kind == MachineKind.Other },
            plate = current.plate.ifBlank { null },
            // Zero means "do not remind me", which is a real preference for an old
            // machine that gets serviced when it complains rather than on a schedule.
            serviceIntervalHours = current.serviceEveryText.toDoubleOrNull()?.takeIf { it > 0 },
            hoursUsed = current.hoursText.toDoubleOrNull(),
        )
        if (current.canSeeFinance && current.financeChanged) {
            // A refusal here must not undo the machine that was just added.
            runCatching {
                app.directoryRepository.setMachineFinance(
                    orgId = s.orgId,
                    can = s.can,
                    machineId = machineId,
                    monthlyMinor = MoneyInput.parse(current.monthlyText),
                    hoursPerYear = current.hoursPerYearText.toIntOrNull(),
                )
            }
        }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/** Entering what a machine costs to own takes the same trust as seeing what the business pays. */
internal fun canEnterFinance(can: com.example.fieldtrack.data.session.Capabilities) =
    com.example.fieldtrack.data.session.Capability.EditMachines in can &&
        com.example.fieldtrack.data.session.Capability.SeePeopleCost in can

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
    onTypeLabel: (String) -> Unit = {},
    onName: (String) -> Unit,
    onPlate: (String) -> Unit,
    onHours: (String) -> Unit,
    onServiceEvery: (String) -> Unit,
    onMonthly: (String) -> Unit,
    onHoursPerYear: (String) -> Unit,
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
            // Icon beside the name, because a driver recognises the shape before the word;
            // the list opens in a sheet so it no longer fills the form.
            FtDropdown(
                options = MachineKinds,
                selected = state.kind,
                text = { stringResource(it.labelRes()) },
                icon = { iconFor(it) },
                onPick = onKind,
                label = stringResource(R.string.machines_kind),
                searchHint = stringResource(R.string.machines_kind_search),
            )

            if (state.kind == MachineKind.Other) {
                FtTextField(
                    label = stringResource(R.string.machines_type_label),
                    value = state.typeLabel,
                    onValueChange = onTypeLabel,
                    placeholder = stringResource(R.string.machines_type_label_hint),
                )
            }

            FtTextField(
                label = stringResource(R.string.machines_name),
                value = state.name,
                onValueChange = onName,
                placeholder = stringResource(state.kind.nameHintRes()),
                focused = true,
            )
            if (state.kind.hasPlate || state.plate.isNotBlank()) {
                FtTextField(
                    label = stringResource(R.string.machines_plate),
                    value = state.plate,
                    onValueChange = onPlate,
                    placeholder = stringResource(R.string.machines_plate_hint),
                )
            }
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
            // Only for somebody who may see what the business pays: a driver adding
            // a machine is not asked, and is not told there is a question to skip.
            if (state.canSeeFinance) {
                FtSectionLabel(
                    stringResource(R.string.machines_own_title),
                    Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        FtTextField(
                            label = stringResource(R.string.machines_own_monthly),
                            value = state.monthlyText,
                            onValueChange = onMonthly,
                            prefix = Money.style.symbol,
                            suffix = stringResource(R.string.machines_own_per_month),
                            keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        FtTextField(
                            label = stringResource(R.string.machines_own_hours_year),
                            value = state.hoursPerYearText,
                            onValueChange = onHoursPerYear,
                            suffix = stringResource(R.string.machines_service_unit),
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }
                Text(
                    text = if (state.hoursPerYearTooMany) {
                        stringResource(R.string.machines_own_too_many)
                    } else {
                        stringResource(R.string.machines_own_note)
                    },
                    style = FieldTrackTheme.type.bodySmall,
                    color = if (state.hoursPerYearTooMany) {
                        FieldTrackTheme.colors.dueInk
                    } else {
                        FieldTrackTheme.colors.inkTertiary
                    },
                )
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
    MachineKind.Excavator -> R.string.machine_excavator
    MachineKind.Seeder -> R.string.machine_seeder
    MachineKind.Mulcher -> R.string.machine_mulcher
    MachineKind.Plough -> R.string.machine_plough
    MachineKind.Harrow -> R.string.machine_harrow
    MachineKind.DroneSprayer -> R.string.machine_drone_sprayer
    MachineKind.WaterTanker -> R.string.machine_water_tanker
    MachineKind.SugarcaneHarvester -> R.string.machine_sugarcane_harvester
    MachineKind.PotatoDigger -> R.string.machine_potato_digger
    MachineKind.Ridger -> R.string.machine_ridger
    MachineKind.GrainCart -> R.string.machine_grain_cart
    MachineKind.Pump -> R.string.machine_pump
}
