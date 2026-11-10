package com.example.fieldtrack.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * When the plan's day turns over, and whether drivers see everybody's work.
 *
 * The hour is checked against the clock at the foot, in a sentence, so what was set is
 * what was meant: a harvest crew that works to 2 a.m. wants the plan to stay on
 * yesterday until after that, and cannot tell from a number alone whether it will.
 */
@Composable
fun PlanSettingsScreen(
    state: PlanSettingsState,
    onBack: () -> Unit,
    onHour: (Int) -> Unit,
    onDriversSeeAll: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    AdaptiveScreen(modifier.imePadding()) {
        FtTopBar(
            title = stringResource(R.string.plan_settings_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (!state.loaded) return@AdaptiveScreen

        if (!state.allowed) {
            FtCard(modifier = Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(R.string.plan_settings_not_for_you),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            return@AdaptiveScreen
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FtSectionLabel(stringResource(R.string.plan_settings_day_starts))
            FtCard {
                Text(
                    text = stringResource(R.string.plan_settings_day_starts_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FtIconButton(
                        icon = FieldTrackIcons.Minus,
                        contentDescription = stringResource(R.string.plan_settings_earlier_cd),
                        onClick = { onHour(-1) },
                        tint = if (state.canStepEarlier) colors.ink else colors.inkTertiary,
                    )
                    Text(
                        text = timeOfDay(state.settings.dayStartHour * 60),
                        style = FieldTrackTheme.type.sectionTitle,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FtIconButton(
                        icon = FieldTrackIcons.Add,
                        contentDescription = stringResource(R.string.plan_settings_later_cd),
                        onClick = { onHour(1) },
                        tint = if (state.canStepLater) colors.ink else colors.inkTertiary,
                    )
                }
            }

            FtCard(padding = 0.dp) {
                Row(
                    Modifier
                        .toggleable(value = state.settings.driversSeeAll, role = Role.Switch, onValueChange = onDriversSeeAll)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.plan_settings_see_all),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.plan_settings_see_all_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = state.settings.driversSeeAll,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.surface,
                            uncheckedTrackColor = colors.line,
                        ),
                    )
                }
            }

            // What the hour, as it stands, makes of this moment.
            FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                Text(
                    text = if (state.stillYesterday) {
                        stringResource(
                            R.string.plan_settings_now_before,
                            timeOfDay(state.settings.dayStartHour * 60),
                            dayDate(state.planDay),
                        )
                    } else {
                        stringResource(R.string.plan_settings_now_same, dayDate(state.planDay))
                    },
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(
                text = stringResource(R.string.edit_save),
                onClick = onSave,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
