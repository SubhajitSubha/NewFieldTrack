package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.example.fieldtrack.R
import com.example.fieldtrack.data.settings.AppLanguage
import com.example.fieldtrack.data.settings.GroupingChoice
import com.example.fieldtrack.data.settings.WeekStartChoice
import com.example.fieldtrack.data.settings.ThemeChoice
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * How numbers and weeks are shown: digit grouping and the day a week begins.
 *
 * The everyday choices (language, light or dark, bigger text) sit right on the Settings
 * screen; these two are rarer and come with a sample, so they keep a page of their own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayScreen(
    grouping: GroupingChoice,
    weekStart: WeekStartChoice,
    onGrouping: (GroupingChoice) -> Unit,
    onWeekStart: (WeekStartChoice) -> Unit,
    onBack: () -> Unit,
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
            title = stringResource(R.string.settings_numbers),
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
            FtSectionLabel(stringResource(R.string.money_grouping))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FtChip(
                    label = stringResource(R.string.money_grouping_auto),
                    selected = grouping == GroupingChoice.Auto,
                    onClick = { onGrouping(GroupingChoice.Auto) },
                )
                FtChip(
                    label = stringResource(R.string.money_grouping_indian),
                    selected = grouping == GroupingChoice.Indian,
                    onClick = { onGrouping(GroupingChoice.Indian) },
                )
                FtChip(
                    label = stringResource(R.string.money_grouping_international),
                    selected = grouping == GroupingChoice.International,
                    onClick = { onGrouping(GroupingChoice.International) },
                )
            }
            Text(
                text = stringResource(R.string.money_grouping_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )

            FtSectionLabel(stringResource(R.string.week_starts), Modifier.padding(top = 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    WeekStartChoice.Auto to R.string.week_auto,
                    WeekStartChoice.Monday to R.string.week_monday,
                    WeekStartChoice.Sunday to R.string.week_sunday,
                    WeekStartChoice.Saturday to R.string.week_saturday,
                ).forEach { (choice, label) ->
                    FtChip(
                        label = stringResource(label),
                        selected = weekStart == choice,
                        onClick = { onWeekStart(choice) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.week_starts_note),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )

            FtSectionLabel(stringResource(R.string.display_sample), Modifier.padding(top = 8.dp))
            FtEmphasisCard {
                Text(
                    text = stringResource(R.string.home_earned_today).uppercase(),
                    style = FieldTrackTheme.type.overline,
                    color = colors.onDarkMuted,
                )
                Text(
                    text = money(4_000 * com.example.fieldtrack.designsystem.format.Money.style.unit),
                    style = FieldTrackTheme.type.moneyHero,
                    color = colors.onDark,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = stringResource(R.string.display_sample_line),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.onDarkMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}
