package com.example.fieldtrack.designsystem.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Rupee formatting, in the Indian grouping system.
 *
 * Western grouping would render twelve lakh as 1,200,000 — a contractor reads that
 * wrong at a glance. [rupees] produces 12,00,000, and [rupeesCompact] produces the
 * spoken shorthand (1.8L, 2.4Cr) used when a figure has to fit in a stat tile.
 *
 * Amounts are whole rupees. Paise do not appear anywhere in this business.
 */
/**
 * Indian digit grouping, done here rather than left to a locale.
 *
 * `NumberFormat` for en-IN groups in threes on a plain JVM, and `DecimalFormat`
 * cannot express the pattern at all — Java supports one grouping size, where this
 * system needs three then twos. Relying on whichever locale data a device happens to
 * ship would mean the most important number in the app is right on some handsets and
 * wrong on others, so it is spelled out.
 */
private fun groupIndian(amount: Long): String {
    val digits = amount.toString()
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val rest = digits.dropLast(3)
    val grouped = rest.reversed().chunked(2).joinToString(",").reversed()
    return "$grouped,$lastThree"
}

private const val RUPEE = "₹"
private const val MINUS = "−" // real minus sign, not a hyphen

const val THOUSAND = 1_000L
const val LAKH = 100_000L
const val CRORE = 10_000_000L

/** `12400` to `"₹12,400"`. Negative amounts use a true minus sign. */
fun rupees(amount: Long): String {
    val body = RUPEE + groupIndian(abs(amount))
    return if (amount < 0) MINUS + body else body
}

fun rupees(amount: Double): String = rupees(amount.roundToLong())

/** The same number without the symbol, for places that render ₹ separately. */
fun rupeesPlain(amount: Long): String = groupIndian(amount)

/**
 * `184300` to `"₹1.8L"`. For stat tiles and chart labels only — never on a bill, a
 * receipt or a ledger line, where the exact figure is the whole point.
 */
fun rupeesCompact(amount: Long): String {
    val sign = if (amount < 0) MINUS else ""
    val n = abs(amount)
    val body = when {
        n >= CRORE -> trimZero(n.toDouble() / CRORE) + "Cr"
        n >= LAKH -> trimZero(n.toDouble() / LAKH) + "L"
        n >= 10 * THOUSAND -> trimZero(n.toDouble() / THOUSAND) + "k"
        else -> groupIndian(n)
    }
    return sign + RUPEE + body
}

private fun trimZero(value: Double): String {
    val oneDecimal = (value * 10).roundToLong() / 10.0
    return if (oneDecimal % 1.0 == 0.0) oneDecimal.toLong().toString()
    else String.format(Locale.US, "%.1f", oneDecimal)
}

/**
 * Elapsed work time. Under an hour reads `"0:42"`; a running timer that needs seconds
 * passes [withSeconds].
 */
fun duration(totalSeconds: Long, withSeconds: Boolean = false): String {
    val s = abs(totalSeconds)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (withSeconds) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", hours, minutes)
}

/**
 * Area, trimmed of pointless decimals: `3.5` stays `"3.5"`, `4.0` becomes `"4"`.
 * The unit itself is a string resource, because acre/bigha/kanal/guntha is regional.
 */
fun quantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.1f", value)
