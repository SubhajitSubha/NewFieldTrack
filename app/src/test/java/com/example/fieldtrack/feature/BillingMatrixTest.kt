package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.work.resolveUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which billing modes each kind of work may be sold by.
 *
 * This table is what keeps a job from being measured in one thing and priced in
 * another. The rules below are the ones that would quietly corrupt a khata if the
 * table were edited carelessly, so they are asserted rather than trusted.
 */
class BillingMatrixTest {

    private fun option(key: String) = workTypeOptions.first { it.key == key }

    @Test
    fun `every work type offers at least one way to charge, and lands on one of them`() {
        workTypeOptions.forEach { option ->
            assertTrue(option.key, option.billingModes.isNotEmpty())
            assertTrue(option.key, option.defaultMode in option.billingModes)
            assertEquals(
                "${option.key} lists a mode twice",
                option.billingModes.size, option.billingModes.toSet().size,
            )
        }
    }

    @Test
    fun `a fixed price is always available`() {
        // Two people can agree a lump sum for any work at all.
        workTypeOptions.forEach { option ->
            assertTrue(option.key, BillingMode.Fixed in option.billingModes)
        }
    }

    @Test
    fun `tillage is not sold by the bag, the trip or the kilometre`() {
        listOf("Rotavator", "Ploughing", "Levelling", "Sowing").forEach { key ->
            val modes = option(key).billingModes
            assertFalse(key, BillingMode.PerUnit in modes)
            assertFalse(key, BillingMode.PerTrip in modes)
            assertFalse(key, BillingMode.PerKm in modes)
        }
    }

    @Test
    fun `hauling is sold by the trip, the kilometre or the load, never by the acre`() {
        val modes = option("Hauling").billingModes
        assertEquals(BillingMode.PerTrip, option("Hauling").defaultMode)
        assertTrue(BillingMode.PerKm in modes)
        assertTrue(BillingMode.PerUnit in modes)
        assertFalse("a trolley does not cover an area", BillingMode.PerAcre in modes)
        assertFalse(BillingMode.PerHour in modes)
    }

    @Test
    fun `harvesting may be paid out of the crop`() {
        // A combine taking payment in quintals is normal; nothing else here is.
        assertTrue(BillingMode.PerUnit in option("Harvesting").billingModes)
    }

    @Test
    fun `Other can be charged any way at all`() {
        assertEquals(
            BillingMode.entries.toSet(),
            option("Other").billingModes.toSet(),
        )
    }

    @Test
    fun `every offered mode has a unit to measure it in`() {
        // An empty unit list would crash resolveUnit on first(), at the moment the
        // farmer taps the mode.
        workTypeOptions.flatMap { it.billingModes }.distinct().forEach { mode ->
            assertTrue(mode.name, unitsFor(mode).isNotEmpty())
        }
    }

    @Test
    fun `an area mode keeps the unit the business works in`() {
        assertEquals(
            MeasureUnit.Bigha,
            resolveUnit(BillingMode.PerAcre, MeasureUnit.Bigha),
        )
        // A trip is a trip whatever the business measures land in.
        assertEquals(
            MeasureUnit.Trip,
            resolveUnit(BillingMode.PerTrip, MeasureUnit.Bigha),
        )
    }
}
