package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.jobs.AmendJobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence the correction screen is built around: "they will owe ₹500 less".
 *
 * Pure arithmetic, so it is tested here rather than by typing on a handset — the
 * fixed-price branch in particular is easy to get wrong and hard to reach by hand.
 */
class AmendPreviewTest {

    private fun measured(
        originalQuantity: Double = 3.5,
        originalRate: Long = 1_000,
        quantityText: String = "3.5",
        rateText: String = "1000",
        reason: String = "",
    ) = AmendJobState(
        loaded = true,
        unit = MeasureUnit.Acre,
        billingMode = BillingMode.PerAcre,
        originalQuantity = originalQuantity,
        originalRateRupees = originalRate,
        originalAmountRupees = Math.round(originalQuantity * originalRate),
        quantityText = quantityText,
        rateText = rateText,
        reason = reason,
    )

    @Test
    fun `cutting the area owes less`() {
        val s = measured(quantityText = "3")
        assertEquals(3_000L, s.newAmountRupees)
        assertEquals(-500L, s.deltaRupees)
    }

    @Test
    fun `raising the rate owes more`() {
        val s = measured(rateText = "1200")
        assertEquals(4_200L, s.newAmountRupees)
        assertEquals(700L, s.deltaRupees)
    }

    @Test
    fun `a fixed price job is not multiplied by the quantity`() {
        val s = AmendJobState(
            loaded = true,
            billingMode = BillingMode.Fixed,
            originalQuantity = 1.0,
            originalRateRupees = 5_000,
            originalAmountRupees = 5_000,
            quantityText = "1",
            rateText = "6000",
            reason = "Agreed a higher price",
        )
        assertEquals(6_000L, s.newAmountRupees)
        assertEquals(1_000L, s.deltaRupees)
    }

    @Test
    fun `money may not move without a reason`() {
        assertFalse(measured(quantityText = "3").canSave)
        assertTrue(measured(quantityText = "3", reason = "Measured again").canSave)
    }

    @Test
    fun `a correction that changes nothing needs no reason`() {
        val s = measured()
        assertEquals(0L, s.deltaRupees)
        assertTrue(s.canSave)
    }

    @Test
    fun `a driver without permission cannot save whatever they type`() {
        val s = measured(quantityText = "3", reason = "Measured again").copy(allowed = false)
        assertFalse(s.canSave)
    }

    @Test
    fun `a half typed decimal does not crash the preview`() {
        // Someone typing "4." mid-keystroke must not blank the screen.
        val s = measured(quantityText = "4.")
        assertEquals(4_000L, s.newAmountRupees)
    }
}
