package com.example.fieldtrack.feature.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import androidx.compose.ui.res.pluralStringResource
import com.example.fieldtrack.feature.spokenDuration
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.home.AlertActions
import com.example.fieldtrack.feature.plan.PlanCard
import com.example.fieldtrack.feature.home.AttentionCard
import com.example.fieldtrack.feature.home.FigureCard
import com.example.fieldtrack.feature.home.HeroButton
import com.example.fieldtrack.feature.home.HomeBlock
import com.example.fieldtrack.feature.home.HomeFrame
import com.example.fieldtrack.feature.home.HomeHeader
import com.example.fieldtrack.feature.home.HomeSection
import com.example.fieldtrack.feature.home.MachineTile
import com.example.fieldtrack.feature.home.NotSentMark
import com.example.fieldtrack.feature.home.NOT_YET
import com.example.fieldtrack.feature.home.OpenJobCard
import com.example.fieldtrack.feature.home.Pane
import com.example.fieldtrack.feature.home.SevenDays
import com.example.fieldtrack.feature.home.Stat
import com.example.fieldtrack.feature.home.StatStrip
import com.example.fieldtrack.feature.home.workAndAmount
import com.example.fieldtrack.feature.iconForWork
import com.example.fieldtrack.feature.labelRes

/**
 * What somebody out on a machine sees when they open the app.
 *
 * Built around the job: the deep card is the clock when one is running and the
 * way to start one when it is not, so the first thing under a thumb is always
 * the next thing to do. Below it, their day in three figures, then what they
 * did. No earnings — this is their record of their day, not the till.
 */
@Composable
fun DriverHomeScreen(
    state: DriverHomeState,
    onStartWork: () -> Unit,
    onOpenLive: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onAddPastWork: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenMachine: (String) -> Unit,
    onOpenBackup: () -> Unit,
    alertActions: AlertActions,
    modifier: Modifier = Modifier,
    onOpenPlan: () -> Unit = {},
) {
    val colors = FieldTrackTheme.colors

    val blocks = buildList {
        add(HomeBlock(Pane.Main) {
            val job = state.openJob
            if (job != null) {
                OpenJobCard(
                    job = job,
                    onOpen = { onOpenLive(job.jobId) },
                    onPause = { onPause(job.jobId) },
                    onResume = { onResume(job.jobId) },
                    dark = true,
                )
            } else {
                ReadyCard(
                    machineName = state.machineName,
                    onStart = onStartWork,
                    onAddPast = onAddPastWork,
                )
            }
        })

        add(HomeBlock(Pane.Main) { PlanCard(state.plan, onOpenPlan) })

        // Only when there is something. A driver opens this screen thirty times a
        // day; an "all clear" card every time would teach them to skip the spot
        // where a breakdown will one day appear.
        if (state.alerts.isNotEmpty()) {
            add(HomeBlock(Pane.Main) {
                AttentionCard(state.alerts, state.loaded, alertActions)
            })
        }

        add(HomeBlock(Pane.Main) {
            StatStrip(
                listOf(
                    Stat(
                        label = stringResource(R.string.home_stat_on_machine),
                        value = if (state.loaded) duration(state.secondsToday) else NOT_YET,
                    ),
                    Stat(
                        label = stringResource(R.string.home_stat_jobs_done),
                        value = if (state.loaded) state.jobsToday.toString() else NOT_YET,
                    ),
                    Stat(
                        label = stringResource(R.string.home_stat_covered),
                        value = if (state.areaToday > 0) quantity(state.areaToday) else NOT_YET,
                        note = if (state.areaToday > 0) {
                            com.example.fieldtrack.feature.unitName(state.areaUnit)
                        } else {
                            null
                        },
                    ),
                )
            )
        })

        state.cashWithYouMinor?.takeIf { it > 0 }?.let { cash ->
            add(HomeBlock(Pane.Main) {
                FigureCard(
                    label = stringResource(R.string.home_cash_with_you),
                    value = money(cash),
                    note = stringResource(R.string.home_cash_held_note),
                    onClick = null,
                )
            })
        }

        add(HomeBlock(Pane.Main) {
            // The ready card already offers earlier work; while a job is running
            // it does not, so the way in moves here rather than being said twice.
            HomeSection(
                title = stringResource(R.string.driver_todays_work),
                action = if (state.openJob != null) stringResource(R.string.home_add_past) else null,
                onAction = onAddPastWork,
            )
            if (state.today.isEmpty()) {
                FtCard {
                    Text(
                        text = stringResource(
                            if (state.openJob != null) R.string.home_field_first_running
                            else R.string.driver_nothing_yet
                        ),
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
                            subtitle = workAndAmount(job.workType, job.amountDone, job.unit),
                            leading = {
                                FtIconTile(
                                    icon = iconForWork(job.workType, job.machineKind),
                                    size = 40.dp,
                                )
                            },
                            trailing = {
                                // Time, never money. This is their record of their day.
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = duration(job.elapsedSeconds),
                                        style = FieldTrackTheme.type.numeric,
                                        color = colors.ink,
                                    )
                                    if (!job.sent) NotSentMark()
                                }
                            },
                            onClick = { onOpenJob(job.jobId) },
                        )
                    }
                }
            }
        })

        add(HomeBlock(Pane.Side) {
            HomeSection(stringResource(R.string.home_your_machine))
            if (state.machineId != null) {
                FtCard(padding = 0.dp) {
                    FtListRow(
                        title = state.machineName.orEmpty(),
                        subtitle = stringResource(R.string.driver_your_machine),
                        leading = { MachineTile(state.machineKind, size = 44.dp) },
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
        })

        add(HomeBlock(Pane.Side) {
            HomeSection(stringResource(R.string.home_last_seven))
            FtCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = duration((state.weekHours.sum() * 3600).toLong()),
                        style = FieldTrackTheme.type.moneyMedium,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.home_hours_on_machine),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
                    )
                }
                SevenDays(
                    values = state.weekHours,
                    dark = false,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        })
    }

    HomeFrame(
        header = {
            HomeHeader(
                title = state.displayName,
                subtitle = state.businessName,
                pendingUploads = state.pendingUploads,
                onOpenSync = onOpenBackup,
                stuck = state.uploadsStuck,
                readAloud = if (state.loaded) pluralStringResource(
                    R.plurals.say_driver_today, state.jobsToday, spokenDuration(state.secondsToday), state.jobsToday,
                ) else null,
            )
        },
        blocks = blocks,
        modifier = modifier,
    )
}

/**
 * The deep card when nothing is running: the one button this screen is for.
 *
 * Says which machine they were last on, because "start work" on a phone shared
 * between two tractors raises the question of which one before it is tapped.
 */
@Composable
private fun ReadyCard(
    machineName: String?,
    onStart: () -> Unit,
    onAddPast: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    FtEmphasisCard {
        Text(
            text = stringResource(R.string.home_ready).uppercase(),
            style = FieldTrackTheme.type.overline,
            color = colors.onDarkMuted,
        )
        Text(
            text = machineName ?: stringResource(R.string.home_ready_title),
            style = FieldTrackTheme.type.screenTitle,
            color = colors.onDark,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = stringResource(R.string.work_start_offline_caption),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.onDarkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        HeroButton(
            text = stringResource(R.string.work_start),
            icon = FieldTrackIcons.Play,
            filled = true,
            dark = true,
            onClick = onStart,
            height = 60.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
        )
        Text(
            text = stringResource(R.string.home_add_past),
            style = FieldTrackTheme.type.label,
            color = colors.onDark,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable(role = Role.Button, onClick = onAddPast)
                .padding(vertical = 12.dp),
        )
    }
}
