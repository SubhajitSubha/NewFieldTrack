package com.example.fieldtrack.data.auth

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Phone numbers from any country, kept in one shape.
 *
 * A new number is stored in E.164 (`+919876543210`, `+254712345678`): the country code
 * with a plus, then the national digits. That is what the sign-in provider wants and
 * what makes two spellings of one number equal.
 *
 * Numbers written before countries existed are ten bare digits, all Indian, and are
 * still on phones and servers. They are never rewritten; they are *read* as `+91`, so
 * nobody's customer list changes under them and an old number still matches the same
 * number typed today.
 *
 * Only calling codes are held here, which is a fact about the world. Whether a number
 * is a real one is the carrier's to say; the app only refuses what could not possibly
 * be one.
 */
object PhoneNumbers {

    /** The code every number written before this existed was in. */
    const val LEGACY_DIAL = "91"

    /** The longest a number may be, country code included, by the international standard. */
    private const val MAX_DIGITS = 15

    private val DIAL_CODES: Map<String, String> = mapOf(
        "AD" to "376", "AE" to "971", "AF" to "93", "AG" to "1", "AI" to "1", "AL" to "355",
        "AM" to "374", "AO" to "244", "AR" to "54", "AS" to "1", "AT" to "43", "AU" to "61",
        "AW" to "297", "AX" to "358", "AZ" to "994", "BA" to "387", "BB" to "1", "BD" to "880",
        "BE" to "32", "BF" to "226", "BG" to "359", "BH" to "973", "BI" to "257", "BJ" to "229",
        "BL" to "590", "BM" to "1", "BN" to "673", "BO" to "591", "BQ" to "599", "BR" to "55",
        "BS" to "1", "BT" to "975", "BW" to "267", "BY" to "375", "BZ" to "501", "CA" to "1",
        "CC" to "61", "CD" to "243", "CF" to "236", "CG" to "242", "CH" to "41", "CI" to "225",
        "CK" to "682", "CL" to "56", "CM" to "237", "CN" to "86", "CO" to "57", "CR" to "506",
        "CU" to "53", "CV" to "238", "CW" to "599", "CX" to "61", "CY" to "357", "CZ" to "420",
        "DE" to "49", "DJ" to "253", "DK" to "45", "DM" to "1", "DO" to "1", "DZ" to "213",
        "EC" to "593", "EE" to "372", "EG" to "20", "EH" to "212", "ER" to "291", "ES" to "34",
        "ET" to "251", "FI" to "358", "FJ" to "679", "FK" to "500", "FM" to "691", "FO" to "298",
        "FR" to "33", "GA" to "241", "GB" to "44", "GD" to "1", "GE" to "995", "GF" to "594",
        "GG" to "44", "GH" to "233", "GI" to "350", "GL" to "299", "GM" to "220", "GN" to "224",
        "GP" to "590", "GQ" to "240", "GR" to "30", "GT" to "502", "GU" to "1", "GW" to "245",
        "GY" to "592", "HK" to "852", "HN" to "504", "HR" to "385", "HT" to "509", "HU" to "36",
        "ID" to "62", "IE" to "353", "IL" to "972", "IM" to "44", "IN" to "91", "IO" to "246",
        "IQ" to "964", "IR" to "98", "IS" to "354", "IT" to "39", "JE" to "44", "JM" to "1",
        "JO" to "962", "JP" to "81", "KE" to "254", "KG" to "996", "KH" to "855", "KI" to "686",
        "KM" to "269", "KN" to "1", "KP" to "850", "KR" to "82", "KW" to "965", "KY" to "1",
        "KZ" to "7", "LA" to "856", "LB" to "961", "LC" to "1", "LI" to "423", "LK" to "94",
        "LR" to "231", "LS" to "266", "LT" to "370", "LU" to "352", "LV" to "371", "LY" to "218",
        "MA" to "212", "MC" to "377", "MD" to "373", "ME" to "382", "MF" to "590", "MG" to "261",
        "MH" to "692", "MK" to "389", "ML" to "223", "MM" to "95", "MN" to "976", "MO" to "853",
        "MP" to "1", "MQ" to "596", "MR" to "222", "MS" to "1", "MT" to "356", "MU" to "230",
        "MV" to "960", "MW" to "265", "MX" to "52", "MY" to "60", "MZ" to "258", "NA" to "264",
        "NC" to "687", "NE" to "227", "NF" to "672", "NG" to "234", "NI" to "505", "NL" to "31",
        "NO" to "47", "NP" to "977", "NR" to "674", "NU" to "683", "NZ" to "64", "OM" to "968",
        "PA" to "507", "PE" to "51", "PF" to "689", "PG" to "675", "PH" to "63", "PK" to "92",
        "PL" to "48", "PM" to "508", "PR" to "1", "PS" to "970", "PT" to "351", "PW" to "680",
        "PY" to "595", "QA" to "974", "RE" to "262", "RO" to "40", "RS" to "381", "RU" to "7",
        "RW" to "250", "SA" to "966", "SB" to "677", "SC" to "248", "SD" to "249", "SE" to "46",
        "SG" to "65", "SH" to "290", "SI" to "386", "SJ" to "47", "SK" to "421", "SL" to "232",
        "SM" to "378", "SN" to "221", "SO" to "252", "SR" to "597", "SS" to "211", "ST" to "239",
        "SV" to "503", "SX" to "1", "SY" to "963", "SZ" to "268", "TC" to "1", "TD" to "235",
        "TG" to "228", "TH" to "66", "TJ" to "992", "TK" to "690", "TL" to "670", "TM" to "993",
        "TN" to "216", "TO" to "676", "TR" to "90", "TT" to "1", "TV" to "688", "TW" to "886",
        "TZ" to "255", "UA" to "380", "UG" to "256", "US" to "1", "UY" to "598", "UZ" to "998",
        "VA" to "39", "VC" to "1", "VE" to "58", "VG" to "1", "VI" to "1", "VN" to "84",
        "VU" to "678", "WF" to "681", "WS" to "685", "XK" to "383", "YE" to "967", "YT" to "262",
        "ZA" to "27", "ZM" to "260", "ZW" to "263",
    )

    /** Every calling code there is. No code starts another, so a number can be read off the front. */
    private val DIALS: Set<String> = DIAL_CODES.values.toSet()

    /** A country and the code its numbers begin with. */
    data class Country(val iso: String, val dial: String, val name: String)

    /** The calling code of a country, or null where there is none on record. */
    fun dialOf(iso: String?): String? = iso?.uppercase(Locale.ROOT)?.let { DIAL_CODES[it] }

    /** Every country, named in the person's language, the one they are in first and then by name. */
    fun countries(first: String?, locale: Locale = Locale.getDefault()): List<Country> {
        val all = DIAL_CODES.map { (iso, dial) ->
            Country(iso, dial, Locale("", iso).getDisplayCountry(locale).ifBlank { iso })
        }.sortedBy { it.name.lowercase(locale) }
        val front = first?.uppercase(Locale.ROOT)?.let { f -> all.filter { it.iso == f } }.orEmpty()
        return front + all.filter { it !in front }
    }

    /** The code and national digits of a stored number, bare digits being read as Indian. */
    fun split(stored: String?): Pair<String, String> {
        val raw = stored?.trim().orEmpty()
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return LEGACY_DIAL to ""
        if (!raw.startsWith("+")) return LEGACY_DIAL to digits
        val dial = (1..3).map { digits.take(it) }.firstOrNull { it in DIALS }
            ?: return LEGACY_DIAL to digits
        return dial to digits.drop(dial.length)
    }

    /** `+` and the code and the digits, or empty when there are no digits. */
    fun e164(dial: String, national: String): String {
        val digits = national.filter { it.isDigit() }
        return if (digits.isEmpty()) "" else "+${dial.filter { it.isDigit() }}$digits"
    }

    /** One spelling for one number, so two spellings can be compared. Empty for none. */
    fun normalize(stored: String?): String {
        val (dial, national) = split(stored)
        return e164(dial, national)
    }

    /**
     * What may be typed into the digits box after a country code: digits only, and no
     * more than a number can have. For India a pasted `+91 98765 43210` or `098765 43210`
     * is cut down to the ten digits, because taking the first ten of a pasted number
     * with its code gives a perfectly good number belonging to somebody else.
     */
    fun nationalDigits(input: String, dial: String): String {
        val digits = input.filter { it.isDigit() }
        if (dial == LEGACY_DIAL) return indianMobileDigits(input)
        return digits.take(MAX_DIGITS - dial.length)
    }

    /** Whether this could be a number somebody can be called on. Only what cannot be is refused. */
    fun isPlausible(dial: String, national: String): Boolean {
        val digits = national.filter { it.isDigit() }
        if (dial == LEGACY_DIAL) return isPlausibleIndianMobile(digits)
        return digits.length in 6..(MAX_DIGITS - dial.length)
    }

    /** A number as a person reads it: with its country code and spaced the local way where the platform knows how. */
    fun display(stored: String?, locale: Locale = Locale.getDefault()): String? {
        val e164 = normalize(stored).ifEmpty { return null }
        val formatted = runCatching {
            PhoneNumberUtils.formatNumber(e164, locale.country.ifBlank { "US" })
        }.getOrNull()
        if (!formatted.isNullOrBlank() && formatted != e164) return formatted
        val (dial, national) = split(e164)
        // Where the platform cannot say (a bare JVM, an old handset), the one spacing
        // most users here read without thinking: five and five for an Indian mobile.
        if (dial == LEGACY_DIAL && national.length == 10) return "+91 ${national.take(5)} ${national.drop(5)}"
        return "+$dial $national"
    }
}
