package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.measure.Land
import com.example.fieldtrack.feature.calculator.countsWhole
import com.example.fieldtrack.feature.unitsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The units a farmer anywhere may measure and sell by. */
class UnitsTest {

    @Test
    fun `a hectare and a square metre are land, a bale and a tonne are not`() {
        assertTrue(MeasureUnit.Hectare.isLand)
        assertTrue(MeasureUnit.SquareMetre.isLand)
        for (u in listOf(MeasureUnit.Bale, MeasureUnit.Tonne, MeasureUnit.Bushel, MeasureUnit.Kilogram, MeasureUnit.Litre)) {
            assertFalse("$u is not ground", u.isLand)
        }
    }

    @Test
    fun `a walked field comes out right in hectares and square metres`() {
        val oneAcre = Land.ACRE_SQUARE_METRES
        assertEquals(0.4047, Land.inUnit(oneAcre, Land.fixedPerAcre(MeasureUnit.Hectare)!!), 0.0001)
        assertEquals(4046.86, Land.inUnit(oneAcre, Land.fixedPerAcre(MeasureUnit.SquareMetre)!!), 0.01)
        assertEquals("a hectare is ten thousand square metres", 1.0, Land.inUnit(10_000.0, Land.fixedPerAcre(MeasureUnit.Hectare)!!), 0.0001)
        assertNull("a bigha is not the same size everywhere", Land.fixedPerAcre(MeasureUnit.Bigha))
    }

    @Test
    fun `work by the area offers every way of measuring ground, and work by the load every way of selling one`() {
        val ground = unitsFor(BillingMode.PerAcre)
        assertTrue(MeasureUnit.Hectare in ground && MeasureUnit.Acre in ground && MeasureUnit.Bigha in ground)
        assertTrue(ground.all { it.isLand })
        val loads = unitsFor(BillingMode.PerUnit)
        for (u in listOf(MeasureUnit.Bale, MeasureUnit.Tonne, MeasureUnit.Bushel, MeasureUnit.Kilogram, MeasureUnit.Litre, MeasureUnit.Quintal, MeasureUnit.Bag)) {
            assertTrue("$u", u in loads)
        }
        assertTrue(loads.none { it.isLand })
    }

    @Test
    fun `a bale is counted whole and a tonne is not`() {
        assertTrue(MeasureUnit.Bale.countsWhole)
        assertFalse(MeasureUnit.Tonne.countsWhole)
        assertFalse(MeasureUnit.Hectare.countsWhole)
    }

    @Test
    fun `every unit has a name`() {
        MeasureUnit.entries.forEach { assertNotNull(it.name) }
    }
}
