package com.example.fieldtrack.feature.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * The code, and what it lets a driver do.
 *
 * The code is the biggest thing on the screen because the whole job of this page is
 * being read aloud down a bad line, or squinted at while it is typed into another
 * phone. The permission switches sit below it, where they are read before a code is
 * made rather than discovered afterwards.
 */
@Composable
fun InviteScreen(
    state: InviteState,
    onBack: () -> Unit,
    onMakeCode: () -> Unit,
    onNewCode: () -> Unit,
    onRevoke: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onSeeMoney: (Boolean) -> Unit,
    onAddCustomers: (Boolean) -> Unit,
    onCloseBills: (Boolean) -> Unit,
    daysLeft: (Long) -> Int,
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
            title = stringResource(R.string.invite_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.invite_blurb),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )

            if (state.code != null) {
                CodeCard(
                    code = state.code,
                    daysLeft = state.expiresAtMillis?.let(daysLeft) ?: 0,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FtButton(
                        text = stringResource(R.string.invite_copy),
                        onClick = { onCopy(state.code) },
                        modifier = Modifier.weight(1f),
                        tone = FtButtonTone.Secondary,
                        height = 50.dp,
                    )
                    FtButton(
                        text = stringResource(R.string.invite_new),
                        onClick = onNewCode,
                        modifier = Modifier.weight(1f),
                        tone = FtButtonTone.Secondary,
                        enabled = !state.busy,
                        height = 50.dp,
                    )
                }
                Text(
                    text = stringResource(R.string.invite_new_warning),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            } else if (state.loaded) {
                FtCard {
                    Text(
                        text = stringResource(R.string.invite_none_title),
                        style = FieldTrackTheme.type.rowTitle,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.invite_none_body),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }

            FtSectionLabel(stringResource(R.string.invite_what_can_they_do))
            FtCard(padding = 0.dp) {
                // On for every driver and not negotiable: recording work is the
                // reason the account exists at all.
                PermissionRow(
                    title = stringResource(R.string.invite_perm_jobs),
                    note = stringResource(R.string.invite_perm_jobs_note),
                    checked = true,
                    onChange = null,
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.invite_perm_upkeep),
                    note = stringResource(R.string.invite_perm_upkeep_note),
                    checked = true,
                    onChange = null,
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.invite_perm_money),
                    note = stringResource(R.string.invite_perm_money_note),
                    checked = state.canSeeMoney,
                    // Closing a bill means seeing its amount, so this cannot be
                    // switched off underneath that one.
                    onChange = if (state.hasCode || state.canCloseBills) null else onSeeMoney,
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.invite_perm_bills),
                    note = stringResource(R.string.invite_perm_bills_note),
                    checked = state.canCloseBills,
                    onChange = if (state.hasCode) null else onCloseBills,
                )
                FtRowDivider()
                PermissionRow(
                    title = stringResource(R.string.invite_perm_customers),
                    note = stringResource(R.string.invite_perm_customers_note),
                    checked = state.canAddCustomers,
                    onChange = if (state.hasCode) null else onAddCustomers,
                )
            }

            // Worth saying plainly: this is the one thing in the app that needs a
            // network, and a driver standing in a field deserves to know which.
            FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                Text(
                    text = stringResource(R.string.invite_offline_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
            }

            if (state.code != null) {
                FtButton(
                    text = stringResource(R.string.invite_revoke),
                    onClick = onRevoke,
                    tone = FtButtonTone.Danger,
                    enabled = !state.busy,
                    height = 50.dp,
                )
            }

            Box(Modifier.height(dimens.space4))
        }

        Column(Modifier.padding(dimens.screenPadding)) {
            if (state.code != null) {
                FtButton(
                    text = stringResource(R.string.invite_share),
                    onClick = { onShare(state.code) },
                    icon = FieldTrackIcons.WhatsApp,
                    height = 58.dp,
                )
            } else {
                FtButton(
                    text = stringResource(R.string.invite_make),
                    onClick = onMakeCode,
                    enabled = state.canInvite && !state.busy,
                    height = 58.dp,
                )
            }
        }
    }
}

/** The code itself, big enough to read off a cracked screen at arm's length. */
@Composable
private fun CodeCard(code: String, daysLeft: Int) {
    val colors = FieldTrackTheme.colors

    FtCard(background = colors.primaryPressed, borderColor = colors.primaryPressed) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.invite_your_code).uppercase(),
                style = FieldTrackTheme.type.overline,
                color = colors.onDarkMuted,
            )
            Text(
                text = code,
                style = FieldTrackTheme.type.moneyLarge.copy(
                    fontSize = 44.sp,
                    letterSpacing = 6.sp,
                ),
                color = colors.onDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (daysLeft <= 0) {
                    stringResource(R.string.invite_expires_today)
                } else {
                    pluralStringResource(R.plurals.count_days, daysLeft, daysLeft)
                        .let { stringResource(R.string.invite_valid_days_text, it) }
                },
                style = FieldTrackTheme.type.caption,
                color = colors.onDarkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One thing a driver may or may not do. Null [onChange] means it is not a choice. */
@Composable
private fun PermissionRow(
    title: String,
    note: String,
    checked: Boolean,
    onChange: ((Boolean) -> Unit)?,
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
                color = if (onChange != null) colors.ink else colors.inkSecondary,
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
            enabled = onChange != null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.surface,
                uncheckedTrackColor = colors.line,
                disabledCheckedTrackColor = colors.primary.copy(alpha = 0.5f),
            ),
        )
    }
}
