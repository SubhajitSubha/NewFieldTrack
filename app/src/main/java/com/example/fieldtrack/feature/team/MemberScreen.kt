package com.example.fieldtrack.feature.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.RolePreset
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.component.FtAvatar
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.rupeesCompact
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.initialsOf
import com.example.fieldtrack.data.auth.formatIndianPhone
import androidx.compose.ui.res.pluralStringResource
import com.example.fieldtrack.feature.timeAgo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.fieldtrack.feature.workLabel

/**
 * One driver: who they are, what they have done, and what they are trusted with.
 *
 * The permission switches are the part that matters. Every capability check in the
 * app reads from them, and until this screen existed they could only be set by the
 * defaults a new member was created with.
 */
@Composable
fun MemberScreen(
    state: MemberState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onPreset: (RolePreset) -> Unit,
    onSeeMoney: (Boolean) -> Unit,
    onAddCustomers: (Boolean) -> Unit,
    onCloseBills: (Boolean) -> Unit,
    onEditTeam: (Boolean) -> Unit,
    onEditMachines: (Boolean) -> Unit,
    onPayOut: (Boolean) -> Unit,
    onSeePeopleCost: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onCall: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    // Taking somebody off used to happen on the tap. It stops a job they may be
    // in the middle of and closes the business to them, and neither is something
    // a thumb should be able to do by accident.
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    // Making an owner is the only change on this screen that cannot be undone
    // from this side: the new owner can take the old one off.
    var confirmOwner by rememberSaveable { mutableStateOf(false) }
    // Folded away by default. Somebody who picked a job from the list above has
    // already answered all seven of these, and seven switches on open invites
    // an owner to fiddle with permissions they were not asked about.
    var showSwitches by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .imePadding()
    ) {
        FtTopBar(
            title = stringResource(
                if (state.role == MemberRole.Owner) R.string.more_owner
                else R.string.team_driver
            ),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            FtCard {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    FtAvatar(
                        initials = initialsOf(state.displayName),
                        size = 74.dp,
                        background = colors.primaryWash,
                        contentColor = colors.primaryPressed,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.displayName,
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = listOfNotNull(
                                state.phone?.takeIf { it.isNotBlank() }?.let { formatIndianPhone(it) },
                                state.joinedAtMillis?.let {
                                    stringResource(R.string.team_joined, dayMonthYear(it))
                                },
                            ).joinToString(" · "),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatCard(
                    value = state.jobCount.toString(),
                    label = stringResource(R.string.team_stat_jobs),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = (state.totalSeconds / 3600).toString(),
                    label = stringResource(R.string.team_stat_hours),
                    modifier = Modifier.weight(1f),
                )
                // Hidden from a driver who may not see money, even about themselves:
                // what the business earned off their work is the owner's figure.
                if (state.canSeeTheirEarnings) {
                    StatCard(
                        value = rupeesCompact(state.broughtInRupees),
                        label = stringResource(R.string.team_stat_brought_in),
                        valueColor = colors.primaryPressed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Said plainly, because it is the thing an owner worries about when told
            // their driver also works for somebody else. Never on your own record,
            // where it would be telling you that you cannot see your own books.
            if (!state.isYou) {
                FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                    Text(
                        text = stringResource(R.string.team_privacy_note, state.displayName),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            state.problem?.let { problem ->
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = stringResource(problem),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            // Only for other people: your own backlog has a screen of its own,
            // and it is live there rather than however stale this beacon is.
            if (!state.isYou && state.outOfTouch) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = stringResource(R.string.team_quiet_title),
                        style = FieldTrackTheme.type.sectionTitle,
                        color = colors.dueInk,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = stringResource(
                            R.string.team_quiet_note,
                            timeAgo(requireNotNull(state.unsentAtMillis)),
                        ),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            } else if (!state.isYou && state.unsentCount > 0) {
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.team_unsent_title, state.unsentCount, state.unsentCount,
                        ),
                        style = FieldTrackTheme.type.sectionTitle,
                        color = colors.dueInk,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.team_unsent_note),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                    state.unsentAtMillis?.let { at ->
                        Spacer(Modifier.height(7.dp))
                        // Said as "counted two hours ago", never as a fact about
                        // now: the number is only as fresh as their last signal.
                        Text(
                            text = stringResource(R.string.team_unsent_as_of, timeAgo(at)),
                            style = FieldTrackTheme.type.caption,
                            color = colors.dueInk,
                        )
                    }
                }
            }

            if (state.editable) {
                // A job, not seven switches. An owner knows what a senior
                // driver is; they do not know which four of seven permissions
                // add up to one. The switches are still underneath, and still
                // the only thing any capability check reads.
                FtSectionLabel(stringResource(R.string.team_preset_title))
                FtCard(padding = 0.dp) {
                    state.offeredPresets.forEachIndexed { index, preset ->
                        if (index > 0) FtRowDivider()
                        RoleRow(
                            title = stringResource(presetTitle(preset)),
                            note = stringResource(presetNote(preset)),
                            selected = state.preset == preset,
                            danger = preset == RolePreset.Owner,
                            // The one choice this side cannot undo, so it is
                            // the one choice that asks twice.
                            onSelect = {
                                if (preset == RolePreset.Owner) confirmOwner = true
                                else onPreset(preset)
                            },
                        )
                    }
                    if (state.preset == null) {
                        FtRowDivider()
                        // Shown only once it is already true, and not selectable:
                        // there is no set of switches called "by hand" to apply.
                        // It is a state the screen reports, not a mistake.
                        RoleRow(
                            title = stringResource(R.string.team_preset_by_hand),
                            note = stringResource(R.string.team_preset_by_hand_note),
                            selected = true,
                            onSelect = null,
                        )
                    }
                }
                if (!state.editableByOwnerOnly) {
                    Text(
                        text = stringResource(R.string.team_preset_manager_only_owner),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                FtSectionLabel(stringResource(R.string.team_preset_means))
                FtCard { GrantSummary(state) }

                FtButton(
                    text = stringResource(
                        if (showSwitches) R.string.team_preset_hide
                        else R.string.team_preset_change
                    ),
                    onClick = { showSwitches = !showSwitches },
                    tone = FtButtonTone.Secondary,
                    height = 50.dp,
                )

                if (showSwitches) {
                    FtSectionLabel(
                        stringResource(R.string.team_allowed_to),
                        Modifier.padding(top = 4.dp),
                    )
                    // Said before the switches, because three unchecked switches
                    // read as "this person can do nothing" — and an owner deciding
                    // what to grant should know what the job already includes.
                    Text(
                        text = stringResource(R.string.team_allowed_baseline),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    FtCard(padding = 0.dp) {
                        PermissionRow(
                            title = stringResource(R.string.team_perm_money),
                            note = stringResource(R.string.team_perm_money_note),
                            checked = state.canSeeMoney || state.role == MemberRole.Owner,
                            onChange = onSeeMoney,
                            // Closing a bill means seeing the amount on it, so this one
                            // cannot be switched off underneath it. Nor can any of the
                            // three be chosen for an owner, who holds them all by
                            // definition — switches that quietly do nothing are worse
                            // than no switches.
                            enabled = !state.canCloseBills && state.role != MemberRole.Owner,
                        )
                        FtRowDivider()
                        PermissionRow(
                            title = stringResource(R.string.team_perm_customers),
                            note = stringResource(R.string.team_perm_customers_note),
                            checked = state.canAddCustomers || state.role == MemberRole.Owner,
                            enabled = state.role != MemberRole.Owner,
                            onChange = onAddCustomers,
                        )
                        FtRowDivider()
                        PermissionRow(
                            title = stringResource(R.string.team_perm_bills),
                            note = stringResource(R.string.team_perm_bills_note),
                            checked = state.canCloseBills || state.role == MemberRole.Owner,
                            enabled = state.role != MemberRole.Owner,
                            onChange = onCloseBills,
                        )
                    }

                    // The switches that make somebody more than a driver. Kept in
                    // their own card, and only an owner may hand them on: a
                    // manager able to grant these could make a peer, and between
                    // them they could put the owner out of their own business.
                    FtSectionLabel(
                        stringResource(R.string.team_trusted_with),
                        Modifier.padding(top = 4.dp),
                    )
                    if (!state.editableByOwnerOnly) {
                        Text(
                            text = stringResource(R.string.team_perm_owner_only),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    FtCard(padding = 0.dp) {
                        PermissionRow(
                            title = stringResource(R.string.team_perm_team),
                            note = stringResource(R.string.team_perm_team_note),
                            checked = state.canEditTeam || state.role == MemberRole.Owner,
                            enabled = state.editableByOwnerOnly && state.role != MemberRole.Owner,
                            onChange = onEditTeam,
                        )
                        FtRowDivider()
                        PermissionRow(
                            title = stringResource(R.string.team_perm_machines),
                            note = stringResource(R.string.team_perm_machines_note),
                            checked = state.canEditMachines || state.role == MemberRole.Owner,
                            enabled = state.editableByOwnerOnly && state.role != MemberRole.Owner,
                            onChange = onEditMachines,
                        )
                        FtRowDivider()
                        PermissionRow(
                            title = stringResource(R.string.team_perm_payout),
                            note = stringResource(R.string.team_perm_payout_note),
                            checked = state.canPayOut || state.role == MemberRole.Owner,
                            enabled = state.editableByOwnerOnly && state.role != MemberRole.Owner,
                            onChange = onPayOut,
                        )
                        FtRowDivider()
                        PermissionRow(
                            title = stringResource(R.string.team_perm_people_cost),
                            note = stringResource(R.string.team_perm_people_cost_note),
                            checked = state.canSeePeopleCost || state.role == MemberRole.Owner,
                            enabled = state.editableByOwnerOnly && state.role != MemberRole.Owner,
                            onChange = onSeePeopleCost,
                        )
                    }
                }

                FtSectionLabel(
                    stringResource(R.string.team_details),
                    Modifier.padding(top = 4.dp),
                )
                FtTextField(
                    label = stringResource(R.string.team_member_name),
                    value = state.displayName,
                    onValueChange = onName,
                )
                FtTextField(
                    label = stringResource(R.string.edit_business_phone),
                    value = state.phone.orEmpty(),
                    onValueChange = onPhone,
                    prefix = "+91",
                    keyboardType = KeyboardType.Phone,
                )
            } else {
                FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                    Text(
                        text = stringResource(
                            if (state.isYou) R.string.team_not_yourself
                            else R.string.edit_not_allowed
                        ),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }

        if (state.editable || onCall != null) {
            Column(
                Modifier.padding(dimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (onCall != null) {
                        FtButton(
                            text = stringResource(R.string.team_call),
                            onClick = onCall,
                            modifier = Modifier.weight(1f),
                            tone = FtButtonTone.Secondary,
                            height = 54.dp,
                        )
                    }
                    if (state.editable) {
                        FtButton(
                            text = stringResource(R.string.edit_save),
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            enabled = state.canSave,
                            height = 54.dp,
                        )
                    }
                }
                if (state.editable) {
                    FtButton(
                        text = stringResource(
                            if (state.active) R.string.team_remove else R.string.team_take_back
                        ),
                        onClick = {
                            if (state.active) confirmRemove = true else onSetActive(true)
                        },
                        tone = if (state.active) FtButtonTone.Danger else FtButtonTone.Secondary,
                        height = 50.dp,
                    )
                }
            }
        }
    }

    if (confirmOwner) {
        AlertDialog(
            onDismissRequest = { confirmOwner = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(
                        R.string.team_preset_owner_confirm_title, state.displayName,
                    ),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.team_preset_owner_confirm_body),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOwner = false
                        onPreset(RolePreset.Owner)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.team_preset_owner_confirm_yes),
                        style = FieldTrackTheme.type.label,
                        color = colors.due,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOwner = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        style = FieldTrackTheme.type.label,
                        color = colors.ink,
                    )
                }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.team_remove_title, state.displayName),
                    style = FieldTrackTheme.type.sectionTitle,
                    color = colors.ink,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The two warnings come first, because they are the ones
                    // that cost something and they are both easy to forget.
                    state.liveWorkType?.let { workType ->
                        Text(
                            text = stringResource(
                                R.string.team_remove_running,
                                "%d:%02d".format(
                                    state.liveSeconds / 3600,
                                    (state.liveSeconds % 3600) / 60,
                                ),
                                workLabel(workType),
                            ),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.dueInk,
                        )
                    }
                    if (state.unsentCount > 0 || state.outOfTouch) {
                        Text(
                            text = stringResource(R.string.team_remove_unsent),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.dueInk,
                        )
                    }
                    Text(
                        text = stringResource(R.string.team_remove_keeps),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Text(
                        text = stringResource(R.string.team_remove_closes),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Text(
                        text = stringResource(R.string.team_remove_back),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onSetActive(false)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.team_remove_yes),
                        style = FieldTrackTheme.type.label,
                        color = colors.due,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(
                        text = stringResource(R.string.team_remove_keep),
                        style = FieldTrackTheme.type.label,
                        color = colors.ink,
                    )
                }
            },
        )
    }
}

@StringRes
private fun presetTitle(preset: RolePreset): Int = when (preset) {
    RolePreset.Driver -> R.string.team_role_driver
    RolePreset.SeniorDriver -> R.string.team_preset_senior
    RolePreset.Manager -> R.string.team_preset_manager
    RolePreset.Owner -> R.string.team_role_owner
}

@StringRes
private fun presetNote(preset: RolePreset): Int = when (preset) {
    RolePreset.Driver -> R.string.team_preset_driver_note
    RolePreset.SeniorDriver -> R.string.team_preset_senior_note
    RolePreset.Manager -> R.string.team_preset_manager_note
    RolePreset.Owner -> R.string.team_preset_owner_note
}

/**
 * One job somebody can hold.
 *
 * A radio rather than a switch, because these are four answers to one
 * question. Four switches would let an owner turn all of them off, and there
 * is no such thing as a member who is nothing.
 */
@Composable
private fun RoleRow(
    title: String,
    note: String,
    selected: Boolean,
    onSelect: (() -> Unit)?,
    danger: Boolean = false,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onSelect == null) Modifier
                else Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = onSelect != null)
        Column(Modifier.padding(start = 11.dp)) {
            Text(
                text = title,
                style = FieldTrackTheme.type.rowTitle,
                color = if (danger) colors.due else colors.ink,
            )
            Text(
                text = note,
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * What the chosen job actually comes to, in the screen's own words.
 *
 * Only what is granted is listed. A seven-line tick-and-cross table is the
 * switch list again with worse controls, and the question somebody has after
 * picking "senior driver" is "so what can he do", not "so what are all seven
 * settings".
 */
@Composable
private fun GrantSummary(state: MemberState) {
    val colors = FieldTrackTheme.colors

    if (state.role == MemberRole.Owner) {
        // An owner's capabilities never come from these columns, so listing
        // the columns for one would be describing a thing that is not read.
        Text(
            text = stringResource(R.string.team_preset_owner_means),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.ink,
        )
        return
    }

    val granted = buildList {
        if (state.canSeeMoney) add(R.string.team_perm_money)
        if (state.canAddCustomers) add(R.string.team_perm_customers)
        if (state.canCloseBills) add(R.string.team_perm_bills)
        if (state.canEditTeam) add(R.string.team_perm_team)
        if (state.canEditMachines) add(R.string.team_perm_machines)
        if (state.canPayOut) add(R.string.team_perm_payout)
        if (state.canSeePeopleCost) add(R.string.team_perm_people_cost)
    }

    if (granted.isEmpty()) {
        Text(
            text = stringResource(R.string.team_preset_nothing_more),
            style = FieldTrackTheme.type.bodySmall,
            color = colors.inkSecondary,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        granted.forEach { label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FieldTrackIcons.Check,
                    contentDescription = null,
                    // Green, not the amber a rupee figure gets: this tick means
                    // "yes, granted", and it is not money.
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(label),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.ink,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.team_preset_rest_off),
            style = FieldTrackTheme.type.caption,
            color = colors.inkTertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The figure first and what it counts underneath, in its own card. */
@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = FieldTrackTheme.colors.ink,
) {
    FtCard(modifier = modifier, padding = 14.dp, shape = FieldTrackTheme.shapes.medium) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = FieldTrackTheme.type.moneyMedium,
                color = valueColor,
            )
            Text(
                text = label,
                style = FieldTrackTheme.type.overline,
                color = FieldTrackTheme.colors.inkTertiary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** One thing a driver may or may not do, with what it means underneath. */
@Composable
private fun PermissionRow(
    title: String,
    note: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = FieldTrackTheme.type.rowTitle,
                color = if (enabled) colors.ink else colors.inkTertiary,
            )
            Text(
                text = note,
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.line,
            ),
        )
    }
}
