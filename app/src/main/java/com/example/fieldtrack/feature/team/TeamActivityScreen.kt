package com.example.fieldtrack.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.TeamEventKind
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.dayMonthYear

/**
 * Who was let in, who was let go, and who was given sight of the money.
 *
 * Grouped by day and newest first, because the question is nearly always "what
 * just happened" and only occasionally "what happened in March".
 */
@Composable
fun TeamActivityScreen(
    state: TeamActivityState,
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
            title = stringResource(R.string.changes_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.changes_intro),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }

            if (state.loaded && state.changes.isEmpty()) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(R.string.changes_none),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            // Grouped on the fly rather than in the model: the only thing the
            // grouping depends on is the day a row fell in, which the row knows.
            val byDay = state.changes.groupBy { dayMonthYear(it.atMillis) }
            byDay.forEach { (day, changes) ->
                item(key = "day-$day") { FtSectionLabel(day) }
                items(changes, key = { it.id }) { change ->
                    FtCard {
                        Text(
                            text = sentenceFor(change),
                            style = FieldTrackTheme.type.body,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = clockTime(change.atMillis),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(dimens.screenPadding)) }
        }
    }
}

/**
 * One change, said as a sentence.
 *
 * Written out per kind rather than assembled from parts, because a log read by
 * somebody worried about their money should read like a person wrote it.
 */
@Composable
private fun sentenceFor(change: TeamChange): String {
    val who = change.aboutName.orEmpty()
    return when (change.kind) {
        TeamEventKind.Joined -> stringResource(
            R.string.changes_joined, change.byName, change.detail.orEmpty(),
        )
        TeamEventKind.Removed -> stringResource(R.string.changes_removed, change.byName, who)
        TeamEventKind.TakenBack -> stringResource(R.string.changes_taken_back, change.byName, who)
        TeamEventKind.MadeOwner -> stringResource(R.string.changes_made_owner, change.byName, who)
        TeamEventKind.SteppedBack -> if (change.aboutSelf) {
            stringResource(R.string.changes_stepped_back_self, change.byName)
        } else {
            stringResource(R.string.changes_stepped_back, change.byName, who)
        }
        TeamEventKind.PermissionsChanged -> if (change.detail == "money") {
            stringResource(R.string.changes_saw_money, change.byName, who)
        } else {
            stringResource(R.string.changes_permissions, change.byName, who)
        }
        TeamEventKind.CodeSent -> stringResource(
            R.string.changes_code_sent, change.byName, change.detail.orEmpty(),
        )
        TeamEventKind.CodeCancelled -> stringResource(
            R.string.changes_code_cancelled, change.byName, change.detail.orEmpty(),
        )
        TeamEventKind.Merged -> stringResource(R.string.changes_merged, who, change.byName)
    }
}
