package com.example.fieldtrack.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.format.rupeesCompact
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.iconFor

/**
 * One machine: what it is doing, what it has earned, what it has cost.
 *
 * Earned and diesel sit side by side on purpose. Those two numbers are the argument
 * for or against a machine, and an owner who can see one without the other is being
 * told half the story.
 */
@Composable
fun MachineDetailScreen(
    state: MachineDetailState,
    reporting: Boolean,
    service: MachineServiceDraft?,
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    onFuel: () -> Unit,
    onService: () -> Unit,
    onBreakdown: () -> Unit,
    onSaveBreakdown: (BreakdownCategory, String?) -> Unit,
    onDismissReport: () -> Unit,
    onServiceDescription: (String) -> Unit,
    onServiceCost: (String) -> Unit,
    onServiceVendor: (String) -> Unit,
    onSaveService: () -> Unit,
    onDismissService: () -> Unit,
    onOpenJob: (String) -> Unit,
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
            title = state.name,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = onEdit?.let {
                {
                    FtIconButton(
                        icon = FieldTrackIcons.Edit,
                        contentDescription = stringResource(R.string.machine_edit_cd),
                        onClick = it,
                    )
                }
            },
        )

        if (state.loaded && !state.exists) {
            FtCard(modifier = Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(R.string.machine_gone),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            FtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FtIconTile(
                        icon = iconFor(state.kind),
                        size = 68.dp,
                        background = colors.primaryWash,
                        tint = colors.primaryPressed,
                    )
                    Column(Modifier.padding(start = 14.dp)) {
                        FtStatusPill(
                            status = when (state.status) {
                                MachineStatus.Working -> FtStatus.Running
                                MachineStatus.Down -> FtStatus.Breakdown
                                MachineStatus.Idle -> FtStatus.Idle
                            },
                            label = stringResource(state.status.labelRes()),
                        )
                        state.plate?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.inkTertiary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        state.driverName?.let {
                            Text(
                                text = stringResource(R.string.machine_driving, it),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.inkTertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatTile(
                    label = stringResource(R.string.machine_hours),
                    value = quantity(state.hoursUsed),
                    modifier = Modifier.weight(1f),
                )
                // Money is the owner's business, not every driver's.
                if (state.canSeeMoney) {
                    StatTile(
                        label = stringResource(R.string.machine_earned),
                        value = rupeesCompact(state.earnedRupees),
                        valueColor = colors.money,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.machine_diesel),
                        value = rupeesCompact(state.dieselRupees),
                        valueColor = colors.due,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ServiceBanner(state, onService)

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                QuickAction(
                    icon = FieldTrackIcons.Fuel,
                    label = stringResource(R.string.machine_action_fuel),
                    onClick = onFuel,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = FieldTrackIcons.Service,
                    label = stringResource(R.string.machine_action_service),
                    onClick = onService,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = FieldTrackIcons.Breakdown,
                    label = stringResource(R.string.machine_action_breakdown),
                    onClick = onBreakdown,
                    modifier = Modifier.weight(1f),
                    tint = if (state.openBreakdowns > 0) colors.due else colors.inkSecondary,
                )
            }

            FtSectionLabel(stringResource(R.string.machine_recent_jobs))
            if (state.recentJobs.isEmpty()) {
                if (state.loaded) {
                    FtCard {
                        Text(
                            text = stringResource(R.string.machine_no_jobs),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
            } else {
                FtCard(padding = 0.dp) {
                    state.recentJobs.forEachIndexed { index, job ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = job.customerName,
                            subtitle = listOf(
                                job.workType,
                                dayMonth(job.workedAtMillis),
                            ).joinToString(" · "),
                            statusStripe = if (job.live) colors.primary else null,
                            trailing = if (state.canSeeMoney) {
                                {
                                    Text(
                                        text = rupees(job.amountRupees),
                                        style = FieldTrackTheme.type.moneyMedium,
                                        color = colors.ink,
                                    )
                                }
                            } else null,
                            onClick = { onOpenJob(job.id) },
                        )
                    }
                }
            }

            Box(Modifier.height(dimens.space4))
        }
    }

    if (reporting) {
        ReportBreakdownSheet(
            machineName = state.name,
            onSave = onSaveBreakdown,
            onDismiss = onDismissReport,
        )
    }

    if (service != null) {
        ServiceSheet(
            draft = service,
            onDescription = onServiceDescription,
            onCost = onServiceCost,
            onVendor = onServiceVendor,
            onSave = onSaveService,
            onDismiss = onDismissService,
        )
    }
}

/** Logging work done on this machine. No picker: the machine is the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceSheet(
    draft: MachineServiceDraft,
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
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.upkeep_new_service),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
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

/**
 * Reporting a fault on a machine that is not on a job.
 *
 * The same question the live-work sheet asks, because it is the same question — but
 * reached from the machine rather than the work, for a tractor that broke in the
 * yard or would not start this morning.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReportBreakdownSheet(
    machineName: String,
    onSave: (BreakdownCategory, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    var chosen by rememberSaveable { mutableStateOf<BreakdownCategory?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

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
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.machine_report_title, machineName),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
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
                value = note,
                onValueChange = { note = it },
                label = stringResource(R.string.machine_report_note),
                placeholder = stringResource(R.string.machine_report_note_hint),
            )
            FtButton(
                text = stringResource(R.string.machine_report_save),
                onClick = { chosen?.let { onSave(it, note.ifBlank { null }) } },
                enabled = chosen != null,
                tone = FtButtonTone.Danger,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
        }
    }
}

/** How close the next service is, or that nothing is scheduled. */
@Composable
private fun ServiceBanner(state: MachineDetailState, onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors
    val hours = state.serviceInHours

    if (state.openBreakdowns > 0) {
        FtCard(
            background = colors.dueWash,
            borderColor = colors.dueWash,
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.machine_breakdowns_open, state.openBreakdowns, state.openBreakdowns,
                ),
                style = FieldTrackTheme.type.rowTitle,
                color = colors.dueInk,
            )
        }
        return
    }

    if (hours == null) {
        FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
            Text(
                text = stringResource(R.string.machine_no_schedule),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkTertiary,
            )
        }
        return
    }

    FtCard(
        background = if (state.serviceOverdue) colors.dueWash else colors.surfaceMuted,
        borderColor = if (state.serviceOverdue) colors.dueWash else colors.line,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = if (state.serviceOverdue) {
                stringResource(R.string.machine_service_overdue)
            } else {
                stringResource(
                    R.string.machine_service_in,
                    stringResource(R.string.upkeep_hours_value, quantity(hours)),
                )
            },
            style = FieldTrackTheme.type.rowTitle,
            color = if (state.serviceOverdue) colors.dueInk else colors.ink,
        )
        state.lastServiceAtHours?.let {
            Text(
                text = stringResource(
                    R.string.machine_service_last,
                    stringResource(R.string.upkeep_hours_value, quantity(it)),
                ),
                style = FieldTrackTheme.type.caption,
                color = if (state.serviceOverdue) colors.dueInk else colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = FieldTrackTheme.colors.ink,
) {
    FtCard(modifier = modifier, padding = 14.dp, shape = FieldTrackTheme.shapes.medium) {
        Text(
            text = label.uppercase(),
            style = FieldTrackTheme.type.overline,
            color = FieldTrackTheme.colors.inkTertiary,
        )
        Text(
            text = value,
            style = FieldTrackTheme.type.moneyMedium,
            color = valueColor,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = FieldTrackTheme.colors.inkSecondary,
) {
    FtCard(
        modifier = modifier.clickable(onClick = onClick),
        padding = 13.dp,
        shape = FieldTrackTheme.shapes.medium,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FtIconTile(icon = icon, size = 30.dp, background = Color.Transparent, tint = tint)
            Text(
                text = label,
                style = FieldTrackTheme.type.caption,
                color = FieldTrackTheme.colors.ink,
            )
        }
    }
}
