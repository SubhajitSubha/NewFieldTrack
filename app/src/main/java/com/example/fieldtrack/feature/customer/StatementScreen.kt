package com.example.fieldtrack.feature.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.daysAgo

/**
 * What a statement will cover, decided before it is sent.
 *
 * The summary card at the bottom is the reason this screen exists. Sending a customer
 * a demand for money is not an action to take blind, so the count and the total track
 * every tap — nobody should have to generate a document to find out what is on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatementScreen(
    state: StatementState,
    onBack: () -> Unit,
    onPeriod: (StatementPeriod) -> Unit,
    onPickDates: () -> Unit,
    onContent: (StatementContent) -> Unit,
    onPickJobs: () -> Unit,
    onPreview: () -> Unit,
    onSend: () -> Unit,
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
            title = stringResource(R.string.statement_title),
            subtitle = state.customerName.ifBlank { null },
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FtSectionLabel(stringResource(R.string.statement_period))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PeriodChip(R.string.statement_period_season, StatementPeriod.Season, state, onPeriod)
                PeriodChip(R.string.statement_period_year, StatementPeriod.FinancialYear, state, onPeriod)
                PeriodChip(R.string.statement_period_three_months, StatementPeriod.ThreeMonths, state, onPeriod)
                // Offered only when there is a settling point to count from; a chip
                // that silently means "everything" would be a small lie.
                if (!state.neverSettled) {
                    PeriodChip(R.string.statement_period_settled, StatementPeriod.SinceSettled, state, onPeriod)
                }
                PeriodChip(R.string.statement_period_everything, StatementPeriod.Everything, state, onPeriod)
                FtChip(
                    label = stringResource(R.string.statement_period_pick),
                    selected = state.period == StatementPeriod.Custom,
                    onClick = onPickDates,
                )
            }
            Text(
                text = rangeLabel(state),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )

            FtSectionLabel(
                stringResource(R.string.statement_content),
                Modifier.padding(top = 8.dp),
            )
            FtChoiceCard(
                title = stringResource(R.string.statement_everything),
                description = stringResource(R.string.statement_everything_note),
                selected = state.content == StatementContent.Everything,
                onClick = { onContent(StatementContent.Everything) },
            )
            FtChoiceCard(
                title = stringResource(R.string.statement_unpaid),
                description = stringResource(R.string.statement_unpaid_note),
                selected = state.content == StatementContent.UnpaidOnly,
                onClick = { onContent(StatementContent.UnpaidOnly) },
            )
            FtChoiceCard(
                title = stringResource(R.string.statement_payments),
                description = stringResource(R.string.statement_payments_note),
                selected = state.content == StatementContent.PaymentsOnly,
                onClick = { onContent(StatementContent.PaymentsOnly) },
            )
            FtChoiceCard(
                title = stringResource(R.string.statement_pick_jobs),
                description = stringResource(R.string.statement_pick_jobs_note),
                selected = state.content == StatementContent.PickedJobs,
                onClick = {
                    onContent(StatementContent.PickedJobs)
                    onPickJobs()
                },
            )

            Box(Modifier.padding(bottom = dimens.space4))
        }

        Column(
            Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            SummaryCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FtButton(
                    text = stringResource(R.string.statement_look),
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    tone = FtButtonTone.Secondary,
                    enabled = state.canSend,
                    height = 56.dp,
                )
                FtButton(
                    text = stringResource(
                        if (state.working) R.string.statement_making else R.string.statement_send
                    ),
                    onClick = onSend,
                    modifier = Modifier.weight(1.25f),
                    tone = FtButtonTone.Send,
                    icon = FieldTrackIcons.WhatsApp,
                    enabled = state.canSend,
                    height = 56.dp,
                )
            }
        }
    }
}

@Composable
private fun PeriodChip(
    labelRes: Int,
    period: StatementPeriod,
    state: StatementState,
    onPeriod: (StatementPeriod) -> Unit,
) {
    FtChip(
        label = stringResource(labelRes),
        selected = state.period == period,
        onClick = { onPeriod(period) },
    )
}

/** What the chosen options add up to, in money, before anything is sent. */
@Composable
private fun SummaryCard(state: StatementState) {
    val colors = FieldTrackTheme.colors
    val owes = state.showsDebt && state.totalRupees > 0

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                color = when {
                    state.lineCount == 0 -> colors.surfaceMuted
                    owes -> colors.dueWash
                    else -> colors.primaryWash
                },
                shape = FieldTrackTheme.shapes.card,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (state.lineCount == 0) {
            Text(
                text = stringResource(R.string.statement_nothing),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkTertiary,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FtSectionLabel(
                        stringResource(
                            if (state.showsDebt) R.string.statement_will_show
                            else R.string.statement_will_show_received
                        ),
                        color = if (owes) colors.dueInk else colors.primaryPressed,
                    )
                    Text(
                        text = summaryDetail(state),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                FtMoney(
                    amount = state.totalRupees,
                    size = FtMoneySize.Medium,
                    tone = if (owes) FtMoneyTone.Due else FtMoneyTone.Received,
                )
            }
        }
    }
}

/** "2 jobs · oldest 5 days", or just the count when nothing is overdue. */
@Composable
private fun summaryDetail(state: StatementState): String {
    val count = when (state.content) {
        StatementContent.UnpaidOnly, StatementContent.PickedJobs ->
            pluralStringResource(R.plurals.statement_jobs, state.lineCount, state.lineCount)
        else ->
            pluralStringResource(R.plurals.statement_lines, state.lineCount, state.lineCount)
    }
    val oldest = state.oldestUnpaidMillis
        ?.takeIf { state.content == StatementContent.UnpaidOnly }
        ?.let { stringResource(R.string.statement_oldest, daysAgo(it)) }
    return listOfNotNull(count, oldest).joinToString(" · ")
}

/** The days the chosen period resolves to, spelled out under the chips. */
@Composable
private fun rangeLabel(state: StatementState): String {
    val end = state.endMillis?.let { dayMonthYear(it) }
        ?: dayMonthYear(System.currentTimeMillis())
    val start = state.startMillis ?: return when {
        state.period == StatementPeriod.SinceSettled ->
            stringResource(R.string.statement_never_settled)
        else -> stringResource(R.string.statement_range_open, end)
    }
    return stringResource(R.string.statement_range, dayMonthYear(start), end)
}
