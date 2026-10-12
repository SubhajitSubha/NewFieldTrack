package com.example.fieldtrack.data.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Phone sign-in on Firebase.
 *
 * The callbacks are turned into a single suspending answer, which is the shape the
 * screen actually needs: ask, then wait for exactly one outcome. Firebase can call
 * back more than once for one request — auto-retrieval firing after a code was
 * already sent is normal — so every path is guarded to resume the coroutine once.
 */
class FirebasePhoneAuth(
    private val activityProvider: () -> Activity?,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : PhoneAuth {

    override fun current(): SignedIn? = auth.currentUser?.let {
        SignedIn(uid = it.uid, phone = it.phoneNumber)
    }

    override suspend fun sendCode(phoneE164: String, resendToken: Any?): AuthResult =
        suspendCancellableCoroutine { continuation ->
            val activity = activityProvider() ?: run {
                continuation.resume(
                    AuthResult.Failed(AuthFailure.Unknown, "The screen is no longer open")
                )
                return@suspendCancellableCoroutine
            }
            // Firebase calls these on more than one occasion for a single request.
            val answered = AtomicBoolean(false)
            fun answer(result: AuthResult) {
                if (answered.compareAndSet(false, true)) continuation.resume(result)
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                /** The handset read the SMS itself. Nothing to type. */
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { answer(AuthResult.Verified(it.user.asSignedIn())) }
                        .addOnFailureListener { answer(it.asFailure()) }
                }

                override fun onVerificationFailed(e: FirebaseException) = answer(e.asFailure())

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) = answer(AuthResult.CodeSent(verificationId, token))
            }

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(AUTO_RETRIEVE_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .apply {
                    (resendToken as? PhoneAuthProvider.ForceResendingToken)
                        ?.let { setForceResendingToken(it) }
                }
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    override suspend fun verify(verificationId: String, code: String): AuthResult =
        suspendCancellableCoroutine { continuation ->
            val credential = runCatching {
                PhoneAuthProvider.getCredential(verificationId, code)
            }.getOrElse {
                continuation.resume(AuthResult.Failed(AuthFailure.WrongCode, it.describe()))
                return@suspendCancellableCoroutine
            }
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    continuation.resume(AuthResult.Verified(it.user.asSignedIn()))
                }
                .addOnFailureListener { continuation.resume(it.asFailure()) }
        }

    override suspend fun signOut() = auth.signOut()

    private fun com.google.firebase.auth.FirebaseUser?.asSignedIn() = SignedIn(
        uid = this?.uid.orEmpty(),
        phone = this?.phoneNumber,
    )

    private companion object {
        /**
         * How long to wait for the handset to read the SMS before giving up on
         * auto-retrieval. Long enough for a slow network to deliver it; short enough
         * that somebody staring at an empty box is not left there.
         */
        const val AUTO_RETRIEVE_SECONDS = 60L
    }
}

/** Firebase's own words, sorted into the few cases a screen can do something about. */
private fun Throwable.asFailure(): AuthResult.Failed {
    val reason = when {
        this is FirebaseTooManyRequestsException -> AuthFailure.TooManyAttempts
        this is FirebaseAuthInvalidCredentialsException -> when {
            message?.contains("expired", ignoreCase = true) == true -> AuthFailure.CodeExpired
            message?.contains("phone number", ignoreCase = true) == true -> AuthFailure.BadNumber
            else -> AuthFailure.WrongCode
        }
        this is java.io.IOException -> AuthFailure.Unreachable
        message?.contains("network", ignoreCase = true) == true -> AuthFailure.Unreachable
        message?.contains("blocked", ignoreCase = true) == true -> AuthFailure.TooManyAttempts
        else -> AuthFailure.Unknown
    }
    return AuthResult.Failed(reason, describe())
}

private fun Throwable.describe(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
