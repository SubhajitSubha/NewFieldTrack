package com.example.fieldtrack.data.auth

/** Who the server believes is using this phone. */
data class SignedIn(
    val uid: String,
    /** E.164, as the provider verified it: "+919876543210". */
    val phone: String?,
)

/** What came back from asking for a code, or from sending one. */
sealed interface AuthResult {
    /** A code is on its way; hold [token] to resend without starting over. */
    data class CodeSent(val verificationId: String, val resendToken: Any?) : AuthResult

    /**
     * Signed in without the user typing anything.
     *
     * Android reads the SMS itself on most handsets, and a test number skips the
     * message entirely. Both land here, so the OTP screen must be ready to be
     * overtaken before the person has touched the keypad.
     */
    data class Verified(val user: SignedIn) : AuthResult

    /** The number was refused, the code was wrong, or the network was not there. */
    data class Failed(val reason: AuthFailure, val message: String) : AuthResult
}

/**
 * Why a sign-in did not happen, in the terms the screen has to explain.
 *
 * Separated from the provider's own codes because the screen needs to decide what to
 * say and what to offer next, and "17010" is not an answer to either.
 */
enum class AuthFailure {
    /** The number is not a real one. Ask again, do not retry. */
    BadNumber,

    /** The six digits were wrong. Let them type again. */
    WrongCode,

    /** The code has gone stale. They need a new one. */
    CodeExpired,

    /** Too many attempts from this number or device. Waiting is the only fix. */
    TooManyAttempts,

    /** No network, or the provider was unreachable. Worth retrying as-is. */
    Unreachable,

    /** Anything else, including a project that is not set up for phone sign-in. */
    Unknown,
}

/**
 * Phone sign-in, behind an interface.
 *
 * Firebase's phone flow is callbacks, a static singleton and an Activity reference,
 * none of which a test can hold. Everything above this line — the countdown, what
 * counts as a valid number, what the screen says when a code expires — is decided in
 * ordinary code and tested with [FakePhoneAuth].
 */
interface PhoneAuth {

    /** The person already signed in on this device, if any. */
    fun current(): SignedIn?

    /**
     * Asks for a code on [phoneE164].
     *
     * May come back [AuthResult.Verified] without a code ever being typed: Android
     * auto-retrieves the SMS on most handsets.
     */
    suspend fun sendCode(phoneE164: String, resendToken: Any? = null): AuthResult

    /** Finishes the sign-in with the six digits the user typed. */
    suspend fun verify(verificationId: String, code: String): AuthResult

    suspend fun signOut()
}

/** A number India's carriers would actually route: ten digits, not starting 0–5. */
fun isPlausibleIndianMobile(digits: String): Boolean =
    digits.length == 10 && digits.first() in '6'..'9'

/**
 * The ten digits out of whatever was typed, pasted or dictated.
 *
 * People hand over their number with the country code on it — "+91 98765 43210" is
 * how it is written on a visiting card and how WhatsApp shows it — into a field that
 * already says +91. Taking the first ten digits of that gives 9198765432, which is a
 * perfectly plausible number belonging to somebody else, and the code goes to them.
 * A twelve-digit string beginning 91 can only be the country code, since no Indian
 * mobile is twelve digits, so it is safe to drop.
 */
fun indianMobileDigits(input: String): String {
    val digits = input.filter { it.isDigit() }
    val national = when {
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        // A leading zero is how the number is dialled from a landline.
        digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }
    return national.take(10)
}

/** "9876543210" as the provider wants it. */
fun toE164India(digits: String): String = "+91$digits"

/** "+919876543210" as a person reads it: "+91 98765 43210". */
fun formatIndianPhone(e164: String): String {
    val digits = e164.removePrefix("+91").filter { it.isDigit() }
    if (digits.length != 10) return e164
    return "+91 ${digits.take(5)} ${digits.drop(5)}"
}
