package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A payment entered wrongly is taken back, not deleted: it stays in the history and counts for nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutVoidTest {

    private lateinit var h: TestHarness
    private val can = Capabilities.ofOwner()
    private val org = TestHarness.ORG_A

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun pay(amount: Long, covers: PayoutCovers = PayoutCovers.Day, recovers: Long = 0, at: Long = 1_000) =
        h.ledger.recordPayout(
            org, can, amountMinor = amount, covers = covers, atMillis = at, byPersonId = TestHarness.OWNER,
            toPersonId = TestHarness.DRIVER, toName = "Jaswant", recoversAdvanceMinor = recovers,
        )

    @Test fun `a payment taken back stays in the history and leaves every total`() = runTest {
        val kept = pay(30_000, at = 1_000)
        val wrong = pay(300_000, at = 2_000)   // an extra nought

        h.ledger.voidPayout(org, can, wrong.id)

        val history = h.ledger.observePayoutsTo(org, TestHarness.DRIVER).first()
        assertEquals("both are still listed, the newest first", listOf(wrong.id, kept.id), history.map { it.id })
        assertNotNull(history.first().voidedAt)
        assertNull(history.last().voidedAt)

        assertEquals("only the real one was paid out", 30_000L, h.db.orgDao().observePaidOutBetween(org, 0, Long.MAX_VALUE).first())
        assertEquals(30_000L, h.db.orgDao().observeLooseWagesBetween(org, 0, Long.MAX_VALUE).first())
        assertEquals(30_000L, h.db.orgDao().observeUnattributablePayouts(org).first())
    }

    @Test fun `taking back an advance, or the pay that took one off, puts the advance right`() = runTest {
        val advance = pay(100_000, PayoutCovers.Advance, at = 1)
        val pay = pay(40_000, PayoutCovers.Period, recovers = 30_000, at = 2)
        assertEquals(70_000L, h.ledger.observeAdvanceOutstanding(org, TestHarness.DRIVER).first())

        h.ledger.voidPayout(org, can, pay.id)
        assertEquals("the pay is gone, so the advance is still all owed", 100_000L,
            h.ledger.observeAdvanceOutstanding(org, TestHarness.DRIVER).first())

        h.ledger.voidPayout(org, can, advance.id)
        assertEquals(0L, h.ledger.observeAdvanceOutstanding(org, TestHarness.DRIVER).first())
    }

    @Test fun `money paid for machine hire is not wages, and a hire payment taken back is owed again`() = runTest {
        val hire = pay(25_000, PayoutCovers.Hire, at = 5)
        assertEquals("not counted as loose wages: the machine's own cost carries it", 0L,
            h.db.orgDao().observeLooseWagesBetween(org, 0, Long.MAX_VALUE).first())
        assertEquals(1, h.db.orgDao().observeHirePayouts(org).first().size)
        h.ledger.voidPayout(org, can, hire.id)
        assertTrue(h.db.orgDao().observeHirePayouts(org).first().isEmpty())
    }

    @Test fun `taking something back twice changes nothing, and it needs the right to pay`() = runTest {
        val p = pay(1_000)
        h.ledger.voidPayout(org, can, p.id)
        val first = h.db.orgDao().findPayout(org, p.id)!!.voidedAt
        h.advanceMinutes(5)
        h.ledger.voidPayout(org, can, p.id)
        assertEquals(first, h.db.orgDao().findPayout(org, p.id)!!.voidedAt)

        val q = pay(2_000)
        val refused = runCatching { h.ledger.voidPayout(org, Capabilities(setOf(Capability.SeePeopleCost)), q.id) }.exceptionOrNull()
        assertTrue(refused is Capabilities.NotAllowed)
        assertNull(h.db.orgDao().findPayout(org, q.id)!!.voidedAt)
    }

    @Test fun `a payment against a job's cost that is taken back leaves the cost owed again`() = runTest {
        val job = h.work.startJob(
            orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator",
            billingMode = com.example.fieldtrack.data.db.BillingMode.PerAcre,
            unit = com.example.fieldtrack.data.db.MeasureUnit.Acre, rateMinor = 1_000,
            operatorPersonId = TestHarness.DRIVER,
        )
        h.ledger.payForJob(
            orgId = org, can = can, jobId = job, amountMinor = 20_000, atMillis = 1_000L,
            byPersonId = TestHarness.OWNER, kind = com.example.fieldtrack.data.db.JobCostKind.Labour, headcount = 2, note = "two men",
        )
        assertEquals(0L, h.ledger.observeOwedOnJobs(org).first())
        val payment = h.db.orgDao().observePayouts(org).first().single()
        h.ledger.voidPayout(org, can, payment.id)
        assertEquals("the cost is still there and nothing settles it now", 20_000L, h.ledger.observeOwedOnJobs(org).first())
    }
}
