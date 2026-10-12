package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Trips priced one at a time.
 *
 * A contractor hauling for one farmer all day does not open a new job when the price
 * changes — the load gets heavier or the field gets further, he says so, and adds the
 * next trip at the new rate. The earlier trips were already agreed and must not move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripPricingTest {

    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After fun tearDown() = h.close()

    private suspend fun tripJob(rate: Long = 400): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Hauling",
        billingMode = BillingMode.PerTrip,
        unit = MeasureUnit.Trip,
        rateRupees = rate,
    )

    private suspend fun job(id: String) = h.db.jobDao().find(TestHarness.ORG_A, id)!!

    @Test
    fun `trips at one rate add up the obvious way`() = runTest {
        val id = tripJob(rate = 400)
        repeat(3) { h.work.addTrip(TestHarness.ORG_A, id) }

        assertEquals(3.0, job(id).quantity, 0.001)
        assertEquals(1_200L, job(id).amountRupees)
    }

    @Test
    fun `raising the rate prices the next trip, not the ones already made`() = runTest {
        val id = tripJob(rate = 400)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.addTrip(TestHarness.ORG_A, id)

        // Heavier load for the rest of the day.
        h.work.setRate(TestHarness.ORG_A, id, 600)
        h.work.addTrip(TestHarness.ORG_A, id)

        assertEquals(3.0, job(id).quantity, 0.001)
        assertEquals("400 + 400 + 600", 1_400L, job(id).amountRupees)
    }

    @Test
    fun `the rate can move more than once in a day`() = runTest {
        val id = tripJob(rate = 300)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.setRate(TestHarness.ORG_A, id, 450)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.setRate(TestHarness.ORG_A, id, 500)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.addTrip(TestHarness.ORG_A, id)

        assertEquals("300 + 450 + 500 + 500", 1_750L, job(id).amountRupees)
    }

    @Test
    fun `undoing a trip takes off what that trip cost, not today's rate`() = runTest {
        val id = tripJob(rate = 400)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.setRate(TestHarness.ORG_A, id, 900)
        h.work.addTrip(TestHarness.ORG_A, id)
        assertEquals(1_300L, job(id).amountRupees)

        h.work.removeLastTrip(TestHarness.ORG_A, id)

        assertEquals("the 900 trip comes off, leaving the 400 one", 400L, job(id).amountRupees)
        assertEquals(1.0, job(id).quantity, 0.001)
    }

    @Test
    fun `undoing back to nothing never goes negative`() = runTest {
        val id = tripJob(rate = 400)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.removeLastTrip(TestHarness.ORG_A, id)
        h.work.removeLastTrip(TestHarness.ORG_A, id)

        assertEquals(0L, job(id).amountRupees)
        assertEquals(0.0, job(id).quantity, 0.001)
    }

    @Test
    fun `the bill is the sum of the trips that were actually made`() = runTest {
        val id = tripJob(rate = 400)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.setRate(TestHarness.ORG_A, id, 600)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.complete(TestHarness.ORG_A, id)

        assertEquals(
            "what reaches the khata is what the trips came to",
            1_000L,
            h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first(),
        )
    }

    @Test
    fun `typing a trip count outright prices them all the same`() = runTest {
        // No per-trip history exists for a number entered in one go.
        val id = tripJob(rate = 400)
        h.work.setQuantity(TestHarness.ORG_A, id, 6.0)

        assertEquals(2_400L, job(id).amountRupees)
    }

    @Test
    fun `the stepper keeps the total in step with the count`() = runTest {
        val id = tripJob(rate = 400)
        h.work.adjustQuantity(TestHarness.ORG_A, id, up = true)
        h.work.adjustQuantity(TestHarness.ORG_A, id, up = true)

        assertEquals(2.0, job(id).quantity, 0.001)
        assertEquals(800L, job(id).amountRupees)
    }

    @Test
    fun `correcting a billed trip job reprices it at the corrected rate`() = runTest {
        val id = tripJob(rate = 400)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.setRate(TestHarness.ORG_A, id, 600)
        h.work.addTrip(TestHarness.ORG_A, id)
        h.work.complete(TestHarness.ORG_A, id)
        assertEquals(1_000L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())

        h.work.amendCompletedJob(
            orgId = TestHarness.ORG_A, jobId = id,
            can = com.example.fieldtrack.data.session.Capabilities.ofOwner(),
            quantity = 2.0, rateRupees = 500,
            reason = "Agreed one price for the day",
        )

        assertEquals(1_000L, job(id).amountRupees)
        assertEquals(1_000L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())
    }

    @Test
    fun `other billing modes are untouched by any of this`() = runTest {
        val id = h.work.startJob(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
            workType = "Rotavator", billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre, rateRupees = 1_000,
        )
        h.work.setQuantity(TestHarness.ORG_A, id, 3.5)

        assertEquals(3_500L, job(id).amountRupees)
        assertEquals("and no trip total is invented", 0L, job(id).tripTotalRupees)
    }
}
