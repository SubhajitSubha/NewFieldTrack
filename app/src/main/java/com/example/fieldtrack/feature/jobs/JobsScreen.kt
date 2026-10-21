package com.example.fieldtrack.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.component.stripeColor
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.measure
import com.example.fieldtrack.feature.workLabel
import com.example.fieldtrack.feature.iconForWork
import com.example.fieldtrack.feature.labelRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobsScreen(
    state: JobsState,
    onFilter: (JobFilter) -> Unit,
    onOpen: (JobCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(title = stringResource(R.string.jobs_title))

        FlowRow(
            Modifier.padding(horizontal = dimens.screenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JobFilter.entries.forEach { filter ->
                FtChip(
                    label = stringResource(filter.labelRes()),
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                top = 10.dp,
                bottom = dimens.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loaded && state.jobs.isEmpty()) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(state.filter.emptyRes()),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
            }

            items(state.jobs, key = { it.id }) { job ->
                FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
                    FtListRow(
                        title = job.customerName,
                        subtitle = job.detail(),
                        // Colour down the edge so a list of jobs scans by state
                        // before a single word is read.
                        statusStripe = job.status.stripeColor(),
                        leading = {
                            FtIconTile(iconForWork(job.workType, job.machineKind))
                        },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                if (job.live) {
                                    Text(
                                        text = duration(job.elapsedSeconds),
                                        style = FieldTrackTheme.type.numeric,
                                        color = colors.ink,
                                    )
                                    FtStatusPill(
                                        status = job.status,
                                        label = stringResource(job.status.labelRes()),
                                    )
                                } else {
                                    FtMoney(job.amountRupees, size = FtMoneySize.Row)
                                    Text(
                                        text = duration(job.elapsedSeconds),
                                        style = FieldTrackTheme.type.caption,
                                        color = colors.inkTertiary,
                                    )
                                }
                            }
                        },
                        onClick = { onOpen(job) },
                    )
                }
            }

            item { Box(Modifier.height(dimens.space4)) }
        }
    }
}

/** "Harvesting · 4 acre · Jaswant driving" — what, how much, and who. */
@Composable
private fun JobCard.detail(): String {
    val measure = if (quantity > 0) measure(quantity, unit) else null
    val who = operatorName?.let {
        if (live) stringResource(R.string.jobs_driving, it) else stringResource(R.string.jobs_by, it)
    }
    // The work type's own name, not the key it is stored under.
    return listOfNotNull(workLabel(workType), measure, who).joinToString(" · ")
}

private fun JobFilter.labelRes() = when (this) {
    JobFilter.Running -> R.string.jobs_filter_running
    JobFilter.Today -> R.string.jobs_filter_today
    JobFilter.Week -> R.string.jobs_filter_week
}

private fun JobFilter.emptyRes() = when (this) {
    JobFilter.Running -> R.string.jobs_none_running
    JobFilter.Today -> R.string.jobs_none_today
    JobFilter.Week -> R.string.jobs_none_week
}

private fun com.example.fieldtrack.designsystem.component.FtStatus.labelRes() = when (this) {
    com.example.fieldtrack.designsystem.component.FtStatus.Running -> R.string.status_running
    com.example.fieldtrack.designsystem.component.FtStatus.Paused -> R.string.status_paused
    else -> R.string.status_completed
}
