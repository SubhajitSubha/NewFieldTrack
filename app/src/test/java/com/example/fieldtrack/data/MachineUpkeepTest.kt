package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.MachineEntity
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
 * A machine that breaks has to be able to come back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineUpkeepTest {

    private lateinit var h: TestHarness

    @Before
    fun setUp() = runTest {
        h = TestHarness()
        h.seed()
        h.db.machineDao().upsert(
            MachineEntity(
                id = MACHINE,
                orgId = TestHarness.ORG_A,
                name = "Swaraj 744",
                kind = MachineKind.Tractor,
                hoursUsed = 1_000.0,
                serviceIntervalHours = 280.0,
                lastServiceAtHours = 800.0,
                createdAt = h.nowMillis,
                updatedAt = h.nowMillis,
            )
        )
    }

    @After
    fun tearDown() = h.close()

    private suspend fun status() =
        h.db.machineDao().find(TestHarness.ORG_A, MACHINE)?.status

    private suspend fun startJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Rotavator",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 1_000,
        machineId = MACHINE,
        operatorPersonId = TestHarness.DRIVER,
    )

    private suspend fun breakIt(jobId: String) = h.work.reportBreakdown(
        orgId = TestHarness.ORG_A,
        jobId = jobId,
        category = BreakdownCategory.Hydraulics,
        note = "pump seized",
    )

    private suspend fun openBreakdownId(): String =
        h.work.observeOpenBreakdowns(TestHarness.ORG_A).first().first().id

    // -------------------------------------------------------------- the trap itself

    @Test
    fun `a machine reported broken can be marked fixed`() = runTest {
        val jobId = startJob()
        breakIt(jobId)
        assertEquals(MachineStatus.Down, status())

        h.work.resolveBreakdown(TestHarness.ORG_A, openBreakdownId())

        assertEquals(
            "an honest breakdown report must not cost the machine",
            MachineStatus.Working,
            status(),
        )
    }

    @Test
    fun `fixing it does not require throwing the job away`() = runTest {
        val jobId = startJob()
        h.advanceMinutes(90)
        breakIt(jobId)

        h.work.resolveBreakdown(TestHarness.ORG_A, openBreakdownId())
        h.work.resume(TestHarness.ORG_A, jobId)

        val job = h.db.jobDao().find(TestHarness.ORG_A, jobId)
        assertNotNull("the day's work survives the repair", job)
        assertEquals(com.example.fieldtrack.data.db.JobStatus.Running, job?.status)
        assertEquals(MachineStatus.Working, status())
    }

    @Test
    fun `a machine with no job left goes back to the yard`() = runTest {
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Tyre, "puncture",
        )
        assertEquals(MachineStatus.Down, status())

        h.work.resolveBreakdown(TestHarness.ORG_A, openBreakdownId())

        assertEquals(MachineStatus.Idle, status())
    }

    @Test
    fun `two faults means fixing one is not enough`() = runTest {
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Tyre, "puncture",
        )
        h.advanceMinutes(5)
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Engine, "overheating",
        )

        val first = h.work.observeOpenBreakdowns(TestHarness.ORG_A).first().last().id
        h.work.resolveBreakdown(TestHarness.ORG_A, first)

        assertEquals("still broken", MachineStatus.Down, status())
        assertEquals(1, h.work.observeOpenBreakdowns(TestHarness.ORG_A).first().size)
    }

    // ------------------------------------------------ status told from the facts

    @Test
    fun `finishing a job does not clear a breakdown that is still open`() = runTest {
        val jobId = startJob()
        breakIt(jobId)
        h.advanceMinutes(30)

        h.work.setQuantity(TestHarness.ORG_A, jobId, 2.0)
        h.work.complete(TestHarness.ORG_A, jobId)

        assertEquals(
            "a tractor with a seized pump must not read as free to send out",
            MachineStatus.Down,
            status(),
        )
    }

    @Test
    fun `discarding a job does not clear a breakdown either`() = runTest {
        val jobId = startJob()
        breakIt(jobId)

        h.work.discard(TestHarness.ORG_A, jobId)

        assertEquals(MachineStatus.Down, status())
    }

    @Test
    fun `resuming a job corrects a machine left looking free`() = runTest {
        val jobId = startJob()
        breakIt(jobId)
        h.work.resolveBreakdown(TestHarness.ORG_A, openBreakdownId())

        // Databases written by the earlier builds really do hold this: a machine
        // marked Idle with a live job still on it. An Idle-looking machine is the
        // one that gets picked for the next job by mistake, so resuming has to put
        // the record straight rather than trust what is stored.
        val stale = h.db.machineDao().find(TestHarness.ORG_A, MACHINE)!!
        h.db.machineDao().upsert(stale.copy(status = MachineStatus.Idle))

        h.work.resume(TestHarness.ORG_A, jobId)

        assertEquals(MachineStatus.Working, status())
    }

    @Test
    fun `a resolved breakdown cannot be resolved twice`() = runTest {
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Tyre, "puncture",
        )
        val id = openBreakdownId()
        h.work.resolveBreakdown(TestHarness.ORG_A, id)
        val firstResolvedAt = h.db.machineDao().breakdownById(id)?.resolvedAt

        h.advanceMinutes(60)
        h.work.resolveBreakdown(TestHarness.ORG_A, id)

        assertEquals(firstResolvedAt, h.db.machineDao().breakdownById(id)?.resolvedAt)
    }

    @Test
    fun `one business cannot close another's breakdown`() = runTest {
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Tyre, "puncture",
        )
        val id = openBreakdownId()

        h.work.resolveBreakdown(TestHarness.ORG_B, id)

        assertNull(h.db.machineDao().breakdownById(id)?.resolvedAt)
        assertEquals(MachineStatus.Down, status())
    }

    @Test
    fun `a startup pass corrects statuses written by the old behaviour`() = runTest {
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Engine, "seized",
        )
        // Exactly what the shipped databases hold: an open breakdown, and a machine
        // stored as free because finishing a job overruled it.
        val stale = h.db.machineDao().find(TestHarness.ORG_A, MACHINE)!!
        h.db.machineDao().upsert(stale.copy(status = MachineStatus.Idle))

        h.work.reconcileMachineStatuses(TestHarness.ORG_A)

        assertEquals(
            "the fleet must not count none down while naming one that is",
            MachineStatus.Down,
            status(),
        )
    }

    @Test
    fun `the startup pass leaves correct statuses alone`() = runTest {
        val jobId = startJob()
        assertEquals(MachineStatus.Working, status())

        h.work.reconcileMachineStatuses(TestHarness.ORG_A)

        assertEquals(MachineStatus.Working, status())
        assertNotNull(h.db.jobDao().find(TestHarness.ORG_A, jobId))
    }

    // ------------------------------------------------------------------- servicing

    @Test
    fun `logging a service moves the next one along`() = runTest {
        h.work.recordService(
            orgId = TestHarness.ORG_A,
            machineId = MACHINE,
            description = "Oil & filter change",
            costRupees = 2_100,
            vendor = "Gill Motors",
        )

        val machine = h.db.machineDao().find(TestHarness.ORG_A, MACHINE)
        assertEquals(1_000.0, machine?.lastServiceAtHours!!, 0.01)
        val records = h.work.observeService(TestHarness.ORG_A, MACHINE).first()
        assertEquals(1, records.size)
        assertEquals(2_100L, records.first().costRupees)
        assertEquals("Gill Motors", records.first().vendor)
    }

    @Test
    fun `logging the repair closes the breakdown it fixed`() = runTest {
        val jobId = startJob()
        breakIt(jobId)
        val breakdownId = openBreakdownId()

        h.work.recordService(
            orgId = TestHarness.ORG_A,
            machineId = MACHINE,
            description = "New hydraulic pump",
            costRupees = 8_400,
            closesBreakdownId = breakdownId,
        )

        assertNotNull(h.db.machineDao().breakdownById(breakdownId)?.resolvedAt)
        assertTrue(h.work.observeOpenBreakdowns(TestHarness.ORG_A).first().isEmpty())
        assertEquals(MachineStatus.Working, status())
    }

    @Test
    fun `a machine overdue for service is listed`() = runTest {
        // Due at 800 + 280 = 1080; it is on 1000, so 80 hours out.
        val due = h.db.machineDao().observeServiceDue(TestHarness.ORG_A, 100.0).first()
        assertEquals(1, due.size)
        assertEquals(MACHINE, due.first().id)
    }

    // ------------------------------------------------------------ reaching the server

    @Test
    fun `every upkeep record is queued for the server`() = runTest {
        h.work.addFuelForMachine(TestHarness.ORG_A, MACHINE, litres = 40.0, costRupees = 3_800)
        h.work.reportBreakdownForMachine(
            TestHarness.ORG_A, MACHINE, BreakdownCategory.Engine, "overheating",
        )
        h.work.recordService(TestHarness.ORG_A, MACHINE, "Oil change", 2_100)
        h.work.resolveBreakdown(TestHarness.ORG_A, openBreakdownId())

        val queued = h.db.outboxDao().nextBatch(500).map { it.tableName }.toSet()
        assertTrue(SyncQueue.TABLE_FUEL in queued)
        assertTrue(SyncQueue.TABLE_BREAKDOWNS in queued)
        assertTrue(SyncQueue.TABLE_SERVICE in queued)
        assertTrue(SyncQueue.TABLE_MACHINES in queued)
    }

    @Test
    fun `diesel can be put in with no job running`() = runTest {
        h.work.addFuelForMachine(TestHarness.ORG_A, MACHINE, litres = 40.0, costRupees = 3_800)

        val entries = h.work.observeFuel(TestHarness.ORG_A, MACHINE).first()
        assertEquals(1, entries.size)
        assertNull("filled in the yard, not on a job", entries.first().jobId)
        assertEquals(3_800L, entries.first().costRupees)
    }

    private companion object {
        const val MACHINE = "machine-1"
    }
}
