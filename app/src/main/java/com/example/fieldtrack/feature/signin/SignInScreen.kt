package com.example.fieldtrack.feature.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.BuildConfig
import com.example.fieldtrack.R
import com.example.fieldtrack.data.auth.AuthFailure
import com.example.fieldtrack.data.auth.formatIndianPhone
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * Signing in with a phone number.
 *
 * Two steps in one screen because they are one thought — "it's me, here's my number"
 * — and because going back to fix a mistyped digit should not feel like starting the
 * app again.
 */
@Composable
fun SignInScreen(
    state: SignInState,
    onDigits: (String) -> Unit,
    onCode: (String) -> Unit,
    onSendCode: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    onBack: (() -> Unit)?,
    /** Set when this phone was signed out because the account was turned off. */
    accountTurnedOff: Boolean = false,
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
            onBack = when {
                state.step == SignInStep.Code -> onChangeNumber
                else -> onBack
            },
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Somebody who was working five minutes ago and is now looking at a
            // sign-in screen needs a reason. Without this the app simply appears
            // to have forgotten them, and the obvious conclusion is that it lost
            // the work with it.
            if (accountTurnedOff) {
                FtCard {
                    Text(
                        text = stringResource(R.string.account_off_title),
                        style = FieldTrackTheme.type.sectionTitle,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.account_off_body),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }

            when (state.step) {
                SignInStep.Phone -> PhoneStep(state, onDigits)
                SignInStep.Code -> CodeStep(state, onCode, onResend, onChangeNumber)
            }

            state.problem?.let { problem ->
                FtCard(background = colors.dueWash, borderColor = colors.dueWash) {
                    Text(
                        text = problem.saidPlainly(),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.dueInk,
                    )
                    // The provider's own words are the only clue when a project is
                    // misconfigured, and useless to the person holding the phone.
                    // They belong in front of whoever can act on them, and nowhere
                    // near a farmer being told his sign-in failed.
                    if (BuildConfig.DEBUG && !state.problemDetail.isNullOrBlank()) {
                        Text(
                            text = stringResource(
                                R.string.signin_err_detail, state.problemDetail,
                            ),
                            style = FieldTrackTheme.type.caption,
                            color = colors.dueInk,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
            }

            Box(Modifier.height(dimens.space4))
        }

        Column(Modifier.padding(dimens.screenPadding)) {
            when (state.step) {
                SignInStep.Phone -> FtButton(
                    text = stringResource(
                        if (state.busy) R.string.signin_sending else R.string.signin_send_code
                    ),
                    onClick = onSendCode,
                    enabled = state.canSendCode,
                    height = 58.dp,
                )
                SignInStep.Code -> FtButton(
                    text = stringResource(
                        if (state.busy) R.string.signin_checking else R.string.signin_verify
                    ),
                    onClick = onVerify,
                    enabled = state.canVerify,
                    height = 58.dp,
                )
            }
        }
    }
}

@Composable
private fun PhoneStep(state: SignInState, onDigits: (String) -> Unit) {
    val colors = FieldTrackTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column {
            Text(
                text = stringResource(R.string.signin_phone_title),
                style = FieldTrackTheme.type.screenTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.signin_phone_body),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Column {
            FtTextField(
                value = state.digits,
                onValueChange = onDigits,
                label = stringResource(R.string.signin_phone_label),
                helper = stringResource(R.string.signin_phone_hint),
                prefix = "+91",
                keyboardType = KeyboardType.Phone,
                focused = true,
            )
        }

        // Said before they type, not after. A farmer handing over a phone number
        // wants to know where it goes, and "customers cannot see it" is the specific
        // worry, not an abstract one about data.
        FtCard {
            Row(verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Icon(
                    imageVector = FieldTrackIcons.Lock,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.signin_privacy),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CodeStep(
    state: SignInState,
    onCode: (String) -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    val colors = FieldTrackTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column {
            Text(
                text = stringResource(R.string.signin_code_title),
                style = FieldTrackTheme.type.screenTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(
                    R.string.signin_code_sent_to, formatIndianPhone(state.phoneE164),
                ),
                style = FieldTrackTheme.type.body,
                color = colors.inkSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = stringResource(R.string.signin_change_number),
                style = FieldTrackTheme.type.label,
                color = colors.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(FieldTrackTheme.shapes.small)
                    .clickable(onClick = onChangeNumber),
            )
        }

        Column {
            FtSectionLabel(stringResource(R.string.signin_code_label))
            CodeBoxes(code = state.code, onCode = onCode)
        }

        FtCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.signin_no_code),
                    style = FieldTrackTheme.type.body,
                    color = colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (state.canResend) {
                    Text(
                        text = stringResource(R.string.signin_resend),
                        style = FieldTrackTheme.type.label,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(FieldTrackTheme.shapes.small)
                            .clickable(onClick = onResend),
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.signin_resend_in,
                            "0:%02d".format(state.resendInSeconds),
                        ),
                        style = FieldTrackTheme.type.label,
                        color = colors.inkTertiary,
                    )
                }
            }
        }
    }
}

/**
 * Six boxes that are really one field.
 *
 * A real input per digit means six focus targets, six keyboards and a backspace that
 * behaves differently in each. One invisible field behind six drawn boxes keeps the
 * look and leaves the typing, pasting and auto-fill working the way the system
 * already knows how to do.
 */
@Composable
private fun CodeBoxes(code: String, onCode: (String) -> Unit) {
    val colors = FieldTrackTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Box {
        BasicTextField(
            value = code,
            onValueChange = onCode,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .focusRequester(focus),
            // Drawn as boxes below; the field itself is only there to hold the text.
            decorationBox = { _ ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    repeat(SignInState.CODE_LENGTH) { index ->
                        val digit = code.getOrNull(index)
                        val filling = index == code.length
                        Box(
                            Modifier
                                .weight(1f)
                                .height(66.dp)
                                .clip(FieldTrackTheme.shapes.medium)
                                .background(colors.surface)
                                .border(
                                    width = if (filling || digit != null) 2.dp else 1.dp,
                                    color = if (filling) colors.primary else colors.line,
                                    shape = FieldTrackTheme.shapes.medium,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = digit?.toString().orEmpty(),
                                style = FieldTrackTheme.type.moneyLarge,
                                color = colors.ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            },
            textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                androidx.compose.ui.graphics.Color.Transparent
            ),
        )
    }
}

/** What went wrong, in words that say what to do about it. */
@Composable
private fun AuthFailure.saidPlainly(): String = when (this) {
    AuthFailure.BadNumber -> stringResource(R.string.signin_err_bad_number)
    AuthFailure.WrongCode -> stringResource(R.string.signin_err_wrong_code)
    AuthFailure.CodeExpired -> stringResource(R.string.signin_err_expired)
    AuthFailure.TooManyAttempts -> stringResource(R.string.signin_err_too_many)
    AuthFailure.Unreachable -> stringResource(R.string.signin_err_unreachable)
    AuthFailure.Unknown -> stringResource(R.string.signin_err_unknown)
}

