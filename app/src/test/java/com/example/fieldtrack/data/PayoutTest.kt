package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
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
 * Money leaving the business.
 *
 * The rules worth holding are about honesty in the totals: what can be laid
 * against a machine, what cannot, and the fact that the second kind must still
 * be counted somewhere rather than quietly dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutTest {

    private lateinit var h: TestHarness
    private val owner = Capabilities.ofOwner()

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun aJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Harvesting",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 2_000,
        machineId = "machine-1",
        operatorPersonId = TestHarness.DRIVER,
    )

    @Test
    fun `labourers are a count and an amount, not people with records`() = runTest {
        val payout = h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            amountRupees = 900, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 3,
            note = "Labourers", method = PaymentMethod.Cash,
        )

        assertEquals(3, payout.headcount)
        assertEquals(null, payout.toPersonId)
    }

    @Test
    fun `a payment cannot name a job without a cost to settle`() = runTest {
        // The invariant every machine figure rests on. Money spent on a job
        // and recorded only as a payment would never be counted against that
        // job, because the job's costs are totalled from its own sheet.
        val failed = runCatching {
            h.ledger.recordPayout(
                orgId = TestHarness.ORG_A, can = owner,
                amountRupees = 900, covers = PayoutCovers.Job,
                atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
                jobId = aJob(),
            )
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `a driver and a labourer go through the same door`() = runTest {
        val wage = h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 12_000, covers = PayoutCovers.Period,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            toPersonId = TestHarness.DRIVER, toName = "Jaswant Kumar",
            note = "September",
        )

        // The only difference is whether the person has an account here.
        assertEquals(TestHarness.DRIVER, wage.toPersonId)
        assertEquals(1, wage.headcount)
    }

    @Test
    fun `only what is laid against a job counts towards a machine`() = runTest {
        val jobId = aJob()
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            amountRupees = 900, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 3,
        )
        h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 12_000, covers = PayoutCovers.Period,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            toPersonId = TestHarness.DRIVER, toName = "Jaswant Kumar",
        )

        // A month's wages belong to no machine. Counted here they would make
        // every machine look worse than it is, all at once.
        assertEquals(
            900,
            h.db.orgDao().jobCostOnMachine(
                TestHarness.ORG_A, "machine-1", 0, Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `what no machine can carry is still counted, so a total can admit to it`() = runTest {
        h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 12_000, covers = PayoutCovers.Period,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            toPersonId = TestHarness.DRIVER, toName = "Jaswant Kumar",
        )
        // A day's pay has no job behind it either, so it is just as unusable
        // to a machine. Counting only the month-long ones understated what was
        // being left out, which is the one thing this figure exists to prevent.
        h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 600, covers = PayoutCovers.Day,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            toName = "Labourers", headcount = 2,
        )

        assertEquals(
            12_600,
            h.db.orgDao().paidOutUnattributable(TestHarness.ORG_A, 0, Long.MAX_VALUE),
        )
    }

    @Test
    fun `what is laid against a job is not double counted as left out`() = runTest {
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            amountRupees = 900, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 3,
        )

        assertEquals(
            0,
            h.db.orgDao().paidOutUnattributable(TestHarness.ORG_A, 0, Long.MAX_VALUE),
        )
    }

    @Test
    fun `paying somebody is a power of its own`() = runTest {
        val driver = Capabilities.of(
            requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER))
        )
        assertTrue("a plain driver cannot pay anybody", Capability.PayOut !in driver)

        val failure = runCatching {
            h.ledger.recordPayout(
                orgId = TestHarness.ORG_A, can = driver,
                amountRupees = 500, covers = PayoutCovers.Day,
                atMillis = h.nowMillis, byPersonId = TestHarness.DRIVER,
                toName = "Labourers", headcount = 2,
            )
        }.exceptionOrNull()
        assertTrue(failure is Capabilities.NotAllowed)
    }

    @Test
    fun `money out is sent on like everything else`() = runTest {
        h.db.outboxDao().nextBatch(500).forEach { h.db.outboxDao().remove(it) }

        h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 600, covers = PayoutCovers.Day,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            toPersonId = TestHarness.DRIVER, toName = "Jaswant Kumar",
        )

        assertTrue(
            h.db.outboxDao().nextBatch(500).any { it.tableName == SyncQueue.TABLE_PAYOUTS },
        )
    }
}
