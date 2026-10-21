package com.example.fieldtrack.feature.orgs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.feature.workLabel

/**
 * The businesses this person drives for, and which one the app is looking at.
 *
 * The card at the bottom is not decoration. Somebody asked to put their work into
 * an app owned by the people who pay them deserves to be told, in their own
 * language and before they are asked to trust it, exactly what each of those
 * people can and cannot see.
 */
@Composable
fun OrgSwitcherScreen(
    state: OrgSwitcherState,
    onBack: () -> Unit,
    onSwitch: (String) -> Unit,
    onJoinAnother: () -> Unit,
    onDismissRefusal: () -> Unit,
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
            title = stringResource(R.string.orgs_title),
            subtitle = state.personName,
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
            state.current?.let { current ->
                FtSectionLabel(stringResource(R.string.orgs_right_now))
                FtCard(background = colors.primaryPressed, borderColor = colors.primaryPressed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FtAvatar(
                            initials = initialsOf(current.name),
                            size = 52.dp,
                            background = colors.onPrimary.copy(alpha = 0.18f),
                            contentColor = colors.onDark,
                        )
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(
                                text = current.name,
                                style = FieldTrackTheme.type.sectionTitle,
                                color = colors.onDark,
                            )
                            Text(
                                text = current.liveWorkType?.let {
                                    stringResource(
                                        R.string.orgs_running,
                                        "${workLabel(it)} · ${duration(current.liveSeconds)}",
                                    )
                                } ?: current.lastWorkedLabel(),
                                style = FieldTrackTheme.type.caption,
                                color = colors.onDarkMuted,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }

            FtSectionLabel(stringResource(R.string.orgs_also))
            FtCard(padding = 0.dp) {
                state.others.forEach { org ->
                    FtListRow(
                        title = org.name,
                        subtitle = org.lastWorkedLabel(),
                        leading = {
                            FtAvatar(
                                initials = initialsOf(org.name),
                                size = 46.dp,
                                background = colors.surfaceMuted,
                                contentColor = colors.inkSecondary,
                            )
                        },
                        trailing = if (!org.active) {
                            {
                                // Said rather than hidden. The work is still theirs
                                // to remember even once the season is over.
                                FtStatusPill(
                                    status = FtStatus.Idle,
                                    label = stringResource(R.string.orgs_removed),
                                )
                            }
                        } else null,
                        showChevron = org.active,
                        onClick = if (org.active) ({ onSwitch(org.orgId) }) else null,
                    )
                    FtRowDivider()
                }

                FtListRow(
                    title = stringResource(R.string.orgs_join),
                    subtitle = stringResource(R.string.orgs_join_note),
                    leading = {
                        FtIconTile(
                            icon = FieldTrackIcons.Add,
                            size = 46.dp,
                            background = colors.primaryWash,
                            tint = colors.primaryPressed,
                        )
                    },
                    onClick = onJoinAnother,
                )
            }

            ApartCard()

            Box(Modifier.height(dimens.space4))
        }
    }

    // Shown only after the server has said so, never on a guess. Somebody tapping
    // a business they believe they still work for deserves to be told why nothing
    // happened, rather than watching the row quietly turn into "Ended".
    state.refused?.let { name ->
        AlertDialog(
            onDismissRequest = onDismissRefusal,
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.orgs_refused_title, name),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.orgs_refused_body),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissRefusal) {
                    Text(
                        text = stringResource(R.string.orgs_refused_ok),
                        style = FieldTrackTheme.type.label,
                        color = colors.ink,
                    )
                }
            },
        )
    }
}

/** "Last worked 2 April", or that there is nothing yet. */
@Composable
private fun OrgRow.lastWorkedLabel(): String =
    lastWorkedMillis?.let { stringResource(R.string.orgs_last_worked, dayMonth(it)) }
        ?: stringResource(R.string.orgs_never_worked)

/**
 * What each business can and cannot see.
 *
 * Three plain sentences, two of which are refusals. A driver handing their work to
 * an app their employer owns has a reasonable fear, and the only useful answer to
 * it is a specific one.
 */
@Composable
private fun ApartCard() {
    val colors = FieldTrackTheme.colors

    FtCard(borderColor = colors.line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = FieldTrackIcons.Lock,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.orgs_apart_title),
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
                modifier = Modifier.padding(start = 11.dp),
            )
        }
        Column(
            Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ApartLine(allowed = true, text = stringResource(R.string.orgs_apart_see))
            ApartLine(allowed = false, text = stringResource(R.string.orgs_apart_hidden))
            ApartLine(allowed = false, text = stringResource(R.string.orgs_apart_stays))
        }
    }
}

@Composable
private fun ApartLine(allowed: Boolean, text: String) {
    val colors = FieldTrackTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (allowed) FieldTrackIcons.Check else FieldTrackIcons.Close,
            contentDescription = null,
            tint = if (allowed) colors.money else colors.due,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(17.dp),
        )
        Text(
            text = text,
            style = FieldTrackTheme.type.bodySmall,
            color = colors.ink,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
