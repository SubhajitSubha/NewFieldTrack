package com.example.fieldtrack.data

import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a machine costs, from the database up: who may record it, who can see it,
 * and the queries the cost screens read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineCostTest {

    private lateinit var h: TestHarness
    private val owner = Capabilities.ofOwner()
    private val hour = 3_600_000L
    private val org = TestHarness.ORG_A

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun machine(name: String = "Swaraj 855") =
        h.directory.addMachine(org, name, MachineKind.Tractor)

    private suspend fun job(
        machineId: String,
        hours: Int,
        quantity: Double? = null,
        mode: BillingMode = BillingMode.PerHour,
        unit: MeasureUnit = MeasureUnit.Hour,
        rate: Long = 900,
        type: String = "Rotavator",
    ): String {
        val id = h.work.startJob(
            org, TestHarness.CUSTOMER_A, type, mode, unit, rate,
            machineId = machineId, operatorPersonId = TestHarness.DRIVER,
        )
        h.nowMillis += hours * hour
        if (quantity != null) h.work.setQuantity(org, id, quantity)
        h.work.complete(org, id, TestHarness.DRIVER)
        return id
    }

    private fun only(vararg caps: Capability) = Capabilities(caps.toSet())

    // ------------------------------------------------------- who may record it

    @Test
    fun `an owner records what a machine costs to own, and it is queued for the server`() = runTest {
        val id = machine()
        h.directory.setMachineFinance(org, owner, id, monthlyMinor = 12_000, hoursPerYear = 700)

        val saved = h.directory.findMachineFinance(org, id)!!
        assertEquals(12_000L, saved.monthlyMinor)
        assertEquals(700, saved.hoursPerYear)

        val queued = h.db.outboxDao().nextBatch(100).filter { it.tableName == SyncQueue.TABLE_MACHINE_FINANCE }
        assertEquals(listOf(id), queued.map { it.entityId })
    }

    @Test
    fun `a manager may, a driver may not`() = runTest {
        val id = machine()
        val manager = only(Capability.EditMachines, Capability.SeePeopleCost, Capability.SeeMoney)
        h.directory.setMachineFinance(org, manager, id, 8_000, null)

        for (driver in listOf(
            only(Capability.RecordWork),
            only(Capability.RecordWork, Capability.EditMachines),     // may edit machines, not see the books
            only(Capability.RecordWork, Capability.SeePeopleCost),    // sees the books, may not edit machines
        )) {
            try {
                h.directory.setMachineFinance(org, driver, id, 1, null)
                fail("should have been refused")
            } catch (_: Capabilities.NotAllowed) {
            }
        }
        assertEquals("the refused writes changed nothing", 8_000L,
            h.directory.findMachineFinance(org, id)!!.monthlyMinor)
    }

    @Test
    fun `blank or zero means none, so a paid-off loan stops counting`() = runTest {
        val id = machine()
        h.directory.setMachineFinance(org, owner, id, 9_000, 600)
        h.directory.setMachineFinance(org, owner, id, monthlyMinor = 0, hoursPerYear = null)

        val saved = h.directory.findMachineFinance(org, id)!!
        assertNull(saved.monthlyMinor)
        assertNull(saved.hoursPerYear)
        assertEquals(Costing.Loan.NotEntered, Costing.loanShare(saved, 900.0))
    }

    @Test
    fun `nonsense is refused`() = runTest {
        val id = machine()
        for (bad in listOf<() -> Unit>(
            { runBlocking2 { h.directory.setMachineFinance(org, owner, id, -5, null) } },
            { runBlocking2 { h.directory.setMachineFinance(org, owner, id, 1_000, 9_000) } },
        )) {
            try { bad(); fail("should have been refused") } catch (_: IllegalArgumentException) {}
        }
    }

    private fun runBlocking2(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    @Test
    fun `a record for a machine that is not there is not made`() = runTest {
        h.directory.setMachineFinance(org, owner, "no-such-machine", 5_000, null)
        assertNull(h.directory.findMachineFinance(org, "no-such-machine"))
    }

    // ------------------------------------------------------- reaching the server

    @Test
    fun `the record is marked sent once the server has it, and unsent when changed again`() = runTest {
        val id = machine()
        h.directory.setMachineFinance(org, owner, id, 12_000, 700)
        assertEquals(SyncState.Pending, h.directory.findMachineFinance(org, id)!!.syncState)

        h.db.outboxDao().removeAll(h.db.outboxDao().nextBatch(100))
        assertEquals("accepted: the triggers cover the new table",
            SyncState.Synced, h.directory.findMachineFinance(org, id)!!.syncState)

        h.directory.setMachineFinance(org, owner, id, 13_000, 700)
        assertEquals(SyncState.Pending, h.directory.findMachineFinance(org, id)!!.syncState)
    }

    @Test
    fun `an install that already had the triggers gets the new table covered too`() = runTest {
        val id = machine()
        h.directory.setMachineFinance(org, owner, id, 12_000, null)
        h.db.outboxDao().removeAll(h.db.outboxDao().nextBatch(100))

        // As an upgraded phone is: the old two-table-list triggers exist, and the
        // database is opened again.
        val db = h.db.openHelper.writableDatabase
        db.execSQL("DROP TRIGGER sent_state_on_accept")
        db.execSQL(
            "CREATE TRIGGER sent_state_on_accept AFTER DELETE ON outbox BEGIN " +
                "UPDATE machines SET syncState = 'Synced' WHERE id = OLD.entityId; END"
        )
        com.example.fieldtrack.data.db.SentState.install(db)

        h.directory.setMachineFinance(org, owner, id, 14_000, null)
        h.db.outboxDao().removeAll(h.db.outboxDao().nextBatch(100))
        assertEquals(SyncState.Synced, h.directory.findMachineFinance(org, id)!!.syncState)
    }

    @Test
    fun `a record restored from the server arrives as sent`() = runTest {
        val id = machine()
        RestoreApplier(h.db).apply(
            listOf(
                RemoteRow(
                    SyncQueue.TABLE_MACHINE_FINANCE, id,
                    mapOf(
                        "id" to id, "orgId" to org, "monthlyMinor" to 7_500L,
                        "hoursPerYear" to 500L, "updatedAt" to h.nowMillis, "syncState" to "Pending",
                    ),
                )
            )
        )
        val saved = h.directory.findMachineFinance(org, id)!!
        assertEquals(7_500L, saved.monthlyMinor)
        assertEquals(500, saved.hoursPerYear)
        assertEquals(SyncState.Synced, saved.syncState)
    }

    // --------------------------------------------------- what it cost to run

    @Test
    fun `running cost is read off spent diesel, repairs and finished hours`() = runTest {
        val id = machine()
        job(id, hours = 12)
        job(id, hours = 8)                                 // 20 hours worked
        // Amounts are in paise; the business rounds to the rupee.
        h.work.addFuelForMachine(org, id, 100.0, 900_000)    // yard fill: counts, it was spent
        h.work.recordService(org, id, "Oil", 300_000, null)
        h.directory.setMachineFinance(org, owner, id, 1_000_000, 600)

        val cost = h.directory.observeRunningCosts(org).first().getValue(id)
        assertEquals(20.0, cost.hours, 0.001)
        val r = cost.rounded()
        assertEquals(45_000L, r.diesel)
        assertEquals(15_000L, r.upkeep)
        assertEquals("120,000 over 600 hours", 20_000L, r.loan)
        assertEquals(80_000L, r.total)
    }

    @Test
    fun `running cost leaves out machines that are gone and other businesses`() = runTest {
        val a = machine("Swaraj")
        val other = h.directory.addMachine(TestHarness.ORG_B, "Not ours", MachineKind.Tractor)
        h.work.addFuelForMachine(TestHarness.ORG_B, other, 50.0, 5_000)

        val costs = h.directory.observeRunningCosts(org).first()
        assertTrue(a in costs)
        assertTrue(other !in costs)
    }

    @Test
    fun `work is grouped by what was done and how it was measured`() = runTest {
        val id = machine()
        job(id, hours = 5, quantity = 10.0, mode = BillingMode.PerAcre, unit = MeasureUnit.Bigha, rate = 1_000)
        job(id, hours = 5, quantity = 12.0, mode = BillingMode.PerAcre, unit = MeasureUnit.Bigha, rate = 1_000)
        job(id, hours = 4, quantity = null, type = "Ploughing")        // hourly: quantity is the clock
        h.work.startJob(org, TestHarness.CUSTOMER_A, "Rotavator", BillingMode.Fixed, MeasureUnit.Piece, 5_000, machineId = id)
            .also { h.work.complete(org, it) }                           // fixed price: nothing to divide

        val kinds = h.directory.observeWorkKinds(org, id).first().associateBy { it.workType to it.unit }
        val rotavator = kinds.getValue("Rotavator" to MeasureUnit.Bigha)
        assertEquals(22.0, rotavator.quantity, 0.001)
        assertEquals(10 * 3600L, rotavator.seconds)
        assertEquals(22_000L, rotavator.earnedMinor)
        assertEquals(2, rotavator.jobs)
        assertEquals(4.0, kinds.getValue("Ploughing" to MeasureUnit.Hour).quantity, 0.001)
        assertTrue("fixed-price work has no per-unit cost", kinds.keys.none { it.second == MeasureUnit.Piece })
    }

    // ---------------------------------------------------------- jobs that lost

    @Test
    fun `a job's margin counts its diesel, and a loss is the shortfall`() = runTest {
        val id = machine()
        val loser = job(id, hours = 2, rate = 500)                    // ₹1,000 billed
        h.work.addFuel(org, loser, 20.0, 1_400, TestHarness.DRIVER)   // ₹1,400 of diesel on it
        val fine = job(id, hours = 2, rate = 500)

        val margins = h.db.orgDao().observeJobMargins(org, 0, Long.MAX_VALUE).first().associateBy { it.jobId }
        assertEquals(1_400L, margins.getValue(loser).fuelMinor)
        assertEquals(400L, margins.getValue(loser).lostMinor)
        assertNull(margins.getValue(fine).lostMinor)
    }

    @Test
    fun `a job whose crew is unpriced is not called a loss`() = runTest {
        val id = machine()
        val jobId = job(id, hours = 2, rate = 500)
        h.ledger.recordJobCost(org, owner, jobId, JobCostKind.Labour, headcount = 4,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis)
        h.ledger.recordJobCost(org, owner, jobId, JobCostKind.Other, amountMinor = 5_000,
            note = "Tractor hire", byPersonId = TestHarness.OWNER, atMillis = h.nowMillis)

        val margin = h.db.orgDao().observeJobMargins(org, 0, Long.MAX_VALUE).first().single()
        assertEquals(1, margin.unpriced)
        assertNull("the 5,000 is real but the crew is not yet counted", margin.lostMinor)
    }

    @Test
    fun `once everything is priced a loss is the shortfall`() = runTest {
        val id = machine()
        val jobId = job(id, hours = 2, rate = 500)                    // 1,000 billed
        val crew = h.ledger.recordJobCost(org, owner, jobId, JobCostKind.Labour, headcount = 2,
            byPersonId = TestHarness.OWNER, atMillis = h.nowMillis)
        h.ledger.priceJobCost(org, owner, crew.id, 1_500)

        val margin = h.db.orgDao().observeJobMargins(org, 0, Long.MAX_VALUE).first().single()
        assertEquals(0, margin.unpriced)
        assertEquals(500L, margin.lostMinor)
    }

    // ------------------------------------------------------------------ diesel

    @Test
    fun `diesel per hour is only fuel logged on jobs over only those jobs' time`() = runTest {
        val id = machine()
        val onJob = job(id, hours = 4)
        h.work.addFuel(org, onJob, 12.0, 1_100, TestHarness.DRIVER)
        job(id, hours = 6)                                            // no fuel logged on it
        h.work.addFuelForMachine(org, id, 80.0, 7_000)                // yard fill: belongs to no job

        val burnt = h.db.machineDao().observeFuelOnJobs(org, 0, Long.MAX_VALUE).first().single()
        assertEquals(12.0, burnt.litres, 0.001)
        assertEquals("the six hours with no diesel on them are not counted", 4 * 3600L, burnt.seconds)
        assertEquals(1, burnt.jobs)
    }
}
