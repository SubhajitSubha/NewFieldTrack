package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.PayBasis
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.TeamEventKind
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.repository.PeopleRepository
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.session.Capabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Trust and money between the people of a business: cash a driver holds until it is handed
 * over, a person's particulars, and what their work has earned under their pay terms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleMoneyTest {

    private lateinit var h: TestHarness
    private lateinit var people: PeopleRepository
    private val owner = Capabilities.ofOwner()
    private val driverCan = Capabilities(emptySet())
    private val org = TestHarness.ORG_A

    @Before fun setUp() = runTest {
        h = TestHarness(); h.seed()
        h.db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(org, TestHarness.OWNER), orgId = org, personId = TestHarness.OWNER, displayName = "Harpal",
                role = MemberRole.Owner, joinedAt = 1, updatedAt = 1,
            )
        )
        val team = TeamRepository(db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids)
        people = PeopleRepository(db = h.db, syncQueue = h.syncQueue, team = team, clock = h.clock, ids = h.ids)
    }
    @After fun tearDown() = h.close()

    private suspend fun cashFromFarmer(amount: Long, by: String = TestHarness.DRIVER) =
        h.ledger.recordPayment(org, TestHarness.CUSTOMER_A, amount, PaymentMethod.Cash, byPersonId = by)

    @Test fun `a driver holds the cash they took until it is handed over`() = runTest {
        cashFromFarmer(400_000)
        cashFromFarmer(100_000)
        h.ledger.recordPayment(org, TestHarness.CUSTOMER_A, 50_000, PaymentMethod.Upi, byPersonId = TestHarness.DRIVER)
        assertEquals("cash only: a UPI payment went straight to the bank", 500_000L,
            people.observeCashHeld(org).first()[TestHarness.DRIVER])

        val handed = people.recordHandover(org, owner, TestHarness.DRIVER, 400_000, byPersonId = TestHarness.OWNER)
        assertEquals(100_000L, people.observeCashHeld(org).first()[TestHarness.DRIVER])

        people.voidHandover(org, owner, handed, byPersonId = TestHarness.OWNER)
        assertEquals("a hand-over taken back means he holds it again", 500_000L,
            people.observeCashHeld(org).first()[TestHarness.DRIVER])
        assertEquals(TestHarness.OWNER, h.db.orgDao().findHandover(org, handed)!!.voidedByPersonId)
    }

    @Test fun `a cash payment put right is not cash anybody holds`() = runTest {
        val id = cashFromFarmer(200_000)
        val entry = h.db.ledgerDao().observeSince(org, 0).first().single { it.id == id }
        h.ledger.reverse(org, entry, "wrong farmer", byPersonId = TestHarness.OWNER)
        assertNull(people.observeCashHeld(org).first()[TestHarness.DRIVER])
    }

    @Test fun `only somebody who receives the money may write a hand-over down`() = runTest {
        cashFromFarmer(10_000)
        val refused = runCatching { people.recordHandover(org, driverCan, TestHarness.DRIVER, 10_000, byPersonId = TestHarness.DRIVER) }
        assertTrue(refused.isFailure)
    }

    @Test fun `a person may change their own particulars, and an owner's change is in the log`() = runTest {
        people.saveDetails(org, driverCan, TestHarness.DRIVER, byPersonId = TestHarness.DRIVER,
            payoutUpi = "jaswant@upi", payoutBank = null, licenceNumber = "pb10 2019 0001234", licenceExpiresDay = 21000,
            emergencyName = "Kuldeep", emergencyPhone = "9876500000")
        val mine = people.observeDetails(org, TestHarness.DRIVER).first()!!
        assertEquals("jaswant@upi", mine.payoutUpi)
        assertEquals("PB10 2019 0001234", mine.licenceNumber)
        assertTrue("their own change needs no note", h.db.orgDao().observeTeamEvents(org).first().none { it.kind == TeamEventKind.DetailsChanged })

        people.saveDetails(org, owner, TestHarness.DRIVER, byPersonId = TestHarness.OWNER,
            payoutUpi = "someone-else@upi", payoutBank = null, licenceNumber = null, licenceExpiresDay = null,
            emergencyName = null, emergencyPhone = null)
        val logged = h.db.orgDao().observeTeamEvents(org).first().single { it.kind == TeamEventKind.DetailsChanged }
        assertEquals(TestHarness.DRIVER, logged.aboutPersonId)
        assertEquals(TestHarness.OWNER, logged.byPersonId)

        val refused = runCatching {
            people.saveDetails(org, driverCan, TestHarness.OWNER, byPersonId = TestHarness.DRIVER,
                payoutUpi = "x@upi", payoutBank = null, licenceNumber = null, licenceExpiresDay = null,
                emergencyName = null, emergencyPhone = null)
        }
        assertTrue("nobody else's particulars without the right to run the team", refused.isFailure)
    }

    @Test fun `pay by the day adds up from the days worked, and wages paid come off it`() = runTest {
        people.savePayTerms(org, owner, TestHarness.DRIVER, TestHarness.OWNER, PayBasis.PerDay, rateMinor = 60_000, fromMillis = 0)
        repeat(2) {
            val job = h.work.startJob(org, TestHarness.CUSTOMER_A, "ploughing", BillingMode.PerUnit, MeasureUnit.Acre, 100_000,
                operatorPersonId = TestHarness.DRIVER)
            h.advanceMinutes(60)
            h.work.complete(org, job)
            h.advanceMinutes(24 * 60)
        }
        h.ledger.recordPayout(org, owner, amountMinor = 50_000, covers = PayoutCovers.Day, atMillis = h.clock.now(),
            byPersonId = TestHarness.OWNER, toPersonId = TestHarness.DRIVER, toName = "Jaswant")
        val wages = people.observeWages(org).first()[TestHarness.DRIVER]!!
        assertEquals("two days at 600", 120_000L, wages.earnedMinor)
        assertEquals(50_000L, wages.paidMinor)
        assertEquals(70_000L, wages.leftMinor)
        assertTrue("the change of terms is logged, without the figure",
            h.db.orgDao().observeTeamEvents(org).first().any { it.kind == TeamEventKind.PayTermsChanged && it.detail == null })
    }

    @Test fun `a month's pay builds up a thirtieth a day`() = runTest {
        val start = h.clock.now()
        people.savePayTerms(org, owner, TestHarness.DRIVER, TestHarness.OWNER, PayBasis.Monthly, rateMinor = 1_500_000, fromMillis = start)
        h.advanceMinutes(15 * 24 * 60)
        assertEquals(750_000L, people.observeWages(org).first()[TestHarness.DRIVER]!!.earnedMinor)
    }

    @Test fun `only somebody who pays people sets how they are paid`() = runTest {
        val refused = runCatching {
            people.savePayTerms(org, driverCan, TestHarness.DRIVER, TestHarness.DRIVER, PayBasis.PerDay, 100_000, 0)
        }
        assertTrue(refused.isFailure)
    }
}
