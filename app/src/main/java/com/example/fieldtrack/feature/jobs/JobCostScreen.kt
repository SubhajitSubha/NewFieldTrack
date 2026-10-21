package com.example.fieldtrack.feature.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * What a job took beyond the machine.
 *
 * Asked in the order somebody says it: who, how many, what it cost, and
 * whether the cash has actually gone. Everything about money disappears for a
 * reader who may not record it, leaving one question they can answer from
 * where they are standing.
 */
@Composable
fun JobCostScreen(
    state: JobCostState,
    onBack: () -> Unit,
    onKind: (JobCostKind) -> Unit,
    onHeadcount: (Int) -> Unit,
    onUncounted: () -> Unit,
    onCounted: () -> Unit,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onPaid: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    val labour = state.kind == JobCostKind.Labour

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            // The amount field opens the keyboard, which otherwise sits over
            // the save button at exactly the moment somebody reaches for it.
            .imePadding()
    ) {
        FtTopBar(
            // Matches the button that opened it, and does not shift when the
            // kind is switched: "Who else worked" over a tea bill is wrong,
            // and a title that changes under the thumb is worse.
            title = stringResource(
                if (state.canPrice) R.string.cost_add else R.string.jobcost_title
            ),
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

            // Only offered to somebody who can put a figure on it: recording
            // "tea and food" with no amount tells nobody anything.
            if (state.canPrice && state.existingId == null) {
                FtCard(padding = 0.dp) {
                    PickRow(
                        title = stringResource(R.string.jobcost_kind_labour),
                        note = "",
                        selected = labour,
                        onSelect = { onKind(JobCostKind.Labour) },
                    )
                    FtRowDivider()
                    PickRow(
                        title = stringResource(R.string.jobcost_kind_other),
                        note = stringResource(R.string.jobcost_kind_other_note),
                        selected = !labour,
                        onSelect = { onKind(JobCostKind.Other) },
                    )
                }
            }

            if (labour && state.existingId == null) {
                FtSectionLabel(stringResource(R.string.jobcost_how_many))
                FtCard {
                    if (state.headcount == null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.jobcost_gang),
                                style = FieldTrackTheme.type.sectionTitle,
                                color = colors.ink,
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
                                onClick = { onHeadcount(state.headcount - 1) },
                                tone = FtButtonTone.Secondary,
                                height = 46.dp,
                                modifier = Modifier.size(46.dp),
                            )
                            Text(
                                text = state.headcount.toString(),
                                style = FieldTrackTheme.type.moneyMedium,
                                color = colors.ink,
                            )
                            FtButton(
                                text = "+",
                                onClick = { onHeadcount(state.headcount + 1) },
                                tone = FtButtonTone.Secondary,
                                height = 46.dp,
                                modifier = Modifier.size(46.dp),
                            )
                        }
                        Spacer(Modifier.height(11.dp))
                        FtRowDivider()
                        Spacer(Modifier.height(10.dp))
                        FtButton(
                            text = stringResource(R.string.jobcost_uncounted),
                            onClick = onUncounted,
                            tone = FtButtonTone.Secondary,
                            height = 42.dp,
                        )
                        Text(
                            text = stringResource(R.string.jobcost_uncounted_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            if (state.canPrice) {
                FtSectionLabel(stringResource(R.string.jobcost_what_cost))
                FtTextField(
                    label = stringResource(R.string.jobcost_amount),
                    value = state.amountText,
                    onValueChange = onAmount,
                    prefix = "₹",
                    keyboardType = KeyboardType.Number,
                )
                state.eachRupees?.let { each ->
                    Text(
                        text = stringResource(
                            if (state.eachExact) R.string.jobcost_each
                            else R.string.jobcost_each_about,
                            rupees(each),
                        ),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (labour) {
                    Text(
                        // Their answer to rates that vary by what each man did.
                        text = stringResource(R.string.jobcost_split_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                if (!labour) {
                    FtSectionLabel(stringResource(R.string.jobcost_what_for))
                    FtTextField(
                        label = stringResource(R.string.jobcost_note_hint),
                        value = state.note,
                        onValueChange = onNote,
                    )
                    // Read out here rather than inside the click: a string
                    // resource is a composable read, and a lambda is not one.
                    val tea = stringResource(R.string.jobcost_note_tea)
                    val travel = stringResource(R.string.jobcost_note_travel)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FtButton(
                            text = tea,
                            onClick = { onNote(tea) },
                            tone = FtButtonTone.Secondary,
                            height = 42.dp,
                            modifier = Modifier.weight(1f),
                        )
                        FtButton(
                            text = travel,
                            onClick = { onNote(travel) },
                            tone = FtButtonTone.Secondary,
                            height = 42.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                FtCard(padding = 0.dp) {
                    // "Paid them" is right for a crew and wrong for a tea
                    // stall, so the person and the thing get different words.
                    PickRow(
                        title = stringResource(
                            if (labour) R.string.jobcost_paid
                            else R.string.jobcost_paid_other
                        ),
                        note = stringResource(R.string.jobcost_paid_note),
                        selected = state.paid,
                        onSelect = { onPaid(true) },
                    )
                    FtRowDivider()
                    PickRow(
                        title = stringResource(
                            if (labour) R.string.jobcost_owed
                            else R.string.jobcost_owed_other
                        ),
                        note = stringResource(R.string.jobcost_owed_note),
                        selected = !state.paid,
                        onSelect = { onPaid(false) },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.jobcost_heads_only_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
            FtButton(
                text = stringResource(R.string.jobcost_save),
                onClick = onSave,
                enabled = state.canSave,
            )
        }
    }
}

@Composable
private fun PickRow(
    title: String,
    note: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 13.dp)) {
            Text(text = title, style = FieldTrackTheme.type.rowTitle, color = colors.ink)
            if (note.isNotEmpty()) {
                Text(
                    text = note,
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
