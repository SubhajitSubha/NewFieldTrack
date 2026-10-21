package com.example.fieldtrack.feature

import com.example.fieldtrack.data.TestHarness
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.feature.customer.previousSeasonStart
import com.example.fieldtrack.feature.customer.seasonStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * A season's figures.
 *
 * The claim this screen makes is that nothing is left out — so the tests are
 * about what lands in the total that could not land anywhere else, and about
 * the boundary between one season and the next, which is the only place the
 * sums can quietly attribute a day's work to the wrong half of the year.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeasonTest {

    private lateinit var h: TestHarness
    private val owner = Capabilities.ofOwner()
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private fun millis(date: LocalDate) =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private suspend fun aJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Harvesting",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 1_000,
        machineId = "machine-1",
        operatorPersonId = TestHarness.DRIVER,
    )

    @Test
    fun `kharif begins in June and rabi in November`() {
        assertEquals(LocalDate.of(2026, 6, 1), seasonStart(millis(LocalDate.of(2026, 8, 14)), zone))
        assertEquals(LocalDate.of(2026, 11, 1), seasonStart(millis(LocalDate.of(2026, 12, 2)), zone))
        // January to May is the back half of the rabi that began in November.
        assertEquals(LocalDate.of(2025, 11, 1), seasonStart(millis(LocalDate.of(2026, 3, 9)), zone))
    }

    @Test
    fun `the season before is found by stepping back, not by subtracting months`() {
        // The two are not the same length — June to November is five months and
        // November to June is seven — so arithmetic on months lands in the
        // wrong season twice a year.
        assertEquals(
            LocalDate.of(2025, 11, 1),
            previousSeasonStart(millis(LocalDate.of(2026, 8, 14)), zone),
        )
        assertEquals(
            LocalDate.of(2026, 6, 1),
            previousSeasonStart(millis(LocalDate.of(2026, 12, 2)), zone),
        )
    }

    @Test
    fun `wages no machine could carry are in the season's total`() = runTest {
        val jobId = aJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 4.0)
        h.work.complete(TestHarness.ORG_A, jobId, byPersonId = TestHarness.OWNER)

        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            amountRupees = 2_000, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 4,
        )
        // A month's wages belong to no job and no machine. The machine screen
        // has to leave this out and say so; a season is exactly the stretch of
        // time it was for, so here it belongs in the total.
        h.ledger.recordPayout(
            orgId = TestHarness.ORG_A, can = owner,
            amountRupees = 12_000, covers = PayoutCovers.Period,
            atMillis = h.nowMillis, byPersonId = TestHarness.OWNER,
            toPersonId = TestHarness.DRIVER,
        )

        val dao = h.db.orgDao()
        val onJobs = dao.observeJobCostBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first()
        val loose = dao.observeLooseWagesBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first()

        assertEquals(2_000, onJobs)
        assertEquals(12_000, loose)
        assertEquals("the season carries both", 14_000, onJobs + loose)

        // And the machine still carries only what it can.
        assertEquals(
            2_000,
            dao.observeJobCostOnMachine(TestHarness.ORG_A, "machine-1").first(),
        )
    }

    @Test
    fun `settling a job cost does not count it twice in the season`() = runTest {
        val jobId = aJob()
        h.ledger.payForJob(
            orgId = TestHarness.ORG_A, can = owner, jobId = jobId,
            amountRupees = 2_000, atMillis = h.nowMillis,
            byPersonId = TestHarness.OWNER, headcount = 4,
        )

        val dao = h.db.orgDao()
        // The payment settles a cost already counted, so it is not loose money.
        assertEquals(0, dao.observeLooseWagesBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first())
        assertEquals(
            2_000,
            dao.observeJobCostBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first(),
        )
    }

    @Test
    fun `a cost nobody priced is counted as missing, not as nought`() = runTest {
        h.ledger.recordJobCost(
            orgId = TestHarness.ORG_A, can = owner, jobId = aJob(),
            kind = JobCostKind.Labour, headcount = 3,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis,
        )

        val dao = h.db.orgDao()
        assertEquals(0, dao.observeJobCostBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first())
        // Which is why the screen refuses a total rather than quietly
        // understating one by however much that crew turns out to have cost.
        assertEquals(1, dao.observeUnpricedBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first())
    }

    @Test
    fun `work falls in the season it was done in, not the one being read`() = runTest {
        val jobId = aJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 4.0)
        h.work.complete(TestHarness.ORG_A, jobId, byPersonId = TestHarness.OWNER)

        val dao = h.db.orgDao()
        val workedAt = h.nowMillis
        val thisSeason = millis(seasonStart(workedAt, zone))
        val lastSeason = millis(previousSeasonStart(workedAt, zone))

        // In the season it happened in...
        assertEquals(
            4_000,
            dao.observeEarnedBetween(TestHarness.ORG_A, thisSeason, Long.MAX_VALUE).first(),
        )
        // ...and not in the one before, which ends where this one starts.
        assertEquals(
            0,
            dao.observeEarnedBetween(TestHarness.ORG_A, lastSeason, thisSeason - 1).first(),
        )
    }
}
