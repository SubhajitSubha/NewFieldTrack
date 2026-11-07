package com.example.fieldtrack.feature.plan

import androidx.compose.ui.text.input.KeyboardType
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.data.db.PaymentMethod
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.CancelReason
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChipTone
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.work.ClockSheet
import com.example.fieldtrack.feature.work.PickCustomerSheet
import com.example.fieldtrack.feature.workTypeOptions

/** What a booking screen can ask of whatever is behind it. */
class BookingFormActions(
    val onQuery: (String) -> Unit,
    val onCustomer: (CustomerEntity) -> Unit,
    val onNewFarmer: () -> Unit,
    val onField: (String?) -> Unit,
    val onWork: (WorkTypeOption) -> Unit,
    val onDay: (Long) -> Unit,
    val onPart: (DayPart) -> Unit,
    val onTime: (Int?) -> Unit,
    val onMachine: (String?) -> Unit,
    val onDriver: (String?) -> Unit,
    val onNote: (String) -> Unit,
    val onAdvance: (String) -> Unit = {},
    val onAdvanceMethod: (PaymentMethod) -> Unit = {},
    val onSave: () -> Unit,
    val onCancel: (CancelReason) -> Unit,
    val onDone: () -> Unit,
    val onReopen: () -> Unit,
)

/**
 * One booking, new or being changed.
 *
 * Ordered as the phone call goes: the farmer, the work, the day, and only then the
 * machine and the driver — the two a call rarely settles, and so the two that can be
 * left as they are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingFormScreen(
    state: BookingFormState,
    onBack: () -> Unit,
    actions: BookingFormActions,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var choosingFarmer by rememberSaveable { mutableStateOf(false) }
    var choosingDay by rememberSaveable { mutableStateOf(false) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    var cancelling by rememberSaveable { mutableStateOf(false) }

    AdaptiveScreen(modifier.imePadding()) {
        FtTopBar(
            title = stringResource(if (state.id == null) R.string.plan_form_new else R.string.plan_form_edit),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (!state.loaded) return@AdaptiveScreen

        if (state.notFound || (!state.canPlan && state.id == null)) {
            FtCard(modifier = Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(if (state.notFound) R.string.plan_form_missing else R.string.plan_form_not_for_you),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            return@AdaptiveScreen
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Banner(state)

            FtSectionLabel(stringResource(R.string.plan_form_for))
            FtCard(
                onClick = if (state.editable) { { choosingFarmer = true } } else null,
                padding = 12.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.customer?.let { customer ->
                        FtAvatar(
                            initialsOf(customer.name),
                            size = 40.dp,
                            background = colors.primaryWash,
                            contentColor = colors.primaryPressed,
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = if (state.customer != null) 12.dp else 0.dp)) {
                        Text(
                            text = state.customer?.name ?: stringResource(R.string.plan_form_choose_farmer),
                            style = FieldTrackTheme.type.rowTitle,
                            color = if (state.customer != null) colors.ink else colors.primary,
                        )
                        state.customer?.village?.let {
                            Text(text = it, style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
                        }
                    }
                    if (state.editable && state.customer != null) {
                        Text(
                            text = stringResource(R.string.plan_form_change),
                            style = FieldTrackTheme.type.label,
                            color = colors.primary,
                        )
                    }
                }
            }

            if (state.fields.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.field_which))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.fields.forEach { field ->
                        FtChip(
                            label = field.name,
                            selected = state.fieldId == field.id,
                            onClick = { if (state.editable) actions.onField(field.id) },
                        )
                    }
                    FtChip(
                        label = stringResource(R.string.field_not_said),
                        selected = state.fieldId == null,
                        onClick = { if (state.editable) actions.onField(null) },
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.plan_form_work))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                workTypeOptions.forEach { option ->
                    FtChip(
                        label = stringResource(option.labelRes),
                        selected = state.workType.key == option.key,
                        onClick = { if (state.editable) actions.onWork(option) },
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.plan_form_day))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val week = (0..6).map { state.today + it }
                // A day outside the week, or one that has gone: shown as chosen, so the
                // screen says what is saved rather than quietly showing something else.
                if (state.day !in week) {
                    FtChip(label = dayDate(state.day), selected = true, onClick = {})
                }
                week.forEach { day ->
                    FtChip(
                        label = if (day - state.today <= 1) dayName(day, state.today) else dayDate(day),
                        selected = state.day == day,
                        onClick = { if (state.editable) actions.onDay(day) },
                    )
                }
                FtChip(
                    label = stringResource(R.string.plan_another_day),
                    selected = false,
                    onClick = { if (state.editable) choosingDay = true },
                )
            }
            if (state.day < state.today && state.status == BookingStatus.Planned && !state.started) {
                Text(
                    text = stringResource(R.string.plan_form_day_passed),
                    style = FieldTrackTheme.type.caption,
                    color = colors.dueInk,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayPart.entries.forEach { part ->
                    FtChip(
                        label = partName(part),
                        selected = state.part == part,
                        onClick = { if (state.editable) actions.onPart(part) },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FtChip(
                    label = stringResource(R.string.plan_any_time),
                    selected = state.startMinute == null,
                    onClick = { if (state.editable) actions.onTime(null) },
                )
                FtChip(
                    label = state.startMinute?.let { stringResource(R.string.plan_at_time, timeOfDay(it)) }
                        ?: stringResource(R.string.plan_set_time),
                    selected = state.startMinute != null,
                    onClick = { if (state.editable) choosingTime = true },
                )
            }

            FtSectionLabel(stringResource(R.string.start_machine))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FtChip(
                    label = stringResource(R.string.plan_no_machine),
                    selected = state.machineId == null,
                    onClick = { if (state.editable) actions.onMachine(null) },
                )
                if (state.machineGone) {
                    FtChip(
                        label = stringResource(R.string.plan_machine_removed),
                        selected = true,
                        onClick = {},
                        tone = FtChipTone.Warning,
                    )
                }
                state.machines.forEach { machine ->
                    val down = machine.status == MachineStatus.Down
                    FtChip(
                        label = if (down) stringResource(R.string.start_machine_down, machine.name) else machine.name,
                        selected = state.machineId == machine.id,
                        onClick = { if (state.editable) actions.onMachine(machine.id) },
                        tone = if (down) FtChipTone.Warning else FtChipTone.Normal,
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.plan_form_driver))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FtChip(
                    label = stringResource(R.string.plan_anyone),
                    selected = state.driverId == null,
                    onClick = { if (state.editable) actions.onDriver(null) },
                )
                // Somebody who has left stays visible while they are the one chosen, so the
                // screen does not quietly show "anyone" over a booking that names them.
                state.team.filter { it.active || it.personId == state.driverId }.forEach { member ->
                    FtChip(
                        label = if (member.active) member.displayName
                        else stringResource(R.string.plan_driver_left, member.displayName),
                        selected = state.driverId == member.personId,
                        onClick = { if (state.editable) actions.onDriver(member.personId) },
                        tone = if (member.active) FtChipTone.Normal else FtChipTone.Warning,
                    )
                }
            }

            Warnings(state)

            FtTextField(
                label = stringResource(R.string.plan_form_note),
                value = state.note,
                onValueChange = { if (state.editable) actions.onNote(it) },
                placeholder = stringResource(R.string.plan_form_note_hint),
            )
            // A token taken with the booking, which goes on the farmer's account at once.
            if (state.id == null && state.editable && state.canTakeMoney) {
                FtTextField(
                    label = stringResource(R.string.plan_form_advance),
                    value = state.advanceText,
                    onValueChange = actions.onAdvance,
                    prefix = Money.style.symbol,
                    keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
                    helper = stringResource(R.string.plan_form_advance_note),
                )
                if (state.advanceText.isNotBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Money.methods.forEach { method ->
                            FtChip(
                                label = stringResource(method.labelRes),
                                selected = state.advanceMethod == method,
                                onClick = { actions.onAdvanceMethod(method) },
                            )
                        }
                    }
                }
            }
            state.advanceTakenMinor?.let { taken ->
                Text(
                    text = stringResource(R.string.plan_form_advance_taken, money(taken)),
                    style = FieldTrackTheme.type.bodySmall,
                    color = FieldTrackTheme.colors.inkSecondary,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }

        Column(Modifier.padding(dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                state.editable -> {
                    FtButton(
                        text = stringResource(R.string.plan_form_save),
                        onClick = actions.onSave,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.id != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.day <= state.today) {
                                FtButton(
                                    text = stringResource(R.string.plan_mark_done),
                                    onClick = actions.onDone,
                                    tone = FtButtonTone.Secondary,
                                    height = 48.dp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            FtButton(
                                text = stringResource(R.string.plan_cancel_booking),
                                onClick = { cancelling = true },
                                tone = FtButtonTone.Danger,
                                height = 48.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                state.canPlan && !state.started -> FtButton(
                    text = stringResource(R.string.plan_reopen),
                    onClick = actions.onReopen,
                    tone = FtButtonTone.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (choosingFarmer) {
        PickCustomerSheet(
            customers = state.customers,
            query = state.query,
            onQuery = actions.onQuery,
            onPick = { choosingFarmer = false; actions.onCustomer(it) },
            onDismiss = { choosingFarmer = false },
            onNew = { choosingFarmer = false; actions.onNewFarmer() },
            title = stringResource(R.string.plan_form_pick_farmer),
        )
    }
    if (choosingDay) {
        PlanDayPicker(
            day = maxOf(state.day, state.today),
            notBefore = state.today,
            onDismiss = { choosingDay = false },
            onPicked = { choosingDay = false; actions.onDay(it) },
        )
    }
    if (choosingTime) {
        ClockSheet(
            minutes = state.startMinute,
            onDismiss = { choosingTime = false },
            onPicked = { choosingTime = false; actions.onTime(it) },
        )
    }
    if (cancelling) {
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text(stringResource(R.string.plan_cancel_why)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CancelReason.entries.forEach { reason ->
                        FtButton(
                            text = stringResource(reasonText(reason)),
                            onClick = { cancelling = false; actions.onCancel(reason) },
                            tone = FtButtonTone.Secondary,
                            height = 48.dp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { cancelling = false }) { Text(stringResource(R.string.plan_keep_booking)) }
            },
        )
    }
}

internal fun reasonText(reason: CancelReason): Int = when (reason) {
    CancelReason.Rain -> R.string.plan_reason_rain
    CancelReason.Farmer -> R.string.plan_reason_farmer
    CancelReason.Machine -> R.string.plan_reason_machine
    CancelReason.Other -> R.string.plan_reason_other
}

/** Said when the booking is no longer a live plan, so the screen does not look editable and refuse. */
@Composable
private fun Banner(state: BookingFormState) {
    val text = when {
        state.started -> R.string.plan_form_started
        state.status == BookingStatus.Done -> R.string.plan_form_is_done
        state.status == BookingStatus.Cancelled -> R.string.plan_form_is_cancelled
        else -> return
    }
    val colors = FieldTrackTheme.colors
    FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
        Text(text = stringResource(text), style = FieldTrackTheme.type.bodySmall, color = colors.inkSecondary)
        state.cancelReason?.takeIf { state.status == BookingStatus.Cancelled }?.let {
            Text(
                text = stringResource(reasonText(it)),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** What this booking would clash with, said in words. Never stops a save. */
@Composable
private fun Warnings(state: BookingFormState) {
    if (!state.editable || !state.hasWarning) return
    val colors = FieldTrackTheme.colors
    val machine = state.machines.firstOrNull { it.id == state.machineId }?.name.orEmpty()
    val driver = state.team.firstOrNull { it.personId == state.driverId }?.displayName.orEmpty()
    FtCard(background = colors.dueWash, borderColor = colors.due) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.machineClash?.let {
                WarningLine(
                    stringResource(
                        R.string.plan_warn_machine_booked,
                        machine,
                        partName(it.booking.part).lowercase(),
                        state.customerNames[it.booking.customerId].orEmpty(),
                    )
                )
            }
            state.driverClash?.let {
                WarningLine(
                    stringResource(
                        R.string.plan_warn_driver_booked,
                        driver,
                        partName(it.booking.part).lowercase(),
                        state.customerNames[it.booking.customerId].orEmpty(),
                    )
                )
            }
            if (state.machineDown) WarningLine(stringResource(R.string.plan_warn_machine_down, machine))
            if (state.machineGone) WarningLine(stringResource(R.string.plan_flag_machine_gone))
            if (state.driverGone) WarningLine(stringResource(R.string.plan_flag_driver_gone))
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Text(text = text, style = FieldTrackTheme.type.bodySmall, color = FieldTrackTheme.colors.dueInk)
}
