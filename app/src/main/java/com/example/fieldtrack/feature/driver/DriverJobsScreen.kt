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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.WeekBars
import com.example.fieldtrack.feature.dayOfMonth
import com.example.fieldtrack.feature.monthShort
import com.example.fieldtrack.feature.measure
import com.example.fieldtrack.feature.workLabel
import com.example.fieldtrack.feature.labelRes

/**
 * A driver's own work over time.
 *
 * Hours are the headline because hours are what a driver is paid for. The owner's
 * version of this screen leads with money; leading with it here would be showing
 * somebody the till they do not own, next to the work they actually did.
 */
@Composable
fun DriverJobsScreen(
    state: DriverJobsState,
    onPeriod: (DriverPeriod) -> Unit,
    onAddPastWork: () -> Unit,
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
            title = stringResource(R.string.driver_my_work),
            subtitle = state.businessName,
            action = {
                FtIconButton(
                    icon = FieldTrackIcons.Add,
                    contentDescription = stringResource(R.string.driver_add_earlier_cd),
                    onClick = onAddPastWork,
                    tint = colors.onPrimary,
                    background = colors.primary,
                )
            },
        )

        // Scrollable, because three chips at a larger font or in a longer language
        // will not fit across a phone.
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DriverPeriod.entries.forEach { option ->
                FtChip(
                    label = stringResource(option.labelRes()),
                    selected = state.period == option,
                    onClick = { onPeriod(option) },
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding)
                .padding(top = 13.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            FtCard(background = colors.ink, borderColor = colors.ink) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(state.period.headlineRes()).uppercase(),
                            style = FieldTrackTheme.type.overline,
                            color = colors.onDarkMuted,
                        )
                        Text(
                            text = duration(state.totalSeconds),
                            style = FieldTrackTheme.type.moneyLarge,
                            color = colors.onDark,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.driver_job_count, state.jobCount, state.jobCount,
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.onDarkMuted,
                        )
                        if (state.areaCovered > 0) {
                            Text(
                                text = measure(state.areaCovered, state.areaUnit),
                                style = FieldTrackTheme.type.caption,
                                color = colors.onDarkMuted,
                            )
                        }
                    }
                }
            }

            WeekBars(state.weekHours)

            FtSectionLabel(stringResource(R.string.driver_every_job))
            if (state.jobs.isEmpty()) {
                if (state.loaded) {
                    FtCard {
                        Text(
                            text = stringResource(R.string.driver_no_work),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
            } else {
                FtCard(padding = 0.dp) {
                    state.jobs.forEachIndexed { index, job ->
                        if (index > 0) FtRowDivider()
                        WorkRow(job) { onOpenJob(job.jobId) }
                    }
                }
            }

            Box(Modifier.height(dimens.space6))
        }
    }
}

/** Date down the left, work in the middle, time on the right. */
@Composable
private fun WorkRow(job: DriverWorkRow, onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .clip(FieldTrackTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A date gutter rather than an icon: this list is read down the dates,
        // because the question is "what did I do on Tuesday".
        Column(
            Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dayOfMonth(job.workedAtMillis),
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = monthShort(job.workedAtMillis),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
        }

        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.customerName,
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                )
                // Said plainly, because a typed-in job and a timed one are not the
                // same evidence, and a driver whose hours are queried should be able
                // to see which is which on his own screen.
                if (job.byHand) {
                    Box(
                        Modifier
                            .padding(start = 7.dp)
                            .clip(FieldTrackTheme.shapes.chip)
                            .background(colors.surfaceMuted)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.driver_by_hand).uppercase(),
                            style = FieldTrackTheme.type.overline,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }
            Text(
                text = job.summary(),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Text(
            text = duration(job.elapsedSeconds),
            style = FieldTrackTheme.type.moneyMedium,
            color = if (job.live) colors.primary else colors.ink,
        )
    }
}

@Composable
private fun DriverWorkRow.summary(): String {
    val work = workLabel(workType)
    if (amountDone <= 0.0) return work
    return "$work · ${measure(amountDone, unit)}"
}

private fun DriverPeriod.labelRes() = when (this) {
    DriverPeriod.Week -> R.string.driver_period_week
    DriverPeriod.Month -> R.string.driver_period_month
    DriverPeriod.Season -> R.string.driver_period_season
}

private fun DriverPeriod.headlineRes() = when (this) {
    DriverPeriod.Week -> R.string.driver_hours_week
    DriverPeriod.Month -> R.string.driver_hours_month
    DriverPeriod.Season -> R.string.driver_hours_season
}
