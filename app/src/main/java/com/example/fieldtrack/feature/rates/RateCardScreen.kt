package com.example.fieldtrack.feature.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.billingLabel
import com.example.fieldtrack.feature.labelRes
import com.example.fieldtrack.feature.unitInPhrase
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.workTypeOptions

/**
 * The price list, and the one place it can be corrected.
 *
 * Reads as a list of what this business charges rather than as a form. A contractor
 * opens it to check a number before quoting, far more often than to change one, so
 * the numbers are the biggest thing on the screen and editing hides behind a tap.
 */
@Composable
fun RateCardScreen(
    state: RateCardState,
    draft: RateDraft?,
    confirmDelete: RateRow?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RateRow) -> Unit,
    onPerCustomer: (Boolean) -> Unit,
    onDraftWorkType: (WorkTypeOption) -> Unit,
    onDraftBillingMode: (BillingMode) -> Unit,
    onDraftUnit: (com.example.fieldtrack.data.db.MeasureUnit) -> Unit,
    onDraftAmount: (String) -> Unit,
    onDraftLabel: (String) -> Unit,
    onDraftSave: () -> Unit,
    onDraftDismiss: () -> Unit,
    onAskDelete: (RateRow) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
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
            title = stringResource(R.string.rates_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = if (state.canEdit) {
                {
                    IconButton(
                        onClick = onAdd,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(FieldTrackTheme.shapes.small)
                            .background(colors.primary),
                    ) {
                        Icon(
                            imageVector = FieldTrackIcons.Add,
                            contentDescription = stringResource(R.string.rates_add),
                            tint = colors.onPrimary,
                        )
                    }
                }
            } else null,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(
                text = stringResource(R.string.rates_blurb),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )

            if (state.rates.isEmpty()) {
                if (state.loaded) {
                    FtCard {
                        Text(
                            text = stringResource(R.string.rates_empty_title),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.rates_empty_body),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            } else {
                FtCard(padding = 0.dp) {
                    state.rates.forEachIndexed { index, rate ->
                        if (index > 0) FtRowDivider()
                        RateListRow(
                            rate = rate,
                            onClick = if (state.canEdit) ({ onEdit(rate) }) else null,
                        )
                    }
                }
            }

            // Only an owner may change how rates are kept; a driver sees the list.
            if (state.canEdit) {
                FtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.rates_per_customer),
                                style = FieldTrackTheme.type.rowTitle,
                                color = colors.ink,
                            )
                            Text(
                                text = stringResource(
                                    if (state.perCustomer) R.string.rates_per_customer_note
                                    else R.string.rates_per_customer_off_note
                                ),
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkTertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Switch(
                            checked = state.perCustomer,
                            onCheckedChange = onPerCustomer,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary,
                                uncheckedThumbColor = colors.surface,
                                uncheckedTrackColor = colors.line,
                            ),
                        )
                    }

                    // Says what the switch has actually collected. Without this the
                    // setting is an abstraction; with it, it is a count of deals.
                    if (state.customerDeals > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.rates_customer_deals,
                                state.customerDeals,
                                state.customerDeals,
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                    }
                }
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }
    }

    if (draft != null) {
        RateSheet(
            draft = draft,
            onWorkType = onDraftWorkType,
            onBillingMode = onDraftBillingMode,
            onUnit = onDraftUnit,
            onAmount = onDraftAmount,
            onLabel = onDraftLabel,
            onSave = onDraftSave,
            onDismiss = onDraftDismiss,
            onDelete = state.rates.firstOrNull { it.id == draft.id }?.let { row ->
                { onAskDelete(row) }
            },
        )
    }

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.rates_delete_confirm),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            },
            // The reassurance belongs here, where the worry is. Nobody deleting a
            // rate is sure whether it unpicks the bills they already sent.
            text = {
                Text(
                    text = stringResource(R.string.rates_delete_body),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        text = stringResource(R.string.rates_delete_yes),
                        style = FieldTrackTheme.type.label,
                        color = colors.due,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = FieldTrackTheme.type.label,
                        color = colors.inkSecondary,
                    )
                }
            },
        )
    }
}

/** Work, what it is charged by, and the number — in that reading order. */
@Composable
private fun RateListRow(rate: RateRow, onClick: (() -> Unit)?) {
    val colors = FieldTrackTheme.colors
    FtListRow(
        title = rate.label?.let {
            stringResource(R.string.rates_label_for, stringResource(rate.workType.labelRes), it)
        } ?: stringResource(rate.workType.labelRes),
        subtitle = billingLabel(rate.billingMode, rate.unit),
        leading = {
            FtIconTile(
                icon = rate.workType.icon,
                size = 44.dp,
                background = colors.surfaceMuted,
                tint = colors.inkSecondary,
            )
        },
        trailing = {
            Text(
                text = rupees(rate.amountRupees),
                style = FieldTrackTheme.type.moneyMedium,
                color = colors.ink,
            )
        },
        onClick = onClick,
    )
}

/** Adding or correcting one rate. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RateSheet(
    draft: RateDraft,
    onWorkType: (WorkTypeOption) -> Unit,
    onBillingMode: (BillingMode) -> Unit,
    onUnit: (com.example.fieldtrack.data.db.MeasureUnit) -> Unit,
    onAmount: (String) -> Unit,
    onLabel: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = FieldTrackTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.editing) R.string.rates_edit else R.string.rates_new
                ),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )

            FtSectionLabel(stringResource(R.string.rates_work))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                workTypeOptions.forEach { option ->
                    FtChip(
                        label = stringResource(option.labelRes),
                        selected = draft.workType.key == option.key,
                        onClick = { onWorkType(option) },
                    )
                }
            }

            // Only the modes that make sense for this work. Charging rotavating by
            // the trip is not a choice anybody should be offered.
            FtSectionLabel(stringResource(R.string.rates_charge_by))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.workType.billingModes.forEach { mode ->
                    FtChip(
                        label = billingLabel(mode, draft.unit),
                        selected = draft.billingMode == mode,
                        onClick = { onBillingMode(mode) },
                    )
                }
            }

            val units = unitsFor(draft.billingMode)
            if (units.size > 1) {
                FtSectionLabel(stringResource(R.string.start_counted_in))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    units.forEach { unit ->
                        FtChip(
                            label = stringResource(unit.labelRes),
                            selected = draft.unit == unit,
                            onClick = { onUnit(unit) },
                        )
                    }
                }
            }

            FtTextField(
                value = draft.amountText,
                onValueChange = onAmount,
                label = stringResource(R.string.rates_amount),
                prefix = "₹",
                suffix = stringResource(R.string.start_rate_per, unitInPhrase(draft.unit)),
                keyboardType = KeyboardType.Number,
                focused = true,
            )

            FtTextField(
                value = draft.label,
                onValueChange = onLabel,
                label = stringResource(R.string.rates_label),
                helper = stringResource(R.string.rates_label_hint),
            )

            FtButton(
                text = stringResource(R.string.rates_save),
                onClick = onSave,
                enabled = draft.canSave,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )

            if (onDelete != null) {
                FtButton(
                    text = stringResource(R.string.rates_delete),
                    onClick = onDelete,
                    tone = FtButtonTone.Danger,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                )
            }
        }
    }
}
