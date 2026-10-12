package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.financialYear
import com.example.fieldtrack.data.db.formatBillNumber
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Numbering a bill.
 *
 * The number ends up on a printed document a customer keeps and an accountant may
 * read, so two rules matter: it must restart each April, and no two bills in a year
 * may share one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillNumberTest {

    private val india = ZoneId.of("Asia/Kolkata")
    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After fun tearDown() = h.close()

    private fun millisOf(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(india).toInstant().toEpochMilli()

    @Test
    fun `the financial year runs April to March`() {
        assertEquals("2026-27", financialYear(millisOf(2026, 4, 1), india))
        assertEquals("2026-27", financialYear(millisOf(2026, 12, 31), india))
        assertEquals("2026-27", financialYear(millisOf(2027, 3, 31), india))
        assertEquals("2027-28", financialYear(millisOf(2027, 4, 1), india))
    }

    @Test
    fun `March is still last year's books`() {
        // The single most common off-by-one in Indian billing.
        assertEquals("2025-26", financialYear(millisOf(2026, 3, 31), india))
    }

    @Test
    fun `a year ending in a round hundred keeps two digits`() {
        assertEquals("2099-00", financialYear(millisOf(2099, 6, 1), india))
    }

    @Test
    fun `a bill number is padded so a column lines up`() {
        assertEquals("2026-27/0001", formatBillNumber("2026-27", 1))
        assertEquals("2026-27/0241", formatBillNumber("2026-27", 241))
        assertEquals("2026-27/12345", formatBillNumber("2026-27", 12345))
    }

    @Test
    fun `each finished job takes the next number`() = runTest {
        val first = bill()
        val second = bill()
        val third = bill()

        assertEquals(listOf(1, 2, 3), listOf(first, second, third).map { it.second })
        assertEquals(first.first, second.first)
    }

    @Test
    fun `an unfinished job has no number at all`() = runTest {
        val id = startJob()
        val job = h.db.jobDao().find(TestHarness.ORG_A, id)!!
        assertEquals(null, job.billSeq)
        assertEquals(null, job.billFy)

        h.work.complete(TestHarness.ORG_A, id)
        assertNotNull(h.db.jobDao().find(TestHarness.ORG_A, id)!!.billSeq)
    }

    @Test
    fun `numbering is per business, so two owners do not share a run`() = runTest {
        val a = bill()
        val otherOrg = h.work.startJob(
            orgId = TestHarness.ORG_B,
            customerId = TestHarness.CUSTOMER_B,
            workType = "Harvesting",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_800,
        )
        h.work.setQuantity(TestHarness.ORG_B, otherOrg, 1.0)
        h.work.complete(TestHarness.ORG_B, otherOrg)

        assertEquals(1, a.second)
        assertEquals(
            "the other business starts its own run at one",
            1,
            h.db.jobDao().find(TestHarness.ORG_B, otherOrg)!!.billSeq,
        )
    }

    private suspend fun startJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Rotavator",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 1_000,
    )

    /** Finishes a job and returns its (financial year, number). */
    private suspend fun bill(): Pair<String, Int> {
        val id = startJob()
        h.work.setQuantity(TestHarness.ORG_A, id, 1.0)
        h.work.complete(TestHarness.ORG_A, id)
        val job = h.db.jobDao().find(TestHarness.ORG_A, id)!!
        return job.billFy!! to job.billSeq!!
    }
}
