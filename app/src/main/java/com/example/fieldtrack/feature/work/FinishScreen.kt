package com.example.fieldtrack.feature.work

import com.example.fieldtrack.designsystem.format.Money
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
import com.example.fieldtrack.feature.measure
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
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.designsystem.theme.OnPaper
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.data.auth.formatPhone
import androidx.compose.foundation.selection.selectable
import com.example.fieldtrack.designsystem.component.FtRowDivider

@Composable
fun FinishScreen(
    step: Int,
    state: FinishState,
    payment: PaymentState,
    receivedMinor: Long,
    finalBalanceMinor: Long,
    onBack: () -> Unit,
    onToBill: () -> Unit,
    onChooseAll: () -> Unit,
    onChooseSome: () -> Unit,
    onChooseNothing: () -> Unit,
    onAmount: (String) -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onMethodLabel: (String) -> Unit = {},
    labour: FinishLabour,
    onAnyLabour: (Boolean) -> Unit,
    onLabourHeadcount: (Int) -> Unit,
    onLabourUncounted: () -> Unit,
    onLabourCounted: () -> Unit,
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
    discountText: String = "",
    onDiscount: (String) -> Unit = {},
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
                0 -> ReviewStep(
                    state = state,
                    labour = labour,
                    onAnyLabour = onAnyLabour,
                    onHeadcount = onLabourHeadcount,
                    onUncounted = onLabourUncounted,
                    onCounted = onLabourCounted,
                )
                1 -> BillStep(state, payment, onChooseAll, onChooseSome, onChooseNothing, onAmount, onMethod, discountText, onDiscount, onMethodLabel)
                else -> SavedStep(state, receivedMinor, finalBalanceMinor)
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
private fun ReviewStep(
    state: FinishState,
    labour: FinishLabour,
    onAnyLabour: (Boolean) -> Unit,
    onHeadcount: (Int) -> Unit,
    onUncounted: () -> Unit,
    onCounted: () -> Unit,
) {
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
                measure(state.quantity, state.unit),
            )
        }
        SummaryRow(
            stringResource(R.string.finish_rate),
            when {
                // Naming one rate for trips that were charged three different ways
                // is the sort of line a customer notices and stops trusting.
                state.ratesVaried -> stringResource(R.string.finish_rate_varies)
                state.billingMode == BillingMode.Fixed -> money(state.rateMinor)
                else -> stringResource(
                    R.string.start_rate_per,
                    com.example.fieldtrack.feature.unitName(state.unit)
                ).let { per -> "${money(state.rateMinor)} $per" }
            },
        )
    }

    FtSectionLabel(stringResource(R.string.finish_who_else))
    FtCard(padding = 0.dp) {
        CrewRow(
            title = stringResource(R.string.finish_just_me),
            selected = !labour.any,
            onSelect = { onAnyLabour(false) },
        )
        FtRowDivider()
        CrewRow(
            title = stringResource(R.string.finish_labourers_on_it),
            selected = labour.any,
            onSelect = { onAnyLabour(true) },
        )
        if (labour.any) {
            FtRowDivider()
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                if (labour.headcount == null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.jobcost_gang),
                            style = FieldTrackTheme.type.sectionTitle,
                            color = FieldTrackTheme.colors.ink,
                        )
                        FtButton(
                            text = stringResource(R.string.jobcost_counted_instead),
                            onClick = onCounted,
                            tone = FtButtonTone.Secondary,
                            height = 42.dp,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FtButton(
                            text = "−",
                            onClick = { onHeadcount(labour.headcount - 1) },
                            tone = FtButtonTone.Secondary,
                            height = 46.dp,
                            modifier = Modifier.size(46.dp),
                        )
                        Text(
                            text = labour.headcount.toString(),
                            style = FieldTrackTheme.type.moneyMedium,
                            color = FieldTrackTheme.colors.ink,
                        )
                        FtButton(
                            text = "+",
                            onClick = { onHeadcount(labour.headcount + 1) },
                            tone = FtButtonTone.Secondary,
                            height = 46.dp,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                    FtButton(
                        text = stringResource(R.string.jobcost_uncounted),
                        onClick = onUncounted,
                        tone = FtButtonTone.Secondary,
                        height = 42.dp,
                    )
                }
                Text(
                    // Said here so nobody expects to type an amount: this is
                    // the same question whoever is finishing the job.
                    text = stringResource(R.string.jobcost_heads_only_note),
                    style = FieldTrackTheme.type.caption,
                    color = FieldTrackTheme.colors.inkSecondary,
                )
            }
        }
    }

    FtEmphasisCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                FtSectionLabel(
                    stringResource(R.string.finish_total),
                    color = FieldTrackTheme.colors.onDarkMuted,
                )
                FtMoney(
                    state.jobAmountMinor,
                    size = FtMoneySize.Hero,
                    onDark = true,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/**
 * The job's line on a bill, and under it whatever else makes the total: a minimum that was
 * applied, the arrival charge, waiting time, diesel, a discount. With none of them it is the
 * one line it always was.
 */
@Composable
private fun JobBillLines(state: FinishState, title: String) {
    val q = state.quote
    if (q == null || !q.hasExtras) {
        AmountLine(title, state.jobAmountMinor)
        return
    }
    AmountLine(title, q.workMinor)
    if (q.minimumTopUpMinor > 0) AmountLine(stringResource(R.string.finish_line_minimum), q.minimumTopUpMinor)
    if (q.arrivalMinor > 0) AmountLine(stringResource(R.string.finish_line_arrival), q.arrivalMinor)
    if (q.waitingMinor > 0) AmountLine(stringResource(R.string.finish_line_waiting), q.waitingMinor)
    if (q.fuelMinor > 0) AmountLine(stringResource(R.string.finish_line_diesel), q.fuelMinor)
    if (q.discountMinor > 0) {
        AmountLine(stringResource(R.string.finish_line_discount), -q.discountMinor, tone = FtMoneyTone.Received)
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
    discountText: String,
    onDiscount: (String) -> Unit,
    onMethodLabel: (String) -> Unit,
) {
    FtCard {
        FtSectionLabel(stringResource(R.string.finish_bill_for))
        Text(
            text = state.customerName,
            style = FieldTrackTheme.type.rowTitle,
            color = FieldTrackTheme.colors.ink,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        JobBillLines(state, stringResource(R.string.finish_this_job))
        if (state.previousBalanceMinor != 0L) {
            AmountLine(
                stringResource(if (state.previousBalanceMinor < 0) R.string.money_advance_paid else R.string.money_old_balance),
                state.previousBalanceMinor,
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
            FtMoney(state.totalDueMinor, size = FtMoneySize.Medium)
        }
    }

    // Taken off here, where the customer is standing, rather than in a correction afterwards.
    FtTextField(
        label = stringResource(R.string.finish_discount_label),
        value = discountText,
        onValueChange = onDiscount,
        prefix = Money.style.symbol,
        keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
        helper = stringResource(R.string.finish_discount_note),
    )

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
            prefix = Money.style.symbol,
            keyboardType = if (Money.style.showsDecimals) KeyboardType.Decimal else KeyboardType.Number,
            focused = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Money.methods.forEach { method ->
                FtChip(
                    label = stringResource(method.labelRes),
                    selected = payment.method == method,
                    onClick = { onMethod(method) },
                )
            }
        }
        if (payment.method == PaymentMethod.Other) {
            com.example.fieldtrack.feature.OtherMethodName(payment.methodLabel, onMethodLabel)
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
                text = money((state.totalDueMinor - payment.amountMinor).coerceAtLeast(0)),
                style = FieldTrackTheme.type.moneyMedium,
                color = FieldTrackTheme.colors.primaryPressed,
            )
        }
    }
}

@Composable
private fun SavedStep(state: FinishState, receivedMinor: Long, finalBalanceMinor: Long) {
    val remaining = finalBalanceMinor.coerceAtLeast(0)
    FtCard {
        Text(
            text = if (receivedMinor > 0) {
                stringResource(R.string.finish_saved_detail, money(receivedMinor), money(remaining))
            } else {
                stringResource(R.string.finish_saved_nothing, money(remaining))
            },
            style = FieldTrackTheme.type.body,
            color = FieldTrackTheme.colors.inkSecondary,
        )
    }

    FtSectionLabel(stringResource(R.string.finish_receipt_preview))
    OnPaper {
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
                        state.businessPhone?.takeIf { it.isNotBlank() }?.let { formatPhone(it) },
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
            // A fixed price has nothing measured: "0 Piece" beside it read as a mistake.
            val work = com.example.fieldtrack.feature.workLabel(state.workType)
            JobBillLines(state, if (state.billingMode == BillingMode.Fixed) work else "$work · ${measure(state.quantity, state.unit)}")
            if (state.previousBalanceMinor != 0L) {
                AmountLine(stringResource(if (state.previousBalanceMinor < 0) R.string.money_advance_paid else R.string.money_old_balance), state.previousBalanceMinor)
            }
            if (receivedMinor > 0) {
                AmountLine(stringResource(R.string.money_paid_today), -receivedMinor, tone = FtMoneyTone.Received)
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



@Composable
private fun CrewRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = androidx.compose.ui.semantics.Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            style = FieldTrackTheme.type.rowTitle,
            color = FieldTrackTheme.colors.ink,
            modifier = Modifier.padding(start = 13.dp),
        )
    }
}
