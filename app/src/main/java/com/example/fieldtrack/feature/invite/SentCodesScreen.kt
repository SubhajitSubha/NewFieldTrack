package com.example.fieldtrack.feature.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear

/**
 * The codes an owner has sent and not yet seen used.
 *
 * Each one is shown with what it allows and how long it has left, because those
 * are the two things that decide whether it still ought to exist. Taking one
 * down is a button here and nowhere else: before this screen the app could
 * publish a way into the business and offer no way to withdraw it.
 */
@Composable
fun SentCodesScreen(
    state: SentCodesState,
    nowMillis: Long,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onInviteMore: () -> Unit,
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
            title = stringResource(R.string.invites_sent_title),
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
            Text(
                text = stringResource(R.string.invites_sent_intro),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )

            if (state.loaded && state.open.isEmpty()) {
                FtCard {
                    Text(
                        text = stringResource(R.string.invites_none),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            state.open.forEach { sent ->
                FtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sent.code,
                            style = FieldTrackTheme.type.sectionTitle,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = remainingLabel(sent.expiresAtMillis, nowMillis),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = termsOf(sent),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(11.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FtButton(
                            text = stringResource(R.string.invites_send_again),
                            onClick = { onShare(sent.code) },
                            tone = FtButtonTone.Secondary,
                            height = 46.dp,
                            modifier = Modifier.weight(1f),
                        )
                        FtButton(
                            text = stringResource(R.string.invites_cancel),
                            onClick = { onRevoke(sent.inviteId) },
                            tone = FtButtonTone.Danger,
                            height = 46.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.used.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.invites_used))
                FtCard {
                    state.used.forEachIndexed { index, used ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        Text(
                            text = used.personName
                                ?: stringResource(R.string.invites_used_by_unknown),
                            style = FieldTrackTheme.type.body,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(
                                R.string.invites_used_on,
                                used.code,
                                dayMonthYear(used.redeemedAtMillis),
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        if (state.canInvite) {
            Column(Modifier.padding(dimens.screenPadding).fillMaxWidth()) {
                FtButton(
                    text = stringResource(R.string.invites_one_more),
                    onClick = onInviteMore,
                )
            }
        }
    }
}

/** "4 days left", and a plainer warning once it is down to hours. */
@Composable
private fun remainingLabel(expiresAt: Long, now: Long): String {
    val hours = ((expiresAt - now).coerceAtLeast(0L) / 3_600_000L).toInt()
    val days = hours / 24
    return when {
        hours < 1 -> stringResource(R.string.invites_ends_today)
        days < 1 -> pluralStringResource(R.plurals.invites_hours_left, hours, hours)
        else -> pluralStringResource(R.plurals.invites_days_left, days, days)
    }
}

/**
 * What the code lets somebody do, in the order it would worry an owner.
 *
 * Money first, because that is the permission people regret.
 */
@Composable
private fun termsOf(sent: SentCode): String = when {
    sent.canCloseBills -> stringResource(R.string.invites_terms_payment)
    sent.canSeeMoney -> stringResource(R.string.invites_terms_money)
    sent.canAddCustomers -> stringResource(R.string.invites_terms_customers)
    else -> stringResource(R.string.invites_terms_plain)
}
