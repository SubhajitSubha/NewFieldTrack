package com.example.fieldtrack.feature.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
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
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.daysAgo

/**
 * Choosing particular jobs to put on a statement.
 *
 * Unpaid work is listed first and ticked to begin with, because somebody who reaches
 * for this screen is nearly always answering "which one are you talking about?" about
 * a job that has not been paid for. Settled jobs are still here, unticked, for the
 * customer who wants proof that something was cleared.
 */
@Composable
fun StatementPickJobsScreen(
    state: StatementState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val unpaid = state.jobs.filterNot { it.settled }
    val settled = state.jobs.filter { it.settled }
    val pickedTotal = state.jobs
        .filter { it.entryId in state.pickedEntryIds }
        .sumOf { it.amountRupees }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.statement_picker_title),
            subtitle = state.customerName.ifBlank { null },
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = {
                if (state.pickedEntryIds.isNotEmpty()) {
                    // Width-constrained: the action slot sits beside the title, and
                    // a button that fills it pushes the screen's own name off.
                    FtButton(
                        text = stringResource(R.string.statement_picker_clear),
                        onClick = onClear,
                        modifier = Modifier.width(84.dp),
                        tone = FtButtonTone.Quiet,
                        height = 40.dp,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.space4,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.statement_picker_hint),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (unpaid.isNotEmpty()) {
                item { FtSectionLabel(stringResource(R.string.statement_picker_unpaid), color = colors.due) }
                items(unpaid, key = { it.entryId }) { job ->
                    JobRow(job, job.entryId in state.pickedEntryIds, onToggle)
                }
            }

            if (settled.isNotEmpty()) {
                item {
                    FtSectionLabel(
                        stringResource(R.string.statement_picker_settled),
                        Modifier.padding(top = 6.dp),
                    )
                }
                items(settled, key = { it.entryId }) { job ->
                    JobRow(job, job.entryId in state.pickedEntryIds, onToggle)
                }
            }

            item { Box(Modifier.height(dimens.space4)) }
        }

        Column(
            Modifier
                .background(colors.paper)
                .padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.statement_picked,
                        pluralStringResource(
                            R.plurals.statement_jobs,
                            state.pickedEntryIds.size,
                            state.pickedEntryIds.size,
                        ),
                    ),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = rupees(pickedTotal),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            }
            FtButton(
                text = stringResource(R.string.statement_picker_make),
                onClick = onDone,
                enabled = state.pickedEntryIds.isNotEmpty(),
                height = 56.dp,
            )
        }
    }
}

@Composable
private fun JobRow(job: PickableJob, picked: Boolean, onToggle: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    FtCard(
        padding = 0.dp,
        shape = FieldTrackTheme.shapes.medium,
        background = if (picked) colors.primaryWash else colors.surface,
        borderColor = if (picked) colors.primary else colors.line,
        borderWidth = if (picked) FieldTrackTheme.dimens.borderSelected
        else FieldTrackTheme.dimens.hairline,
    ) {
        FtListRow(
            title = job.title.ifBlank { stringResource(R.string.finish_this_job) },
            subtitle = listOfNotNull(
                dayMonth(job.atMillis),
                if (job.settled) stringResource(R.string.statement_paid_label) else daysAgo(job.atMillis),
            ).joinToString(" · "),
            leading = { Tick(picked) },
            trailing = {
                Text(
                    text = rupees(job.amountRupees),
                    style = FieldTrackTheme.type.numeric,
                    color = if (job.settled) colors.inkSecondary else colors.ink,
                )
            },
            onClick = { onToggle(job.entryId) },
        )
    }
}

/** A checkbox drawn to the app's own shapes rather than the platform's. */
@Composable
private fun Tick(picked: Boolean) {
    val colors = FieldTrackTheme.colors
    Box(
        Modifier
            .size(26.dp)
            .background(
                color = if (picked) colors.primary else colors.surface,
                shape = FieldTrackTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Icon(
                imageVector = FieldTrackIcons.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(colors.surfaceMuted, FieldTrackTheme.shapes.small)
            )
        }
    }
}
