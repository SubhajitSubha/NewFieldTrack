package com.example.fieldtrack.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Handing the business to somebody else.
 *
 * Two questions, in the order they matter: who, and whether you stay. Stepping
 * back is offered rather than assumed, because most owners doing this are adding
 * a second pair of hands, not leaving.
 */
@Composable
fun HandOverScreen(
    state: HandOverState,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onStepBack: (Boolean) -> Unit,
    onConfirm: () -> Unit,
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
            title = stringResource(R.string.handover_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.handover_intro),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )

            if (state.loaded && state.candidates.isEmpty()) {
                FtCard {
                    Text(
                        text = stringResource(R.string.handover_nobody),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            if (state.candidates.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.handover_who))
                FtCard(padding = 0.dp) {
                    state.candidates.forEachIndexed { index, heir ->
                        if (index > 0) FtRowDivider()
                        ChoiceRow(
                            title = heir.displayName,
                            note = if (heir.alreadyOwner) {
                                stringResource(R.string.handover_already_owner)
                            } else {
                                stringResource(R.string.handover_is_driver)
                            },
                            selected = state.chosenMembershipId == heir.membershipId,
                            enabled = !heir.alreadyOwner,
                            onSelect = { onChoose(heir.membershipId) },
                        )
                    }
                }

                FtSectionLabel(stringResource(R.string.handover_and_you))
                FtCard(padding = 0.dp) {
                    ChoiceRow(
                        title = stringResource(R.string.handover_stay),
                        note = stringResource(R.string.handover_stay_note),
                        selected = !state.stepBack,
                        onSelect = { onStepBack(false) },
                    )
                    FtRowDivider()
                    ChoiceRow(
                        title = stringResource(R.string.handover_step_back),
                        note = stringResource(R.string.handover_step_back_note),
                        selected = state.stepBack,
                        onSelect = { onStepBack(true) },
                    )
                }

                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = stringResource(R.string.handover_warning),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        if (state.candidates.isNotEmpty()) {
            Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
                FtButton(
                    text = state.chosenName?.let {
                        stringResource(R.string.handover_confirm_named, it)
                    } ?: stringResource(R.string.handover_confirm),
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                )
            }
        }
    }
}

/** A radio row that is a real radio, so the whole line is one target. */
@Composable
private fun ChoiceRow(
    title: String,
    note: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.height(0.dp))
        Column(Modifier.padding(start = 13.dp)) {
            Text(
                text = title,
                style = FieldTrackTheme.type.body,
                color = if (enabled) colors.ink else colors.inkSecondary,
            )
            Text(
                text = note,
                style = FieldTrackTheme.type.caption,
                color = colors.inkSecondary,
            )
        }
    }
}
