package com.example.fieldtrack.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.workLabel
import com.example.fieldtrack.designsystem.format.rupees
import androidx.compose.foundation.layout.imePadding

/**
 * Recording money that went out.
 *
 * Three questions in the order somebody would say them aloud: who, how much,
 * and what it was for. The last one is not housekeeping — it decides whether
 * this can ever be counted against a machine, and the screen says so where it
 * cannot.
 */
@Composable
fun PayOutScreen(
    state: PayOutState,
    onBack: () -> Unit,
    onPerson: (String?) -> Unit,
    onHeadcount: (Int) -> Unit,
    onAmount: (String) -> Unit,
    onCovers: (PayoutCovers) -> Unit,
    onJob: (String?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            // Without this the keyboard sits over the save button and the form
            // cannot be finished at all — the amount field opens it, so it is
            // up at exactly the moment somebody reaches for Record.
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(R.string.payout_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {

            FtSectionLabel(stringResource(R.string.payout_who))
            FtCard(padding = 0.dp) {
                state.people.forEach { person ->
                    PickRow(
                        title = person.name,
                        note = stringResource(R.string.payout_on_the_team),
                        selected = state.toPersonId == person.personId,
                        onSelect = { onPerson(person.personId) },
                    )
                    FtRowDivider()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.payingLabourers,
                            role = Role.RadioButton,
                            onClick = { onPerson(null) },
                        )
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.payingLabourers, onClick = null)
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(
                            text = stringResource(R.string.payout_labourers),
                            style = FieldTrackTheme.type.body,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.payout_labourers_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                        )
                    }
                    if (state.payingLabourers) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FtButton(
                                text = "−",
                                onClick = { onHeadcount(state.headcount - 1) },
                                tone = FtButtonTone.Secondary,
                                height = 38.dp,
                                modifier = Modifier.size(38.dp),
                            )
                            Text(
                                text = state.headcount.toString(),
                                style = FieldTrackTheme.type.sectionTitle,
                                color = colors.ink,
                            )
                            FtButton(
                                text = "+",
                                onClick = { onHeadcount(state.headcount + 1) },
                                tone = FtButtonTone.Secondary,
                                height = 38.dp,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                }
            }

            FtSectionLabel(stringResource(R.string.payout_how_much))
            FtTextField(
                label = stringResource(R.string.payout_amount),
                value = state.amountText,
                onValueChange = onAmount,
                prefix = "₹",
                keyboardType = KeyboardType.Number,
            )
            if (state.payingLabourers && state.headcount > 1) {
                Text(
                    // Their answer to varying rates: more than one entry.
                    text = stringResource(R.string.payout_split_note, state.headcount),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            FtSectionLabel(stringResource(R.string.payout_what_for))
            FtCard(padding = 0.dp) {
                PickRow(
                    title = stringResource(R.string.payout_for_job),
                    note = stringResource(R.string.payout_for_job_note),
                    selected = state.covers == PayoutCovers.Job,
                    onSelect = { onCovers(PayoutCovers.Job) },
                )
                FtRowDivider()
                PickRow(
                    title = stringResource(R.string.payout_for_day),
                    note = stringResource(R.string.payout_for_day_note),
                    selected = state.covers == PayoutCovers.Day,
                    onSelect = { onCovers(PayoutCovers.Day) },
                )
                FtRowDivider()
                PickRow(
                    title = stringResource(R.string.payout_for_period),
                    note = stringResource(R.string.payout_for_period_note),
                    selected = state.covers == PayoutCovers.Period,
                    onSelect = { onCovers(PayoutCovers.Period) },
                )
            }

            if (state.covers == PayoutCovers.Job) {
                FtSectionLabel(stringResource(R.string.payout_which_job))
                FtCard(padding = 0.dp) {
                    state.jobs.forEachIndexed { index, job ->
                        if (index > 0) FtRowDivider()
                        PickRow(
                            title = workLabel(job.label),
                            note = "",
                            selected = state.jobId == job.jobId,
                            onSelect = { onJob(job.jobId) },
                        )
                    }
                }
            }

            if (state.covers == PayoutCovers.Period) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    // Said here rather than discovered later on a report that
                    // quietly looked better than the truth.
                    Text(
                        text = stringResource(R.string.payout_period_warning),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
            FtButton(
                text = if (state.amountRupees > 0) {
                    stringResource(R.string.payout_save_amount, rupees(state.amountRupees))
                } else {
                    stringResource(R.string.payout_save)
                },
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
            Text(text = title, style = FieldTrackTheme.type.body, color = colors.ink)
            if (note.isNotEmpty()) {
                Text(
                    text = note,
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkSecondary,
                )
            }
        }
    }
}
