package com.example.fieldtrack.document

private val ONES = arrayOf(
    "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)

private val TENS = arrayOf(
    "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
)

/** 0–99 in words. Everything above is built from this and a scale word. */
private fun underHundred(value: Int): String = when {
    value < 20 -> ONES[value]
    value % 10 == 0 -> TENS[value / 10]
    else -> "${TENS[value / 10]} ${ONES[value % 10]}"
}

/** 0–999, the group that sits under every Indian scale word. */
private fun underThousand(value: Int): String = when {
    value < 100 -> underHundred(value)
    value % 100 == 0 -> "${ONES[value / 100]} hundred"
    else -> "${ONES[value / 100]} hundred ${underHundred(value % 100)}"
}

/**
 * A rupee amount written out, the way it goes on the bottom of an Indian bill.
 *
 * Grouped in lakh and crore rather than millions, because that is how the figure is
 * read aloud here and the written line exists to be read aloud — it is what stops a
 * digit being argued over later.
 *
 * Returns "Rupees seven thousand four hundred only".
 */
fun rupeesInWords(amountRupees: Long): String {
    if (amountRupees == 0L) return "Rupees zero only"
    if (amountRupees < 0) return "Rupees ${words(-amountRupees)} in credit"
    return "Rupees ${words(amountRupees)} only"
}

private fun words(amount: Long): String {
    val parts = mutableListOf<String>()
    var left = amount

    val crore = (left / 10_000_000).toInt()
    if (crore > 0) {
        // A crore count can itself run past a thousand, so it recurses.
        parts += "${if (crore >= 1000) words(crore.toLong()) else underThousand(crore)} crore"
        left %= 10_000_000
    }
    val lakh = (left / 100_000).toInt()
    if (lakh > 0) {
        parts += "${underHundred(lakh)} lakh"
        left %= 100_000
    }
    val thousand = (left / 1_000).toInt()
    if (thousand > 0) {
        parts += "${underHundred(thousand)} thousand"
        left %= 1_000
    }
    if (left > 0) parts += underThousand(left.toInt())

    // Lower case: this is read as part of "Rupees ... only", never on its own.
    return parts.joinToString(" ")
}
