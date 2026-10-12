package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.feature.customer.buildKhata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The running balance down a khata.
 *
 * Plain JUnit — no database and no Android, because the thing under test is the
 * arithmetic and the ordering, and both are easy to get subtly wrong.
 */
class KhataTest {

    /** The DAO returns newest first, so tests build their input that way too. */
    private fun entry(
        id: String,
        kind: LedgerKind,
        amount: Long,
        at: Long,
        reverses: String? = null,
    ) = LedgerEntryEntity(
        id = id,
        orgId = "org",
        customerId = "cust",
        kind = kind,
        amountRupees = amount,
        method = if (kind == LedgerKind.Payment) PaymentMethod.Cash else null,
        atMillis = at,
        reversesEntryId = reverses,
        createdAt = at,
    )

    @Test
    fun `balance accumulates oldest first and is shown newest first`() {
        val newestFirst = listOf(
            entry("c", LedgerKind.Payment, 5_000, at = 300),
            entry("b", LedgerKind.Charge, 6_800, at = 200),
            entry("a", LedgerKind.Opening, 8_900, at = 100),
        )

        val rows = buildKhata(newestFirst)

        // Newest line stays on top...
        assertEquals(listOf("c", "b", "a"), rows.map { it.id })
        // ...but each line's balance is the total as of that line, read oldest-up:
        // 8,900 then 15,700 then 10,700.
        assertEquals(10_700L, rows[0].balanceAfterRupees)
        assertEquals(15_700L, rows[1].balanceAfterRupees)
        assertEquals(8_900L, rows[2].balanceAfterRupees)
    }

    @Test
    fun `charges raise the balance and payments lower it`() {
        val rows = buildKhata(
            listOf(
                entry("pay", LedgerKind.Payment, 2_000, at = 200),
                entry("job", LedgerKind.Charge, 3_500, at = 100),
            )
        )

        assertTrue(rows.first { it.id == "job" }.isDebit)
        assertFalse(rows.first { it.id == "pay" }.isDebit)
        assertEquals(1_500L, rows.first().balanceAfterRupees)
    }

    @Test
    fun `an adjustment reduces what is owed`() {
        val rows = buildKhata(
            listOf(
                entry("discount", LedgerKind.Adjustment, 500, at = 200),
                entry("job", LedgerKind.Charge, 3_500, at = 100),
            )
        )
        assertEquals(3_000L, rows.first().balanceAfterRupees)
    }

    @Test
    fun `both sides of a reversal are marked so neither can be read as a plain line`() {
        val rows = buildKhata(
            listOf(
                entry("reversal", LedgerKind.Charge, 4_000, at = 200, reverses = "cheque"),
                entry("cheque", LedgerKind.Payment, 4_000, at = 100),
            )
        )

        assertTrue(rows.first { it.id == "reversal" }.isReversal)
        assertTrue("the cancelled entry is flagged too", rows.first { it.id == "cheque" }.isReversal)
        // A bounced cheque nets to nothing owed either way.
        assertEquals(0L, rows.first().balanceAfterRupees)
    }

    @Test
    fun `an empty khata produces no lines rather than a zero row`() {
        assertTrue(buildKhata(emptyList()).isEmpty())
    }
}
