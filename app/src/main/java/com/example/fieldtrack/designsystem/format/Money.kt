package com.example.fieldtrack.designsystem.format

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * How money is written, in one place.
 *
 * Every amount in the app is a whole number of the currency's *minor unit* (paise,
 * cents, pence), never a decimal. What this class decides is how that number is
 * shown and typed:
 *
 * - [currencyCode] and [symbol]: which currency the business keeps its books in.
 * - [digits]: how many decimals the currency has (rupee 2, yen 0, dinar 3), taken from
 *   the platform's own currency data.
 * - [stepMinor]: the smallest amount a bill is rounded to. A business that never
 *   deals in paise keeps 100 and sees no decimals anywhere; one that does keeps 1.
 * - [grouping]: how the digits are grouped, which is the person's habit and not the
 *   business's. Twelve lakh is `12,00,000` to one reader and `1,200,000` to another,
 *   and each reads the other wrong at a glance.
 *
 * Grouping is spelled out here rather than left to a locale, because `DecimalFormat`
 * cannot express three-then-twos and device locale data differs between handsets;
 * the most important number in the app must read the same on all of them.
 */
enum class DigitGrouping {
    /** 12,00,000: the last three digits, then twos. Lakh and crore. */
    Indian,

    /** 1,200,000: threes. Thousand, million, billion. */
    International;

    companion object {
        /** Countries that group in lakh and crore. */
        private val LAKH = setOf("IN", "PK", "BD", "NP", "LK", "MM")

        fun forLocale(locale: Locale): DigitGrouping =
            if (locale.country in LAKH) Indian else International
    }
}

data class MoneyStyle(
    val currencyCode: String = "INR",
    val symbol: String = "₹",
    val digits: Int = 2,
    val stepMinor: Long = 100,
    val grouping: DigitGrouping = DigitGrouping.Indian,
    val decimalSeparator: Char = '.',
    val groupSeparator: Char = ',',
    val symbolAfter: Boolean = false,
) {
    /** The same money written with another person's grouping, for when they change their setting. */
    fun withGrouping(grouping: DigitGrouping, locale: Locale = Locale.getDefault()): MoneyStyle = copy(
        grouping = grouping,
        groupSeparator = if (grouping == DigitGrouping.Indian) ',' else DecimalFormatSymbols.getInstance(locale).groupingSeparator,
    )

    /** How many minor units make one major unit: 100 for rupees, 1 for yen. */
    val unit: Long = pow10(digits)

    /**
     * Whether decimals are shown and typed. Only when the currency has them *and* the
     * business bills in fractions of them; a rupee business that never deals in paise
     * sees `₹12,400`, not `₹12,400.00`.
     */
    val showsDecimals: Boolean get() = digits > 0 && stepMinor < unit

    companion object {
        /** Currencies whose small coin is not used in trade, so bills round to whole units. */
        private val WHOLE_BY_HABIT = setOf("INR", "PKR", "BDT", "LKR", "NPR", "MMK", "IDR", "VND", "KRW", "JPY")

        private val SYMBOLS = mapOf(
            "INR" to "₹", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
            "CNY" to "¥", "KRW" to "₩", "PKR" to "Rs", "BDT" to "৳", "LKR" to "Rs",
            "NPR" to "Rs", "BRL" to "R$", "MXN" to "$", "ARS" to "$", "ZAR" to "R",
            "KES" to "KSh", "NGN" to "₦", "UGX" to "USh", "TZS" to "TSh", "ETB" to "Br",
            "PHP" to "₱", "IDR" to "Rp", "VND" to "₫", "THB" to "฿", "TRY" to "₺",
            "RUB" to "₽", "UAH" to "₴", "PLN" to "zł", "EGP" to "E£", "AUD" to "A$",
            "CAD" to "C$", "NZD" to "NZ$", "CHF" to "CHF",
        )

        /** The plain step for a currency: whole units for those that never use the coin. */
        fun defaultStepMinor(currencyCode: String): Long {
            val digits = digitsOf(currencyCode)
            return if (currencyCode in WHOLE_BY_HABIT) pow10(digits) else 1L
        }

        fun digitsOf(currencyCode: String): Int =
            runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
                .getOrDefault(2).coerceAtLeast(0)

        fun symbolOf(currencyCode: String, locale: Locale = Locale.getDefault()): String =
            SYMBOLS[currencyCode]
                ?: runCatching { Currency.getInstance(currencyCode).getSymbol(locale) }
                    .getOrDefault(currencyCode)

        /**
         * The style for a business: its currency and rounding, written in the way the
         * person reading it is used to.
         */
        fun of(
            currencyCode: String,
            stepMinor: Long,
            grouping: DigitGrouping = DigitGrouping.forLocale(Locale.getDefault()),
            locale: Locale = Locale.getDefault(),
        ): MoneyStyle {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            return MoneyStyle(
                currencyCode = currencyCode,
                symbol = symbolOf(currencyCode, locale),
                digits = digitsOf(currencyCode),
                stepMinor = stepMinor.coerceAtLeast(1),
                grouping = grouping,
                decimalSeparator = symbols.decimalSeparator,
                groupSeparator = if (grouping == DigitGrouping.Indian) ',' else symbols.groupingSeparator,
                symbolAfter = symbolFollows(locale),
            )
        }

        private fun symbolFollows(locale: Locale): Boolean = runCatching {
            val pattern = (NumberFormat.getCurrencyInstance(locale) as DecimalFormat).toPattern()
            pattern.isNotEmpty() && pattern[0] == '#'
        }.getOrDefault(false)

        fun pow10(n: Int): Long {
            var v = 1L
            repeat(n) { v *= 10 }
            return v
        }
    }
}

/**
 * The style the screen in front of the user is showing money in: the active business's
 * currency with this person's habits.
 *
 * Held as Compose state so a switch of business redraws every figure at once. Code that
 * writes for one particular business, a statement above all, passes that business's
 * style explicitly and never reads this: a bill must not change currency because the
 * user switched business while it was being made.
 */
object Money {
    var style: MoneyStyle by mutableStateOf(MoneyStyle())

    /**
     * How this person groups digits, from the setting or else from the phone's own
     * region. Business styles are built with it, so a statement made on this phone
     * reads the way this person reads.
     */
    var grouping: DigitGrouping by mutableStateOf(DigitGrouping.forLocale(Locale.getDefault()))

    /** The ways of paying the business on screen offers, for the next payment. */
    var methods: List<com.example.fieldtrack.data.db.PaymentMethod> by mutableStateOf(
        com.example.fieldtrack.data.billing.PaymentMethods.defaultsFor("INR")
    )
}

private const val MINUS = "−" // real minus sign, not a hyphen

const val THOUSAND = 1_000L
const val LAKH = 100_000L
const val CRORE = 10_000_000L

private fun group(digits: String, style: MoneyStyle): String {
    if (digits.length <= 3) return digits
    val sep = style.groupSeparator
    val lastThree = digits.takeLast(3)
    val rest = digits.dropLast(3)
    val size = if (style.grouping == DigitGrouping.Indian) 2 else 3
    val grouped = rest.reversed().chunked(size).joinToString(sep.toString()).reversed()
    return "$grouped$sep$lastThree"
}

/** The number with grouping and, if this business bills in fractions, its decimals. */
private fun number(amountMinor: Long, style: MoneyStyle): String {
    val unit = style.unit
    if (!style.showsDecimals) {
        val whole = if (unit == 1L) abs(amountMinor) else (abs(amountMinor) + unit / 2) / unit
        return group(whole.toString(), style)
    }
    val a = abs(amountMinor)
    val fraction = (a % unit).toString().padStart(style.digits, '0')
    return group((a / unit).toString(), style) + style.decimalSeparator + fraction
}

private fun withSymbol(body: String, negative: Boolean, style: MoneyStyle): String {
    val signed = if (negative) MINUS else ""
    return if (style.symbolAfter) "$signed$body ${style.symbol}" else "$signed${style.symbol}$body"
}

/** `1240000` to `"₹12,400"` for a rupee business; negative amounts use a true minus sign. */
fun money(amountMinor: Long, style: MoneyStyle = Money.style): String {
    val negative = amountMinor < 0 && number(amountMinor, style).any { it in '1'..'9' }
    return withSymbol(number(amountMinor, style), negative, style)
}

fun money(amountMinor: Double, style: MoneyStyle = Money.style): String =
    money(amountMinor.roundToLong(), style)

/** The same number without the symbol, for places that render the symbol separately. */
fun moneyPlain(amountMinor: Long, style: MoneyStyle = Money.style): String {
    val body = number(amountMinor, style)
    return if (amountMinor < 0 && body.any { it in '1'..'9' }) MINUS + body else body
}

/**
 * `18430000` to `"₹1.8L"` (or `"$1.8M"`). For stat tiles and chart labels only, never on
 * a bill, a receipt or a ledger line, where the exact figure is the whole point.
 */
fun moneyCompact(amountMinor: Long, style: MoneyStyle = Money.style): String {
    val major = abs(amountMinor).toDouble() / style.unit
    val negative = amountMinor < 0 && major >= 0.5
    val indian = style.grouping == DigitGrouping.Indian
    val body = when {
        indian && major >= CRORE -> trimZero(major / CRORE) + "Cr"
        indian && major >= LAKH -> trimZero(major / LAKH) + "L"
        !indian && major >= 1_000_000_000 -> trimZero(major / 1_000_000_000) + "B"
        !indian && major >= 1_000_000 -> trimZero(major / 1_000_000) + "M"
        major >= 10 * THOUSAND -> trimZero(major / THOUSAND) + "k"
        else -> group(((abs(amountMinor) + style.unit / 2) / style.unit).toString(), style)
    }
    return withSymbol(body, negative, style)
}

private fun trimZero(value: Double): String {
    val oneDecimal = (value * 10).roundToLong() / 10.0
    return if (oneDecimal % 1.0 == 0.0) oneDecimal.toLong().toString()
    else String.format(Locale.US, "%.1f", oneDecimal)
}

/**
 * Money going in and out of a text field.
 *
 * The field shows a plain number the person types (`1500`, or `12.50` where the
 * business bills in fractions) and the app keeps minor units. Every amount typed
 * anywhere goes through here, so there is one rule for how many decimals are allowed
 * and one place that turns text into the number that is stored.
 */
object MoneyInput {
    /** Digits before the point. Eight is far past any real bill and stops a slipped thumb. */
    const val MAX_WHOLE_DIGITS = 8

    /**
     * What a field keeps of whatever was typed or pasted.
     *
     * The last point or comma is the decimal separator where the business bills in
     * fractions, and everything before it is digit grouping, so `1,500.50` pasted
     * reads as fifteen hundred and fifty, not one and a half. The one exception is a
     * comma followed by exactly three digits (`1,500`), which is a thousands mark.
     * A business that only deals in whole units has no decimals: a point ends the
     * number and commas are ignored.
     */
    fun clean(text: String, style: MoneyStyle = Money.style): String {
        val sep = maxOf(text.lastIndexOf('.'), text.lastIndexOf(','))
        var wholeText = text
        var fractionText: String? = null
        if (sep >= 0) {
            val after = text.substring(sep + 1)
            val groupingComma = text[sep] == ',' && after.count { it.isDigit() } == 3
            if (style.showsDecimals && !groupingComma) {
                wholeText = text.substring(0, sep)
                fractionText = after.filter { it.isDigit() }.take(style.digits)
            } else if (!style.showsDecimals && text[sep] == '.') {
                wholeText = text.substring(0, sep)
            }
        }
        val digits = wholeText.filter { it.isDigit() }
        val whole = digits.trimStart('0').ifEmpty { if (digits.isNotEmpty()) "0" else "" }
            .take(MAX_WHOLE_DIGITS)
        return when {
            fractionText == null -> whole
            else -> (whole.ifEmpty { "0" }) + "." + fractionText
        }
    }

    /** The stored amount for what was typed, or null for nothing usable. */
    fun parse(text: String, style: MoneyStyle = Money.style): Long? {
        val cleaned = clean(text, style)
        if (cleaned.isEmpty()) return null
        val whole = cleaned.substringBefore('.').toLongOrNull() ?: return null
        val fraction = cleaned.substringAfter('.', "").padEnd(style.digits, '0')
            .take(style.digits).ifEmpty { "0" }.toLong()
        return whole * style.unit + fraction
    }

    /** An amount as it should sit in a field to be edited: plain, no symbol, no grouping. */
    fun text(amountMinor: Long, style: MoneyStyle = Money.style): String {
        val a = abs(amountMinor)
        if (!style.showsDecimals) return ((a + style.unit / 2) / style.unit).toString()
        val fraction = a % style.unit
        val whole = a / style.unit
        return if (fraction == 0L) whole.toString()
        else "$whole.${fraction.toString().padStart(style.digits, '0').trimEnd('0')}"
    }
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
