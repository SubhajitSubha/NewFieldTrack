package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Carrying an install's records onto the identity it can actually prove.
 *
 * Exercised against the DAO rather than SessionStore, because the preference side
 * needs a real Context and the part that must not go wrong is the SQL: an identity
 * rewritten in some tables and not others leaves the rows disagreeing about who did
 * the work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityAdoptionTest {

    private lateinit var h: TestHarness

    @Before
    fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After
    fun tearDown() = h.close()

    private suspend fun adoptAll(from: String, to: String) {
        h.db.orgDao().adoptMemberships(from, to)
        h.db.orgDao().adoptInvites(from, to)
        h.db.jobDao().adoptJobs(from, to)
        h.db.jobDao().adoptJobEvents(from, to)
        h.db.machineDao().adoptFuel(from, to)
        h.db.machineDao().adoptBreakdowns(from, to)
        h.db.ledgerDao().adoptEntries(from, to)
    }

    private suspend fun startJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Rotavator",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 1_000,
        machineId = "machine-1",
        operatorPersonId = TestHarness.DRIVER,
    )

    @Test
    fun `work follows the person onto their proven identity`() = runTest {
        val jobId = startJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 2.0)
        h.work.complete(TestHarness.ORG_A, jobId)

        adoptAll(TestHarness.DRIVER, UID)

        val job = h.db.jobDao().find(TestHarness.ORG_A, jobId)
        assertEquals("the job keeps its operator, under the new name", UID, job?.operatorPersonId)
        val events = h.work.observeEvents(jobId).first()
        assertTrue("and so does everything that happened on it", events.isNotEmpty())
        assertTrue(events.all { it.byPersonId == null || it.byPersonId == UID })
    }

    @Test
    fun `the membership is rekeyed, not duplicated`() = runTest {
        val before = h.db.orgDao().observeEveryone(TestHarness.ORG_A).first().size

        adoptAll(TestHarness.DRIVER, UID)

        val after = h.db.orgDao().observeEveryone(TestHarness.ORG_A).first()
        assertEquals("nobody is added or lost", before, after.size)
        val moved = after.single { it.personId == UID }
        assertEquals(
            "the id is rebuilt so a rule can still find it",
            "${TestHarness.ORG_A}_$UID",
            moved.id,
        )
    }

    @Test
    fun `money collected keeps its collector`() = runTest {
        h.ledger.recordPayment(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            amountRupees = 5_000,
            method = PaymentMethod.Cash,
            byPersonId = TestHarness.DRIVER,
        )

        adoptAll(TestHarness.DRIVER, UID)

        val entries = h.ledger.observeLedger(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
        assertTrue(entries.isNotEmpty())
        assertTrue(
            "a payment with no collector is a payment nobody is answerable for",
            entries.filter { it.byPersonId != null }.all { it.byPersonId == UID },
        )
    }

    @Test
    fun `somebody else's records are left alone`() = runTest {
        val jobId = startJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 1.0)
        h.work.complete(TestHarness.ORG_A, jobId)

        // Adopting for a different person must not sweep up this one's work.
        adoptAll(TestHarness.OWNER, UID)

        val job = h.db.jobDao().find(TestHarness.ORG_A, jobId)
        assertEquals(TestHarness.DRIVER, job?.operatorPersonId)
    }

    @Test
    fun `a person in two businesses carries both memberships`() = runTest {
        adoptAll(TestHarness.DRIVER, UID)

        val inA = h.db.orgDao().membership(TestHarness.ORG_A, UID)
        val inB = h.db.orgDao().membership(TestHarness.ORG_B, UID)
        assertNotNull("still works for the first", inA)
        assertNotNull("and the second", inB)
        assertEquals("${TestHarness.ORG_A}_$UID", inA?.id)
        assertEquals("${TestHarness.ORG_B}_$UID", inB?.id)
    }

    @Test
    fun `the old identity is left owning nothing`() = runTest {
        adoptAll(TestHarness.DRIVER, UID)

        assertEquals(0, h.db.orgDao().membershipCountFor(TestHarness.DRIVER))
        assertEquals(2, h.db.orgDao().membershipCountFor(UID))
    }

    @Test
    fun `an install with no history has nothing to carry`() = runTest {
        assertEquals(0, h.db.orgDao().membershipCountFor("never-seen-before"))
    }

    private companion object {
        const val UID = "DzaCNhtRYhhEPvTyOlNo5ZeWDem2"
    }
}
