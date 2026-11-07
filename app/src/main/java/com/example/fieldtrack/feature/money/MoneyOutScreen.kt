package com.example.fieldtrack.feature.money

import androidx.compose.foundation.background
import com.example.fieldtrack.feature.labelRes
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.money.MoneyOutRow as Row_
import com.example.fieldtrack.designsystem.format.money

/**
 * What the business has paid out.
 *
 * Shows the total first and what cannot be laid against any machine second,
 * because the second number is the one that makes the first one honest.
 */
@Composable
fun MoneyOutScreen(
    state: MoneyOutState,
    onBack: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
    onStartHirePay: (com.example.fieldtrack.data.repository.HireBalance) -> Unit = {},
    onHireAmount: (String) -> Unit = {},
    onHireMethod: (com.example.fieldtrack.data.db.PaymentMethod) -> Unit = {},
    onSaveHirePay: () -> Unit = {},
    onDismissHirePay: () -> Unit = {},
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.money_out_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        if (!state.mayLook) {
            Column(Modifier.weight(1f).padding(dimens.screenPadding)) {
                FtCard {
                    Text(
                        text = stringResource(R.string.money_out_locked),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                FtCard(background = colors.deep, borderColor = colors.deep) {
                    Text(
                        text = stringResource(R.string.money_out_title).uppercase(),
                        style = FieldTrackTheme.type.caption,
                        color = colors.onDarkMuted,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = money(state.totalMinor),
                        style = FieldTrackTheme.type.moneyHero,
                        color = colors.onDark,
                    )
                    if (state.unattributableMinor > 0) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = stringResource(
                                R.string.money_out_unattributable,
                                money(state.unattributableMinor),
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.onDarkMuted,
                        )
                    }
                }
            }

            // Owed money is shown here but never added in. This screen is the
            // till: the hero figure above is what has actually left it, and
            // folding in work that has not been paid for would make the one
            // number somebody reconciles against their cash box wrong.
            if (state.owedMinor > 0) {
                item(key = "owed") {
                    FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                        Text(
                            text = stringResource(
                                R.string.money_out_owed, money(state.owedMinor),
                            ),
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.dueInk,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.money_out_owed_note),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.dueInk,
                        )
                    }
                }
            }

            // A cost with no figure on it is worse than one that is owed: owed
            // money is at least counted against its job, while this is missing
            // from every total in the app and nothing else says so.
            if (state.unpricedCount > 0) {
                item(key = "unpriced") {
                    FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.money_out_unpriced,
                                state.unpricedCount,
                                state.unpricedCount,
                            ),
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.money_out_unpriced_note),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            // What is owed to the people whose machines are in use, with what has been paid.
            if (state.hire.isNotEmpty()) {
                item(key = "hire-title") { FtSectionLabel(stringResource(R.string.hire_section)) }
                items(state.hire, key = { "hire-" + it.payeeKey }) { balance ->
                    FtCard {
                        Text(text = balance.name, style = FieldTrackTheme.type.sectionTitle, color = colors.ink)
                        Text(
                            text = balance.machines.joinToString(", ") { it.name },
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.hire_owed_paid, money(balance.owedMinor), money(balance.paidMinor)),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                        )
                        Text(
                            text = stringResource(R.string.hire_left, money(balance.balanceMinor.coerceAtLeast(0))),
                            style = FieldTrackTheme.type.rowTitle,
                            color = if (balance.balanceMinor > 0) colors.dueInk else colors.ink,
                        )
                        if (state.mayPay && balance.balanceMinor > 0) {
                            Spacer(Modifier.height(8.dp))
                            FtButton(
                                text = stringResource(R.string.hire_pay),
                                onClick = { onStartHirePay(balance) },
                                tone = com.example.fieldtrack.designsystem.component.FtButtonTone.Secondary,
                                height = 48.dp,
                            )
                        }
                    }
                }
            }

            if (state.loaded && state.rows.isEmpty()) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(R.string.money_out_none),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            val byDay = state.rows.groupBy { dayMonthYear(it.atMillis) }
            byDay.forEach { (day, rows) ->
                item(key = "d-$day") { FtSectionLabel(day) }
                items(rows, key = { it.id }) { row -> PaidRow(row) }
            }

            item { Spacer(Modifier.height(dimens.screenPadding)) }
        }

        if (state.mayPay) {
            Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
                FtButton(text = stringResource(R.string.money_out_pay), onClick = onPay)
            }
        }
        state.paying?.let { pay ->
            HirePaySheet(pay, onHireAmount, onHireMethod, onSaveHirePay, onDismissHirePay)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HirePaySheet(
    pay: HirePay,
    onAmount: (String) -> Unit,
    onMethod: (com.example.fieldtrack.data.db.PaymentMethod) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.hire_pay_title, pay.balance.name),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            com.example.fieldtrack.designsystem.component.FtTextField(
                label = stringResource(R.string.hire_pay_amount),
                value = pay.amountText,
                onValueChange = onAmount,
                prefix = com.example.fieldtrack.designsystem.format.Money.style.symbol,
                keyboardType = if (com.example.fieldtrack.designsystem.format.Money.style.showsDecimals)
                    androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number,
                focused = true,
            )
            com.example.fieldtrack.designsystem.component.FtPick(
                options = com.example.fieldtrack.designsystem.format.Money.methods,
                selected = pay.method,
                text = { stringResource(it.labelRes) },
                onPick = onMethod,
                label = stringResource(R.string.hire_pay_method),
                searchHint = stringResource(R.string.pick_search),
            )
            FtButton(text = stringResource(R.string.hire_pay_save), onClick = onSave, enabled = pay.canSave)
        }
    }
}

@Composable
private fun PaidRow(row: Row_) {
    val colors = FieldTrackTheme.colors
    FtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name.ifBlank {
                        pluralStringResource(
                            R.plurals.money_out_heads, row.headcount, row.headcount,
                        )
                    },
                    style = FieldTrackTheme.type.body.copy(
                        textDecoration = if (row.voided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    ),
                    color = if (row.voided) colors.inkTertiary else colors.ink,
                )
                Text(
                    text = when (row.covers) {
                        PayoutCovers.Job -> stringResource(R.string.money_out_for_job)
                        PayoutCovers.Day -> stringResource(R.string.money_out_for_day)
                        PayoutCovers.Period -> stringResource(R.string.money_out_for_period)
                        PayoutCovers.Advance -> stringResource(R.string.money_out_for_advance)
                        PayoutCovers.Hire -> stringResource(R.string.money_out_for_hire)
                    },
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkSecondary,
                )
            }
            Text(
                text = money(row.amountMinor),
                style = FieldTrackTheme.type.sectionTitle.copy(
                    textDecoration = if (row.voided) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                ),
                color = if (row.voided) colors.inkTertiary else colors.ink,
            )
        }
    }
}
