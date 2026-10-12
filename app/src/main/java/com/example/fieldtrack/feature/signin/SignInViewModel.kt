package com.example.fieldtrack.feature.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.data.auth.AuthFailure
import com.example.fieldtrack.data.auth.AuthResult
import com.example.fieldtrack.data.auth.PhoneAuth
import com.example.fieldtrack.data.auth.SignedIn
import com.example.fieldtrack.data.auth.indianMobileDigits
import com.example.fieldtrack.data.auth.isPlausibleIndianMobile
import com.example.fieldtrack.data.auth.toE164India
import com.example.fieldtrack.data.db.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInStep { Phone, Code }

data class SignInState(
    val step: SignInStep = SignInStep.Phone,
    val digits: String = "",
    val code: String = "",
    val busy: Boolean = false,
    /** Null when nothing has gone wrong; otherwise what to tell them. */
    val problem: AuthFailure? = null,
    val problemDetail: String? = null,
    /** Seconds until another code may be asked for. Zero means now. */
    val resendInSeconds: Int = 0,
) {
    val phoneE164: String get() = toE164India(digits)
    val canSendCode: Boolean get() = !busy && isPlausibleIndianMobile(digits)
    val canVerify: Boolean get() = !busy && code.length == CODE_LENGTH
    val canResend: Boolean get() = !busy && resendInSeconds == 0

    companion object {
        const val CODE_LENGTH = 6
    }
}

/**
 * Signing in with a phone number.
 *
 * Everything the screen reacts to is decided here — when the button may be pressed,
 * how long the resend timer has left, what a failure means — so it is all testable
 * against a fake, with no Firebase and no Activity anywhere near it.
 */
class SignInViewModel(
    private val auth: PhoneAuth,
    private val clock: Clock = Clock.System,
    private val onSignedIn: suspend (SignedIn) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: Any? = null
    private var countdown: Job? = null

    fun setDigits(value: String) = _state.update {
        it.copy(digits = indianMobileDigits(value), problem = null)
    }

    fun setCode(value: String) = _state.update {
        it.copy(
            code = value.filter { c -> c.isDigit() }.take(SignInState.CODE_LENGTH),
            problem = null,
        )
    }

    /** Back to the number, keeping it so they can correct a digit rather than retype. */
    fun changeNumber() {
        countdown?.cancel()
        verificationId = null
        _state.update {
            it.copy(step = SignInStep.Phone, code = "", problem = null, resendInSeconds = 0)
        }
    }

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode) return
        request(current.phoneE164, resend = false)
    }

    fun resend() {
        val current = _state.value
        if (!current.canResend) return
        request(current.phoneE164, resend = true)
    }

    private fun request(phoneE164: String, resend: Boolean) = viewModelScope.launch {
        _state.update { it.copy(busy = true, problem = null) }
        when (val result = auth.sendCode(phoneE164, resendToken.takeIf { resend })) {
            is AuthResult.CodeSent -> {
                verificationId = result.verificationId
                resendToken = result.resendToken
                _state.update {
                    it.copy(step = SignInStep.Code, busy = false, code = "")
                }
                startCountdown()
            }
            // The handset read the SMS before the user saw the screen.
            is AuthResult.Verified -> finish(result.user)
            is AuthResult.Failed -> fail(result)
        }
    }

    fun verify() {
        val current = _state.value
        if (!current.canVerify) return
        val id = verificationId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, problem = null) }
            when (val result = auth.verify(id, current.code)) {
                is AuthResult.Verified -> finish(result.user)
                is AuthResult.Failed -> fail(result)
                // A code request cannot come back from verifying one.
                is AuthResult.CodeSent -> fail(
                    AuthResult.Failed(AuthFailure.Unknown, "Unexpected answer")
                )
            }
        }
    }

    private suspend fun finish(user: SignedIn) {
        countdown?.cancel()
        onSignedIn(user)
        _state.update { it.copy(busy = false, problem = null) }
    }

    private fun fail(result: AuthResult.Failed) {
        _state.update {
            it.copy(
                busy = false,
                problem = result.reason,
                problemDetail = result.message,
                // A bad number is not something to retype a code over; send them back.
                step = if (result.reason == AuthFailure.BadNumber) SignInStep.Phone else it.step,
                code = if (result.reason == AuthFailure.WrongCode) "" else it.code,
            )
        }
    }

    /**
     * Counts down to when another code may be asked for.
     *
     * Not a cosmetic delay: every resend is an SMS somebody pays for, and a button
     * that can be hammered is a button that gets hammered when a message is slow.
     */
    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var remaining = RESEND_SECONDS
            while (remaining > 0) {
                _state.update { it.copy(resendInSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(resendInSeconds = 0) }
        }
    }

    private companion object {
        const val RESEND_SECONDS = 30
    }
}
