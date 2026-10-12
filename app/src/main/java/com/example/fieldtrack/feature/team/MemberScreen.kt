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
import com.example.fieldtrack.data.db.MemberRole
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
    onSeeMoney: (Boolean) -> Unit,
    onAddCustomers: (Boolean) -> Unit,
    onCloseBills: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onCall: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

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
                                state.phone?.takeIf { it.isNotBlank() }?.let { "+91 $it" },
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

            if (state.editable) {
                FtSectionLabel(stringResource(R.string.team_details))
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

                FtSectionLabel(
                    stringResource(R.string.team_allowed_to),
                    Modifier.padding(top = 4.dp),
                )
                FtCard(padding = 0.dp) {
                    PermissionRow(
                        title = stringResource(R.string.team_perm_money),
                        note = stringResource(R.string.team_perm_money_note),
                        checked = state.canSeeMoney,
                        onChange = onSeeMoney,
                        // Closing a bill means seeing the amount on it, so this one
                        // cannot be switched off underneath it.
                        enabled = !state.canCloseBills,
                    )
                    FtRowDivider()
                    PermissionRow(
                        title = stringResource(R.string.team_perm_customers),
                        note = stringResource(R.string.team_perm_customers_note),
                        checked = state.canAddCustomers,
                        onChange = onAddCustomers,
                    )
                    FtRowDivider()
                    PermissionRow(
                        title = stringResource(R.string.team_perm_bills),
                        note = stringResource(R.string.team_perm_bills_note),
                        checked = state.canCloseBills,
                        onChange = onCloseBills,
                    )
                }
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
                        onClick = { onSetActive(!state.active) },
                        tone = if (state.active) FtButtonTone.Danger else FtButtonTone.Secondary,
                        height = 50.dp,
                    )
                }
            }
        }
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
