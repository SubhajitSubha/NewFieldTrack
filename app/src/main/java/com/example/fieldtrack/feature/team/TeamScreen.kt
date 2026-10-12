package com.example.fieldtrack.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.initialsOf

/**
 * Who works here.
 *
 * Grouped by what matters at a glance on a working morning: the people out on a
 * machine, then everyone free, then anyone who has left. A driver's own name carries
 * a marker, because an owner scanning the list needs to find themselves quickly and
 * cannot edit their own access from here anyway.
 */
@Composable
fun TeamScreen(
    state: TeamState,
    onBack: () -> Unit,
    onOpenMember: (String) -> Unit,
    onAddMember: (() -> Unit)?,
    onInvite: (() -> Unit)?,
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
            title = stringResource(R.string.team_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = {
                if (onAddMember != null) {
                    FtIconButton(
                        icon = FieldTrackIcons.Add,
                        contentDescription = stringResource(R.string.team_add_cd),
                        onClick = onAddMember,
                        tint = colors.onPrimary,
                        background = colors.primary,
                    )
                }
            },
        )

        if (onInvite != null) {
            Box(Modifier.padding(horizontal = dimens.screenPadding, vertical = 4.dp)) {
                FtButton(
                    text = stringResource(R.string.team_invite),
                    onClick = onInvite,
                    icon = FieldTrackIcons.Team,
                    tone = FtButtonTone.Secondary,
                    height = 52.dp,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (state.loaded && state.members.size <= 1) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(R.string.team_empty_title),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.team_empty_body),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (state.working.isNotEmpty()) {
                item { FtSectionLabel(stringResource(R.string.team_working_now)) }
                items(state.working, key = { it.membershipId }) { member ->
                    MemberCard(member, onOpenMember)
                }
            }

            if (state.idle.isNotEmpty()) {
                item {
                    FtSectionLabel(
                        stringResource(R.string.team_everyone_else),
                        Modifier.padding(top = 6.dp),
                    )
                }
                items(state.idle, key = { it.membershipId }) { member ->
                    MemberCard(member, onOpenMember)
                }
            }

            if (state.former.isNotEmpty()) {
                item {
                    FtSectionLabel(
                        stringResource(R.string.team_no_longer_here),
                        Modifier.padding(top = 6.dp),
                    )
                }
                items(state.former, key = { it.membershipId }) { member ->
                    MemberCard(member, onOpenMember)
                }
            }

            item { Box(Modifier.height(dimens.space4)) }
        }
    }
}

@Composable
private fun MemberCard(member: TeamMemberRow, onOpen: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    FtCard(
        padding = 0.dp,
        shape = FieldTrackTheme.shapes.medium,
        borderColor = if (member.working) colors.primary else colors.line,
    ) {
        FtListRow(
            title = member.displayName,
            subtitle = member.subtitle(),
            // Colour down the edge so the people out on a machine read first.
            statusStripe = if (member.working) colors.primary else null,
            leading = {
                FtAvatar(
                    initials = initialsOf(member.displayName),
                    size = 46.dp,
                    background = if (member.working) colors.primaryWash else colors.surfaceMuted,
                    contentColor = if (member.working) colors.primaryPressed else colors.inkSecondary,
                )
            },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    if (member.working) {
                        Text(
                            text = duration(member.elapsedSeconds),
                            style = FieldTrackTheme.type.numeric,
                            color = colors.ink,
                        )
                    }
                    FtStatusPill(
                        status = member.pillStatus(),
                        label = stringResource(member.pillLabel()),
                    )
                }
            },
            onClick = { onOpen(member.membershipId) },
        )
    }
}

/** "Owner · you", or what they are driving when they are out on a job. */
@Composable
private fun TeamMemberRow.subtitle(): String = listOfNotNull(
    stringResource(
        if (role == MemberRole.Owner) R.string.more_owner else R.string.more_operator
    ),
    workingOn,
    stringResource(R.string.team_you).takeIf { isYou },
).joinToString(" · ")

private fun TeamMemberRow.pillStatus(): FtStatus = when {
    !active -> FtStatus.Idle
    paused -> FtStatus.Paused
    working -> FtStatus.Running
    else -> FtStatus.Idle
}

private fun TeamMemberRow.pillLabel(): Int = when {
    !active -> R.string.team_left
    paused -> R.string.status_paused
    working -> R.string.status_running
    else -> R.string.team_free
}
