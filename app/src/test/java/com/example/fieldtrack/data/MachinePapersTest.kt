package com.example.fieldtrack.data

import com.example.fieldtrack.data.costing.Assets
import com.example.fieldtrack.data.costing.PaperStatus
import com.example.fieldtrack.data.costing.Papers
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.PaperKind
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import com.example.fieldtrack.data.db.entity.MachinePaperEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
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

/** What a machine is worth, and the papers it has to carry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachinePapersTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A
    private val owner = Capabilities.ofOwner()

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private fun finance(price: Long?, day: Long?, resale: Long? = null, life: Int? = null) = MachineFinanceEntity(
        id = "m", orgId = org, purchasedDay = day, purchaseMinor = price, resaleMinor = resale, lifeYears = life, updatedAt = 1,
    )

    // ------------------------------------------------------------------ worth

    @Test fun `a machine loses an equal share of its price each year down to what it should fetch`() {
        val f = finance(price = 1_000_000_00, day = 0, resale = 200_000_00, life = 8)
        assertEquals(1_000_000_00L, Assets.bookValue(f, today = 0))
        assertTrue("a year in, about a year's share has come off", Math.abs(Assets.bookValue(f, today = 365)!! - 900_000_00L) < 100_000L)
        assertEquals(200_000_00L, Assets.bookValue(f, today = 366 * 8))
        assertEquals("never below what it will fetch, however old", 200_000_00L, Assets.bookValue(f, today = 365 * 30))
        assertEquals(100_000_00L, Assets.yearlyDepreciation(f))
    }

    @Test fun `with no price or no date there is no worth to state, and no resale means it runs down to nothing`() {
        assertNull(Assets.bookValue(null, 100))
        assertNull(Assets.bookValue(finance(price = 500, day = null), 100))
        assertNull(Assets.bookValue(finance(price = null, day = 0), 100))
        assertEquals(0L, Assets.bookValue(finance(price = 1_000, day = 0, life = 5), today = 365 * 6))
        assertEquals("a machine bought tomorrow is still worth its price", 1_000L, Assets.bookValue(finance(price = 1_000, day = 50), today = 10))
    }

    // ----------------------------------------------------------------- papers

    private fun paper(day: Long?) = MachinePaperEntity(id = "p", orgId = org, machineId = "m", kind = PaperKind.Insurance, expiresDay = day, updatedAt = 1)

    @Test fun `a paper is valid on its last day, soon within the window, and expired after`() {
        assertEquals(PaperStatus.NoDate, Papers.status(paper(null), 100))
        assertEquals(PaperStatus.Valid(120), Papers.status(paper(220), 100))
        assertEquals(PaperStatus.Soon(30), Papers.status(paper(130), 100))
        assertEquals("the last valid day", PaperStatus.Soon(0), Papers.status(paper(100), 100))
        assertEquals(PaperStatus.Expired(1), Papers.status(paper(99), 100))
        assertEquals("a wider window warns earlier", PaperStatus.Soon(45), Papers.status(paper(145), 100, warnDays = 60))
    }

    @Test fun `only what is running out or out is worth mentioning, soonest first`() {
        val list = listOf(paper(500).copy(id = "a"), paper(110).copy(id = "b"), paper(90).copy(id = "c"), paper(null).copy(id = "d"), paper(105).copy(id = "e", deletedAt = 5))
        assertEquals(listOf("c", "b"), Papers.needingAttention(list, today = 100).map { it.id })
    }

    @Test fun `papers are saved, corrected, and removed, and every phone hears of each`() = runTest {
        val machine = h.directory.addMachine(org, "Swaraj", MachineKind.Tractor)
        val id = h.directory.savePaper(org, owner, machine, PaperKind.Insurance, " POL-77 ", 20_000, "Third party", photoId = null)
        assertEquals("POL-77", h.db.machineDao().findPaper(org, id)!!.number)
        h.directory.savePaper(org, owner, machine, PaperKind.Insurance, "POL-77", 20_400, null, paperId = id)
        assertEquals(20_400L, h.db.machineDao().findPaper(org, id)!!.expiresDay)
        assertEquals(1, h.directory.observePapersFor(org, machine).first().size)
        h.directory.removePaper(org, owner, id)
        assertEquals(0, h.directory.observePapersFor(org, machine).first().size)
        assertNotNull("kept as a deleted row", h.db.machineDao().findPaper(org, id)!!.deletedAt)
        val queued = h.db.outboxDao().nextBatch(100).filter { it.tableName == "machine_papers" }
        assertEquals(3, queued.size)
    }

    @Test fun `a driver cannot change a machine's papers`() = runTest {
        val machine = h.directory.addMachine(org, "Swaraj", MachineKind.Tractor)
        val driver = Capabilities(setOf(Capability.RecordWork))
        var refused = false
        try { h.directory.savePaper(org, driver, machine, PaperKind.Permit, null, null, null) } catch (e: Exception) { refused = true }
        assertTrue(refused)
    }

    @Test fun `what a machine is worth is written with its finance, and only by somebody who may see what the business pays`() = runTest {
        val machine = h.directory.addMachine(org, "Swaraj", MachineKind.Tractor)
        h.directory.setMachineFinance(org, owner, machine, null, null, purchasedDay = 19_000, purchaseMinor = 7_500_000_00, resaleMinor = 1_500_000_00, lifeYears = 12)
        val f = h.db.machineDao().findFinance(org, machine)!!
        assertEquals(7_500_000_00L, f.purchaseMinor); assertEquals(12, f.lifeYears); assertEquals(19_000L, f.purchasedDay)
        var refused = false
        try { h.directory.setMachineFinance(org, Capabilities(setOf(Capability.RecordWork)), machine, null, null, purchaseMinor = 1) } catch (e: Exception) { refused = true }
        assertTrue(refused)
    }
}
