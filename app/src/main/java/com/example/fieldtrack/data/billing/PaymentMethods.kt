package com.example.fieldtrack.data.billing

import com.example.fieldtrack.data.db.PaymentMethod

/**
 * Which ways of paying a business offers.
 *
 * The list on screen is the business's own, so a Kenyan contractor is not shown UPI and an
 * Indian one is not asked about mobile money. Cash is always there, because it is how the
 * first payment is nearly always made and a list without it would have nowhere to put one.
 * A payment already recorded in a method the business no longer offers still reads as it
 * was; only what is offered for the next one changes.
 */
object PaymentMethods {

    private val MOBILE_MONEY = setOf(
        "KES", "TZS", "UGX", "RWF", "GHS", "ZMW", "MWK", "XOF", "XAF", "ETB", "BDT", "PKR",
        "PHP", "IDR", "MMK", "KHR", "LAK", "NGN",
    )

    /**
     * What is usual where a currency is used, and "Other" last for the way nobody here
     * thought of, which the payer then names.
     */
    fun defaultsFor(currencyCode: String): List<PaymentMethod> = when {
        currencyCode == "INR" ->
            listOf(PaymentMethod.Cash, PaymentMethod.Upi, PaymentMethod.Bank, PaymentMethod.Cheque)
        currencyCode in MOBILE_MONEY ->
            listOf(PaymentMethod.Cash, PaymentMethod.MobileMoney, PaymentMethod.Bank)
        else ->
            listOf(PaymentMethod.Cash, PaymentMethod.Bank, PaymentMethod.Card, PaymentMethod.Cheque)
    } + PaymentMethod.Other

    /** The methods a business offers: what it chose, else what is usual for its money. */
    fun offered(stored: String?, currencyCode: String): List<PaymentMethod> {
        val chosen = stored?.split(',')
            ?.mapNotNull { name -> PaymentMethod.entries.firstOrNull { it.name == name.trim() } }
            ?.distinct()
            .orEmpty()
        val list = chosen.ifEmpty { defaultsFor(currencyCode) }
        // Cash first and "Other" last are always there: the first payment is nearly always cash,
        // and a way nobody listed still has to go somewhere, under the payer's own name.
        val withCash = if (PaymentMethod.Cash in list) list else listOf(PaymentMethod.Cash) + list
        return withCash.filter { it != PaymentMethod.Other } + PaymentMethod.Other
    }

    /** "Other" is always offered, so it is never part of what a business chose. */
    fun encode(methods: List<PaymentMethod>): String =
        methods.filter { it != PaymentMethod.Other }.joinToString(",") { it.name }
}
