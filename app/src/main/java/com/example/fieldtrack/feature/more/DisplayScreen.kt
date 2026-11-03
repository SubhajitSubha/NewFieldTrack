package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.example.fieldtrack.data.settings.ThemeChoice
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChoiceCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Light or dark, and how big the words are.
 *
 * Changes apply the moment they are tapped, and the sample at the bottom is drawn
 * in whatever is chosen — so the person sees the result on this screen rather than
 * having to go back and find out.
 */
@Composable
fun DisplayScreen(
    theme: ThemeChoice,
    biggerText: Boolean,
    onTheme: (ThemeChoice) -> Unit,
    onBiggerText: (Boolean) -> Unit,
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
            title = stringResource(R.string.display_title),
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
            // Words only. The icon set has no sun or moon, and a map pin standing
            // for "light" would be a picture of the wrong thing.
            FtSectionLabel(stringResource(R.string.display_theme))
            FtChoiceCard(
                title = stringResource(R.string.display_theme_system),
                description = stringResource(R.string.display_theme_system_note),
                selected = theme == ThemeChoice.System,
                onClick = { onTheme(ThemeChoice.System) },
            )
            FtChoiceCard(
                title = stringResource(R.string.display_theme_light),
                description = stringResource(R.string.display_theme_light_note),
                selected = theme == ThemeChoice.Light,
                onClick = { onTheme(ThemeChoice.Light) },
            )
            FtChoiceCard(
                title = stringResource(R.string.display_theme_dark),
                description = stringResource(R.string.display_theme_dark_note),
                selected = theme == ThemeChoice.Dark,
                onClick = { onTheme(ThemeChoice.Dark) },
            )

            FtSectionLabel(stringResource(R.string.display_text), Modifier.padding(top = 8.dp))
            FtCard(padding = 0.dp) {
                Row(
                    Modifier
                        .toggleable(
                            value = biggerText,
                            role = Role.Switch,
                            onValueChange = onBiggerText,
                        )
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.display_bigger_text),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.display_bigger_text_note),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = biggerText,
                        // The whole row toggles; the switch only shows the state.
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

            FtSectionLabel(stringResource(R.string.display_sample), Modifier.padding(top = 8.dp))
            FtEmphasisCard {
                Text(
                    text = stringResource(R.string.home_earned_today).uppercase(),
                    style = FieldTrackTheme.type.overline,
                    color = colors.onDarkMuted,
                )
                Text(
                    text = rupees(4_000),
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
