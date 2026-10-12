package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.feature.customer.outstandingCharges
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
 * The ledger is the part of this app where being wrong costs a contractor real money,
 * so it gets the closest tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryTest {

    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After fun tearDown() = h.close()

    @Test
    fun `balance is the sum of entries, not a stored number`() = runTest {
        h.ledger.recordOpeningBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 8_900)
        h.ledger.recordPayment(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 5_000, PaymentMethod.Cash)

        assertEquals(3_900L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())
    }

    @Test
    fun `two offline payments both survive instead of overwriting each other`() = runTest {
        h.ledger.recordOpeningBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 12_400)

        // The owner takes 5,000 in cash while the driver takes 2,000 by UPI, neither
        // phone having seen the other. Both entries land; the balance is correct.
        h.ledger.recordPayment(
            TestHarness.ORG_A, TestHarness.CUSTOMER_A, 5_000, PaymentMethod.Cash,
            byPersonId = TestHarness.OWNER,
        )
        h.ledger.recordPayment(
            TestHarness.ORG_A, TestHarness.CUSTOMER_A, 2_000, PaymentMethod.Upi,
            byPersonId = TestHarness.DRIVER,
        )

        assertEquals(5_400L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())
        assertEquals(3, h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first().size)
    }

    @Test
    fun `reversing a payment restores the balance and keeps both lines visible`() = runTest {
        h.ledger.recordOpeningBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 10_000)
        h.ledger.recordPayment(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 4_000, PaymentMethod.Cheque)

        val payment = h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
            .first { it.kind == LedgerKind.Payment }

        h.ledger.reverse(TestHarness.ORG_A, payment, reason = "Cheque bounced")

        assertEquals(10_000L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())

        val lines = h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
        assertEquals(3, lines.size)
        assertTrue("the original payment is still on the khata", lines.any { it.id == payment.id })
        assertTrue("the reversal names what it cancels", lines.any { it.reversesEntryId == payment.id })
    }

    @Test
    fun `one organisation cannot see another's money`() = runTest {
        h.ledger.recordOpeningBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 50_000)
        h.ledger.recordOpeningBalance(TestHarness.ORG_B, TestHarness.CUSTOMER_B, 70_000)

        assertEquals(50_000L, h.ledger.observeTotalOwed(TestHarness.ORG_A).first())
        assertEquals(70_000L, h.ledger.observeTotalOwed(TestHarness.ORG_B).first())

        // The driver works for both. Asking organisation A about organisation B's
        // customer must return nothing rather than leak across the boundary.
        assertEquals(0L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_B).first())
    }

    @Test
    fun `settled customers drop out of the dues list`() = runTest {
        h.ledger.recordOpeningBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 3_000)
        assertEquals(1, h.ledger.observeBalances(TestHarness.ORG_A).first().size)

        h.ledger.recordPayment(TestHarness.ORG_A, TestHarness.CUSTOMER_A, 3_000, PaymentMethod.Cash)
        assertTrue(h.ledger.observeBalances(TestHarness.ORG_A).first().isEmpty())
    }

    @Test
    fun `a negative amount is refused rather than silently flipping direction`() = runTest {
        val failure = runCatching {
            h.ledger.recordPayment(TestHarness.ORG_A, TestHarness.CUSTOMER_A, -500, PaymentMethod.Cash)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
    @Test
    fun `a payment taken at the bill remembers the job, without changing how it settles`() = runTest {
        // Provenance, not allocation. The customer's screen can say what the money
        // was handed over for, while the khata still clears the oldest debt first —
        // cash itself carries no instruction about which job it is meant for.
        val older = h.work.startJob(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
            workType = "Harvesting", billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre, rateRupees = 1_000,
        )
        h.work.setQuantity(TestHarness.ORG_A, older, 3.0)
        h.work.complete(TestHarness.ORG_A, older)
        h.advanceMinutes(90)

        val newer = h.work.startJob(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
            workType = "Rotavator", billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre, rateRupees = 1_000,
        )
        h.work.setQuantity(TestHarness.ORG_A, newer, 2.0)
        h.work.complete(TestHarness.ORG_A, newer)

        h.ledger.recordPayment(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
            amountRupees = 2_000, method = PaymentMethod.Cash, jobId = newer,
        )

        val entries = h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
        val payment = entries.first { it.kind == LedgerKind.Payment }
        assertEquals("the screen can say what it was paid for", newer, payment.jobId)

        // The ₹2,000 still clears the older ₹3,000 job first, leaving ₹1,000 of it.
        val due = outstandingCharges(entries)
        assertEquals(2, due.size)
        assertEquals(older, due.first().entry.jobId)
        assertEquals(1_000L, due.first().outstandingRupees)
    }

}
