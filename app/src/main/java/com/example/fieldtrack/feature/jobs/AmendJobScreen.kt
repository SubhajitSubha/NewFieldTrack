package com.example.fieldtrack.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtMoney
import com.example.fieldtrack.designsystem.component.FtMoneySize
import com.example.fieldtrack.designsystem.component.FtMoneyTone
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.labelRes

/**
 * Correcting a job the customer has already been billed for.
 *
 * The screen is built around one sentence: "they will owe ₹500 less". Everything
 * else is support for it. A farmer changing an acre figure a week later is not
 * thinking about ledgers — they are thinking about what to collect on Sunday — so
 * the consequence is shown in money, live, before anything is saved.
 */
@Composable
fun AmendJobScreen(
    state: AmendJobState,
    onBack: () -> Unit,
    onQuantity: (String) -> Unit,
    onRate: (String) -> Unit,
    onReason: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val unitLabel = stringResource(state.unit.labelRes)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(R.string.amend_title),
            subtitle = listOf(state.customerName, state.workType)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { null },
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_close_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Said plainly and first: the old bill is not being erased. Someone who
            // believes they are rubbing out a number behaves differently from
            // someone who knows they are adding a correction underneath it.
            FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                Text(
                    text = stringResource(R.string.amend_billed_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }

            if (state.billingMode != BillingMode.Fixed) {
                FtTextField(
                    label = stringResource(R.string.amend_quantity),
                    value = state.quantityText,
                    onValueChange = onQuantity,
                    suffix = unitLabel,
                    keyboardType = KeyboardType.Decimal,
                    helper = stringResource(
                        R.string.amend_was,
                        "${quantity(state.originalQuantity)} $unitLabel",
                    ),
                    focused = true,
                )
            }

            FtTextField(
                label = stringResource(R.string.amend_rate),
                value = state.rateText,
                onValueChange = onRate,
                prefix = "₹",
                keyboardType = KeyboardType.Number,
                helper = stringResource(R.string.amend_was, rupees(state.originalRateRupees)),
                focused = state.billingMode == BillingMode.Fixed,
            )

            // Only asked for when money actually moves — see AmendJobState.canSave.
            if (state.deltaRupees != 0L) {
                FtTextField(
                    label = stringResource(R.string.amend_reason),
                    value = state.reason,
                    onValueChange = onReason,
                    placeholder = stringResource(R.string.amend_reason_hint),
                )
            }

            if (!state.allowed) {
                Text(
                    text = stringResource(R.string.edit_not_allowed),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.dueInk,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }

        // The consequence rides with the button rather than scrolling away above it.
        // "They will owe ₹1,000 more" is the whole decision being made here, and a
        // disabled button with the reason for it off-screen is how people give up.
        Column(
            Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChangeSummary(state)
            if (state.deltaRupees != 0L && state.reason.isBlank()) {
                Text(
                    text = stringResource(R.string.amend_reason_required),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }
            FtButton(
                text = stringResource(R.string.amend_save),
                onClick = onSave,
                enabled = state.canSave,
            )
        }
    }
}

/** What the correction does to the khata, in money, as it is typed. */
@Composable
private fun ChangeSummary(state: AmendJobState) {
    val colors = FieldTrackTheme.colors
    val delta = state.deltaRupees
    val unchanged = delta == 0L

    FtCard(
        background = when {
            unchanged -> colors.surfaceMuted
            delta > 0 -> colors.dueWash
            else -> colors.primaryWash
        },
        borderColor = when {
            unchanged -> colors.line
            delta > 0 -> colors.dueWash
            else -> colors.primaryWash
        },
    ) {
        if (unchanged) {
            Text(
                text = stringResource(R.string.amend_no_change),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkTertiary,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    FtSectionLabel(
                        stringResource(
                            if (delta > 0) R.string.amend_now_owes_more
                            else R.string.amend_now_owes_less,
                            rupees(kotlin.math.abs(delta)),
                        ),
                        color = if (delta > 0) colors.dueInk else colors.primaryPressed,
                    )
                    FtMoney(
                        amount = state.newAmountRupees,
                        size = FtMoneySize.Large,
                        tone = if (delta > 0) FtMoneyTone.Due else FtMoneyTone.Received,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.amend_was,
                        rupees(state.originalAmountRupees),
                    ),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}
