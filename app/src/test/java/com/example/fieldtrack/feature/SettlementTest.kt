package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.feature.customer.oldestUnpaidAt
import com.example.fieldtrack.feature.customer.outstandingCharges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Working out what a customer still owes for.
 *
 * Money comes in on account, so which jobs are unpaid is derived rather than
 * recorded. This decides what goes on a dues list and how old the debt is said to
 * be — the number a contractor uses to decide whether to make the phone call.
 */
class SettlementTest {

    private var clock = 0L

    private fun line(
        kind: LedgerKind,
        amount: Long,
        day: Int,
        jobId: String? = null,
    ) = LedgerEntryEntity(
        id = "e${++clock}",
        orgId = "org",
        customerId = "cust",
        kind = kind,
        amountRupees = amount,
        jobId = jobId,
        atMillis = day * 86_400_000L,
        createdAt = day * 86_400_000L,
    )

    private fun charge(amount: Long, day: Int, jobId: String? = null) =
        line(LedgerKind.Charge, amount, day, jobId)

    private fun payment(amount: Long, day: Int) = line(LedgerKind.Payment, amount, day)

    @Test
    fun `nothing owed means nothing outstanding`() {
        val entries = listOf(charge(3_000, 1), payment(3_000, 2))
        assertTrue(outstandingCharges(entries).isEmpty())
        assertNull(oldestUnpaidAt(entries))
    }

    @Test
    fun `money clears the oldest debt first`() {
        val entries = listOf(
            charge(3_000, 1, "job-a"),
            charge(5_000, 10, "job-b"),
            payment(3_000, 12),
        )
        val due = outstandingCharges(entries)

        assertEquals(1, due.size)
        assertEquals("job-b", due.first().entry.jobId)
        assertEquals(5_000L, due.first().outstandingRupees)
    }

    @Test
    fun `a part payment leaves the rest of that charge on the list`() {
        val entries = listOf(charge(5_000, 1, "job-a"), payment(2_000, 3))
        val due = outstandingCharges(entries)

        assertEquals(1, due.size)
        assertEquals(3_000L, due.first().outstandingRupees)
    }

    @Test
    fun `age is measured from the oldest debt still open, not the oldest charge ever`() {
        // Two seasons of trading, all settled, then one job last week.
        val entries = listOf(
            charge(20_000, 1),
            payment(20_000, 2),
            charge(3_500, 700),
        )
        assertEquals(
            "a long-standing customer who owes for one recent job is days late, not years",
            700 * 86_400_000L,
            oldestUnpaidAt(entries),
        )
    }

    @Test
    fun `an opening balance is settled like any other charge`() {
        val entries = listOf(
            line(LedgerKind.Opening, 2_400, 1),
            charge(7_200, 18, "job-a"),
            payment(6_000, 32),
        )
        val due = outstandingCharges(entries)

        // 6,000 clears the 2,400 opening and 3,600 of the job.
        assertEquals(1, due.size)
        assertEquals("job-a", due.first().entry.jobId)
        assertEquals(3_600L, due.first().outstandingRupees)
    }

    @Test
    fun `correcting a job down reduces that job, not the oldest one`() {
        val entries = listOf(
            charge(3_000, 1, "job-a"),
            charge(5_000, 10, "job-b"),
            line(LedgerKind.Adjustment, 1_000, 11, jobId = "job-b"),
        )
        val due = outstandingCharges(entries)

        assertEquals(2, due.size)
        assertEquals(3_000L, due.first { it.entry.jobId == "job-a" }.outstandingRupees)
        assertEquals(4_000L, due.first { it.entry.jobId == "job-b" }.outstandingRupees)
    }

    @Test
    fun `a goodwill credit with no job behind it clears the oldest debt`() {
        val entries = listOf(
            charge(3_000, 1, "job-a"),
            charge(5_000, 10, "job-b"),
            line(LedgerKind.Adjustment, 3_000, 11),
        )
        val due = outstandingCharges(entries)

        assertEquals(1, due.size)
        assertEquals("job-b", due.first().entry.jobId)
    }

    @Test
    fun `paying more than is owed leaves nothing outstanding`() {
        val entries = listOf(charge(3_000, 1), payment(5_000, 2))
        assertTrue(outstandingCharges(entries).isEmpty())
    }

    @Test
    fun `the season Ramesh actually had comes out at two unpaid jobs`() {
        // The worked example on the statement document: six jobs, four payments.
        val entries = listOf(
            line(LedgerKind.Opening, 2_400, 1),
            charge(7_200, 18, "harvest"),
            payment(6_000, 32),
            charge(3_000, 72, "rotavator-jun"),
            payment(2_000, 75),
            charge(2_400, 119, "hauling"),
            charge(1_500, 131, "spraying"),
            payment(3_500, 174),
            charge(3_900, 188, "levelling"),
            charge(3_500, 193, "rotavator-oct"),
            payment(5_000, 193),
        )
        val due = outstandingCharges(entries)

        assertEquals(2, due.size)
        assertEquals(listOf("levelling", "rotavator-oct"), due.map { it.entry.jobId })
        assertEquals(7_400L, due.sumOf { it.outstandingRupees })
        assertEquals(188 * 86_400_000L, oldestUnpaidAt(entries))
    }

    @Test
    fun `a lump sum after the harvest clears the oldest jobs first`() {
        // The shape of nearly every payment in this business: one amount, months of
        // work behind it, and not quite enough to cover the lot.
        val entries = listOf(
            charge(7_200, 18, "harvest"),
            charge(3_000, 72, "rotavator"),
            charge(2_400, 119, "hauling"),
        )
        val cleared = com.example.fieldtrack.feature.customer.clearedBy(entries, 9_000)

        assertEquals(2, cleared.size)
        assertEquals("harvest", cleared[0].entry.jobId)
        assertEquals(7_200L, cleared[0].clearedRupees)
        assertTrue(cleared[0].fully)

        assertEquals("rotavator", cleared[1].entry.jobId)
        assertEquals("the rest goes against the next one", 1_800L, cleared[1].clearedRupees)
        assertFalse("and does not finish it", cleared[1].fully)
    }

    @Test
    fun `paying nothing clears nothing`() {
        val entries = listOf(charge(3_000, 1))
        assertTrue(com.example.fieldtrack.feature.customer.clearedBy(entries, 0).isEmpty())
    }

    @Test
    fun `money beyond what is owed is not allocated anywhere`() {
        val entries = listOf(charge(3_000, 1, "only"))
        val cleared = com.example.fieldtrack.feature.customer.clearedBy(entries, 5_000)

        assertEquals(1, cleared.size)
        assertEquals("the job takes what it is owed and no more", 3_000L, cleared[0].clearedRupees)
    }

    @Test
    fun `a second payment later picks up where the first left off`() {
        val entries = listOf(
            charge(7_200, 18, "harvest"),
            charge(3_000, 72, "rotavator"),
            payment(9_000, 190),
        )
        // The 9,000 cleared the harvest and 1,800 of the rotavator; 1,200 remains.
        val cleared = com.example.fieldtrack.feature.customer.clearedBy(entries, 1_200)

        assertEquals(1, cleared.size)
        assertEquals("rotavator", cleared[0].entry.jobId)
        assertTrue("which finishes it off", cleared[0].fully)
    }

}
