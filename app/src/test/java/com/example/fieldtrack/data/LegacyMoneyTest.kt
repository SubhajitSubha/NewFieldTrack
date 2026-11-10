package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.LegacyMoney
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents and queued changes written when money was whole rupees, read now that it
 * is minor units. The same code serves a server document from an older build and a
 * change queued on a phone before it updated.
 */
class LegacyMoneyTest {

    @Test
    fun `an old job document gets minor units under the new names`() {
        val old = buildJsonObject {
            put("id", "j1"); put("rateRupees", 800L); put("tripTotalRupees", 4_000L); put("quantity", 2.5)
        }
        val now = LegacyMoney.upgrade("jobs", old, formatted = false)
        assertEquals(80_000L, now["rateMinor"]!!.jsonPrimitive.longOrNull)
        assertEquals(400_000L, now["tripTotalMinor"]!!.jsonPrimitive.longOrNull)
        assertEquals("a measurement is not money", 2.5, now["quantity"]!!.jsonPrimitive.doubleOrNull)
        assertFalse(now.containsKey("rateRupees"))
        assertFalse(now.containsKey("tripTotalRupees"))
    }

    @Test
    fun `every money table is covered`() {
        val cases = mapOf(
            "ledger_entries" to ("amountRupees" to "amountMinor"),
            "payouts" to ("amountRupees" to "amountMinor"),
            "job_costs" to ("amountRupees" to "amountMinor"),
            "job_cost_amounts" to ("amountRupees" to "amountMinor"),
            "rates" to ("amountRupees" to "amountMinor"),
            "fuel_entries" to ("costRupees" to "costMinor"),
            "service_records" to ("costRupees" to "costMinor"),
            "machine_finance" to ("monthlyRupees" to "monthlyMinor"),
        )
        for ((table, names) in cases) {
            val now = LegacyMoney.upgrade(table, buildJsonObject { put(names.first, 250L) }, formatted = false)
            assertEquals(table, 25_000L, now[names.second]!!.jsonPrimitive.longOrNull)
            assertFalse(table, now.containsKey(names.first))
        }
    }

    @Test
    fun `a document already in minor units is left exactly as it is`() {
        val current = buildJsonObject { put("id", "l1"); put("amountMinor", 300_000L) }
        assertEquals(current, LegacyMoney.upgrade("ledger_entries", current, formatted = true))
        assertEquals(current, LegacyMoney.upgrade("ledger_entries", current, formatted = false))
    }

    @Test
    fun `an old trip price is scaled but an old litres figure is not`() {
        val trip = buildJsonObject { put("type", "TripAdded"); put("numericValue", 900.0) }
        val litres = buildJsonObject { put("type", "FuelAdded"); put("numericValue", 20.0) }
        assertEquals(90_000.0, LegacyMoney.upgrade("job_events", trip, false)["numericValue"]!!.jsonPrimitive.doubleOrNull)
        assertEquals(20.0, LegacyMoney.upgrade("job_events", litres, false)["numericValue"]!!.jsonPrimitive.doubleOrNull)
    }

    @Test
    fun `a trip price already in minor units is not scaled twice`() {
        val trip = buildJsonObject { put("type", "TripAdded"); put("numericValue", 90_000.0) }
        assertEquals(trip, LegacyMoney.upgrade("job_events", trip, formatted = true))
    }

    @Test
    fun `other tables pass through untouched`() {
        val customer: JsonObject = buildJsonObject { put("id", "c1"); put("name", "Ramesh") }
        assertEquals(customer, LegacyMoney.upgrade("customers", customer, false))
        assertTrue(LegacyMoney.upgrade("bookings", customer, false).containsKey("name"))
        assertEquals(JsonPrimitive("Ramesh"), LegacyMoney.upgrade("customers", customer, false)["name"])
    }
}
