package com.example.fieldtrack.data

import com.example.fieldtrack.data.costing.Hire
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.HireBasis
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineOwner
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.dao.HireFacts
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import com.example.fieldtrack.data.repository.HireRepository
import com.example.fieldtrack.data.session.Capabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A machine that belongs to somebody else: what its work earns them, and what comes off as they are paid. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineHireTest {

    private lateinit var h: TestHarness
    private val machineId = "machine-1"
    private val can = Capabilities.ofOwner()

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
        h.db.machineDao().upsert(
            MachineEntity(id = machineId, orgId = TestHarness.ORG_A, name = "Swaraj 744", kind = MachineKind.Tractor, createdAt = 0, updatedAt = 0)
        )
    }

    @After fun tearDown() = h.close()

    private fun finance(basis: HireBasis, rate: Long? = null, share: Int? = null) = MachineFinanceEntity(
        id = machineId, orgId = TestHarness.ORG_A, updatedAt = 1,
        ownerKind = MachineOwner.Outsider, ownerName = "Gurnam Singh",
        hireBasis = basis, hireRateMinor = rate, hireSharePercent = share, hireFrom = 0,
    )

    @Test fun `each way of paying works out what the work earned the owner`() {
        val facts = HireFacts(machineId, seconds = 5_400, landQuantity = 4.0, days = 3, amountMinor = 40_000)
        assertEquals("an hour and a half at 400 an hour", 600L, Hire.owedMinor(finance(HireBasis.PerHour, rate = 400), facts))
        assertEquals("three days at 500", 1_500L, Hire.owedMinor(finance(HireBasis.PerDay, rate = 500), facts))
        assertEquals("four acres at 150", 600L, Hire.owedMinor(finance(HireBasis.PerAcre, rate = 150), facts))
        assertEquals("a quarter of what it billed", 10_000L, Hire.owedMinor(finance(HireBasis.Share, share = 25), facts))
    }

    @Test fun `a machine that is the business's own, or has no terms yet, is owed nothing`() {
        val facts = HireFacts(machineId, 3_600, 1.0, 1, 1_000)
        assertEquals(0L, Hire.owedMinor(null, facts))
        assertEquals(0L, Hire.owedMinor(MachineFinanceEntity(id = machineId, orgId = "o", updatedAt = 1), facts))
        assertFalse(Hire.applies(finance(HireBasis.PerHour, 400).copy(hireFrom = null)))
    }

    @Test fun `a person is the same person however their name was typed`() {
        assertEquals(Hire.nameKey("Gurnam Singh"), Hire.nameKey("  gurnam   SINGH "))
        assertEquals("p:abc", Hire.payeeKey("abc", "Whoever"))
        assertEquals(Hire.nameKey("Gurnam Singh"), Hire.payeeKey(null, "gurnam singh"))
        assertNull(Hire.payeeKey(null, " "))
    }

    private suspend fun hireOutsider(basis: HireBasis, rate: Long? = null, share: Int? = null) =
        h.directory.setMachineOwnership(
            TestHarness.ORG_A, can, machineId, MachineOwner.Outsider, ownerName = "Gurnam Singh",
            basis = basis, rateMinor = rate, sharePercent = share,
        )

    private suspend fun workAnHour(quantity: Double = 2.0): String {
        val id = h.work.startJob(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator",
            billingMode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rateMinor = 1_000,
            machineId = machineId, operatorPersonId = TestHarness.DRIVER,
        )
        h.work.setQuantity(TestHarness.ORG_A, id, quantity)
        h.advanceMinutes(60)
        h.work.complete(TestHarness.ORG_A, id, byPersonId = TestHarness.DRIVER)
        return id
    }

    @Test fun `what is owed builds up from finished work and comes down as the owner is paid`() = runTest {
        hireOutsider(HireBasis.PerHour, rate = 40_000)
        workAnHour()

        val repo = HireRepository(h.db)
        val before = repo.observe(TestHarness.ORG_A).first().single()
        assertEquals("Gurnam Singh", before.name)
        assertEquals(40_000L, before.owedMinor)
        assertEquals(40_000L, before.balanceMinor)

        h.ledger.recordPayout(
            TestHarness.ORG_A, can, amountMinor = 15_000, covers = PayoutCovers.Hire, atMillis = 5,
            byPersonId = TestHarness.OWNER, toName = "gurnam singh",
        )
        val after = repo.observe(TestHarness.ORG_A).first().single()
        assertEquals(15_000L, after.paidMinor)
        assertEquals("the name is matched however it was typed", 25_000L, after.balanceMinor)
    }

    @Test fun `work finished before the hire began is not owed for`() = runTest {
        workAnHour()
        h.advanceMinutes(1)   // the agreement is made after the work was done
        hireOutsider(HireBasis.PerHour, rate = 40_000)
        assertEquals(0L, HireRepository(h.db).observe(TestHarness.ORG_A).first().single().owedMinor)
    }

    @Test fun `a team member's machine is owed to that member by id`() = runTest {
        h.directory.setMachineOwnership(
            TestHarness.ORG_A, can, machineId, MachineOwner.Member,
            ownerPersonId = TestHarness.DRIVER, basis = HireBasis.Share, sharePercent = 50,
        )
        workAnHour(quantity = 3.0)   // 3 acres at 1,000 = 3,000 billed
        val line = HireRepository(h.db).observe(TestHarness.ORG_A).first().single()
        assertEquals(TestHarness.DRIVER, line.personId)
        assertEquals(1_500L, line.owedMinor)
    }

    @Test fun `only a window of the work is counted when a season is asked for`() = runTest {
        hireOutsider(HireBasis.PerHour, rate = 40_000)
        workAnHour()
        val repo = HireRepository(h.db)
        assertEquals(40_000L, repo.observeOwedBetween(TestHarness.ORG_A, 0, Long.MAX_VALUE).first())
        assertEquals("a window the work is not in", 0L, repo.observeOwedBetween(TestHarness.ORG_A, 0, 1).first())
    }

    @Test fun `setting whose machine it is keeps the loan and worth, and editing the loan keeps whose it is`() = runTest {
        h.directory.setMachineFinance(
            TestHarness.ORG_A, can, machineId, monthlyMinor = 500_000, hoursPerYear = 600,
            purchasedDay = 19_000, purchaseMinor = 90_000_000, resaleMinor = 20_000_000, lifeYears = 10,
        )
        hireOutsider(HireBasis.PerDay, rate = 100_000)
        h.directory.setLoan(TestHarness.ORG_A, can, machineId, monthlyMinor = 300_000, hoursPerYear = 400)
        h.directory.setMachineFinance(
            TestHarness.ORG_A, can, machineId, monthlyMinor = 300_000, hoursPerYear = 400,
            purchasedDay = 19_100, purchaseMinor = 80_000_000, resaleMinor = 10_000_000, lifeYears = 8,
        )

        val f = h.db.machineDao().findFinance(TestHarness.ORG_A, machineId)!!
        assertEquals(MachineOwner.Outsider, f.ownerKind)
        assertEquals(HireBasis.PerDay, f.hireBasis)
        assertEquals(100_000L, f.hireRateMinor)
        assertEquals(80_000_000L, f.purchaseMinor)
        assertEquals(300_000L, f.monthlyMinor)

        h.directory.setLoan(TestHarness.ORG_A, can, machineId, monthlyMinor = null, hoursPerYear = null)
        assertEquals("the worth is not blanked by editing the loan", 80_000_000L,
            h.db.machineDao().findFinance(TestHarness.ORG_A, machineId)!!.purchaseMinor)
    }

    @Test fun `going back to the business's own clears the terms`() = runTest {
        hireOutsider(HireBasis.PerHour, rate = 40_000)
        h.directory.setMachineOwnership(TestHarness.ORG_A, can, machineId, MachineOwner.Own)
        val f = h.db.machineDao().findFinance(TestHarness.ORG_A, machineId)!!
        assertNull(f.ownerKind); assertNull(f.hireBasis); assertNull(f.hireFrom)
        assertTrue(HireRepository(h.db).observe(TestHarness.ORG_A).first().isEmpty())
    }

    @Test fun `somebody else's machine needs a way of paying, and a share is a real share`() = runTest {
        val bad = listOf<suspend () -> Unit>(
            { h.directory.setMachineOwnership(TestHarness.ORG_A, can, machineId, MachineOwner.Outsider, ownerName = "A", basis = null) },
            { h.directory.setMachineOwnership(TestHarness.ORG_A, can, machineId, MachineOwner.Outsider, ownerName = "A", basis = HireBasis.PerHour, rateMinor = 0) },
            { h.directory.setMachineOwnership(TestHarness.ORG_A, can, machineId, MachineOwner.Outsider, ownerName = "A", basis = HireBasis.Share, sharePercent = 150) },
            { h.directory.setMachineOwnership(TestHarness.ORG_A, can, machineId, MachineOwner.Outsider, ownerName = " ", basis = HireBasis.PerHour, rateMinor = 5) },
            { h.directory.setMachineOwnership(TestHarness.ORG_A, can, machineId, MachineOwner.Member, ownerPersonId = null, basis = HireBasis.PerHour, rateMinor = 5) },
        )
        bad.forEach { attempt ->
            val threw = runCatching { attempt() }.exceptionOrNull() is IllegalArgumentException
            assertTrue("refused", threw)
        }
    }
}
