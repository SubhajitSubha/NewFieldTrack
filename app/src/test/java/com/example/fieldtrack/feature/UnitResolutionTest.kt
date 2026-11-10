package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.work.resolveUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which unit a job is counted in.
 *
 * Regression: picking a work type used to apply that type's built-in default unit,
 * which silently put a bigha business back into acres the moment they chose
 * "Harvesting". Land units are regional, and getting one wrong misprices every bill.
 */
class UnitResolutionTest {

    @Test
    fun `a bigha business stays in bigha for area work`() {
        assertEquals(
            MeasureUnit.Bigha,
            resolveUnit(BillingMode.PerAcre, orgLandUnit = MeasureUnit.Bigha),
        )
    }

    @Test
    fun `an acre business stays in acres`() {
        assertEquals(
            MeasureUnit.Acre,
            resolveUnit(BillingMode.PerAcre, orgLandUnit = MeasureUnit.Acre),
        )
    }

    @Test
    fun `land units do not leak into modes that have nothing to do with land`() {
        val bigha = MeasureUnit.Bigha
        assertEquals(MeasureUnit.Trip, resolveUnit(BillingMode.PerTrip, bigha))
        assertEquals(MeasureUnit.Hour, resolveUnit(BillingMode.PerHour, bigha))
        assertEquals(MeasureUnit.Kilometre, resolveUnit(BillingMode.PerKm, bigha))
    }

    @Test
    fun `per-unit work falls back to the first sensible option`() {
        assertEquals(
            MeasureUnit.Quintal,
            resolveUnit(BillingMode.PerUnit, orgLandUnit = MeasureUnit.Kanal),
        )
    }
}
