package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.feature.customer.StatementContent
import com.example.fieldtrack.feature.customer.StatementPeriod
import com.example.fieldtrack.feature.customer.balanceBefore
import com.example.fieldtrack.feature.customer.entriesFor
import com.example.fieldtrack.feature.customer.financialYearStart
import com.example.fieldtrack.feature.customer.lastSettledAt
import com.example.fieldtrack.feature.customer.periodStart
import com.example.fieldtrack.feature.customer.seasonStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Choosing which days and which lines a statement covers.
 *
 * The periods are the part a farmer picks without thinking about it, so each one has
 * to mean what they would assume. "Since we last settled" in particular: everything
 * before the account last hit zero is closed business, and reprinting it is how an
 * old argument gets restarted.
 */
class StatementOptionsTest {

    private val india: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(india).toInstant().toEpochMilli()

    private fun line(kind: LedgerKind, amount: Long, millis: Long, id: String = "e$millis") =
        LedgerEntryEntity(
            id = id, orgId = "org", customerId = "cust", kind = kind,
            amountRupees = amount, atMillis = millis, createdAt = millis,
        )

    private fun charge(amount: Long, millis: Long, id: String = "c$millis") =
        line(LedgerKind.Charge, amount, millis, id)

    private fun payment(amount: Long, millis: Long, id: String = "p$millis") =
        line(LedgerKind.Payment, amount, millis, id)

    // ------------------------------------------------------------------ seasons

    @Test
    fun `the season running in October began in June`() {
        assertEquals(LocalDate.of(2026, 6, 1), seasonStart(at(2026, 10, 11), india))
    }

    @Test
    fun `the season running in December began in November`() {
        assertEquals(LocalDate.of(2026, 11, 1), seasonStart(at(2026, 12, 20), india))
    }

    @Test
    fun `February is the back half of the season that began last November`() {
        assertEquals(LocalDate.of(2025, 11, 1), seasonStart(at(2026, 2, 14), india))
    }

    @Test
    fun `the financial year starts in April, so March still belongs to the old one`() {
        assertEquals(LocalDate.of(2026, 4, 1), financialYearStart(at(2026, 10, 11), india))
        assertEquals(LocalDate.of(2025, 4, 1), financialYearStart(at(2026, 3, 31), india))
    }

    // ------------------------------------------------------------- last settled

    @Test
    fun `settling up is the last time the balance touched zero`() {
        val entries = listOf(
            charge(3_000, at(2026, 4, 1)),
            payment(3_000, at(2026, 5, 1)),
            charge(2_000, at(2026, 6, 1)),
            payment(2_000, at(2026, 7, 1)),
            charge(5_000, at(2026, 8, 1)),
        )
        assertEquals(at(2026, 7, 1), lastSettledAt(entries))
    }

    @Test
    fun `an account that has never been square has no settling point`() {
        val entries = listOf(charge(3_000, at(2026, 4, 1)), payment(1_000, at(2026, 5, 1)))
        assertNull(lastSettledAt(entries))
    }

    @Test
    fun `overpaying counts as settling`() {
        val entries = listOf(charge(3_000, at(2026, 4, 1)), payment(4_000, at(2026, 5, 1)))
        assertEquals(at(2026, 5, 1), lastSettledAt(entries))
    }

    @Test
    fun `the settling payment itself is closed business and is not reprinted`() {
        val entries = listOf(
            charge(3_000, at(2026, 4, 1)),
            payment(3_000, at(2026, 5, 1)),
            charge(5_000, at(2026, 6, 1)),
        )
        val start = periodStart(StatementPeriod.SinceSettled, at(2026, 10, 11), entries, zone = india)!!
        val shown = entriesFor(entries, StatementContent.Everything, start, null)

        assertEquals("only what came after the account was squared", 1, shown.size)
        assertEquals(at(2026, 6, 1), shown.first().atMillis)
    }

    @Test
    fun `a never-settled account falls back to showing everything`() {
        val entries = listOf(charge(3_000, at(2026, 4, 1)))
        assertNull(periodStart(StatementPeriod.SinceSettled, at(2026, 10, 11), entries, zone = india))
    }

    // ------------------------------------------------------------------ opening

    @Test
    fun `the opening figure is everything that happened before the period`() {
        val entries = listOf(
            charge(7_200, at(2026, 1, 10)),
            payment(4_800, at(2026, 2, 10)),
            charge(3_000, at(2026, 6, 10)),
        )
        val start = periodStart(StatementPeriod.FinancialYear, at(2026, 10, 11), entries, zone = india)

        assertEquals("7,200 billed less 4,800 paid, both before April", 2_400L, balanceBefore(entries, start))
    }

    @Test
    fun `showing everything means there is nothing carried in`() {
        val entries = listOf(charge(7_200, at(2026, 1, 10)))
        assertEquals(0L, balanceBefore(entries, null))
    }

    // ------------------------------------------------------------------ content

    @Test
    fun `only payments drops the charges`() {
        val entries = listOf(
            charge(3_000, at(2026, 4, 1)),
            payment(1_000, at(2026, 5, 1)),
            charge(2_000, at(2026, 6, 1)),
            payment(500, at(2026, 7, 1)),
        )
        val shown = entriesFor(entries, StatementContent.PaymentsOnly, null, null)

        assertEquals(2, shown.size)
        assertEquals(listOf(1_000L, 500L), shown.map { it.amountRupees })
    }

    @Test
    fun `a period excludes what falls outside it at both ends`() {
        val entries = listOf(
            charge(1_000, at(2026, 1, 1)),
            charge(2_000, at(2026, 6, 1)),
            charge(3_000, at(2026, 12, 1)),
        )
        val shown = entriesFor(
            entries,
            StatementContent.Everything,
            at(2026, 4, 1),
            at(2026, 10, 1),
        )
        assertEquals(1, shown.size)
        assertEquals(2_000L, shown.first().amountRupees)
    }

    @Test
    fun `lines come out oldest first whatever order they arrive in`() {
        val entries = listOf(
            charge(3_000, at(2026, 8, 1)),
            charge(1_000, at(2026, 4, 1)),
            payment(500, at(2026, 6, 1)),
        )
        val shown = entriesFor(entries, StatementContent.Everything, null, null)
        assertEquals(
            listOf(at(2026, 4, 1), at(2026, 6, 1), at(2026, 8, 1)),
            shown.map { it.atMillis },
        )
    }
}
