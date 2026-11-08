package com.example.fieldtrack.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.fieldtrack.designsystem.format.money
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.pluralStringResource
import com.example.fieldtrack.designsystem.component.FtIconTile
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row

/**
 * Who works here, and for each of them what an owner wants at a glance: their job, what they
 * are doing right now, anything owed either way, and a button to ring them.
 *
 * Grouped by what matters on a working morning: the people out on a machine, then everyone
 * free, then anyone who has left. One way to add somebody, which then asks the one question
 * that matters (will they use the app or not). Roles and the record of changes sit at the
 * foot under Manage.
 */
@Composable
fun TeamScreen(
    state: TeamState,
    onBack: () -> Unit,
    onOpenMember: (String) -> Unit,
    onAddMember: (() -> Unit)?,
    onInvite: (() -> Unit)?,
    onSentCodes: (() -> Unit)?,
    onHandOver: (() -> Unit)?,
    onChanges: (() -> Unit)?,
    onMergeDuplicate: ((DuplicatePair) -> Unit)?,
    onNotDuplicate: ((DuplicatePair) -> Unit)?,
    modifier: Modifier = Modifier,
    /** The owner's own roles. Null for anybody but an owner. */
    onRoles: (() -> Unit)? = null,
    onCall: (String) -> Unit = {},
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var choosingHow by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.team_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        // One way in. Whether they will have the app is asked next, in their words.
        if (onInvite != null || onAddMember != null) {
            Box(Modifier.padding(horizontal = dimens.screenPadding, vertical = 4.dp)) {
                FtButton(
                    text = stringResource(R.string.team_add_person),
                    onClick = { choosingHow = true },
                    icon = FieldTrackIcons.Add,
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

            // Asked at the top, and only once: answered either way it goes away
            // for good. Two men on one family phone is ordinary, and a question
            // that cannot be settled is one that gets ignored.
            state.possibleDuplicate?.let { pair ->
                if (onMergeDuplicate != null && onNotDuplicate != null) {
                    item {
                        FtCard {
                            Text(
                                text = stringResource(R.string.dupe_title),
                                style = FieldTrackTheme.type.sectionTitle,
                                color = colors.ink,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = stringResource(
                                    R.string.dupe_note,
                                    pair.keepName,
                                    pair.phone,
                                    pair.absorbName,
                                ),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.inkSecondary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                FtButton(
                                    text = stringResource(R.string.dupe_yes),
                                    onClick = { onMergeDuplicate(pair) },
                                    height = 46.dp,
                                    modifier = Modifier.weight(1f),
                                )
                                FtButton(
                                    text = stringResource(R.string.dupe_no),
                                    onClick = { onNotDuplicate(pair) },
                                    tone = FtButtonTone.Secondary,
                                    height = 46.dp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // A business with one owner lives on one handset. Said here, at the
            // top, because the moment to fix it is while everything still works
            // — afterwards there is no way back in from inside the app at all.
            if (state.ownerCount == 1 && onHandOver != null) {
                item {
                    FtCard(
                        background = colors.dueWash,
                        borderColor = colors.dueWash,
                        onClick = onHandOver,
                    ) {
                        Text(
                            text = stringResource(R.string.team_one_owner_title),
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.dueInk,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.team_one_owner_note),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.dueInk,
                        )
                        // The card opens the hand-over, so it says so in a line that looks like an action.
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.team_one_owner_action),
                                style = FieldTrackTheme.type.label,
                                color = colors.dueInk,
                            )
                            androidx.compose.material3.Icon(
                                FieldTrackIcons.Forward,
                                contentDescription = null,
                                tint = colors.dueInk,
                                modifier = Modifier.padding(start = 4.dp).size(16.dp),
                            )
                        }
                    }
                }
            }

            // Codes already out in the world, above the people, because an owner
            // who sent three and sees one driver needs to know the other two
            // are still live and can be taken back.
            if (state.openCodeCount > 0 && onSentCodes != null) {
                item {
                    FtCard(padding = 0.dp) {
                        FtListRow(
                            title = pluralStringResource(
                                R.plurals.team_codes_waiting,
                                state.openCodeCount,
                                state.openCodeCount,
                            ),
                            subtitle = stringResource(R.string.team_codes_waiting_note),
                            leading = { FtIconTile(FieldTrackIcons.Team, size = 40.dp) },
                            showChevron = true,
                            onClick = onSentCodes,
                        )
                    }
                }
            }

            if (state.working.isNotEmpty()) {
                item { FtSectionLabel(stringResource(R.string.team_working_now)) }
                items(state.working, key = { it.membershipId }) { member ->
                    MemberCard(member, state, onOpenMember, onCall)
                }
            }

            if (state.idle.isNotEmpty()) {
                item {
                    // "Everyone else" only reads right under a list of people who are out working.
                    FtSectionLabel(
                        stringResource(if (state.working.isEmpty()) R.string.team_everyone else R.string.team_everyone_else),
                        Modifier.padding(top = 6.dp),
                    )
                }
                items(state.idle, key = { it.membershipId }) { member ->
                    MemberCard(member, state, onOpenMember, onCall)
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
                    MemberCard(member, state, onOpenMember, onCall)
                }
            }

            // ---- Manage: after the people, because the people are what this page is for.
            if (onRoles != null || onChanges != null) {
                item {
                    FtSectionLabel(stringResource(R.string.team_manage), Modifier.padding(top = 6.dp))
                }
                item {
                    FtCard(padding = 0.dp) {
                        if (onRoles != null) {
                            FtListRow(
                                title = stringResource(R.string.roles_title),
                                subtitle = stringResource(R.string.roles_row_note),
                                subtitleMaxLines = 2,
                                leading = { FtIconTile(FieldTrackIcons.Lock, size = 40.dp) },
                                showChevron = true,
                                onClick = onRoles,
                            )
                        }
                        if (onRoles != null && onChanges != null) com.example.fieldtrack.designsystem.component.FtRowDivider()
                        if (onChanges != null) {
                            FtListRow(
                                title = stringResource(R.string.team_changes_row),
                                subtitle = stringResource(R.string.team_changes_row_note),
                                subtitleMaxLines = 2,
                                leading = { FtIconTile(FieldTrackIcons.Clock, size = 40.dp) },
                                showChevron = true,
                                onClick = onChanges,
                            )
                        }
                    }
                }
            }

            item { Box(Modifier.height(dimens.space4)) }
        }
    }

    if (choosingHow) {
        AddPersonSheet(
            onInvite = onInvite?.let { invite -> { choosingHow = false; invite() } },
            onWriteDown = onAddMember?.let { add -> { choosingHow = false; add() } },
            onDismiss = { choosingHow = false },
        )
    }
}

/** The one question before adding somebody: will they use the app on their own phone? */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddPersonSheet(onInvite: (() -> Unit)?, onWriteDown: (() -> Unit)?, onDismiss: () -> Unit) {
    val colors = FieldTrackTheme.colors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        contentColor = colors.ink,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.team_add_how),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            FtCard(padding = 0.dp) {
                if (onInvite != null) FtListRow(
                    title = stringResource(R.string.team_add_invite),
                    subtitle = stringResource(R.string.team_add_invite_note),
                    subtitleMaxLines = 2,
                    leading = { FtIconTile(FieldTrackIcons.WhatsApp, size = 42.dp) },
                    showChevron = true,
                    onClick = onInvite,
                )
                if (onInvite != null && onWriteDown != null) com.example.fieldtrack.designsystem.component.FtRowDivider()
                if (onWriteDown != null) FtListRow(
                    title = stringResource(R.string.team_add_write),
                    subtitle = stringResource(R.string.team_add_write_note),
                    subtitleMaxLines = 2,
                    leading = { FtIconTile(FieldTrackIcons.Edit, size = 42.dp) },
                    showChevron = true,
                    onClick = onWriteDown,
                )
            }
        }
    }
}

@Composable
private fun MemberCard(member: TeamMemberRow, state: TeamState, onOpen: (String) -> Unit, onCall: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    FtCard(
        padding = 0.dp,
        shape = FieldTrackTheme.shapes.medium,
        borderColor = if (member.working) colors.primary else colors.line,
    ) {
        Row(
            Modifier
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onOpen(member.membershipId) }
                .padding(start = 14.dp, end = 6.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FtAvatar(
                initials = initialsOf(member.displayName),
                size = 46.dp,
                background = if (member.working) colors.primaryWash else colors.surfaceMuted,
                contentColor = if (member.working) colors.primaryPressed else colors.inkSecondary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = member.displayName,
                    style = FieldTrackTheme.type.rowTitle,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        roleTitle(member.role, member.grants, state.customRoles),
                        stringResource(R.string.team_you).takeIf { member.isYou },
                    ).joinToString(" · "),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
                // What they are doing now, in words rather than a coloured pill to decode.
                Text(
                    text = when {
                        !member.active -> stringResource(R.string.team_left)
                        member.working && member.paused ->
                            stringResource(R.string.team_status_paused, member.workingOn.orEmpty())
                        member.working ->
                            stringResource(R.string.team_status_on, member.workingOn.orEmpty(), duration(member.elapsedSeconds))
                        else -> stringResource(R.string.team_free)
                    },
                    style = FieldTrackTheme.type.caption,
                    color = if (member.working && !member.paused) colors.primaryPressed else colors.inkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val owed = listOfNotNull(
                    stringResource(R.string.team_advance_left, money(member.advanceMinor)).takeIf { member.advanceMinor > 0 },
                    stringResource(R.string.team_hire_left, money(member.hireLeftMinor)).takeIf { member.hireLeftMinor > 0 },
                )
                if (owed.isNotEmpty()) Text(
                    text = owed.joinToString(" · "),
                    style = FieldTrackTheme.type.caption,
                    color = colors.dueInk,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val phone = member.phone?.takeIf { it.isNotBlank() }
            if (phone != null && !member.isYou && member.active) {
                FtIconButton(
                    icon = FieldTrackIcons.Call,
                    contentDescription = stringResource(R.string.team_call_cd, member.displayName),
                    onClick = { onCall(phone) },
                    tint = colors.primary,
                )
            }
        }
    }
}
