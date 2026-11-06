package com.example.fieldtrack.data.region

import android.content.Context
import android.telephony.TelephonyManager
import com.example.fieldtrack.data.db.MeasureUnit
import java.util.Currency
import java.util.Locale

/**
 * What the phone says about where its owner works, so that nobody is asked what the
 * phone already knows.
 *
 * Nothing here needs a permission and nothing leaves the phone: the SIM's country, the
 * network's country and the language settings are all readable by any app. They are
 * only a *guess* — a SIM bought at home and used abroad, a phone on a roaming network —
 * which is why onboarding shows the result and lets it be changed, and why only what
 * is costly to get wrong (the currency and the land unit) is ever confirmed.
 */
object Region {

    /** The first guess for a new business. */
    data class Guess(
        /** ISO 3166 country, upper case, or null when the phone gave no hint at all. */
        val country: String?,
        val currencyCode: String,
        val landUnit: MeasureUnit,
    )

    /**
     * The country the phone belongs to: the SIM first, because it is where the owner
     * lives; then the network, then the language setting's region.
     */
    fun countryOf(simCountry: String?, networkCountry: String?, locale: Locale = Locale.getDefault()): String? =
        listOf(simCountry, networkCountry, locale.country)
            .firstOrNull { !it.isNullOrBlank() && it.length == 2 }
            ?.uppercase(Locale.ROOT)

    /** The currency a country trades in, or null where the platform does not know. */
    fun currencyOf(country: String?): String? {
        if (country == null) return null
        return runCatching { Currency.getInstance(Locale("", country)).currencyCode }.getOrNull()
    }

    /** What land is usually measured in where the guess lands. Settable: it is a habit, not a law. */
    fun landUnitOf(country: String?): MeasureUnit = when (country) {
        // Hectares are added with the other units; until a business picks, the acre is the
        // measure most of the countries that still use one of their own agree on.
        else -> MeasureUnit.Acre
    }

    fun guess(simCountry: String?, networkCountry: String?, locale: Locale = Locale.getDefault()): Guess {
        val country = countryOf(simCountry, networkCountry, locale)
        return Guess(
            country = country,
            currencyCode = currencyOf(country) ?: "USD",
            landUnit = landUnitOf(country),
        )
    }

    /** The guess for this phone. */
    fun detect(context: Context): Guess {
        val telephony = runCatching {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }.getOrNull()
        return guess(
            simCountry = runCatching { telephony?.simCountryIso }.getOrNull(),
            networkCountry = runCatching { telephony?.networkCountryIso }.getOrNull(),
        )
    }

    /** A currency's name in the person's own language, or its code where the phone has none. */
    fun nameOf(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code)

    /** A currency as a person picks it: the code and its name in their own language. */
    data class CurrencyOption(val code: String, val name: String, val symbol: String)

    /**
     * Every currency the platform knows, the guessed one first and then the ones most
     * farmers will be looking for, then the rest by name.
     */
    fun currencies(first: String?, locale: Locale = Locale.getDefault()): List<CurrencyOption> {
        val all = runCatching { Currency.getAvailableCurrencies() }.getOrDefault(emptySet())
            .filter { it.currencyCode.length == 3 }
            .map {
                CurrencyOption(
                    code = it.currencyCode,
                    name = it.getDisplayName(locale),
                    symbol = com.example.fieldtrack.designsystem.format.MoneyStyle.symbolOf(it.currencyCode, locale),
                )
            }
        val byCode = all.associateBy { it.code }
        val front = (listOfNotNull(first) + COMMON).distinct().mapNotNull { byCode[it] }
        val frontCodes = front.map { it.code }.toSet()
        return front + all.filter { it.code !in frontCodes }.sortedBy { it.name.lowercase(locale) }
    }

    private val COMMON = listOf(
        "INR", "USD", "EUR", "GBP", "BRL", "IDR", "PKR", "BDT", "NPR", "LKR", "KES", "NGN", "ZAR",
        "UGX", "TZS", "ETB", "PHP", "VND", "THB", "MXN", "ARS", "AUD", "CAD", "TRY", "EGP",
    )
}
