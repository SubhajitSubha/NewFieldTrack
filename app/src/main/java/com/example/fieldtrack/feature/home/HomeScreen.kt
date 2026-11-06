package com.example.fieldtrack.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.iconForWork
import com.example.fieldtrack.feature.measure
import com.example.fieldtrack.feature.plan.PlanCard

/**
 * A one-person business's home.
 *
 * The deep card is the day's money, because for someone who is the whole
 * business that is the answer to "how is it going". Directly under it, the
 * job in hand or the button to start one; then what needs them; then who owes.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onStartWork: () -> Unit,
    onOpenLive: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenSeason: () -> Unit,
    onAddPastWork: () -> Unit,
    /** Opens a finished job. Null when this person may not amend. */
    onOpenJob: ((String) -> Unit)?,
    onOpenBackup: () -> Unit,
    alertActions: AlertActions,
    modifier: Modifier = Modifier,
    onOpenPlan: () -> Unit = {},
) {
    val blocks = buildList {
        if (state.canSeeMoney) {
            add(HomeBlock(Pane.Main) {
                TodayMoneyCard(
                    loaded = state.loaded,
                    earned = state.earnedTodayMinor,
                    jobs = state.jobsToday,
                    area = state.areaToday.takeIf { it > 0 }?.let { measure(it, state.areaUnit) },
                    week = state.weekEarned,
                )
            })
        }

        add(HomeBlock(Pane.Main) {
            OwnWork(
                job = state.openJob,
                onStartWork = onStartWork,
                onAddPastWork = onAddPastWork,
                onOpenLive = onOpenLive,
                onPause = onPause,
                onResume = onResume,
                // Without the money card above, the job is the screen's one deep card.
                dark = !state.canSeeMoney,
            )
        })

        add(HomeBlock(Pane.Main) { PlanCard(state.plan, onOpenPlan) })

        add(HomeBlock(Pane.Main) { AttentionCard(state.alerts, state.loaded, alertActions) })

        if (state.canSeeMoney) {
            add(HomeBlock(Pane.Main) {
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

        add(HomeBlock(Pane.Side) {
            HomeSection(stringResource(R.string.home_todays_work))
            TodayList(state.today, state.canSeeMoney, onOpenJob)
        })
    }

    HomeFrame(
        header = {
            HomeHeader(
                title = state.displayName,
                subtitle = state.businessName.takeIf { it != state.displayName },
                pendingUploads = state.pendingUploads,
                onOpenSync = onOpenBackup,
                stuck = state.uploadsStuck,
            )
        },
        blocks = blocks,
        modifier = modifier,
    )
}

/** The day's money on the deep card, with the week behind it for scale. */
@Composable
internal fun TodayMoneyCard(
    loaded: Boolean,
    earned: Long,
    jobs: Int,
    area: String?,
    week: List<Double>,
    overline: String = stringResource(R.string.home_earned_today),
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = FieldTrackTheme.colors
    FtEmphasisCard {
        Text(
            text = overline.uppercase(),
            style = FieldTrackTheme.type.overline,
            color = colors.onDarkMuted,
        )
        Text(
            text = if (loaded) money(earned) else NOT_YET,
            style = FieldTrackTheme.type.moneyHero,
            color = colors.onDark,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = listOfNotNull(
                pluralStringResource(R.plurals.count_jobs, jobs, jobs),
                area,
            ).joinToString(stringResource(R.string.cost_detail_sep)),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.onDarkMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
        extra?.invoke(this)
        Box(
            Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .height(FieldTrackTheme.dimens.hairline)
                .background(colors.onDarkFaint)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.home_last_seven),
                style = FieldTrackTheme.type.caption,
                color = colors.onDarkMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = money(week.sum().toLong()),
                style = FieldTrackTheme.type.numeric,
                color = colors.onDark,
            )
        }
        SevenDays(week, dark = true, modifier = Modifier.padding(top = 12.dp), barHeight = 40.dp)
    }
}

/** The job in hand, or the way to start one. */
@Composable
internal fun OwnWork(
    job: OpenJob?,
    onStartWork: () -> Unit,
    onAddPastWork: () -> Unit,
    onOpenLive: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    dark: Boolean,
    startLabel: String = stringResource(R.string.work_start),
) {
    if (job != null) {
        OpenJobCard(
            job = job,
            onOpen = { onOpenLive(job.jobId) },
            onPause = { onPause(job.jobId) },
            onResume = { onResume(job.jobId) },
            dark = dark,
        )
        return
    }
    // Stacked, not side by side: at half a phone's width "Work done earlier"
    // breaks over two lines, and a button whose label wraps reads as two buttons.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FtButton(
            text = startLabel,
            onClick = onStartWork,
            icon = FieldTrackIcons.Play,
        )
        FtButton(
            text = stringResource(R.string.home_add_past),
            onClick = onAddPastWork,
            tone = FtButtonTone.Secondary,
            icon = FieldTrackIcons.Calendar,
            height = FieldTrackTheme.dimens.buttonHeightCompact,
        )
    }
}

/** Owed, and the season so far: the two figures that say whether the business is healthy. */
@Composable
internal fun MoneyFigures(
    loaded: Boolean,
    owed: Long,
    owing: Int,
    season: Long,
    seasonFrom: Long,
    seasonName: String?,
    onOpenCustomers: () -> Unit,
    onOpenSeason: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FigureCard(
            label = stringResource(R.string.money_owed_to_you),
            value = if (loaded) money(owed) else NOT_YET,
            note = if (owing > 0) {
                stringResource(
                    R.string.home_from_customers,
                    pluralStringResource(R.plurals.count_customers, owing, owing),
                )
            } else {
                stringResource(R.string.home_nobody_owes)
            },
            onClick = onOpenCustomers,
            valueColor = if (owed > 0) colors.dueInk else colors.ink,
            modifier = Modifier.weight(1f),
        )
        FigureCard(
            label = stringResource(R.string.home_this_season),
            value = if (loaded) money(season) else NOT_YET,
            note = if (seasonName == null) {
                stringResource(R.string.home_season_since, dayMonth(seasonFrom))
            } else {
                stringResource(R.string.home_season_named_since, seasonName, dayMonth(seasonFrom))
            },
            onClick = onOpenSeason,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun TodayList(
    rows: List<TodayRow>,
    showMoney: Boolean,
    onOpenJob: ((String) -> Unit)?,
) {
    val colors = FieldTrackTheme.colors
    if (rows.isEmpty()) {
        FtCard {
            Text(
                text = stringResource(R.string.home_nothing_yet_title),
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.home_nothing_yet_body),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        return
    }
    FtCard(padding = 0.dp) {
        rows.forEachIndexed { index, row ->
            if (index > 0) FtRowDivider()
            FtListRow(
                title = row.customerName,
                subtitle = listOfNotNull(
                    workAndAmount(row.workType, row.amountDone, row.unit),
                    row.byName,
                ).joinToString(stringResource(R.string.cost_detail_sep)),
                leading = {
                    FtIconTile(iconForWork(row.workType, row.machineKind), size = 40.dp)
                },
                trailing = if (showMoney || !row.sent) {
                    {
                        Column(horizontalAlignment = Alignment.End) {
                            if (showMoney) {
                                Text(
                                    text = money(row.amountMinor),
                                    style = FieldTrackTheme.type.numeric,
                                    color = colors.ink,
                                )
                                row.lostMinor?.let { LostMark(it) }
                            }
                            if (!row.sent) NotSentMark()
                        }
                    }
                } else {
                    null
                },
                // Same-day corrections are the common ones: the acres were guessed
                // at the headland and counted properly over tea an hour later.
                onClick = onOpenJob?.let { open -> { open(row.jobId) } },
            )
        }
    }
}
