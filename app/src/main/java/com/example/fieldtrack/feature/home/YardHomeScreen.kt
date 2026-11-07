package com.example.fieldtrack.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtFitText
import com.example.fieldtrack.data.repository.ClockCheck
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.measure
import com.example.fieldtrack.feature.plan.PlanCard

/** How many of today's jobs the home lists before handing over to the Jobs tab. */
private const val DONE_TODAY_SHOWN = 5

/**
 * The yard at a glance, for an owner with a crew or the manager running it.
 *
 * Phone order is the order of questions: where does everything stand (the deep
 * card), what is wrong, who is out where, how is the money, then the day's
 * finished work. On a tablet the live picture keeps the wide column and the
 * rest stands beside it.
 */
@Composable
fun YardHomeScreen(
    state: YardHomeState,
    onStartWork: () -> Unit,
    onAddPastWork: () -> Unit,
    onOpenLive: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenJobs: () -> Unit,
    onOpenFleet: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenSeason: () -> Unit,
    onOpenBackup: () -> Unit,
    alertActions: AlertActions,
    modifier: Modifier = Modifier,
    onOpenPlan: () -> Unit = {},
    /** False for somebody who may only look: there is no work of their own to start or to show. */
    canRecord: Boolean = true,
) {
    val ownWork = HomeBlock(Pane.Side) {
        if (state.ownJob == null) HomeSection(stringResource(R.string.home_your_own_work))
        OwnWork(
            job = state.ownJob,
            onStartWork = onStartWork,
            onAddPastWork = onAddPastWork,
            onOpenLive = onOpenLive,
            onPause = onPause,
            onResume = onResume,
            dark = false,
        )
    }

    val blocks = buildList {
        add(HomeBlock(Pane.Main) { YardHero(state, onOpenFleet) })

        // Somebody who is driving themselves wants their own clock near the top.
        if (canRecord && state.ownJob != null) add(ownWork)

        add(HomeBlock(Pane.Side) { PlanCard(state.plan, onOpenPlan) })

        add(HomeBlock(Pane.Side) { AttentionCard(state.alerts, state.loaded, alertActions) })

        add(HomeBlock(Pane.Main) {
            HomeSection(stringResource(R.string.home_crew_now))
            CrewList(state.crew, state.loaded, onOpenJob)
        })

        if (state.canSeeMoney) {
            add(HomeBlock(Pane.Side) {
                MoneyFigures(
                    loaded = state.loaded,
                    owed = state.owedMinor,
                    owing = state.owingCustomers,
                    season = state.seasonEarnedMinor,
                    seasonFrom = state.seasonFromMillis,
                    seasonName = state.seasonName,
                    onOpenCustomers = onOpenCustomers,
                    onOpenSeason = onOpenSeason,
                )
            })
        }

        if (canRecord && state.ownJob == null) add(ownWork)

        add(HomeBlock(Pane.Main) {
            HomeSection(
                title = stringResource(R.string.home_done_today),
                action = if (state.doneToday.size > DONE_TODAY_SHOWN) {
                    stringResource(R.string.home_see_all_jobs)
                } else {
                    null
                },
                onAction = onOpenJobs,
            )
            TodayList(
                rows = state.doneToday.take(DONE_TODAY_SHOWN),
                showMoney = state.canSeeMoney,
                onOpenJob = onOpenJob,
            )
        })
    }

    HomeFrame(
        header = {
            HomeHeader(
                title = state.businessName.ifBlank { state.displayName },
                subtitle = state.displayName.takeIf { state.businessName.isNotBlank() },
                pendingUploads = state.pendingUploads,
                onOpenSync = onOpenBackup,
                stuck = state.uploadsStuck,
                readAloud = if (state.loaded) stringResource(
                    R.string.say_yard, state.crew.size + if (state.ownJob != null) 1 else 0, state.machinesWorking, state.machinesDown,
                ) else null,
            )
        },
        blocks = blocks,
        modifier = modifier,
    )
}

/**
 * Right now, on the deep card: people out, machines turning, machines down —
 * then the day's money and the week behind it.
 *
 * Someone not trusted with money sees the same card with hours where the rupees
 * would be, so the card keeps its shape and nothing reads as "hidden".
 */
@Composable
private fun YardHero(state: YardHomeState, onOpenFleet: () -> Unit) {
    val colors = FieldTrackTheme.colors
    val out = state.crew.size + if (state.ownJob != null) 1 else 0
    FtEmphasisCard(onClick = onOpenFleet) {
        Text(
            text = stringResource(R.string.home_right_now).uppercase(),
            style = FieldTrackTheme.type.overline,
            color = colors.onDarkMuted,
        )
        Row(Modifier.padding(top = 10.dp)) {
            DarkFigure(
                value = if (state.loaded) out.toString() else NOT_YET,
                label = stringResource(R.string.home_figure_on_job),
                modifier = Modifier.weight(1f),
            )
            DarkFigure(
                value = if (state.loaded) {
                    stringResource(R.string.home_of_total, state.machinesWorking, state.machinesTotal)
                } else {
                    NOT_YET
                },
                label = stringResource(R.string.home_figure_machines_working),
                modifier = Modifier.weight(1f),
            )
            DarkFigure(
                value = if (state.loaded) state.machinesDown.toString() else NOT_YET,
                label = stringResource(R.string.home_figure_down),
                accent = if (state.machinesDown > 0) colors.dueOnDark else null,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .height(FieldTrackTheme.dimens.hairline)
                .background(colors.onDarkFaint)
        )

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.canSeeMoney) R.string.home_earned_today else R.string.home_jobs_today
                    ),
                    style = FieldTrackTheme.type.caption,
                    color = colors.onDarkMuted,
                )
                Text(
                    text = when {
                        !state.loaded -> NOT_YET
                        state.canSeeMoney -> money(state.earnedTodayMinor)
                        else -> state.jobsToday.toString()
                    },
                    style = FieldTrackTheme.type.moneyLarge,
                    color = colors.onDark,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = listOfNotNull(
                        pluralStringResource(R.plurals.count_jobs, state.jobsToday, state.jobsToday)
                            .takeIf { state.canSeeMoney },
                        state.areaToday.takeIf { it > 0 }?.let { measure(it, state.areaUnit) },
                    ).joinToString(stringResource(R.string.cost_detail_sep)),
                    style = FieldTrackTheme.type.caption,
                    color = colors.onDarkMuted,
                )
                if (state.canSeeMoney && state.collectedTodayMinor > 0) {
                    Text(
                        text = stringResource(
                            R.string.home_collected_today, money(state.collectedTodayMinor),
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.onDark,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(
                    if (state.canSeeMoney) R.string.home_last_seven else R.string.home_last_seven_hours
                ),
                style = FieldTrackTheme.type.caption,
                color = colors.onDarkMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (state.canSeeMoney) {
                    money(state.week.sum().toLong())
                } else {
                    duration((state.week.sum() * 3600).toLong())
                },
                style = FieldTrackTheme.type.numeric,
                color = colors.onDark,
            )
        }
        SevenDays(state.week, dark = true, modifier = Modifier.padding(top = 12.dp), barHeight = 40.dp)
    }
}

@Composable
private fun DarkFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val colors = FieldTrackTheme.colors
    Column(modifier) {
        FtFitText(
            text = value,
            style = FieldTrackTheme.type.moneyLarge,
            color = accent ?: colors.onDark,
        )
        Text(
            text = label,
            style = FieldTrackTheme.type.caption,
            color = colors.onDarkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Who is out, where, and for how long — each clock moving. */
@Composable
private fun CrewList(crew: List<CrewRow>, loaded: Boolean, onOpenJob: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    if (crew.isEmpty()) {
        FtCard {
            Text(
                text = stringResource(if (loaded) R.string.home_crew_none else R.string.home_loading),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkTertiary,
            )
        }
        return
    }
    // Only for the "left on" check; each row's clock ticks on its own.
    val now by rememberNow(active = crew.any { !it.paused }, periodMillis = SLOW_TICK_MILLIS)
    FtCard(padding = 0.dp) {
        crew.forEachIndexed { index, row ->
            if (index > 0) FtRowDivider()
            FtListRow(
                title = row.personName.ifBlank { stringResource(R.string.home_someone) },
                subtitle = listOfNotNull(
                    row.customerName,
                    workAndAmount(row.workType, row.amountDone, row.unit),
                ).joinToString(stringResource(R.string.cost_detail_sep)),
                statusStripe = if (row.paused) colors.money else colors.primary,
                leading = {
                    FtAvatar(
                        initials = initialsOf(row.personName),
                        size = 40.dp,
                        background = if (row.paused) colors.moneyWash else colors.primaryWash,
                        contentColor = if (row.paused) colors.moneyInk else colors.primaryPressed,
                    )
                },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        TickingDuration(
                            elapsedSeconds = row.elapsedSeconds,
                            runningSince = row.runningSince,
                            paused = row.paused,
                            withSeconds = false,
                            style = FieldTrackTheme.type.numeric,
                            color = colors.ink,
                        )
                        if (row.paused) {
                            FtStatusPill(
                                status = FtStatus.Paused,
                                label = stringResource(R.string.status_paused),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        } else if (!row.clockConfirmed && ClockCheck.looksForgotten(row.runningSince, now)) {
                            // The clock in this row is the one that is wrong, so
                            // the row says since when rather than which machine.
                            Text(
                                text = stringResource(
                                    R.string.home_crew_since, sinceMoment(row.runningSince ?: now, now),
                                ),
                                style = FieldTrackTheme.type.caption,
                                color = colors.moneyInk,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        } else if (row.machineName != null) {
                            Text(
                                text = row.machineName,
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkTertiary,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                },
                onClick = { onOpenJob(row.jobId) },
            )
        }
    }
}
