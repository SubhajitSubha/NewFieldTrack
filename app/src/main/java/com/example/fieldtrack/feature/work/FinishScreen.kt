package com.example.fieldtrack.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.labelRes

@Composable
fun FinishScreen(
    step: Int,
    state: FinishState,
    payment: PaymentState,
    receivedRupees: Long,
    finalBalanceRupees: Long,
    onBack: () -> Unit,
    onToBill: () -> Unit,
    onChooseAll: () -> Unit,
    onChooseSome: () -> Unit,
    onChooseNothing: () -> Unit,
    onAmount: (String) -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onSms: () -> Unit,
    onCopy: () -> Unit,
    onPdf: () -> Unit,
    onPrint: () -> Unit,
    /** True while a PDF is being made, so the row cannot be tapped twice. */
    makingPdf: Boolean = false,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = when (step) {
                0 -> stringResource(R.string.finish_check_title)
                1 -> stringResource(R.string.finish_bill_title)
                else -> stringResource(R.string.finish_saved)
            },
            onBack = if (step < 2) onBack else null,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            when (step) {
                0 -> ReviewStep(state)
                1 -> BillStep(state, payment, onChooseAll, onChooseSome, onChooseNothing, onAmount, onMethod)
                else -> SavedStep(state, receivedRupees, finalBalanceRupees)
            }
            Box(Modifier.height(dimens.space4))
        }

        Column(
            Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                0 -> FtButton(stringResource(R.string.finish_make_bill), onToBill, height = 60.dp)
                1 -> FtButton(
                    text = stringResource(R.string.finish_save_and_send),
                    onClick = onSave,
                    enabled = !payment.saving,
                    height = 60.dp,
                )
                else -> {
                    // Not every customer has WhatsApp, and not every bill is sent at
                    // all — some are read out over a call and the number copied into
                    // whatever the two of them already use.
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FtButton(
                            text = stringResource(
                                if (makingPdf) R.string.finish_pdf_making else R.string.finish_pdf
                            ),
                            onClick = onPdf,
                            modifier = Modifier.weight(if (makingPdf) 2.4f else 1f),
                            tone = FtButtonTone.Secondary,
                            enabled = !makingPdf,
                            height = 52.dp,
                        )
                        if (!makingPdf) {
                            FtButton(
                                text = stringResource(R.string.finish_print),
                                onClick = onPrint,
                                modifier = Modifier.weight(1f),
                                tone = FtButtonTone.Secondary,
                                height = 52.dp,
                            )
                            FtButton(
                                text = stringResource(R.string.finish_send_sms),
                                onClick = onSms,
                                modifier = Modifier.weight(1f),
                                tone = FtButtonTone.Secondary,
                                height = 52.dp,
                            )
                            FtButton(
                                text = stringResource(R.string.finish_copy),
                                onClick = onCopy,
                                modifier = Modifier.weight(1f),
                                tone = FtButtonTone.Secondary,
                                height = 52.dp,
                            )
                        }
                    }
                    FtButton(
                        text = stringResource(R.string.action_send_whatsapp),
                        onClick = onShare,
                        tone = FtButtonTone.Send,
                        icon = FieldTrackIcons.WhatsApp,
                        height = 60.dp,
                    )
                    FtButton(stringResource(R.string.finish_done), onDone, tone = FtButtonTone.Quiet, height = 50.dp)
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(state: FinishState) {
    val colors = FieldTrackTheme.colors
    FtCard(background = colors.primaryWash, borderColor = colors.primaryWash) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FieldTrackIcons.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(25.dp),
                )
            }
            Column(Modifier.padding(start = 13.dp)) {
                Text(
                    text = stringResource(R.string.finish_work_done),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.primaryPressed,
                )
                Text(
                    text = state.customerName,
                    style = FieldTrackTheme.type.caption,
                    color = colors.primaryPressed,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    FtCard(padding = 0.dp) {
        SummaryRow(
            stringResource(R.string.finish_time_worked),
            duration(state.elapsedSeconds),
        )
        if (state.billingMode != BillingMode.PerHour && state.billingMode != BillingMode.Fixed) {
            SummaryRow(
                stringResource(R.string.finish_amount_done),
                "${quantity(state.quantity)} ${stringResource(state.unit.labelRes)}",
            )
        }
        SummaryRow(
            stringResource(R.string.finish_rate),
            when {
                // Naming one rate for trips that were charged three different ways
                // is the sort of line a customer notices and stops trusting.
                state.ratesVaried -> stringResource(R.string.finish_rate_varies)
                state.billingMode == BillingMode.Fixed -> rupees(state.rateRupees)
                else -> stringResource(
                    R.string.start_rate_per,
                    stringResource(state.unit.labelRes)
                ).let { per -> "${rupees(state.rateRupees)} $per" }
            },
        )
    }

    FtEmphasisCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                FtSectionLabel(
                    stringResource(R.string.finish_total),
                    color = FieldTrackTheme.colors.onDarkMuted,
                )
                FtMoney(
                    state.jobAmountRupees,
                    size = FtMoneySize.Hero,
                    onDark = true,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillStep(
    state: FinishState,
    payment: PaymentState,
    onChooseAll: () -> Unit,
    onChooseSome: () -> Unit,
    onChooseNothing: () -> Unit,
    onAmount: (String) -> Unit,
    onMethod: (PaymentMethod) -> Unit,
) {
    FtCard {
        FtSectionLabel(stringResource(R.string.finish_bill_for))
        Text(
            text = state.customerName,
            style = FieldTrackTheme.type.rowTitle,
            color = FieldTrackTheme.colors.ink,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        AmountLine(stringResource(R.string.finish_this_job), state.jobAmountRupees)
        if (state.previousBalanceRupees != 0L) {
            AmountLine(
                stringResource(R.string.money_old_balance),
                state.previousBalanceRupees,
                tone = FtMoneyTone.Due,
            )
        }
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(FieldTrackTheme.colors.ink)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.money_total_due),
                style = FieldTrackTheme.type.sectionTitle,
                color = FieldTrackTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            FtMoney(state.totalDueRupees, size = FtMoneySize.Medium)
        }
    }

    FtSectionLabel(stringResource(R.string.money_did_they_pay))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        FtChip(stringResource(R.string.money_paid_all), payment.choice == PaymentChoice.All, onChooseAll)
        FtChip(stringResource(R.string.money_paid_some), payment.choice == PaymentChoice.Some, onChooseSome)
        FtChip(stringResource(R.string.money_paid_none), payment.choice == PaymentChoice.Nothing, onChooseNothing)
    }

    if (payment.choice != PaymentChoice.Nothing) {
        FtTextField(
            label = stringResource(R.string.money_how_much_given),
            value = payment.amountText,
            onValueChange = onAmount,
            prefix = "₹",
            keyboardType = KeyboardType.Number,
            focused = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaymentMethod.entries.forEach { method ->
                FtChip(
                    label = stringResource(method.labelRes),
                    selected = payment.method == method,
                    onClick = { onMethod(method) },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(FieldTrackTheme.shapes.medium)
                .background(FieldTrackTheme.colors.primaryWash)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.money_left_after),
                style = FieldTrackTheme.type.label,
                color = FieldTrackTheme.colors.primaryPressed,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = rupees((state.totalDueRupees - payment.amountRupees).coerceAtLeast(0)),
                style = FieldTrackTheme.type.moneyMedium,
                color = FieldTrackTheme.colors.primaryPressed,
            )
        }
    }
}

@Composable
private fun SavedStep(state: FinishState, receivedRupees: Long, finalBalanceRupees: Long) {
    val remaining = finalBalanceRupees.coerceAtLeast(0)
    FtCard {
        Text(
            text = if (receivedRupees > 0) {
                stringResource(R.string.finish_saved_detail, rupees(receivedRupees), rupees(remaining))
            } else {
                stringResource(R.string.finish_saved_nothing, rupees(remaining))
            },
            style = FieldTrackTheme.type.body,
            color = FieldTrackTheme.colors.inkSecondary,
        )
    }

    FtSectionLabel(stringResource(R.string.finish_receipt_preview))
    FtCard {
        // The letterhead, as the customer will see it. Each line only when it is
        // set: a farmer with no GST number should not be handed a bill with a gap
        // where one would go.
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconTile(
                icon = FieldTrackIcons.Tractor,
                size = 38.dp,
                background = FieldTrackTheme.colors.primary,
                tint = FieldTrackTheme.colors.onPrimary,
            )
            Column(Modifier.padding(start = 11.dp)) {
                Text(
                    text = state.businessName.ifBlank { stringResource(R.string.app_name) },
                    style = FieldTrackTheme.type.rowTitle,
                    color = FieldTrackTheme.colors.ink,
                )
                val letterhead = listOfNotNull(
                    state.businessAddress?.takeIf { it.isNotBlank() },
                    state.businessPhone?.takeIf { it.isNotBlank() }?.let { "+91 $it" },
                ).joinToString(" · ")
                if (letterhead.isNotBlank()) {
                    Text(
                        text = letterhead,
                        style = FieldTrackTheme.type.caption,
                        color = FieldTrackTheme.colors.inkTertiary,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(FieldTrackTheme.dimens.hairline)
                .background(FieldTrackTheme.colors.line)
        )
        FtSectionLabel(stringResource(R.string.finish_bill_for))
        Text(
            text = state.customerName,
            style = FieldTrackTheme.type.rowTitle,
            color = FieldTrackTheme.colors.ink,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        AmountLine("${state.workType} · ${quantity(state.quantity)} ${stringResource(state.unit.labelRes)}", state.jobAmountRupees)
        if (state.previousBalanceRupees != 0L) {
            AmountLine(stringResource(R.string.money_old_balance), state.previousBalanceRupees)
        }
        if (receivedRupees > 0) {
            AmountLine(stringResource(R.string.money_paid_today), -receivedRupees, tone = FtMoneyTone.Received)
        }
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(FieldTrackTheme.colors.ink)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.money_balance),
                style = FieldTrackTheme.type.sectionTitle,
                color = FieldTrackTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            // A settled bill is not a debt, so it should not be shouting in red.
            FtMoney(
                amount = remaining,
                size = FtMoneySize.Medium,
                tone = if (remaining > 0) FtMoneyTone.Due else FtMoneyTone.Received,
            )
        }

        state.businessUpiId?.takeIf { it.isNotBlank() }?.let { upi ->
            Text(
                text = stringResource(R.string.finish_pay_upi, upi),
                style = FieldTrackTheme.type.bodySmall,
                color = FieldTrackTheme.colors.primaryPressed,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // The line that makes the slip a document: when, which bill, and who made it.
        Text(
            text = listOfNotNull(
                state.billedAtMillis?.let { dayMonthYear(it) },
                state.billNumber?.let { stringResource(R.string.finish_bill_no, it) },
                stringResource(R.string.finish_made_with),
            ).joinToString(" · "),
            style = FieldTrackTheme.type.caption,
            color = FieldTrackTheme.colors.inkTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FieldTrackTheme.type.bodySmall,
            color = FieldTrackTheme.colors.inkSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = FieldTrackTheme.type.numeric,
            color = FieldTrackTheme.colors.ink,
        )
    }
}

@Composable
private fun AmountLine(label: String, amount: Long, tone: FtMoneyTone = FtMoneyTone.Neutral) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FieldTrackTheme.type.bodySmall,
            color = FieldTrackTheme.colors.inkSecondary,
            modifier = Modifier.weight(1f),
        )
        FtMoney(amount, size = FtMoneySize.Row, tone = tone)
    }
}

private val PaymentMethod.labelRes: Int
    get() = when (this) {
        PaymentMethod.Cash -> R.string.method_cash
        PaymentMethod.Upi -> R.string.method_upi
        PaymentMethod.Bank -> R.string.method_bank
        PaymentMethod.Cheque -> R.string.method_cheque
    }
