package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.JobEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The same arithmetic everywhere a bill is worked out.
 *
 * A job's amount is worked out in Kotlin when a bill is made and again in SQL when a
 * season or a customer is totalled. If the two round differently by even one paisa,
 * a total stops matching the jobs under it, and the owner finds out when a customer
 * does. So the two are compared directly, in a business that bills whole rupees and
 * one that bills cents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoneyRoundingTest {

    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After fun tearDown() = h.close()

    private suspend fun job(id: String, step: Long, mode: BillingMode, quantity: Double, rate: Long, trips: Long = 0) {
        h.db.jobDao().upsert(
            JobEntity(
                id = id, orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
                workType = "Rotavator", billingMode = mode, unit = MeasureUnit.Acre,
                rateMinor = rate, stepMinor = step, quantity = quantity, tripTotalMinor = trips,
                status = JobStatus.Completed, workedAt = 10, recordedAt = 10, createdAt = 10, updatedAt = 10,
            )
        )
    }

    private val quantities = listOf(0.25, 1.0, 2.5, 2.75, 3.33, 7.125, 10.0, 12.5, 99.99)
    private val rates = listOf(1L, 99L, 333L, 1_001L, 123_457L, 1_250_033L)

    private suspend fun totalFromSql() =
        h.db.orgDao().observeEarnedBetween(TestHarness.ORG_A, 0, 100).first()

    @Test
    fun `a rupee business totals to the same whole rupees as its bills`() = runTest {
        var expected = 0L
        var n = 0
        for (q in quantities) for (r in rates) {
            job("j${n++}", step = 100, BillingMode.PerAcre, q, r)
            val bill = com.example.fieldtrack.data.billing.Billing.amount(BillingMode.PerAcre, q, r, 100)
            assertEquals("a bill is whole rupees: $q x $r", 0L, bill % 100)
            expected += bill
        }
        assertEquals(expected, totalFromSql())
    }

    @Test
    fun `a business that bills cents totals to the same cents as its bills`() = runTest {
        var expected = 0L
        var n = 0
        for (q in quantities) for (r in rates) {
            job("j${n++}", step = 1, BillingMode.PerAcre, q, r)
            expected += com.example.fieldtrack.data.billing.Billing.amount(BillingMode.PerAcre, q, r, 1)
        }
        assertEquals(expected, totalFromSql())
    }

    @Test
    fun `fixed and trip jobs are untouched by rounding`() = runTest {
        job("fixed", step = 100, BillingMode.Fixed, 7.0, 500_050)
        job("trips", step = 100, BillingMode.PerTrip, 5.0, 90_000, trips = 412_345)
        assertEquals(500_050L + 412_345L, totalFromSql())
    }

    @Test
    fun `each job keeps the rounding it was made with when the business changes its mind`() = runTest {
        job("old", step = 100, BillingMode.PerAcre, 0.333, 123_400)
        job("new", step = 1, BillingMode.PerAcre, 0.333, 123_400)
        // Whatever the business setting is now, the first is whole rupees and the second is exact.
        assertEquals(41_100L + 41_092L, totalFromSql())
        assertTrue(h.work.observeJob(TestHarness.ORG_A, "old").first()!!.amountMinor % 100 == 0L)
    }

    @Test
    fun `a new job takes the business's rounding and keeps it`() = runTest {
        val org = h.db.orgDao().findOrg(TestHarness.ORG_B)!!
        h.db.orgDao().upsertOrg(org.copy(currencyCode = "USD", moneyStepMinor = 1))
        val cents = h.work.startJob(
            orgId = TestHarness.ORG_B, customerId = TestHarness.CUSTOMER_B, workType = "Rotavator",
            billingMode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rateMinor = 1_250,
        )
        val rupees = h.work.startJob(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator",
            billingMode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rateMinor = 125_000,
        )
        assertEquals(1L, h.work.observeJob(TestHarness.ORG_B, cents).first()!!.stepMinor)
        assertEquals(100L, h.work.observeJob(TestHarness.ORG_A, rupees).first()!!.stepMinor)
        // The business changes its setting afterwards: the job already made does not move.
        h.db.orgDao().upsertOrg(org.copy(currencyCode = "USD", moneyStepMinor = 100))
        assertEquals(1L, h.work.observeJob(TestHarness.ORG_B, cents).first()!!.stepMinor)
    }
}
