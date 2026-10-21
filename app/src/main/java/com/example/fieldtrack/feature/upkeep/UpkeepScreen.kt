package com.example.fieldtrack.feature.upkeep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.labelRes

/**
 * Service and repairs across the yard.
 *
 * What is broken comes first and carries the only button that matters, because the
 * reason this screen exists is that a machine reported broken had no way back. Until
 * it did, the app quietly punished an honest driver for saying the pump had gone.
 */
@Composable
fun UpkeepScreen(
    state: UpkeepState,
    draft: ServiceDraft?,
    onBack: () -> Unit,
    onMarkRepaired: (String) -> Unit,
    onLogRepair: (BrokenMachine) -> Unit,
    onLogService: () -> Unit,
    onDraftMachine: (String) -> Unit,
    onDraftDescription: (String) -> Unit,
    onDraftCost: (String) -> Unit,
    onDraftVendor: (String) -> Unit,
    onDraftSave: () -> Unit,
    onDraftDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.upkeep_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (state.broken.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.upkeep_broken))
                state.broken.forEach { broken ->
                    BrokenCard(
                        broken = broken,
                        canEdit = state.canEdit,
                        onMarkRepaired = { onMarkRepaired(broken.breakdownId) },
                        onLogRepair = { onLogRepair(broken) },
                    )
                }
            }

            if (state.dueSoon.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.upkeep_coming_up))
                state.dueSoon.forEach { due -> ServiceDueCard(due) }
            }

            if (state.loaded && state.broken.isEmpty() && state.dueSoon.isEmpty()) {
                FtCard {
                    Text(
                        text = stringResource(R.string.upkeep_all_well),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }

            if (state.done.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.upkeep_done))
                FtCard(padding = 0.dp) {
                    state.done.forEachIndexed { index, record ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = record.description,
                            subtitle = listOfNotNull(
                                record.machineName.takeIf { it.isNotBlank() },
                                dayMonth(record.atMillis),
                                record.vendor,
                            ).joinToString(" · "),
                            leading = {
                                FtIconTile(
                                    icon = FieldTrackIcons.Check,
                                    size = 40.dp,
                                    background = colors.moneyWash,
                                    tint = colors.money,
                                )
                            },
                            // The repair is operational; the bill for it is not.
                            trailing = record.costRupees
                                ?.takeIf { state.canSeeMoney }
                                ?.let { cost ->
                                    {
                                        Text(
                                            text = rupees(cost),
                                            style = FieldTrackTheme.type.moneyMedium,
                                            color = colors.ink,
                                        )
                                    }
                                },
                        )
                    }
                }
            }

            Box(Modifier.height(dimens.space4))
        }

        if (state.canEdit) {
            Column(Modifier.padding(dimens.screenPadding)) {
                FtButton(
                    text = stringResource(R.string.upkeep_log_service),
                    onClick = onLogService,
                    icon = FieldTrackIcons.Add,
                    enabled = state.machines.isNotEmpty(),
                    height = 54.dp,
                )
            }
        }
    }

    if (draft != null) {
        ServiceSheet(
            draft = draft,
            machines = state.machines,
            onMachine = onDraftMachine,
            onDescription = onDraftDescription,
            onCost = onDraftCost,
            onVendor = onDraftVendor,
            onSave = onDraftSave,
            onDismiss = onDraftDismiss,
        )
    }
}

/** A machine that is out of action, with the way back on it. */
@Composable
private fun BrokenCard(
    broken: BrokenMachine,
    canEdit: Boolean,
    onMarkRepaired: () -> Unit,
    onLogRepair: () -> Unit,
) {
    val colors = FieldTrackTheme.colors

    FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconTile(
                icon = iconFor(broken.kind),
                size = 44.dp,
                background = colors.surface,
                tint = colors.due,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = broken.machineName,
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.dueInk,
                )
                Text(
                    text = stringResource(
                        R.string.upkeep_reported, dayMonth(broken.reportedAtMillis),
                    ),
                    style = FieldTrackTheme.type.caption,
                    color = colors.dueInk,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = stringResource(broken.category.labelRes),
                style = FieldTrackTheme.type.label,
                color = colors.dueInk,
            )
        }

        broken.note?.let { note ->
            Text(
                text = note,
                style = FieldTrackTheme.type.bodySmall,
                color = colors.dueInk,
                modifier = Modifier.padding(top = 9.dp),
            )
        }

        // Worth saying out loud: the work is still there, waiting. Somebody deciding
        // whether to report a breakdown honestly needs to know it costs them nothing.
        if (broken.jobWaiting) {
            Text(
                text = stringResource(R.string.upkeep_job_waiting),
                style = FieldTrackTheme.type.caption,
                color = colors.dueInk,
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        if (canEdit) {
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                // Two ways out, because both really happen: a loose wire pushed back
                // on in the field costs nothing and has no paperwork, and a pump
                // replaced at a workshop has a bill the owner wants kept.
                FtButton(
                    text = stringResource(R.string.upkeep_mark_repaired),
                    onClick = onMarkRepaired,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                )
                FtButton(
                    text = stringResource(R.string.upkeep_log_repair),
                    onClick = onLogRepair,
                    modifier = Modifier.weight(1f),
                    tone = FtButtonTone.Secondary,
                    height = 48.dp,
                )
            }
        }
    }
}

/** How close a machine is to its next service. */
@Composable
private fun ServiceDueCard(due: ServiceDue) {
    val colors = FieldTrackTheme.colors
    val tint = if (due.overdue) colors.due else colors.dueInk

    FtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconTile(
                icon = iconFor(due.kind),
                size = 44.dp,
                background = if (due.overdue) colors.dueWash else colors.surfaceMuted,
                tint = if (due.overdue) colors.due else colors.inkSecondary,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = due.machineName,
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (due.overdue) {
                        stringResource(R.string.upkeep_overdue)
                    } else {
                        stringResource(
                            R.string.upkeep_hours_to_go,
                            stringResource(R.string.upkeep_hours_value, quantity(due.hoursToGo)),
                        )
                    },
                    style = FieldTrackTheme.type.label,
                    color = tint,
                )
            }
        }

        LinearProgressIndicator(
            progress = { due.progress },
            modifier = Modifier
                .padding(top = 11.dp)
                .fillMaxWidth()
                .height(8.dp),
            color = if (due.overdue) colors.due else colors.money,
            trackColor = colors.surfaceMuted,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.upkeep_last_at,
                    stringResource(R.string.upkeep_hours_value, quantity(due.lastServiceAtHours)),
                ),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
            Text(
                text = stringResource(
                    R.string.upkeep_due_at,
                    stringResource(R.string.upkeep_hours_value, quantity(due.dueAtHours)),
                ),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
        }
    }
}

/** Logging what was done, and what it cost. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ServiceSheet(
    draft: ServiceDraft,
    machines: List<com.example.fieldtrack.data.db.entity.MachineEntity>,
    onMachine: (String) -> Unit,
    onDescription: (String) -> Unit,
    onCost: (String) -> Unit,
    onVendor: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.closesBreakdownId != null) R.string.upkeep_new_repair
                    else R.string.upkeep_new_service
                ),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )

            if (machines.isEmpty()) {
                Text(
                    text = stringResource(R.string.upkeep_no_machines),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            } else {
                FtSectionLabel(stringResource(R.string.upkeep_which_machine))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    machines.forEach { machine ->
                        FtChip(
                            label = machine.name,
                            selected = draft.machineId == machine.id,
                            onClick = { onMachine(machine.id) },
                        )
                    }
                }
            }

            FtTextField(
                value = draft.description,
                onValueChange = onDescription,
                label = stringResource(R.string.upkeep_what_was_done),
                helper = stringResource(R.string.upkeep_what_hint),
                focused = true,
            )

            FtTextField(
                value = draft.costText,
                onValueChange = onCost,
                label = stringResource(R.string.upkeep_cost),
                prefix = "₹",
                keyboardType = KeyboardType.Number,
            )

            FtTextField(
                value = draft.vendor,
                onValueChange = onVendor,
                label = stringResource(R.string.upkeep_vendor),
            )

            if (draft.closesBreakdownId != null) {
                Text(
                    text = stringResource(R.string.upkeep_closes_breakdown),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkSecondary,
                )
            }

            FtButton(
                text = stringResource(R.string.upkeep_save),
                onClick = onSave,
                enabled = draft.canSave,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
        }
    }
}
