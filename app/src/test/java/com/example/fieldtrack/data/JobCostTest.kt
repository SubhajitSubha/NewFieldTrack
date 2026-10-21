package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.session.Capabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a job cost beyond the machine.
 *
 * Two tables hold money that looks alike, so almost everything worth testing
 * here is arithmetic: that a rupee is counted once, that work nobody has paid
 * for is still counted, and that a figure nobody may see does not quietly
 * become nought in a total somebody acts on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobCostTest {

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

    private suspend fun driverCan(): Capabilities = Capabilities.of(
        requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER))
    )

    @Test
    fun `paying for a job counts the money once, not twice`() = runTest {
        val jobId = aJob()
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            amountRupees = 2_000, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 4,
        )

        // The cost sheet holds it. The payment settles it and is not a second
        // cost — this is the whole reason the two tables are tied together.
        assertEquals(
            2_000,
            h.db.orgDao().observeJobCostOnMachine(TestHarness.ORG_A, "machine-1").first(),
        )
        assertEquals(
            0,
            h.db.orgDao().observeUnattributablePayouts(TestHarness.ORG_A).first(),
        )
        assertEquals(0, h.db.orgDao().observeOwedOnJobs(TestHarness.ORG_A).first())
    }

    @Test
    fun `work nobody has paid for still counts against the machine`() = runTest {
        val jobId = aJob()
        h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            kind = JobCostKind.Labour, headcount = 4, amountRupees = 2_000,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )

        // A machine that looks profitable only because its crew has not been
        // paid yet is the lie the profit screen exists to prevent.
        assertEquals(
            2_000,
            h.db.orgDao().observeJobCostOnMachine(TestHarness.ORG_A, "machine-1").first(),
        )
        assertEquals(2_000, h.db.orgDao().observeOwedOnJobs(TestHarness.ORG_A).first())
    }

    @Test
    fun `a cost paid in two halves is owed the difference, then nothing`() = runTest {
        val jobId = aJob()
        val cost = h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            kind = JobCostKind.Labour, headcount = 4, amountRupees = 2_000,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )

        h.ledger.settleJobCost(
            orgId = TestHarness.ORG_A, can = owner, costId = cost.id,
            amountRupees = 800, atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
        )
        assertEquals(1_200, h.db.orgDao().observeOwedOnJobs(TestHarness.ORG_A).first())

        h.ledger.settleJobCost(
            orgId = TestHarness.ORG_A, can = owner, costId = cost.id,
            amountRupees = 1_200, atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
        )
        assertEquals(0, h.db.orgDao().observeOwedOnJobs(TestHarness.ORG_A).first())

        // Paid twice over, the job still cost what it cost.
        assertEquals(
            2_000,
            h.db.orgDao().observeJobCostOnMachine(TestHarness.ORG_A, "machine-1").first(),
        )
    }

    @Test
    fun `overpaying one job is not a credit against another`() = runTest {
        val cost = h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            kind = JobCostKind.Labour, headcount = 2, amountRupees = 1_000,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )
        h.ledger.settleJobCost(
            orgId = TestHarness.ORG_A, can = owner, costId = cost.id,
            amountRupees = 1_500, atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
        )
        h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            kind = JobCostKind.Labour, headcount = 2, amountRupees = 900,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )

        // The ₹500 over on the first job is a thing to sort out there, not a
        // silent discount on what is still owed on the second.
        assertEquals(900, h.db.orgDao().observeOwedOnJobs(TestHarness.ORG_A).first())
    }

    @Test
    fun `a driver records how many worked and no money at all`() = runTest {
        val jobId = aJob()
        val cost = h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = driverCan(), jobId = jobId,
            kind = JobCostKind.Labour, headcount = 4,
            byPersonId = TestHarness.DRIVER, atMillis = h.nowMillis,
        )

        assertEquals(4, cost.headcount)
        assertNull(cost.amountRupees)
        assertEquals(1, h.db.orgDao().observeUnpricedJobCostCount(TestHarness.ORG_A).first())
        // Nothing to owe, because nobody has said what it is worth.
        assertEquals(0, h.db.orgDao().observeOwedOnJobs(TestHarness.ORG_A).first())
    }

    @Test
    fun `a driver cannot put a figure on it`() = runTest {
        val failed = runCatching {
            h.ledger.recordJobCost(
                orgId = TestHarness.ORG_A, can = driverCan(), jobId = aJob(),
                kind = JobCostKind.Labour, headcount = 4, amountRupees = 2_000,
                byPersonId = TestHarness.DRIVER, atMillis = h.nowMillis,
            )
        }
        assertTrue(failed.exceptionOrNull() is Capabilities.NotAllowed)
    }

    @Test
    fun `nobody counted is a real answer, not a missing one`() = runTest {
        val cost = h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            kind = JobCostKind.Labour, headcount = null, amountRupees = 6_000,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )

        // A gang the jamadar brought, paid by the acre. The money is known and
        // counted; the heads were never counted and are not invented here.
        assertNull(cost.headcount)
        assertEquals(6_000L, cost.amountRupees)
    }

    @Test
    fun `a headcount of nought is not an entry`() = runTest {
        val failed = runCatching {
            h.ledger.recordJobCost(
                orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
                kind = JobCostKind.Labour, headcount = 0,
                byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
            )
        }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `a day's pay is still left out, and a job's cost is not`() = runTest {
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            amountRupees = 2_000, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 4,
        )
        h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 600, covers = PayoutCovers.Day,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            headcount = 2,
        )

        assertEquals(
            600,
            h.db.orgDao().observeUnattributablePayouts(TestHarness.ORG_A).first(),
        )
    }

    @Test
    fun `tea money is counted but is not a wage`() = runTest {
        val jobId = aJob()
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            amountRupees = 2_000, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 4,
        )
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            amountRupees = 300, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, kind = JobCostKind.Other,
            note = "Tea and food",
        )

        val costs = h.ledger.observeJobCosts(TestHarness.ORG_A, jobId).first()
        assertEquals(2, costs.size)
        assertEquals(
            300L,
            costs.first { it.cost.kind == JobCostKind.Other }.cost.amountRupees,
        )
        // Both are real costs of the job, whatever permission guards them.
        assertEquals(
            2_300,
            h.db.orgDao().observeJobCostOnMachine(TestHarness.ORG_A, "machine-1").first(),
        )
    }

    @Test
    fun `a cost with no figure is sent as one row`() = runTest {
        val cost = h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            kind = JobCostKind.Labour, headcount = 3,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )
        val queued = h.db.outboxDao().nextBatch(100).filter { it.entityId == cost.id }
        assertEquals(listOf("job_costs"), queued.map { it.tableName })
    }

    @Test
    fun `the rupees travel apart from the headcount`() = runTest {
        val cost = h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            kind = JobCostKind.Labour, headcount = 4, amountRupees = 2_000,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )
        val queued = h.db.outboxDao().nextBatch(100).filter { it.entityId == cost.id }

        // Two documents: the work facts where every member may read them, the
        // money where only the money people may. Facts first, so a pass that
        // stops between them leaves a cost waiting to be priced rather than a
        // price belonging to no cost.
        assertEquals(listOf("job_costs", "job_cost_amounts"), queued.map { it.tableName })

        // And the readable one must not carry the figure. A rule grants a
        // whole document; it cannot hide a field.
        val facts = queued.first { it.tableName == "job_costs" }.payloadJson
        assertFalse(
            "the wage must not ride along in the row every member can read",
            facts.contains("amountRupees"),
        )
        assertTrue(facts.contains("headcount"))
        assertTrue(
            queued.first { it.tableName == "job_cost_amounts" }.payloadJson.contains("2000")
        )
    }
}
