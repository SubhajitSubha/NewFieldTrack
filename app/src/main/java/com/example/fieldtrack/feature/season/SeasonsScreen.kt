package com.example.fieldtrack.feature.season

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.fieldtrack.data.season.SeasonCalendar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * When this business's seasons begin.
 *
 * One card a season: an optional name, and the day and month it starts. Nothing
 * here is a year, because a season begins on the same day every year; what the
 * dates mean in practice is said at the foot — which season today falls in — so
 * somebody can check what they typed does what they meant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeasonsScreen(
    state: SeasonsState,
    onBack: () -> Unit,
    onName: (Int, String) -> Unit,
    onDay: (Int, String) -> Unit,
    onMonth: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onUsual: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    AdaptiveScreen(modifier.imePadding()) {
        FtTopBar(
            title = stringResource(R.string.season_edit_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (!state.loaded) return@AdaptiveScreen

        // Refuses on its own, as the other owner-only screens do, rather than
        // trusting that only the owner's profile ever links here.
        if (!state.allowed) {
            FtCard(modifier = Modifier.padding(dimens.screenPadding)) {
                Text(
                    text = stringResource(R.string.season_edit_not_for_you),
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
            Text(
                text = stringResource(R.string.season_edit_intro),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )

            state.rows.forEachIndexed { index, row ->
                FtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FtSectionLabel(
                            stringResource(R.string.season_edit_season_n, index + 1),
                            Modifier.weight(1f),
                        )
                        if (state.canRemove) {
                            FtIconButton(
                                icon = FieldTrackIcons.Close,
                                contentDescription = stringResource(R.string.season_edit_remove_cd, index + 1),
                                onClick = { onRemove(row.id) },
                            )
                        }
                    }
                    FtTextField(
                        label = stringResource(R.string.season_edit_name),
                        value = row.name,
                        onValueChange = { onName(row.id, it) },
                        placeholder = stringResource(R.string.season_edit_name_hint),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    FtSectionLabel(
                        stringResource(R.string.season_edit_begins),
                        Modifier.padding(top = 16.dp, bottom = 9.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        FtTextField(
                            value = row.dayText,
                            onValueChange = { onDay(row.id, it) },
                            keyboardType = KeyboardType.Number,
                            placeholder = stringResource(R.string.season_edit_day),
                            modifier = Modifier.width(88.dp),
                        )
                        FlowRow(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (1..12).forEach { month ->
                                FtChip(
                                    label = monthName(month),
                                    selected = row.month == month,
                                    onClick = { onMonth(row.id, month) },
                                )
                            }
                        }
                    }
                    if (!row.dayValid) {
                        Text(
                            text = stringResource(R.string.season_edit_day_range, row.maxDay),
                            style = FieldTrackTheme.type.caption,
                            color = colors.dueInk,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else if (state.repeatsEarlier(index)) {
                        // On the card that repeats the date, where the eye already is: said
                        // at the foot of the screen it was below the fold, beside a Save
                        // that was greyed out for no reason anyone could see.
                        Text(
                            text = stringResource(R.string.season_edit_duplicate),
                            style = FieldTrackTheme.type.caption,
                            color = colors.dueInk,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (state.canAdd) {
                FtButton(
                    text = stringResource(R.string.season_edit_add),
                    onClick = onAdd,
                    tone = FtButtonTone.Secondary,
                    icon = FieldTrackIcons.Add,
                )
            }
            FtButton(
                text = stringResource(R.string.season_edit_usual),
                onClick = onUsual,
                tone = FtButtonTone.Secondary,
            )

            // What the dates, as they stand, make of today — the check that what was
            // typed is what was meant.
            state.today?.let { today ->
                FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                    Text(
                        text = today.name?.let {
                            stringResource(
                                R.string.season_edit_today,
                                it,
                                dayMonthOf(today.start.monthValue, today.start.dayOfMonth),
                            )
                        } ?: stringResource(
                            R.string.season_edit_today_unnamed,
                            dayMonthOf(today.start.monthValue, today.start.dayOfMonth),
                        ),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
            Box(Modifier.padding(bottom = dimens.space4))
        }
        Box(Modifier.padding(dimens.screenPadding)) {
            FtButton(
                text = stringResource(R.string.edit_save),
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
