package com.example.fieldtrack.feature.join

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Joining somebody else's business with a code.
 *
 * Two steps, and the second one matters more than it looks: a code arrives on
 * WhatsApp and gets typed by somebody who cannot check it against anything. Naming
 * the business before a single row is written is their only chance to notice it is
 * the wrong one — and saying plainly what their boss will and will not see is the
 * difference between a tool they trust and one they are wary of.
 */
@Composable
fun JoinScreen(
    state: JoinState,
    onCode: (String) -> Unit,
    onName: (String) -> Unit,
    onCheck: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    onSkip: (() -> Unit)?,
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
            title = "",
            onBack = if (state.step == JoinStep.Preview) onBack else onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state.step) {
                JoinStep.Code -> CodeStep(state, onCode)
                JoinStep.Preview -> PreviewStep(state, onName)
            }

            state.problem?.let { problem ->
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = problem.saidPlainly(),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                }
            }

            Box(Modifier.height(dimens.space4))
        }

        Column(
            Modifier.padding(dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            when (state.step) {
                JoinStep.Code -> {
                    FtButton(
                        text = stringResource(
                            if (state.busy) R.string.join_checking else R.string.join_check
                        ),
                        onClick = onCheck,
                        enabled = state.canCheck,
                        height = 58.dp,
                    )
                    onSkip?.let {
                        FtButton(
                            text = stringResource(R.string.join_skip),
                            onClick = it,
                            tone = FtButtonTone.Secondary,
                            height = 52.dp,
                        )
                    }
                }
                JoinStep.Preview -> FtButton(
                    text = if (state.busy) {
                        stringResource(R.string.join_joining)
                    } else {
                        stringResource(R.string.join_confirm, state.invite?.orgName.orEmpty())
                    },
                    onClick = onJoin,
                    enabled = state.canJoin,
                    height = 58.dp,
                )
            }
        }
    }
}

@Composable
private fun CodeStep(state: JoinState, onCode: (String) -> Unit) {
    val colors = FieldTrackTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text(
                text = stringResource(R.string.join_title),
                style = FieldTrackTheme.type.screenTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.join_body),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        FtTextField(
            value = state.code,
            onValueChange = onCode,
            label = stringResource(R.string.join_label),
            focused = true,
        )

        // The one place the app admits it needs a network, said before they try
        // rather than as an error after.
        FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
            Text(
                text = stringResource(R.string.join_needs_signal),
                style = FieldTrackTheme.type.bodySmall,
                color = colors.inkSecondary,
            )
        }
    }
}

@Composable
private fun PreviewStep(state: JoinState, onName: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    val invite = state.invite ?: return

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            text = stringResource(R.string.join_preview_title),
            style = FieldTrackTheme.type.screenTitle,
            color = colors.ink,
        )

        // The business name, as large as the app can make it. This is the check.
        FtCard(background = colors.primaryPressed, borderColor = colors.primaryPressed) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = invite.orgName,
                    style = FieldTrackTheme.type.screenTitle.copy(fontSize = 26.sp),
                    color = colors.onDark,
                )
                Text(
                    text = stringResource(R.string.join_preview_as),
                    style = FieldTrackTheme.type.caption,
                    color = colors.onDarkMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        FtTextField(
            value = state.name,
            onValueChange = onName,
            label = stringResource(R.string.join_your_name),
            helper = stringResource(R.string.join_name_note),
        )

        FtSectionLabel(stringResource(R.string.join_you_can))
        FtCard {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Allowed(stringResource(R.string.join_can_jobs))
                Allowed(stringResource(R.string.join_can_upkeep))
                if (invite.canSeeMoney) Allowed(stringResource(R.string.join_can_money))
                if (invite.canCloseBills) Allowed(stringResource(R.string.join_can_bills))
                if (invite.canAddCustomers) Allowed(stringResource(R.string.join_can_customers))
                if (!invite.canSeeMoney) {
                    Text(
                        text = stringResource(R.string.join_cannot_money),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
        }

        // Said to the driver, about the driver. Somebody being asked to put their
        // work into their boss's app deserves to be told where the line is.
        FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = FieldTrackIcons.Lock,
                    contentDescription = null,
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.join_privacy),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(start = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun Allowed(text: String) {
    val colors = FieldTrackTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        FtIconTile(
            icon = FieldTrackIcons.Check,
            size = 26.dp,
            background = colors.moneyWash,
            tint = colors.money,
        )
        Text(
            text = text,
            style = FieldTrackTheme.type.bodySmall,
            color = colors.ink,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** What went wrong, in words that say what to do next. */
@Composable
private fun JoinProblem.saidPlainly(): String = when (this) {
    JoinProblem.NotFound -> stringResource(R.string.join_err_not_found)
    JoinProblem.Expired -> stringResource(R.string.join_err_expired)
    JoinProblem.AlreadyUsed -> stringResource(R.string.join_err_used)
    JoinProblem.Unreachable -> stringResource(R.string.join_err_unreachable)
}
