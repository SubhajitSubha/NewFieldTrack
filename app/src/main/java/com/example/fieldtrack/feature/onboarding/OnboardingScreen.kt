package com.example.fieldtrack.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.feature.region.CurrencyPickerSheet
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.voice.rememberVoiceInput

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onBack: () -> Unit,
    onOwnerKind: (OwnerKind) -> Unit,
    onName: (String) -> Unit,
    onBusinessName: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onLandUnit: (MeasureUnit) -> Unit,
    onMachineKind: (MachineKind) -> Unit,
    onMachineName: (String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onMachineTypeLabel: (String) -> Unit = {},
) {
    if (state.step == OnboardingStep.Welcome) {
        WelcomeStep(onNext, modifier)
        return
    }

    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = when (state.step) {
                OnboardingStep.Role -> stringResource(R.string.onboard_role_title)
                OnboardingStep.You -> stringResource(R.string.onboard_you_title)
                OnboardingStep.Business -> stringResource(R.string.onboard_business_title)
                OnboardingStep.Region -> stringResource(R.string.onboard_region_title)
                OnboardingStep.Machine -> stringResource(R.string.onboard_machine_title)
                else -> stringResource(R.string.onboard_done_title)
            },
            onBack = if (state.step != OnboardingStep.Done) onBack else null,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (state.step) {
                OnboardingStep.Role -> RoleStep(state, onOwnerKind)
                OnboardingStep.You -> YouStep(state, onName)
                OnboardingStep.Business -> BusinessStep(state, onBusinessName)
                OnboardingStep.Region -> RegionStep(state, onCurrency, onLandUnit)
                OnboardingStep.Machine -> MachineStep(state, onMachineKind, onMachineName, onMachineTypeLabel)
                else -> DoneStep(state)
            }
            Box(Modifier.height(dimens.space4))
        }

        Box(Modifier.padding(dimens.screenPadding)) {
            when (state.step) {
                OnboardingStep.Machine -> FtButton(
                    text = stringResource(R.string.onboard_finish),
                    onClick = onNext,
                    enabled = !state.saving,
                    height = 60.dp,
                )
                OnboardingStep.Done -> FtButton(
                    text = stringResource(R.string.onboard_done_action),
                    onClick = onFinish,
                    icon = FieldTrackIcons.Play,
                    enabled = !state.saving,
                    height = 62.dp,
                )
                else -> FtButton(
                    text = stringResource(R.string.action_continue),
                    onClick = onNext,
                    enabled = state.canAdvance,
                    height = 60.dp,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onboard_welcome_title),
                style = FieldTrackTheme.type.moneyLarge,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.onboard_welcome_body),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 14.dp, bottom = 32.dp),
            )

            Point(FieldTrackIcons.Play, R.string.onboard_point_start, R.string.onboard_point_start_body, colors.primaryWash, colors.primary)
            Point(FieldTrackIcons.money(), R.string.onboard_point_owed, R.string.onboard_point_owed_body, colors.dueWash, colors.due)
            Point(FieldTrackIcons.WhatsApp, R.string.onboard_point_bill, R.string.onboard_point_bill_body, colors.primaryWash, colors.send)
        }

        FtButton(
            text = stringResource(R.string.onboard_get_started),
            onClick = onNext,
            height = 62.dp,
        )
        Text(
            text = stringResource(R.string.onboard_no_signup),
            style = FieldTrackTheme.type.caption,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun Point(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titleRes: Int,
    bodyRes: Int,
    tileBackground: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FtIconTile(icon, size = 52.dp, background = tileBackground, tint = tint)
        Column {
            Text(
                text = stringResource(titleRes),
                style = FieldTrackTheme.type.sectionTitle,
                color = FieldTrackTheme.colors.ink,
            )
            Text(
                text = stringResource(bodyRes),
                style = FieldTrackTheme.type.bodySmall,
                color = FieldTrackTheme.colors.inkTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.RoleStep(state: OnboardingState, onOwnerKind: (OwnerKind) -> Unit) {
    Text(
        text = stringResource(R.string.onboard_role_caption),
        style = FieldTrackTheme.type.bodySmall,
        color = FieldTrackTheme.colors.inkSecondary,
    )
    FtChoiceCard(
        title = stringResource(R.string.onboard_role_solo),
        description = stringResource(R.string.onboard_role_solo_body),
        icon = FieldTrackIcons.Tractor,
        selected = state.ownerKind == OwnerKind.Solo,
        onClick = { onOwnerKind(OwnerKind.Solo) },
    )
    FtChoiceCard(
        title = stringResource(R.string.onboard_role_business),
        description = stringResource(R.string.onboard_role_business_body),
        icon = FieldTrackIcons.Team,
        selected = state.ownerKind == OwnerKind.Business,
        onClick = { onOwnerKind(OwnerKind.Business) },
    )
    // Honest about what does not work yet rather than offering a door that opens on
    // nothing: joining someone else's business needs a server, which arrives with sync.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FieldTrackTheme.shapes.medium)
            .background(FieldTrackTheme.colors.surfaceMuted)
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = FieldTrackIcons.Lock,
            contentDescription = null,
            tint = FieldTrackTheme.colors.inkTertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.onboard_role_driver_note),
            style = FieldTrackTheme.type.caption,
            color = FieldTrackTheme.colors.inkSecondary,
        )
    }
}

@Composable
private fun ColumnScope.YouStep(state: OnboardingState, onName: (String) -> Unit) {
    Text(
        text = stringResource(R.string.onboard_you_caption),
        style = FieldTrackTheme.type.bodySmall,
        color = FieldTrackTheme.colors.inkSecondary,
    )
    val voice = rememberVoiceInput(
        prompt = stringResource(R.string.onboard_your_name_hint),
        onText = onName,
    )
    FtTextField(
        label = stringResource(R.string.onboard_your_name),
        value = state.name,
        onValueChange = onName,
        placeholder = stringResource(R.string.onboard_your_name_hint),
        focused = true,
        onVoiceInput = if (voice.available) voice.start else null,
        voiceContentDescription = stringResource(R.string.onboard_speak_name_cd),
    )
}

@Composable
private fun ColumnScope.BusinessStep(
    state: OnboardingState,
    onBusinessName: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.onboard_business_caption),
        style = FieldTrackTheme.type.bodySmall,
        color = FieldTrackTheme.colors.inkSecondary,
    )
    FtTextField(
        label = stringResource(R.string.onboard_business_name),
        value = state.businessName,
        onValueChange = onBusinessName,
        placeholder = stringResource(R.string.onboard_business_name_hint),
        focused = true,
    )
}

/**
 * The two things that are costly to get wrong, shown already filled in from the phone.
 *
 * A wrong currency would write every amount in the wrong money, and a wrong land
 * unit would quote every field in a measure the farmer does not use. Everything else
 * the phone told us is used without asking.
 */
@Composable
private fun ColumnScope.RegionStep(
    state: OnboardingState,
    onCurrency: (String) -> Unit,
    onLandUnit: (MeasureUnit) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.onboard_region_caption),
        style = FieldTrackTheme.type.bodySmall,
        color = FieldTrackTheme.colors.inkSecondary,
    )
    FtSectionLabel(stringResource(R.string.onboard_money_in))
    FtCard(padding = 0.dp) {
        FtListRow(
            title = Region.nameOf(state.currencyCode),
            subtitle = "${MoneyStyle.symbolOf(state.currencyCode)} · ${state.currencyCode}",
            showChevron = true,
            onClick = { picking = true },
        )
    }
    LandUnitPicker(state, onLandUnit)
    if (picking) {
        CurrencyPickerSheet(
            current = state.currencyCode,
            guessed = state.guessedCurrency,
            onPick = { onCurrency(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.LandUnitPicker(state: OnboardingState, onLandUnit: (MeasureUnit) -> Unit) {
    FtSectionLabel(stringResource(R.string.onboard_land_unit))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            MeasureUnit.Acre, MeasureUnit.Hectare, MeasureUnit.Bigha, MeasureUnit.Kanal, MeasureUnit.Guntha
        ).forEach { unit ->
            FtChip(
                label = stringResource(unit.labelRes),
                selected = state.landUnit == unit,
                onClick = { onLandUnit(unit) },
            )
        }
    }
}

@Composable
private fun ColumnScope.MachineStep(
    state: OnboardingState,
    onMachineKind: (MachineKind) -> Unit,
    onMachineName: (String) -> Unit,
    onMachineTypeLabel: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.onboard_machine_caption),
        style = FieldTrackTheme.type.bodySmall,
        color = FieldTrackTheme.colors.inkSecondary,
    )
    val kinds = listOf(
        MachineKind.Tractor, MachineKind.Harvester, MachineKind.Rotavator,
        MachineKind.Trolley, MachineKind.Thresher, MachineKind.Sprayer,
    )
    kinds.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { kind ->
                MachineTile(
                    kind = kind,
                    selected = state.machineKind == kind,
                    onClick = { onMachineKind(kind) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    // The six above are the common ones; every other kind, and "Other" with its own name, is here.
    com.example.fieldtrack.designsystem.component.FtDropdown(
        options = com.example.fieldtrack.feature.machine.MachineKinds.filter { it !in kinds },
        selected = state.machineKind.takeIf { it !in kinds },
        text = { stringResource(it.labelRes()) },
        icon = { iconFor(it) },
        onPick = onMachineKind,
        placeholder = stringResource(R.string.onboard_machine_more),
        searchHint = stringResource(R.string.machines_kind_search),
        sheetTitle = stringResource(R.string.machines_kind),
    )
    if (state.machineKind == MachineKind.Other) {
        FtTextField(
            label = stringResource(R.string.machines_type_label),
            value = state.machineTypeLabel,
            onValueChange = onMachineTypeLabel,
            placeholder = stringResource(R.string.machines_type_label_hint),
        )
    }
    FtTextField(
        label = stringResource(R.string.onboard_machine_name),
        value = state.machineName,
        onValueChange = onMachineName,
        placeholder = stringResource(R.string.onboard_machine_name_hint),
        helper = stringResource(R.string.onboard_machine_skip),
    )
}

@Composable
private fun MachineTile(
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
            .padding(vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = iconFor(kind),
            contentDescription = null,
            tint = if (selected) colors.primary else colors.inkSecondary,
            modifier = Modifier.size(FieldTrackTheme.dimens.machineIcon),
        )
        Text(
            text = stringResource(kind.labelRes()),
            style = FieldTrackTheme.type.caption,
            color = if (selected) colors.primaryPressed else colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.DoneStep(state: OnboardingState) {
    val colors = FieldTrackTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(colors.primaryWash),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FieldTrackIcons.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.onboard_done_body, state.effectiveBusinessName),
        style = FieldTrackTheme.type.body,
        color = colors.inkSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
    if (state.machineName.isNotBlank()) {
        FtCard(padding = 15.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FtIconTile(iconFor(state.machineKind), size = 42.dp)
                Text(
                    text = state.machineName,
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                    modifier = Modifier.padding(start = 13.dp),
                )
            }
        }
    }
}

private fun MachineKind.labelRes(): Int = when (this) {
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
