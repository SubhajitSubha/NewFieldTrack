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
import com.example.fieldtrack.data.db.HireBasis
import com.example.fieldtrack.data.db.MachineOwner
import kotlinx.coroutines.flow.first
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtPick
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
    /** Whose machine it is: the business's own unless somebody says otherwise. */
    val owner: MachineOwner = MachineOwner.Own,
    val ownerPersonId: String? = null,
    val ownerName: String = "",
    val hireBasis: HireBasis = HireBasis.PerHour,
    val hireRateText: String = "",
    val hireShareText: String = "",
    /** The team members a machine could belong to: (person id, name). */
    val team: List<Pair<String, String>> = emptyList(),
    /** The business's land unit, which a rate by the land covered is per. */
    val landUnit: com.example.fieldtrack.data.db.MeasureUnit = com.example.fieldtrack.data.db.MeasureUnit.Acre,
    /** The ownership the form opened with, so a save can tell a change from a redisplay. */
    val initialOwnership: String = "",
) {
    /** Everything about whose machine it is, in one comparable line. */
    val ownershipSignature: String
        get() = listOf(owner, ownerPersonId, ownerName.trim(), hireBasis, hireRateText, hireShareText).joinToString("|")

    val ownershipChanged: Boolean get() = ownershipSignature != initialOwnership

    val hireRateMinor: Long? get() = MoneyInput.parse(hireRateText)?.takeIf { it > 0 }
    val hireSharePercent: Int? get() = hireShareText.toIntOrNull()?.takeIf { it in 1..100 }

    /** Somebody else's machine is only complete once it says who and how they are paid. */
    val ownershipComplete: Boolean
        get() = when (owner) {
            MachineOwner.Own -> true
            else -> (if (owner == MachineOwner.Member) ownerPersonId != null else ownerName.isNotBlank()) &&
                (if (hireBasis == HireBasis.Share) hireSharePercent != null else hireRateMinor != null)
        }

    /** A year has 8,760 hours; nothing works more than that, so it is a typo. */
    val hoursPerYearTooMany: Boolean
        get() = (hoursPerYearText.toIntOrNull() ?: 0) > MAX_HOURS_PER_YEAR

    val hoursChanged: Boolean get() = hoursText != initialHoursText

    val financeChanged: Boolean
        get() = monthlyText != initialMonthlyText || hoursPerYearText != initialHoursPerYearText

    val canSave: Boolean get() = name.isNotBlank() && !saving && !hoursPerYearTooMany && ownershipComplete

    companion object {
        const val MAX_HOURS_PER_YEAR = 8_760
    }
}

internal fun MutableStateFlow<AddMachineState>.setOwner(kind: MachineOwner) = update { it.copy(owner = kind) }
internal fun MutableStateFlow<AddMachineState>.setOwnerPerson(personId: String) = update { it.copy(ownerPersonId = personId) }
internal fun MutableStateFlow<AddMachineState>.setOwnerName(v: String) = update { it.copy(ownerName = v.take(60)) }
internal fun MutableStateFlow<AddMachineState>.setHireBasis(basis: HireBasis) = update { it.copy(hireBasis = basis) }
internal fun MutableStateFlow<AddMachineState>.setHireRate(v: String) = update { it.copy(hireRateText = MoneyInput.clean(v)) }
internal fun MutableStateFlow<AddMachineState>.setHireShare(v: String) = update { it.copy(hireShareText = v.filter { c -> c.isDigit() }.take(3)) }

/** Writes whose machine it is, when that was changed; a refusal must not undo the rest of the save. */
internal suspend fun saveOwnership(
    app: FieldFlowApplication,
    session: com.example.fieldtrack.data.session.Session,
    machineId: String,
    s: AddMachineState,
) {
    if (!s.canSeeFinance || !s.ownershipChanged) return
    runCatching {
        app.directoryRepository.setMachineOwnership(
            orgId = session.orgId, can = session.can, machineId = machineId, kind = s.owner,
            ownerPersonId = s.ownerPersonId, ownerName = s.ownerName,
            basis = s.hireBasis.takeIf { s.owner != MachineOwner.Own },
            rateMinor = s.hireRateMinor, sharePercent = s.hireSharePercent,
        )
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

    fun setOwner(kind: MachineOwner) = _state.setOwner(kind)
    fun setOwnerPerson(personId: String) = _state.setOwnerPerson(personId)
    fun setOwnerName(v: String) = _state.setOwnerName(v)
    fun setHireBasis(basis: HireBasis) = _state.setHireBasis(basis)
    fun setHireRate(v: String) = _state.setHireRate(v)
    fun setHireShare(v: String) = _state.setHireShare(v)

    init {
        viewModelScope.launch {
            val session = app.sessionStore.load() ?: return@launch
            val team = app.teamRepository.observeTeam(session.orgId).first()
                .filter { it.active }.map { it.personId to it.displayName }
            _state.update {
                val opened = it.copy(
                    canSeeFinance = canEnterFinance(session.can), team = team, landUnit = session.landUnit,
                )
                opened.copy(initialOwnership = opened.ownershipSignature)
            }
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
                app.directoryRepository.setLoan(
                    orgId = s.orgId,
                    can = s.can,
                    machineId = machineId,
                    monthlyMinor = MoneyInput.parse(current.monthlyText),
                    hoursPerYear = current.hoursPerYearText.toIntOrNull(),
                )
            }
        }
        saveOwnership(app, s, machineId, current)
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/** Entering what a machine costs to own takes the same trust as seeing what the business pays. */
internal fun canEnterFinance(can: com.example.fieldtrack.data.session.Capabilities) =
    com.example.fieldtrack.data.session.Capability.EditMachines in can &&
        com.example.fieldtrack.data.session.Capability.SeePeopleCost in can

/** The editors for whose machine it is, grouped so they travel as one argument. */
class OwnerActions(
    val onOwner: (MachineOwner) -> Unit,
    val onPerson: (String) -> Unit,
    val onName: (String) -> Unit,
    val onBasis: (HireBasis) -> Unit,
    val onRate: (String) -> Unit,
    val onShare: (String) -> Unit,
)

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
    owner: OwnerActions? = null,
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
            if (state.canSeeFinance && owner != null) {
                OwnershipSection(state, owner)
            }
            if (state.canSeeFinance && state.owner == MachineOwner.Own) {
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


/**
 * Whose machine it is, and for somebody else's, how they are paid.
 *
 * Kept to one question until the answer is "somebody else's": most machines are the
 * business's own and never see the rest.
 */
@Composable
private fun OwnershipSection(state: AddMachineState, actions: OwnerActions) {
    val colors = FieldTrackTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FtPick(
            options = MachineOwner.entries.filter { it != MachineOwner.Member || state.team.isNotEmpty() },
            selected = state.owner,
            text = {
                stringResource(
                    when (it) {
                        MachineOwner.Own -> R.string.owner_own
                        MachineOwner.Member -> R.string.owner_member
                        MachineOwner.Outsider -> R.string.owner_outsider
                    }
                )
            },
            onPick = actions.onOwner,
            label = stringResource(R.string.owner_title),
        )
        if (state.owner == MachineOwner.Member) {
            FtPick(
                options = state.team.map { it.first },
                selected = state.ownerPersonId,
                text = { id -> state.team.firstOrNull { it.first == id }?.second.orEmpty() },
                onPick = actions.onPerson,
                label = stringResource(R.string.owner_which_member),
                searchHint = stringResource(R.string.pick_search),
            )
        }
        if (state.owner == MachineOwner.Outsider) {
            FtTextField(
                label = stringResource(R.string.owner_their_name),
                value = state.ownerName,
                onValueChange = actions.onName,
            )
        }
        if (state.owner != MachineOwner.Own) {
            FtPick(
                options = HireBasis.entries,
                selected = state.hireBasis,
                text = {
                    stringResource(
                        when (it) {
                            HireBasis.PerHour -> R.string.hire_by_hour
                            HireBasis.PerDay -> R.string.hire_by_day
                            HireBasis.PerAcre -> R.string.hire_by_land
                            HireBasis.Share -> R.string.hire_by_share
                        }
                    )
                },
                onPick = actions.onBasis,
                label = stringResource(R.string.hire_basis_label),
            )
            if (state.hireBasis == HireBasis.Share) {
                FtTextField(
                    label = stringResource(R.string.hire_share_label),
                    value = state.hireShareText,
                    onValueChange = actions.onShare,
                    suffix = "%",
                    keyboardType = KeyboardType.Number,
                )
            } else {
                val per = when (state.hireBasis) {
                    HireBasis.PerHour -> com.example.fieldtrack.data.db.MeasureUnit.Hour
                    HireBasis.PerDay -> com.example.fieldtrack.data.db.MeasureUnit.Day
                    else -> state.landUnit
                }
                FtTextField(
                    label = stringResource(R.string.hire_rate_label),
                    value = state.hireRateText,
                    onValueChange = actions.onRate,
                    prefix = Money.style.symbol,
                    suffix = stringResource(R.string.start_rate_per, com.example.fieldtrack.feature.unitInPhrase(per)),
                    keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
                )
            }
            Text(
                text = stringResource(R.string.hire_note),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkTertiary,
            )
        }
    }
}
