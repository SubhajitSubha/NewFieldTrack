package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.repository.PlanRepository
import com.example.fieldtrack.data.session.Capabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A token taken when work is booked: it is the farmer's money from that moment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvanceTest {

    private lateinit var h: TestHarness
    private lateinit var plan: PlanRepository
    private val org = TestHarness.ORG_A

    @Before fun setUp() = runTest {
        h = TestHarness(); h.seed()
        plan = PlanRepository(h.db, h.syncQueue, h.clock, h.ids, ledger = h.ledger)
    }
    @After fun tearDown() = h.close()

    private suspend fun book(advance: Long?, method: PaymentMethod = PaymentMethod.Cash) = plan.save(
        can = Capabilities.ofOwner(), orgId = org, id = null, customerId = TestHarness.CUSTOMER_A, fieldId = null,
        workType = "Rotavator", day = 20_000, part = DayPart.Morning, startMinute = null, machineId = null,
        operatorPersonId = null, note = null, advanceMinor = advance, advanceMethod = method, byPersonId = "owner",
    )

    @Test
    fun `a token goes on the account as a payment and is kept on the booking`() = runTest {
        val id = book(50_000, PaymentMethod.Upi)
        assertEquals(50_000L, plan.observe(org, id).first()!!.booking.advanceMinor)
        val entry = h.db.ledgerDao().observeFor(org, TestHarness.CUSTOMER_A).first().single()
        assertEquals(LedgerKind.Payment, entry.kind)
        assertEquals(50_000L, entry.amountMinor)
        assertEquals(PaymentMethod.Upi, entry.method)
        assertEquals(-50_000L, h.ledger.observeBalance(org, TestHarness.CUSTOMER_A).first())
    }

    @Test
    fun `no token means no payment`() = runTest {
        val id = book(null)
        assertNull(plan.observe(org, id).first()!!.booking.advanceMinor)
        assertEquals(0, h.db.ledgerDao().observeFor(org, TestHarness.CUSTOMER_A).first().size)
        val zero = book(0)
        assertNull(plan.observe(org, zero).first()!!.booking.advanceMinor)
    }

    @Test
    fun `the token comes off the bill when the work is done`() = runTest {
        book(50_000)
        val job = h.work.startJob(
            orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator", billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre, rateMinor = 100_000,
        )
        h.work.setQuantity(org, job, 2.0)
        h.work.complete(org, job)
        assertEquals("2,000 billed less 500 already paid", 150_000L, h.ledger.observeBalance(org, TestHarness.CUSTOMER_A).first())
    }

    @Test
    fun `changing a booking later does not take the token again`() = runTest {
        val id = book(50_000)
        plan.save(
            can = Capabilities.ofOwner(), orgId = org, id = id, customerId = TestHarness.CUSTOMER_A, fieldId = null,
            workType = "Rotavator", day = 20_001, part = DayPart.Afternoon, startMinute = null, machineId = null,
            operatorPersonId = null, note = "moved", advanceMinor = 50_000, advanceMethod = PaymentMethod.Cash,
        )
        assertEquals(1, h.db.ledgerDao().observeFor(org, TestHarness.CUSTOMER_A).first().size)
        assertEquals(50_000L, plan.observe(org, id).first()!!.booking.advanceMinor)
    }
}
