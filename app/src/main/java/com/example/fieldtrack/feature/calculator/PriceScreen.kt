package com.example.fieldtrack.feature.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtFitText
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.designsystem.layout.LocalWidthClass
import com.example.fieldtrack.designsystem.layout.WidthClass
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.billingLabel
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.spokenDuration
import com.example.fieldtrack.feature.unitInPhrase
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.workTypeOptions
import java.util.Locale

/**
 * "What would it come to?" — a sum, with nothing saved.
 *
 * On a phone the answer is pinned above the questions, so it is in view while
 * the numbers are being typed under a keyboard; where there is room, it sits
 * beside them instead.
 */
@Composable
fun PriceScreen(
    state: PriceState,
    onBack: () -> Unit,
    onWorkType: (WorkTypeOption) -> Unit,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (MeasureUnit) -> Unit,
    onRate: (String) -> Unit,
    onPickRate: (Long) -> Unit,
    onQuantity: (String) -> Unit,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = FieldTrackTheme.dimens
    val twoPane = LocalWidthClass.current == WidthClass.Expanded

    AdaptiveScreen(modifier.imePadding()) {
        FtTopBar(
            title = stringResource(R.string.price_title),
            subtitle = stringResource(R.string.price_subtitle),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (!state.loaded) return@AdaptiveScreen

        // Refuses on its own rather than trusting that nothing ever links here,
        // exactly as the rate card does: this multiplies that card's prices.
        if (!state.allowed) {
            FtCard(modifier = Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(R.string.rates_not_for_you),
                    style = FieldTrackTheme.type.bodySmall,
                    color = FieldTrackTheme.colors.inkSecondary,
                )
            }
            return@AdaptiveScreen
        }

        val questions: @Composable () -> Unit = {
            Questions(state, onWorkType, onBillingMode, onUnit, onRate, onPickRate, onQuantity, onHours, onMinutes)
        }
        if (twoPane) {
            Row(
                Modifier
                    .weight(1f)
                    .padding(horizontal = dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    questions()
                    Box(Modifier.padding(bottom = dimens.space4))
                }
                Column(Modifier.weight(1f)) { Answer(state) }
            }
        } else {
            Box(
                Modifier
                    .padding(horizontal = dimens.screenPadding)
                    .padding(bottom = 10.dp)
            ) { Answer(state) }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                questions()
                Box(Modifier.padding(bottom = dimens.space4))
            }
        }
    }
}

/** The figure, with the sum that makes it, so it can be checked by eye. */
@Composable
private fun Answer(state: PriceState) {
    val colors = FieldTrackTheme.colors
    val amount = state.amountRupees
    FtEmphasisCard(
        // One spoken unit, announced as it changes: a figure that updates under a
        // typing thumb is invisible to somebody using a screen reader unless it says so.
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        FtSectionLabel(
            text = stringResource(R.string.price_result_label),
            color = colors.onDarkMuted,
        )
        FtFitText(
            text = amount?.let { rupees(it) } ?: stringResource(R.string.price_result_blank),
            style = FieldTrackTheme.type.moneyHero,
            color = colors.onDark,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = if (amount != null) sumLine(state) else missingLine(state),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.onDarkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "4 bigha × ₹1,000 / bigha" — the working, in the order a person would say it. */
@Composable
private fun sumLine(state: PriceState): String {
    if (state.fixedPrice) return stringResource(R.string.billing_fixed_caption)
    val unit = state.input.unit
    val rate = stringResource(R.string.start_rate_per_amount, rupees(state.rateRupees), unitInPhrase(unit))
    val much = when {
        state.byTheHour -> spokenDuration(state.seconds)
        unit == MeasureUnit.Trip -> {
            val n = state.quantity.toInt()
            pluralStringResource(R.plurals.count_trips, n, n)
        }
        else -> stringResource(R.string.measure_value, exactQuantity(state.quantity), unitInPhrase(unit))
    }
    return stringResource(R.string.price_sum, much, rate)
}

/** What is still needed, said as the one next thing rather than a list. */
@Composable
private fun missingLine(state: PriceState): String = stringResource(
    when {
        state.fixedPrice -> R.string.price_need_agreed
        state.rateRupees <= 0 && state.quantity <= 0.0 -> R.string.price_need_both
        state.rateRupees <= 0 -> R.string.price_need_rate
        else -> R.string.price_need_quantity
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Questions(
    state: PriceState,
    onWorkType: (WorkTypeOption) -> Unit,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (MeasureUnit) -> Unit,
    onRate: (String) -> Unit,
    onPickRate: (Long) -> Unit,
    onQuantity: (String) -> Unit,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
) {
    val input = state.input

    Column {
        FtSectionLabel(stringResource(R.string.start_work_type), Modifier.padding(bottom = 9.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            workTypeOptions.forEach { option ->
                FtChip(
                    label = stringResource(option.labelRes),
                    selected = input.workType.key == option.key,
                    onClick = { onWorkType(option) },
                )
            }
        }
    }

    Column {
        FtSectionLabel(stringResource(R.string.price_charged), Modifier.padding(bottom = 9.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            input.workType.billingModes.forEach { mode ->
                FtChip(
                    label = billingLabel(mode, state.landUnit),
                    selected = input.billingMode == mode,
                    onClick = { onBillingMode(mode) },
                )
            }
        }
    }

    val units = unitsFor(input.billingMode)
    if (units.size > 1) {
        Column {
            FtSectionLabel(stringResource(R.string.start_counted_in), Modifier.padding(bottom = 9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                units.forEach { unit ->
                    FtChip(
                        label = stringResource(unit.labelRes),
                        selected = input.unit == unit,
                        onClick = { onUnit(unit) },
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FtTextField(
            label = stringResource(if (state.fixedPrice) R.string.price_agreed else R.string.start_your_rate),
            value = input.rateText,
            onValueChange = onRate,
            prefix = "₹",
            suffix = if (state.fixedPrice) null
            else stringResource(R.string.start_rate_per, unitInPhrase(input.unit)),
            keyboardType = KeyboardType.Number,
        )
        if (state.usualRates.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.start_usual_rate),
                    style = FieldTrackTheme.type.caption,
                    color = FieldTrackTheme.colors.inkTertiary,
                )
                state.usualRates.forEach { suggestion ->
                    FtChip(
                        label = suggestion.label?.let {
                            stringResource(R.string.start_usual_rate_labelled, rupees(suggestion.amountRupees), it)
                        } ?: rupees(suggestion.amountRupees),
                        selected = state.rateRupees == suggestion.amountRupees,
                        onClick = { onPickRate(suggestion.amountRupees) },
                    )
                }
            }
        }
    }

    // A fixed price is the amount above and nothing more: asking "how much work"
    // of a lump sum would be a question with no effect on the answer.
    if (!state.fixedPrice) {
        if (state.byTheHour) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FtTextField(
                    label = stringResource(R.string.price_hours),
                    value = input.hoursText,
                    onValueChange = onHours,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                FtTextField(
                    label = stringResource(R.string.price_minutes),
                    value = input.minutesText,
                    onValueChange = onMinutes,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            FtTextField(
                label = stringResource(R.string.price_how_much),
                value = input.quantityText,
                onValueChange = onQuantity,
                suffix = unitInPhrase(input.unit),
                keyboardType = if (state.countsWhole) KeyboardType.Number else KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A quantity as it was typed: "2.25", not the "2.3" the one-decimal formatter used
 * elsewhere would show beside a bill of ₹2,250 that does not multiply out from it.
 */
internal fun exactQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
