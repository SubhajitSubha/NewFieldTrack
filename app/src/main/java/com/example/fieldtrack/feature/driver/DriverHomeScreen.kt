package com.example.fieldtrack.feature.driver

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.iconForWork
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.measure
import com.example.fieldtrack.feature.workLabel
import com.example.fieldtrack.feature.labelRes

/**
 * What a driver sees when they open the app.
 *
 * Deliberately not the owner's home with things removed. It answers a different
 * question — "what have I done today" rather than "what did the business make" —
 * and carries no money at all. A driver's own screen should feel like his record of
 * his day, not a window onto somebody's takings.
 */
@Composable
fun DriverHomeScreen(
    state: DriverHomeState,
    onStartWork: () -> Unit,
    onResumeWork: (String) -> Unit,
    onAddPastWork: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenMachine: (String) -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.padding(dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FtAvatar(
                initials = initialsOf(state.displayName),
                size = 46.dp,
                background = colors.primary,
                contentColor = colors.onPrimary,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = state.displayName,
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
                Text(
                    text = state.businessName,
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        Column(
            Modifier.padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // Said up front, because a driver out of signal all day needs to know
            // his work is kept before he wonders whether to write it down as well.
            if (state.pendingUploads > 0) {
                // Amber, not red. "Your work is saved and waiting" is reassurance,
                // and dressing it as an error tells a driver with no signal that
                // something has gone wrong when nothing has.
                FtCard(
                    background = colors.moneyWash,
                    borderColor = colors.moneyWash,
                    modifier = Modifier.clickable(onClick = onOpenBackup),
                ) {
                    Text(
                        text = stringResource(R.string.driver_waiting_title),
                        style = FieldTrackTheme.type.rowTitle,
                        color = colors.moneyInk,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.driver_waiting_count,
                            state.pendingUploads,
                            state.pendingUploads,
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.moneyInk,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            StartWorkCard(
                live = state.liveJobId != null,
                paused = state.liveJobPaused,
                onClick = {
                    state.liveJobId?.let(onResumeWork) ?: onStartWork()
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.driver_today),
                    value = duration(state.secondsToday),
                    note = stringResource(R.string.driver_on_machine),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.driver_jobs_done),
                    value = state.jobsToday.toString(),
                    note = if (state.areaToday > 0) {
                        stringResource(
                            R.string.driver_area_covered,
                            measure(state.areaToday, state.areaUnit),
                        )
                    } else {
                        stringResource(R.string.driver_nothing_measured)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.machineId != null) {
                FtCard(padding = 0.dp) {
                    FtListRow(
                        title = state.machineName.orEmpty(),
                        subtitle = stringResource(R.string.driver_your_machine),
                        leading = {
                            FtIconTile(
                                icon = com.example.fieldtrack.feature.iconFor(state.machineKind),
                                size = 48.dp,
                                background = colors.primaryWash,
                                tint = colors.primaryPressed,
                            )
                        },
                        showChevron = true,
                        onClick = { onOpenMachine(state.machineId) },
                    )
                }
            } else {
                FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                    Text(
                        text = stringResource(R.string.driver_no_machine),
                        style = FieldTrackTheme.type.rowTitle,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.driver_no_machine_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.driver_todays_work),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.driver_add_earlier),
                    style = FieldTrackTheme.type.label,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable(onClick = onAddPastWork)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (state.today.isEmpty()) {
                FtCard {
                    Text(
                        text = stringResource(R.string.driver_nothing_yet),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            } else {
                FtCard(padding = 0.dp) {
                    state.today.forEachIndexed { index, job ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = job.customerName,
                            subtitle = job.summary(),
                            statusStripe = if (job.live) colors.primary else null,
                            leading = {
                                FtIconTile(
                                    icon = iconForWork(job.workType, job.machineKind),
                                    size = 40.dp,
                                )
                            },
                            trailing = {
                                // Time, never money. This is his record of his day.
                                Text(
                                    text = duration(job.elapsedSeconds),
                                    style = FieldTrackTheme.type.moneyMedium,
                                    color = colors.ink,
                                )
                            },
                            onClick = { onOpenJob(job.jobId) },
                        )
                    }
                }
            }

            Box(Modifier.height(dimens.space6))
        }
    }
}

/** "Rotavator · 6 acre", or just the work when nothing is counted yet. */
@Composable
private fun DriverJobRow.summary(): String {
    val work = workLabel(workType)
    if (amountDone <= 0.0) return work
    return "$work · ${measure(amountDone, unit)}"
}

@Composable
private fun StartWorkCard(live: Boolean, paused: Boolean, onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors
    FtCard(
        background = colors.primary,
        borderColor = colors.primary,
        modifier = Modifier.clickable(onClick = onClick),
        padding = 22.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconTile(
                icon = if (live && !paused) FieldTrackIcons.Pause else FieldTrackIcons.Play,
                size = 62.dp,
                background = colors.onPrimary.copy(alpha = 0.18f),
                tint = colors.onPrimary,
            )
            Column(Modifier.padding(start = 18.dp)) {
                Text(
                    text = stringResource(
                        if (live) R.string.work_back_to_work else R.string.work_start
                    ),
                    style = FieldTrackTheme.type.screenTitle,
                    color = colors.onPrimary,
                )
                Text(
                    text = stringResource(R.string.work_start_offline_caption),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.onDarkMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    FtCard(modifier = modifier, padding = 16.dp) {
        Text(
            text = label.uppercase(),
            style = FieldTrackTheme.type.overline,
            color = FieldTrackTheme.colors.inkTertiary,
        )
        Text(
            text = value,
            style = FieldTrackTheme.type.moneyLarge,
            color = FieldTrackTheme.colors.ink,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            text = note,
            style = FieldTrackTheme.type.caption,
            color = FieldTrackTheme.colors.inkTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
