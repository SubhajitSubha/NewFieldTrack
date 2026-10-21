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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.daysAgo

@Composable
fun LedgerScreen(
    state: LedgerState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onRecordPayment: () -> Unit,
    onRemind: () -> Unit,
    /** Null when this person may not correct customer details. */
    onEdit: (() -> Unit)?,
    /** Opens the job behind a charge, to correct it. Null when not permitted. */
    onOpenJob: ((String) -> Unit)?,
    onStatement: () -> Unit,
    onNewJob: () -> Unit,
    tab: CustomerTab,
    onTab: (CustomerTab) -> Unit,
    /** Job titles for payments that were taken against a particular job. */
    jobTitles: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val owes = state.balanceRupees > 0

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = state.customerName,
            subtitle = state.village,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = {
                // A reminder is a quick, occasional nudge — it belongs with the other
                // small actions on the customer, not taking a third of a bottom bar.
                if (owes && !state.customerPhone.isNullOrBlank()) {
                    FtIconButton(
                        icon = FieldTrackIcons.WhatsApp,
                        contentDescription = stringResource(R.string.ledger_remind),
                        onClick = onRemind,
                        bordered = true,
                        background = colors.surface,
                        tint = colors.money,
                    )
                }
                if (onEdit != null) {
                    FtIconButton(
                        icon = FieldTrackIcons.Edit,
                        contentDescription = stringResource(R.string.edit_cd),
                        onClick = onEdit,
                        bordered = true,
                        background = colors.surface,
                    )
                }
                if (!state.customerPhone.isNullOrBlank()) {
                    FtIconButton(
                        icon = FieldTrackIcons.Call,
                        contentDescription = stringResource(R.string.ledger_call_cd),
                        onClick = onCall,
                        bordered = true,
                        background = colors.surface,
                    )
                }
            },
        )

        // Belt and braces, as with the rate card: the way in is already gated, but
        // a screen whose whole content is somebody's balance should refuse on its
        // own rather than trust that nothing ever reaches it.
        if (state.loaded && !state.canSeeMoney) {
            FtCard(modifier = Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(R.string.customer_not_for_you),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                // Red when money is out, green when the khata is clear. The colour is
                // the whole message for someone who reads slowly.
                FtCard(
                    background = if (owes) colors.due else colors.primaryWash,
                    borderColor = if (owes) colors.due else colors.primaryWash,
                    padding = 18.dp,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            FtSectionLabel(
                                stringResource(
                                    if (owes) R.string.ledger_they_owe
                                    else R.string.ledger_settled_title
                                ),
                                color = if (owes) colors.onDarkMuted else colors.primaryPressed,
                            )
                            FtMoney(
                                amount = if (owes) state.balanceRupees else 0,
                                size = FtMoneySize.Hero,
                                onDark = owes,
                                tone = if (owes) FtMoneyTone.Neutral else FtMoneyTone.Received,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        state.oldestDebtMillis?.let { oldest ->
                            Text(
                                text = stringResource(R.string.ledger_oldest, daysAgo(oldest)),
                                style = FieldTrackTheme.type.caption,
                                color = colors.onDarkMuted,
                            )
                        }
                    }
                }
            }

            item {
                CustomerTabStrip(
                    selected = tab,
                    onSelect = onTab,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            item { CustomerStatsFor(tab, state) }

            if (tab == CustomerTab.Jobs) {
                if (state.loaded && state.jobs.isEmpty()) {
                    item { CustomerEmpty(R.string.customer_jobs_empty) }
                }
                monthSections(
                    rows = state.jobs,
                    atMillis = { it.atMillis },
                    amount = { it.amountRupees },
                    totalColor = { FieldTrackTheme.colors.inkSecondary },
                    key = { it.id },
                ) { job ->
                    CustomerJobCard(
                        row = job,
                        onClick = job.jobId?.let { id -> onOpenJob?.let { open -> { open(id) } } },
                    )
                }
                item { Box(Modifier.height(dimens.space4)) }
                return@LazyColumn
            }

            if (tab == CustomerTab.Payments) {
                if (state.loaded && state.payments.isEmpty()) {
                    item { CustomerEmpty(R.string.customer_payments_empty) }
                }
                monthSections(
                    rows = state.payments,
                    atMillis = { it.atMillis },
                    amount = { it.amountRupees },
                    totalColor = { FieldTrackTheme.colors.primaryPressed },
                    key = { it.id },
                ) { payment ->
                    CustomerPaymentCard(
                        row = payment,
                        againstLabel = payment.jobId?.let { jobTitles[it] },
                    )
                }
                item { Box(Modifier.height(dimens.space4)) }
                return@LazyColumn
            }

            if (state.loaded && state.rows.isEmpty()) {
                item { CustomerEmpty(R.string.ledger_empty) }
            }

            items(state.rows, key = { it.id }) { row ->
                FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
                    FtListRow(
                        title = row.title(),
                        subtitle = dayMonth(row.atMillis),
                        // "This 3,500 is wrong" is how the argument actually starts,
                        // so the line itself is the way back to the job. Only for a
                        // charge: a payment now carries a job id too, and tapping
                        // money received to go and edit a bill makes no sense.
                        onClick = row.jobId
                            ?.takeIf { row.isDebit }
                            ?.let { id -> onOpenJob?.let { open -> { open(id) } } },
                        leading = {
                            FtIconTile(
                                icon = if (row.isDebit) FieldTrackIcons.Add else FieldTrackIcons.Minus,
                                size = 38.dp,
                                background = if (row.isDebit) colors.dueWash else colors.primaryWash,
                                tint = if (row.isDebit) colors.due else colors.primary,
                            )
                        },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = (if (row.isDebit) "+" else "−") +
                                        rupees(row.amountRupees),
                                    style = FieldTrackTheme.type.numeric,
                                    color = if (row.isDebit) colors.due else colors.primaryPressed,
                                )
                                // The running balance as of this line, so the column
                                // can be read straight down during an argument.
                                Text(
                                    text = rupees(row.balanceAfterRupees),
                                    style = FieldTrackTheme.type.caption,
                                    color = colors.inkTertiary,
                                )
                            }
                        },
                    )
                }
            }

            item { Box(Modifier.height(dimens.space4)) }
        }

        // Pinned rather than scrolled past: these are what the screen is for, and on
        // a customer with forty khata lines they were previously only reachable by
        // scrolling back to the top.
        Row(
            Modifier
                .background(colors.paper)
                .navigationBarsPadding()
                .padding(dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            FtButton(
                text = stringResource(R.string.statement_title),
                onClick = onStatement,
                modifier = Modifier.weight(1f),
                tone = FtButtonTone.Secondary,
                height = 54.dp,
            )
            FtButton(
                text = stringResource(R.string.ledger_got_money),
                onClick = onRecordPayment,
                modifier = Modifier.weight(1f),
                height = 54.dp,
            )
            FtIconButton(
                icon = FieldTrackIcons.Add,
                contentDescription = stringResource(R.string.customer_new_job_cd),
                onClick = onNewJob,
                tint = colors.onPrimary,
                background = colors.primaryPressed,
            )
        }
    }
}

/** What a khata line is called, in words a farmer would use. */
@Composable
private fun LedgerRow.title(): String = when (kind) {
    LedgerKind.Opening -> stringResource(R.string.ledger_opening_label)
    LedgerKind.Payment -> stringResource(
        R.string.ledger_payment_label,
        stringResource(method.labelRes()),
    )
    LedgerKind.Adjustment -> label.ifBlank { stringResource(R.string.ledger_adjustment_label) }
    LedgerKind.Charge -> label.ifBlank { stringResource(R.string.finish_this_job) }
}

@Composable
private fun PaymentMethod?.labelRes(): Int = when (this) {
    PaymentMethod.Upi -> R.string.method_upi
    PaymentMethod.Bank -> R.string.method_bank
    PaymentMethod.Cheque -> R.string.method_cheque
    else -> R.string.method_cash
}

/** The three figures that matter on whichever tab is showing. */
@Composable
private fun CustomerStatsFor(tab: CustomerTab, state: LedgerState) {
    when (tab) {
        CustomerTab.Khata -> CustomerStats(
            state.rows.size.toString() to stringResource(R.string.customer_stat_lines),
            rupees(state.billedRupees) to stringResource(R.string.customer_stat_billed),
            rupees(state.receivedRupees) to stringResource(R.string.customer_stat_received),
        )
        CustomerTab.Jobs -> CustomerStats(
            state.jobs.size.toString() to stringResource(R.string.customer_stat_jobs),
            rupees(state.billedRupees) to stringResource(R.string.customer_stat_billed),
            state.unpaidJobCount.toString() to stringResource(R.string.customer_stat_unpaid),
        )
        CustomerTab.Payments -> CustomerStats(
            rupees(state.receivedRupees) to stringResource(R.string.customer_stat_received),
            state.payments.size.toString() to stringResource(R.string.customer_stat_payments),
            (state.lastPaymentMillis?.let { dayMonth(it) }
                ?: stringResource(R.string.customer_none)) to
                stringResource(R.string.customer_stat_last),
        )
    }
}
