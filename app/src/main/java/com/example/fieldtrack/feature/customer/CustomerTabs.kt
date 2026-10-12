package com.example.fieldtrack.feature.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.monthYear

/**
 * The three faces of a customer's record.
 *
 * The khata is the argument-settling view and stays the default. Jobs answers "what
 * have you actually done for me", payments answers "what have I already given you" —
 * both questions a customer asks out loud, and both previously buried inside a single
 * mixed list that had to be read line by line to answer either.
 */
@Composable
fun CustomerTabStrip(
    selected: CustomerTab,
    onSelect: (CustomerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(FieldTrackTheme.shapes.medium)
            .background(colors.surfaceMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CustomerTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(FieldTrackTheme.shapes.small)
                    .background(if (active) colors.surface else colors.surfaceMuted)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tab.labelRes()),
                    style = FieldTrackTheme.type.label,
                    color = if (active) colors.ink else colors.inkSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun CustomerTab.labelRes(): Int = when (this) {
    CustomerTab.Khata -> R.string.customer_tab_khata
    CustomerTab.Jobs -> R.string.customer_tab_jobs
    CustomerTab.Payments -> R.string.customer_tab_payments
}

/** Three figures across the top of a tab, so the answer is there before scrolling. */
@Composable
fun CustomerStats(vararg stats: Pair<String, String>) {
    val colors = FieldTrackTheme.colors
    FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
        Row {
            stats.forEachIndexed { index, (value, label) ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(FieldTrackTheme.dimens.hairline)
                            .height(STAT_HEIGHT)
                            .background(colors.line)
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = value,
                        style = FieldTrackTheme.type.numeric,
                        color = colors.ink,
                    )
                    Text(
                        text = label,
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

/** Two lines of type plus padding; the dividers are drawn to it. */
private val STAT_HEIGHT = 52.dp

/**
 * A month's worth of rows under one heading, with the month's total beside it.
 *
 * Grouping by month is how a farmer remembers work — "that was the September lot" —
 * and the running total per month is what gets compared against memory.
 */
fun <T> LazyListScope.monthSections(
    rows: List<T>,
    atMillis: (T) -> Long,
    amount: (T) -> Long,
    totalColor: @Composable () -> androidx.compose.ui.graphics.Color,
    key: (T) -> String,
    row: @Composable (T) -> Unit,
) {
    rows.groupBy { monthYear(atMillis(it)) }.forEach { (month, inMonth) ->
        item(key = "month-$month") {
            Row(
                Modifier.padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = month.uppercase(),
                    style = FieldTrackTheme.type.caption,
                    color = FieldTrackTheme.colors.inkTertiary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = rupees(inMonth.sumOf(amount)),
                    style = FieldTrackTheme.type.label,
                    color = totalColor(),
                )
            }
        }
        items(inMonth.size, key = { key(inMonth[it]) }) { row(inMonth[it]) }
    }
}

/** One job, with whether it has been paid for said plainly on the right. */
@Composable
fun CustomerJobCard(row: CustomerJobRow, onClick: (() -> Unit)?) {
    val colors = FieldTrackTheme.colors
    FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
        FtListRow(
            title = row.title(),
            subtitle = listOfNotNull(dayMonth(row.atMillis), row.machineName)
                .joinToString(" · "),
            leading = { FtIconTile(FieldTrackIcons.Tractor, size = 44.dp) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = rupees(row.amountRupees),
                        style = FieldTrackTheme.type.numeric,
                        color = colors.ink,
                    )
                    Text(
                        text = row.statusLabel(),
                        style = FieldTrackTheme.type.caption,
                        color = if (row.settled) colors.primaryPressed else colors.due,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
            onClick = onClick,
        )
    }
}

/** "Rotavator · 3.5 Bigha", or just the work when nothing was measured. */
@Composable
private fun CustomerJobRow.title(): String {
    val base = workType.ifBlank { stringResource(R.string.finish_this_job) }
    if (billingMode == BillingMode.Fixed || quantity <= 0.0) return base
    return "$base · ${quantity(quantity)} ${stringResource(unit.labelRes)}"
}

/** Settled, unpaid, or what is left of it when a payment covered part. */
@Composable
private fun CustomerJobRow.statusLabel(): String = when {
    settled -> stringResource(R.string.customer_job_settled)
    outstandingRupees < amountRupees ->
        stringResource(R.string.customer_job_part_paid, rupees(outstandingRupees))
    else -> stringResource(R.string.customer_job_unpaid)
}

/** One payment, with what it was put against when that is known. */
@Composable
fun CustomerPaymentCard(row: CustomerPaymentRow, againstLabel: String?) {
    val colors = FieldTrackTheme.colors
    FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
        FtListRow(
            title = stringResource(row.method.labelRes()),
            subtitle = listOfNotNull(
                dayMonth(row.atMillis),
                againstLabel?.let { stringResource(R.string.customer_payment_against, it) }
                    ?: stringResource(R.string.customer_payment_on_account),
            ).joinToString(" · "),
            leading = {
                FtIconTile(
                    icon = row.method.icon(),
                    size = 44.dp,
                    background = colors.primaryWash,
                    tint = colors.primary,
                )
            },
            trailing = {
                Text(
                    text = stringResource(
                        R.string.customer_payment_amount,
                        rupees(row.amountRupees),
                    ),
                    style = FieldTrackTheme.type.numeric,
                    color = colors.primaryPressed,
                )
            },
        )
    }
}

private fun PaymentMethod?.labelRes(): Int = when (this) {
    PaymentMethod.Upi -> R.string.method_upi
    PaymentMethod.Bank -> R.string.method_bank
    PaymentMethod.Cheque -> R.string.method_cheque
    else -> R.string.method_cash
}

private fun PaymentMethod?.icon() = when (this) {
    PaymentMethod.Upi -> FieldTrackIcons.Mic
    PaymentMethod.Bank -> FieldTrackIcons.Report
    PaymentMethod.Cheque -> FieldTrackIcons.Bill
    else -> FieldTrackIcons.Rupee
}

/** Said once, in the middle of an empty tab, rather than left blank. */
@Composable
fun CustomerEmpty(textRes: Int) {
    FtCard {
        Text(
            text = stringResource(textRes),
            style = FieldTrackTheme.type.bodySmall,
            color = FieldTrackTheme.colors.inkTertiary,
        )
    }
}
