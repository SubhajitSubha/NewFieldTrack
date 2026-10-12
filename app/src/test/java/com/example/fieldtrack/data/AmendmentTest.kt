package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.repository.WorkError
import com.example.fieldtrack.data.session.Capability
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
 * Correcting work after it has been billed.
 *
 * This is the part of editing that moves real money, so it gets the closest tests:
 * the khata must end up right, and it must get there by adding lines rather than
 * rewriting them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AmendmentTest {

    private lateinit var h: TestHarness
    private val owner = Capabilities.ofOwner()

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After fun tearDown() = h.close()

    private suspend fun billedJob(acres: Double = 3.5, rate: Long = 1_000): String {
        val id = h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Rotavator",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = rate,
            operatorPersonId = TestHarness.DRIVER,
        )
        h.work.setQuantity(TestHarness.ORG_A, id, acres)
        h.work.complete(TestHarness.ORG_A, id)
        return id
    }

    @Test
    fun `correcting an area down adds an adjustment, it does not rewrite the charge`() = runTest {
        val jobId = billedJob(acres = 3.5)   // 3,500
        assertEquals(3_500L, balance())

        val delta = h.work.amendCompletedJob(
            orgId = TestHarness.ORG_A,
            jobId = jobId,
            can = owner,
            quantity = 3.0,
            reason = "Measured again",
        )

        assertEquals(-500L, delta)
        assertEquals(3_000L, balance())

        val lines = h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
        assertEquals("the original charge survives alongside the correction", 2, lines.size)
        assertTrue(lines.any { it.kind == LedgerKind.Charge && it.amountRupees == 3_500L })
        assertTrue(lines.any { it.kind == LedgerKind.Adjustment && it.amountRupees == 500L })
    }

    @Test
    fun `correcting an area up adds a further charge`() = runTest {
        val jobId = billedJob(acres = 3.0)   // 3,000
        val delta = h.work.amendCompletedJob(
            orgId = TestHarness.ORG_A,
            jobId = jobId,
            can = owner,
            quantity = 4.0,
            reason = "Missed half the field",
        )

        assertEquals(1_000L, delta)
        assertEquals(4_000L, balance())
        assertEquals(
            2,
            h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
                .count { it.kind == LedgerKind.Charge },
        )
    }

    @Test
    fun `correcting the rate reprices the whole job`() = runTest {
        val jobId = billedJob(acres = 4.0, rate = 1_000)  // 4,000
        h.work.amendCompletedJob(
            orgId = TestHarness.ORG_A,
            jobId = jobId,
            can = owner,
            rateRupees = 1_200,
            reason = "Agreed rate was higher",
        )
        assertEquals(4_800L, balance())
    }

    @Test
    fun `a correction that changes nothing writes no ledger line`() = runTest {
        val jobId = billedJob(acres = 3.5)
        val before = h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first().size

        val delta = h.work.amendCompletedJob(
            orgId = TestHarness.ORG_A,
            jobId = jobId,
            can = owner,
            note = "Customer asked for a copy",
            reason = "Note only",
        )

        assertEquals(0L, delta)
        assertEquals(
            before,
            h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first().size,
        )
    }

    @Test
    fun `a driver without permission cannot rewrite a bill`() = runTest {
        val jobId = billedJob()
        val plainDriver = Capabilities.of(
            MembershipEntity(
                id = "m", orgId = TestHarness.ORG_A, personId = TestHarness.DRIVER,
                displayName = "Jaswant", role = MemberRole.Operator,
                joinedAt = 0, updatedAt = 0,
            )
        )

        val failure = runCatching {
            h.work.amendCompletedJob(
                orgId = TestHarness.ORG_A, jobId = jobId, can = plainDriver,
                quantity = 99.0, reason = "nope",
            )
        }.exceptionOrNull()

        assertTrue(failure is Capabilities.NotAllowed)
        // The refusal has to be real, not cosmetic: the khata is untouched.
        assertEquals(3_500L, balance())
    }

    @Test
    fun `a trusted driver who closes bills may also correct them`() = runTest {
        val jobId = billedJob()
        val trusted = Capabilities.of(
            MembershipEntity(
                id = "m", orgId = TestHarness.ORG_A, personId = TestHarness.DRIVER,
                displayName = "Jaswant", role = MemberRole.Operator,
                canCloseBills = true, joinedAt = 0, updatedAt = 0,
            )
        )
        assertTrue(Capability.AmendBilledWork in trusted)

        h.work.amendCompletedJob(
            orgId = TestHarness.ORG_A, jobId = jobId, can = trusted,
            quantity = 3.0, reason = "Remeasured",
        )
        assertEquals(3_000L, balance())
    }

    @Test
    fun `a job still running cannot be amended through the billing path`() = runTest {
        val id = h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Rotavator",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_000,
        )
        val failure = runCatching {
            h.work.amendCompletedJob(
                orgId = TestHarness.ORG_A, jobId = id, can = owner,
                quantity = 2.0, reason = "still going",
            )
        }.exceptionOrNull()

        // Open jobs are corrected with the stepper, which has billed nothing yet.
        assertTrue(
            "expected NotFinished, got $failure",
            failure is WorkError.NotFinished,
        )
    }

    private suspend fun balance() =
        h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
}
