package com.example.fieldtrack.feature.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.greetingFor
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.iconForWork
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.workTypeOptions

@Composable
fun HomeScreen(
    state: HomeState,
    onStartWork: () -> Unit,
    onResumeWork: (String) -> Unit,
    onOpenCustomers: () -> Unit,
    onAddPastWork: () -> Unit,
    /** Opens a finished job for correction. Null when this person may not amend. */
    onOpenJob: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = dimens.screenPadding, end = dimens.screenPadding, top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FtAvatar(
                initials = initialsOf(state.displayName),
                background = colors.primary,
                contentColor = colors.onPrimary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = greetingFor(state.now),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
                Text(
                    text = state.displayName,
                    style = FieldTrackTheme.type.screenTitleCompact,
                    color = colors.ink,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.liveJobId != null) {
                ResumeBanner(
                    paused = state.liveJobPaused,
                    onClick = { onResumeWork(state.liveJobId) },
                )
            }

            StartWorkButton(onClick = onStartWork)

            // Right under the button it belongs beside: a phone that went flat is
            // discovered at the moment somebody reaches to start the next job.
            FtButton(
                text = stringResource(R.string.home_add_past),
                onClick = onAddPastWork,
                tone = FtButtonTone.Secondary,
                icon = FieldTrackIcons.Calendar,
                height = 50.dp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FtCard(Modifier.weight(1f)) {
                    FtSectionLabel(stringResource(R.string.money_today))
                    FtMoney(
                        state.earnedTodayRupees,
                        size = FtMoneySize.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.count_jobs, state.jobsToday, state.jobsToday
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // The dues figure is the way into the khata. A contractor who sees a
                // number he wants to act on should be able to tap it.
                FtCard(Modifier.weight(1f), onClick = onOpenCustomers) {
                    FtSectionLabel(stringResource(R.string.money_owed_to_you))
                    FtMoney(
                        state.owedRupees,
                        size = FtMoneySize.Medium,
                        tone = FtMoneyTone.Due,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.home_from_customers,
                            pluralStringResource(
                                R.plurals.count_customers, state.owingCustomers, state.owingCustomers
                            ),
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.home_todays_work), Modifier.padding(top = 4.dp))

            if (state.today.isEmpty()) {
                EmptyToday()
            } else {
                FtCard(padding = 0.dp) {
                    state.today.forEachIndexed { index, row ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = row.customerName,
                            subtitle = row.summary(),
                            leading = { FtIconTile(row.icon()) },
                            trailing = {
                                FtMoney(row.amountRupees, size = FtMoneySize.Row)
                            },
                            // Same-day corrections are the common ones: the acres
                            // were guessed at the headland and counted properly
                            // over tea an hour later.
                            onClick = onOpenJob?.let { open -> { open(row.jobId) } },
                        )
                    }
                }
            }

            Box(Modifier.height(dimens.space6))
        }
    }
}

/**
 * The one thing this screen exists for. A Row rather than a button because it carries
 * two lines, with the click and the button role attached to the whole surface.
 */
@Composable
private fun StartWorkButton(onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FieldTrackTheme.shapes.large)
            .background(colors.primary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(colors.onDarkFaint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FieldTrackIcons.Play,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Column {
            Text(
                text = stringResource(R.string.work_start),
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

@Composable
private fun ResumeBanner(paused: Boolean, onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors
    FtCard(
        background = if (paused) colors.moneyWash else colors.primaryWash,
        borderColor = if (paused) colors.money else colors.primary,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtStatusPill(
                status = if (paused) FtStatus.Paused else FtStatus.Running,
                label = stringResource(
                    if (paused) R.string.home_resume_paused else R.string.home_resume_running
                ),
            )
            Box(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.home_resume_action),
                style = FieldTrackTheme.type.label,
                color = if (paused) colors.moneyInk else colors.primaryPressed,
            )
        }
    }
}

@Composable
private fun EmptyToday() {
    FtCard {
        Text(
            text = stringResource(R.string.home_nothing_yet_title),
            style = FieldTrackTheme.type.rowTitle,
            color = FieldTrackTheme.colors.ink,
        )
        Text(
            text = stringResource(R.string.home_nothing_yet_body),
            style = FieldTrackTheme.type.bodySmall,
            color = FieldTrackTheme.colors.inkTertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The machine's glyph where one was used, otherwise the work's own.
 *
 * A job recorded without a machine is common — a contractor doing a job by hand, or
 * one who never bothered adding his tractor — and a row of anonymous dots tells a
 * non-reading user nothing.
 */
@Composable
private fun TodayRow.icon() = iconForWork(workType, machineKind)

/**
 * "Rotavator · 3.5 acre", or the elapsed time when there is nothing counted — hourly
 * work has no quantity worth showing in a list.
 */
@Composable
private fun TodayRow.summary(): String {
    val unitLabel = stringResource(unit.labelRes)
    val measure = if (amountDone == 0.0) duration(elapsedSeconds)
    else "${quantity(amountDone)} $unitLabel"
    return "$workType · $measure"
}
