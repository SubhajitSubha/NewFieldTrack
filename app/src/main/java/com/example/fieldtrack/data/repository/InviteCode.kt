package com.example.fieldtrack.data.repository

/**
 * Crockford's base-32 alphabet: the digits, then the letters without I, L, O and U.
 *
 * Not invented here, deliberately. A code gets read down a bad line to somebody
 * standing next to a running tractor, or copied off a cracked screen by a driver who
 * does not read English comfortably, and every character that can be mistaken for
 * another turns a working code into a wrong one with no way to tell which. Picking
 * the excluded set by eye is harder than it looks — the obvious one keeps 2 beside Z,
 * 6 beside G and U beside V. This set drops I and L so 1 is unambiguous, drops O so 0
 * is, and drops U both because it reads as V and because leaving it out keeps codes
 * from spelling anything unfortunate.
 */
private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

/** How many characters a code has. Six of this alphabet is a billion codes. */
const val INVITE_CODE_LENGTH = 6

/**
 * Makes a code from a source of randomness.
 *
 * Takes the randomness rather than reaching for it, so a test can pin the code and
 * the real caller hands over a [java.security.SecureRandom]. A guessable invite code
 * would let anyone walk into somebody else's business.
 */
fun generateInviteCode(nextInt: (Int) -> Int): String =
    buildString { repeat(INVITE_CODE_LENGTH) { append(ALPHABET[nextInt(ALPHABET.length)]) } }

/**
 * Tidies what somebody typed into what the code actually is.
 *
 * Case, and the spaces or dashes a code gets read out in, are noise. So are the four
 * excluded letters, but only because this alphabet makes them unambiguous: an O can
 * only have been meant as a zero, and an I or an L as a one, because the letters
 * themselves are never issued. That is a correction, not a guess — which is the
 * difference between being kind about a typo and quietly sending a driver into
 * somebody else's business.
 */
fun normaliseInviteCode(input: String): String =
    input.uppercase()
        .filter { !it.isWhitespace() && it != '-' }
        .map {
            when (it) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> it
            }
        }
        .joinToString("")
        .take(INVITE_CODE_LENGTH)

/** Whether this could be a code at all, before any lookup is attempted. */
fun isWellFormedInviteCode(code: String): Boolean =
    code.length == INVITE_CODE_LENGTH && code.all { it in ALPHABET }
