package com.example.fieldtrack.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes

/** Which panel is open over the running job. */
enum class WorkSheet { None, Menu, Fuel, Note, Rate, Breakdown, HandOver, Discard, Amount }

/**
 * Everything you can do to a job that is already running.
 *
 * These live behind one "More" button rather than on the work screen itself: a driver
 * with the engine going needs the measurement, Pause and Finish and nothing else
 * within reach. What is here is what happens occasionally and must not be hunted for
 * — the machine stops, the rate was agreed wrong, the job passes to another driver.
 *
 * Each panel is only offered when it makes sense. A job with no machine on it cannot
 * log fuel or a breakdown, a one-man outfit has nobody to hand over to, and a driver
 * who may not see money may not set the price.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkSheets(
    open: WorkSheet,
    state: LiveWorkState,
    onOpen: (WorkSheet) -> Unit,
    onNote: (String) -> Unit,
    onRate: (Long) -> Unit,
    onFuel: (Double, Long) -> Unit,
    onBreakdown: (BreakdownCategory, String?) -> Unit,
    onHandOver: (String) -> Unit,
    onDiscard: () -> Unit,
    onAmount: (Double) -> Unit,
) {
    if (open == WorkSheet.None) return
    val colors = FieldTrackTheme.colors
    val close = { onOpen(WorkSheet.None) }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (open) {
                WorkSheet.Menu -> ActionMenu(state, onOpen)
                WorkSheet.Fuel -> FuelPanel(state) { litres, cost -> onFuel(litres, cost); close() }
                WorkSheet.Note -> NotePanel { note -> onNote(note); close() }
                WorkSheet.Rate -> RatePanel(state) { rate -> onRate(rate); close() }
                WorkSheet.Breakdown -> BreakdownPanel { cat, note ->
                    onBreakdown(cat, note)
                    close()
                }
                WorkSheet.HandOver -> HandOverPanel(state) { id -> onHandOver(id); close() }
                WorkSheet.Discard -> DiscardPanel(onCancel = close, onConfirm = onDiscard)
                WorkSheet.Amount -> AmountPanel(state) { amount -> onAmount(amount); close() }
                WorkSheet.None -> Unit
            }
        }
    }
}

@Composable
private fun ActionMenu(state: LiveWorkState, onOpen: (WorkSheet) -> Unit) {
    val colors = FieldTrackTheme.colors
    SheetTitle(stringResource(R.string.work_actions_title))

    if (state.teammates.isNotEmpty()) {
        ActionRow(
            icon = FieldTrackIcons.Team,
            title = stringResource(R.string.work_hand_over_title),
            subtitle = stringResource(R.string.work_hand_over_caption),
            onClick = { onOpen(WorkSheet.HandOver) },
        )
    }
    if (state.canSeeMoney) {
        ActionRow(
            icon = FieldTrackIcons.Rupee,
            title = stringResource(R.string.work_rate_title),
            subtitle = stringResource(
                R.string.work_rate_caption,
                "${rupees(state.rateRupees)} ${stringResource(R.string.start_rate_per, stringResource(state.unit.labelRes))}",
            ),
            onClick = { onOpen(WorkSheet.Rate) },
        )
    }
    ActionRow(
        icon = FieldTrackIcons.Edit,
        title = stringResource(R.string.work_note_title),
        subtitle = stringResource(R.string.work_note_caption),
        onClick = { onOpen(WorkSheet.Note) },
    )
    if (state.hasMachine) {
        ActionRow(
            icon = FieldTrackIcons.Breakdown,
            title = stringResource(R.string.work_breakdown_title),
            subtitle = stringResource(R.string.work_breakdown_caption),
            tint = colors.due,
            background = colors.dueWash,
            onClick = { onOpen(WorkSheet.Breakdown) },
        )
    }
    ActionRow(
        icon = FieldTrackIcons.Close,
        title = stringResource(R.string.work_discard_title),
        subtitle = stringResource(R.string.work_discard_caption),
        tint = colors.due,
        background = colors.dueWash,
        titleColor = colors.due,
        onClick = { onOpen(WorkSheet.Discard) },
    )

    FtButton(
        text = stringResource(R.string.work_back_to_work),
        onClick = { onOpen(WorkSheet.None) },
        tone = FtButtonTone.Secondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun FuelPanel(state: LiveWorkState, onSave: (Double, Long) -> Unit) {
    SheetTitle(stringResource(R.string.work_fuel_title))
    var litres by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) {
            FtTextField(
                label = stringResource(R.string.work_fuel_litres),
                value = litres,
                onValueChange = { v -> litres = v.filter { it.isDigit() || it == '.' } },
                keyboardType = KeyboardType.Decimal,
                focused = true,
            )
        }
        Box(Modifier.weight(1f)) {
            FtTextField(
                label = stringResource(R.string.work_fuel_cost),
                value = cost,
                onValueChange = { v -> cost = v.filter { it.isDigit() } },
                prefix = "₹",
                keyboardType = KeyboardType.Number,
            )
        }
    }
    Hint(stringResource(R.string.work_fuel_note))

    val parsedLitres = litres.toDoubleOrNull() ?: 0.0
    FtButton(
        text = stringResource(R.string.work_fuel_save),
        onClick = { onSave(parsedLitres, cost.toLongOrNull() ?: 0L) },
        enabled = parsedLitres > 0 && state.hasMachine,
    )
}

/**
 * Typing the figure outright instead of stepping to it.
 *
 * For bags and quintals this is the normal case — a thresher run is forty bags, not
 * forty taps — and it is just as welcome for someone who already knows the field is
 * three and a half acres.
 */
@Composable
private fun AmountPanel(state: LiveWorkState, onSave: (Double) -> Unit) {
    SheetTitle(stringResource(R.string.work_type_amount_title))
    var text by rememberSaveable { mutableStateOf("") }

    FtTextField(
        label = stringResource(state.unit.labelRes),
        value = text,
        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
        keyboardType = KeyboardType.Decimal,
        focused = true,
    )
    val parsed = text.toDoubleOrNull() ?: -1.0
    FtButton(
        text = stringResource(R.string.work_type_amount_save),
        onClick = { onSave(parsed) },
        enabled = parsed >= 0.0,
    )
}

@Composable
private fun NotePanel(onSave: (String) -> Unit) {
    SheetTitle(stringResource(R.string.work_note_title))
    var note by rememberSaveable { mutableStateOf("") }

    FtTextField(
        label = stringResource(R.string.work_note_title),
        value = note,
        onValueChange = { note = it },
        placeholder = stringResource(R.string.work_note_hint),
        focused = true,
    )
    FtButton(
        text = stringResource(R.string.work_note_save),
        onClick = { onSave(note) },
        enabled = note.isNotBlank(),
    )
}

@Composable
private fun RatePanel(state: LiveWorkState, onSave: (Long) -> Unit) {
    SheetTitle(stringResource(R.string.work_rate_title))
    var rate by rememberSaveable { mutableStateOf(state.rateRupees.toString()) }

    FtTextField(
        label = stringResource(R.string.start_your_rate),
        value = rate,
        onValueChange = { v -> rate = v.filter { it.isDigit() } },
        prefix = "₹",
        suffix = stringResource(R.string.start_rate_per, stringResource(state.unit.labelRes)),
        keyboardType = KeyboardType.Number,
        focused = true,
    )
    FtButton(
        text = stringResource(R.string.work_rate_save),
        onClick = { onSave(rate.toLongOrNull() ?: 0L) },
        enabled = (rate.toLongOrNull() ?: 0L) > 0,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownPanel(onSave: (BreakdownCategory, String?) -> Unit) {
    SheetTitle(stringResource(R.string.work_breakdown_title))
    var chosen by rememberSaveable { mutableStateOf<BreakdownCategory?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    FtSectionLabel(stringResource(R.string.work_breakdown_what))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BreakdownCategory.entries.forEach { category ->
            FtChip(
                label = stringResource(category.labelRes),
                selected = chosen == category,
                onClick = { chosen = category },
            )
        }
    }
    FtTextField(
        label = stringResource(R.string.work_breakdown_note),
        value = note,
        onValueChange = { note = it },
        placeholder = stringResource(R.string.work_breakdown_note_hint),
    )
    FtButton(
        text = stringResource(R.string.work_breakdown_save),
        onClick = { chosen?.let { onSave(it, note.ifBlank { null }) } },
        tone = FtButtonTone.Danger,
        enabled = chosen != null,
    )
}

@Composable
private fun HandOverPanel(state: LiveWorkState, onPick: (String) -> Unit) {
    SheetTitle(stringResource(R.string.work_hand_over_title))
    Hint(stringResource(R.string.work_hand_over_caption))

    if (state.teammates.isEmpty()) {
        Hint(stringResource(R.string.work_hand_over_nobody))
        return
    }
    state.teammates.forEach { mate ->
        ActionRow(
            icon = FieldTrackIcons.Customer,
            title = mate.displayName,
            subtitle = null,
            onClick = { onPick(mate.personId) },
        )
    }
}

@Composable
private fun DiscardPanel(onCancel: () -> Unit, onConfirm: () -> Unit) {
    SheetTitle(stringResource(R.string.work_discard_confirm))
    Hint(stringResource(R.string.work_discard_confirm_body))

    FtButton(
        text = stringResource(R.string.work_discard_confirm_action),
        onClick = onConfirm,
        tone = FtButtonTone.Danger,
    )
    FtButton(
        text = stringResource(R.string.action_cancel),
        onClick = onCancel,
        tone = FtButtonTone.Secondary,
    )
}

// ------------------------------------------------------------------ small parts

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = FieldTrackTheme.type.sectionTitle,
        color = FieldTrackTheme.colors.ink,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = FieldTrackTheme.type.bodySmall,
        color = FieldTrackTheme.colors.inkTertiary,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = FieldTrackTheme.colors.inkSecondary,
    background: androidx.compose.ui.graphics.Color = FieldTrackTheme.colors.surfaceMuted,
    titleColor: androidx.compose.ui.graphics.Color = FieldTrackTheme.colors.ink,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(FieldTrackTheme.colors.surface, FieldTrackTheme.shapes.medium)
    ) {
        FtListRow(
            title = title,
            subtitle = subtitle,
            titleColor = titleColor,
            leading = { FtIconTile(icon, size = 46.dp, background = background, tint = tint) },
            onClick = onClick,
        )
    }
}


