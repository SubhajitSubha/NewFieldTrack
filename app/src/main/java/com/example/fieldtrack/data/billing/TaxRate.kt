package com.example.fieldtrack.data.billing

/**
 * A sales tax rate, in hundredths of a percent (1600 is 16%).
 *
 * The business's prices are taken to include it: nothing is added to a bill, and the bill
 * says how much of its total is tax. That is how most small contractors quote, and it keeps
 * what the business earned apart from what it collected for the tax office without a
 * second ledger. A business that adds tax on top quotes the tax-inclusive price.
 */
object TaxRate {

    /** The most a rate may be: nobody charges more than the whole price again. */
    private const val MAX_BPS = 10_000

    /** `16`, `7.5` and `7.25` as basis points, or null for nothing usable, nought or over a hundred. */
    fun parse(text: String): Int? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1 || !cleaned.all { it.isDigit() || it == '.' }) return null
        val whole = cleaned.substringBefore('.').ifEmpty { "0" }.toIntOrNull() ?: return null
        val fraction = cleaned.substringAfter('.', "").padEnd(2, '0').take(2).toIntOrNull() ?: return null
        val bps = whole * 100 + fraction
        return bps.takeIf { it in 1..MAX_BPS }
    }

    /** A rate as it sits in a field: `16`, `7.5`, `7.25`. */
    fun text(bps: Int): String {
        val whole = bps / 100
        val fraction = bps % 100
        return if (fraction == 0) whole.toString()
        else "$whole.${fraction.toString().padStart(2, '0').trimEnd('0')}"
    }

    /**
     * The part of [grossMinor] that is tax when the price already includes it at [bps],
     * rounded to the business's own step so it adds up on paper.
     */
    fun includedIn(grossMinor: Long, bps: Int, stepMinor: Long): Long {
        if (bps <= 0 || grossMinor <= 0) return 0
        val step = stepMinor.coerceAtLeast(1)
        val exact = grossMinor.toDouble() * bps / (10_000 + bps)
        return Math.round(exact / step) * step
    }
}
