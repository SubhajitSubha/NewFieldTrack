package com.example.fieldtrack.data.db

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Money written before amounts were kept in minor units, brought up to date.
 *
 * Until then every amount was a whole number of rupees under a name ending in
 * `Rupees`. Now every amount is a count of the currency's smallest unit under a
 * name ending in `Minor`. A phone updated today can still be handed:
 *
 *  - a server document written by an older build (or by this one before it was
 *    updated), and
 *  - a change that was queued on this very phone before the update.
 *
 * Both are rewritten here, by the same code, so there is exactly one account of
 * what the old shape was. The table and field names are spelled out rather than
 * taken from the current entities on purpose: this describes the past, and must not
 * change when a property is renamed in the future.
 *
 * Old documents were all in rupees, so each amount becomes the same amount times
 * 100, and a job gets the rounding step of a rupee business.
 */
internal object LegacyMoney {

    /** Table name to the old field and what it is called now. */
    private val RENAMED: Map<String, List<Pair<String, String>>> = mapOf(
        "fuel_entries" to listOf("costRupees" to "costMinor"),
        "service_records" to listOf("costRupees" to "costMinor"),
        "machine_finance" to listOf("monthlyRupees" to "monthlyMinor"),
        "payouts" to listOf("amountRupees" to "amountMinor"),
        "job_cost_amounts" to listOf("amountRupees" to "amountMinor"),
        "job_costs" to listOf("amountRupees" to "amountMinor"),
        "rates" to listOf("amountRupees" to "amountMinor"),
        "jobs" to listOf("rateRupees" to "rateMinor", "tripTotalRupees" to "tripTotalMinor"),
        "ledger_entries" to listOf("amountRupees" to "amountMinor"),
    )

    /** Job events that carry money in `numericValue`; the others carry litres, seconds or acres. */
    private val MONEY_EVENTS = setOf("TripAdded", "TripRemoved", "RateChanged")

    /** What a rupee is, in minor units. */
    private const val PAISE = 100L

    /**
     * [formatted] is true when the document says it was written in minor units, which
     * is the only way to tell an old job event (a price in rupees) from a new one (a
     * price in paise) — the field has the same name in both. Every other table is told
     * apart by its field names.
     */
    fun upgrade(table: String, json: JsonObject, formatted: Boolean): JsonObject {
        var out: Map<String, kotlinx.serialization.json.JsonElement> = json
        RENAMED[table]?.forEach { (old, new) ->
            val value = out[old] ?: return@forEach
            if (new in out) return@forEach
            out = out - old + (new to times100(value))
        }
        if (table == "job_events" && !formatted) {
            val type = (out["type"] as? JsonPrimitive)?.contentOrNull
            val value = out["numericValue"]
            if (type in MONEY_EVENTS && value is JsonPrimitive && value.contentOrNull != null) {
                out = out + ("numericValue" to times100(value))
            }
        }
        return JsonObject(out)
    }

    private fun times100(value: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
        val primitive = value as? JsonPrimitive ?: return value
        val number = primitive.doubleOrNull ?: return value
        // Whole amounts stay whole numbers; an event's decimal value stays a decimal.
        return if (primitive.content.contains('.')) JsonPrimitive(number * PAISE)
        else JsonPrimitive(primitive.content.toLong() * PAISE)
    }
}
