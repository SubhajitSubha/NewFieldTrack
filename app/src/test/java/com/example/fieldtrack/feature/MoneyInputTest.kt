package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.jobs.AmendJobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What can be typed into a correction to a bill, and what it is read as. */
class MoneyInputTest {

    private fun amend(quantity: String, rate: String = "1000", mode: BillingMode = BillingMode.PerAcre) =
        AmendJobState(
            loaded = true, billingMode = mode, unit = MeasureUnit.Acre,
            originalQuantity = 4.0, originalRateRupees = 1_000, originalAmountRupees = 4_000,
            quantityText = quantity, rateText = rate, reason = "Corrected",
        )

    @Test
    fun `a quantity that is not a number is refused, not priced as nothing`() {
        // "4.5.2" read as nought, the bill became ₹0 and the whole of it was taken off.
        assertFalse(amend("4.5.2").canSave)
        assertFalse(amend("").canSave)
        assertFalse(amend(".").canSave)
    }

    @Test
    fun `a real correction can still be saved`() {
        assertTrue(amend("3.5").canSave)
        assertTrue(amend("0").canSave)
    }

    @Test
    fun `a fixed price has no quantity to be wrong about`() {
        assertTrue(amend("", mode = BillingMode.Fixed).canSave)
    }

    @Test
    fun `a rate has to be there`() {
        assertFalse(amend("3.5", rate = "").canSave)
    }

    private fun past(hours: String, from: Int?, to: Int?, mode: BillingMode = BillingMode.PerHour) =
        com.example.fieldtrack.feature.work.PastWorkState(
            billingMode = mode, quantityText = hours, rateText = "500", fromMinutes = from, toMinutes = to,
        )

    @Test
    fun `hours typed and times that disagree are said, and rounding is not`() {
        assertTrue("6 hours typed, 2 hours of times", past("6", 8 * 60, 10 * 60).hoursDisagreeWithTimes)
        assertFalse("an hour typed for sixty minutes", past("1", 8 * 60, 9 * 60).hoursDisagreeWithTimes)
        assertFalse("ten minutes out is rounding", past("2", 8 * 60, 10 * 60 + 10).hoursDisagreeWithTimes)
        assertFalse("no times, nothing to disagree with", past("6", null, null).hoursDisagreeWithTimes)
        assertFalse("only hourly work has hours to compare", past("6", 8 * 60, 10 * 60, BillingMode.PerAcre).hoursDisagreeWithTimes)
    }
}
