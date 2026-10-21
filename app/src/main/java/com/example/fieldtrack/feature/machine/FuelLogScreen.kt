package com.example.fieldtrack.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth

/** What a machine has drunk, and what it cost. */
@Composable
fun FuelLogScreen(
    state: FuelLogState,
    draft: FuelDraft?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onLitres: (String) -> Unit,
    onCost: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
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
            title = stringResource(R.string.fuel_title),
            subtitle = state.machineName,
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // The total first, because the question behind opening this screen is
            // "what is this machine costing me", not "when was it last filled".
            FtCard(background = colors.ink, borderColor = colors.ink) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // Litres either way; rupees only for somebody allowed them.
                    // A driver is told what went in, not what the owner paid.
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (state.canSeeMoney) R.string.fuel_spent_all
                                else R.string.fuel_spent_hidden
                            ).uppercase(),
                            style = FieldTrackTheme.type.overline,
                            color = colors.onDarkMuted,
                        )
                        Text(
                            text = if (state.canSeeMoney) {
                                rupees(state.totalRupees)
                            } else {
                                stringResource(
                                    R.string.fuel_litres_total, quantity(state.totalLitres),
                                )
                            },
                            style = FieldTrackTheme.type.moneyLarge,
                            color = colors.onDark,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (state.canSeeMoney) {
                        Text(
                            text = stringResource(
                                R.string.fuel_litres_total, quantity(state.totalLitres),
                            ),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.onDarkMuted,
                        )
                    }
                }
            }

            FtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FtIconTile(
                        icon = FieldTrackIcons.Report,
                        size = 42.dp,
                        background = colors.primaryWash,
                        tint = colors.primaryPressed,
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        val perHour = state.litresPerHour
                        Text(
                            text = if (perHour != null) {
                                stringResource(R.string.fuel_per_hour, quantity(perHour))
                            } else {
                                stringResource(R.string.fuel_no_hours)
                            },
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        if (perHour != null) {
                            Text(
                                text = stringResource(R.string.fuel_per_hour_note),
                                style = FieldTrackTheme.type.caption,
                                color = colors.inkTertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            FtSectionLabel(stringResource(R.string.fuel_entries))
            if (state.entries.isEmpty()) {
                if (state.loaded) {
                    FtCard {
                        Text(
                            text = stringResource(R.string.fuel_none),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
            } else {
                FtCard(padding = 0.dp) {
                    state.entries.forEachIndexed { index, entry ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = stringResource(
                                R.string.fuel_litres_total, quantity(entry.litres),
                            ),
                            subtitle = listOfNotNull(
                                dayMonth(entry.atMillis),
                                entry.byName,
                                stringResource(
                                    if (entry.onJob) R.string.fuel_on_job
                                    else R.string.fuel_in_yard
                                ),
                            ).joinToString(" · "),
                            leading = {
                                FtIconTile(
                                    icon = FieldTrackIcons.Fuel,
                                    size = 40.dp,
                                )
                            },
                            trailing = if (state.canSeeMoney) {
                                {
                                    Text(
                                        text = rupees(entry.costRupees),
                                        style = FieldTrackTheme.type.moneyMedium,
                                        color = colors.ink,
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }

            Box(Modifier.height(dimens.space4))
        }

        if (state.canRecord) {
            Column(Modifier.padding(dimens.screenPadding)) {
                FtButton(
                    text = stringResource(R.string.fuel_add),
                    onClick = onAdd,
                    icon = FieldTrackIcons.Add,
                    height = 54.dp,
                )
            }
        }
    }

    if (draft != null) {
        FuelSheet(draft, onLitres, onCost, onSave, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelSheet(
    draft: FuelDraft,
    onLitres: (String) -> Unit,
    onCost: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
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
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.fuel_add),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            FtTextField(
                value = draft.litresText,
                onValueChange = onLitres,
                label = stringResource(R.string.fuel_litres),
                suffix = "L",
                keyboardType = KeyboardType.Decimal,
                focused = true,
            )
            FtTextField(
                value = draft.costText,
                onValueChange = onCost,
                label = stringResource(R.string.fuel_cost),
                prefix = "₹",
                keyboardType = KeyboardType.Number,
            )
            FtButton(
                text = stringResource(R.string.fuel_save),
                onClick = onSave,
                enabled = draft.canSave,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
            )
        }
    }
}
