package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.repository.WorkError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The actions behind the "more" sheet on a running job.
 *
 * Each of these happens in a field with the engine running, so the rules that matter
 * are about what must NOT follow: fuel never reaches the customer's bill, a breakdown
 * stops the clock rather than the job, and a rate may only be edited while nothing
 * has been billed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobActionsTest {

    private lateinit var h: TestHarness
    private val machineId = "machine-1"

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
        h.db.machineDao().upsert(
            MachineEntity(
                id = machineId, orgId = TestHarness.ORG_A, name = "Swaraj 744",
                kind = MachineKind.Tractor, createdAt = 0, updatedAt = 0,
            )
        )
    }

    @After fun tearDown() = h.close()

    private suspend fun runningJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Rotavator",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 1_000,
        machineId = machineId,
        operatorPersonId = TestHarness.DRIVER,
    )

    private suspend fun job(id: String) =
        h.work.observeJob(TestHarness.ORG_A, id).first()!!

    private suspend fun events(id: String) =
        h.work.observeEvents(id).first().map { it.type }

    @Test
    fun `the rate can be corrected while the job is still open`() = runTest {
        val id = runningJob()
        h.work.setRate(TestHarness.ORG_A, id, 1_200)

        assertEquals(1_200L, job(id).rateRupees)
        assertTrue(JobEventType.RateChanged in events(id))
    }

    @Test
    fun `correcting the rate before billing moves no money`() = runTest {
        val id = runningJob()
        h.work.setQuantity(TestHarness.ORG_A, id, 3.0)
        h.work.setRate(TestHarness.ORG_A, id, 1_200)

        // Nothing is owed until the job is completed, whatever the rate says.
        assertEquals(0L, balance())
        h.work.complete(TestHarness.ORG_A, id)
        assertEquals("billed at the corrected rate", 3_600L, balance())
    }

    @Test
    fun `a finished job's rate is not editable this way`() = runTest {
        val id = runningJob()
        h.work.complete(TestHarness.ORG_A, id)

        val failure = runCatching {
            h.work.setRate(TestHarness.ORG_A, id, 9_999)
        }.exceptionOrNull()

        // It has to go through amendCompletedJob, which writes a khata line.
        assertTrue("expected AlreadyFinished, got $failure", failure is WorkError.AlreadyFinished)
    }

    @Test
    fun `fuel is logged against the job and never billed to the customer`() = runTest {
        val id = runningJob()
        h.work.setQuantity(TestHarness.ORG_A, id, 2.0)
        h.work.addFuel(TestHarness.ORG_A, id, litres = 18.0, costRupees = 1_700)

        val fuel = h.db.machineDao().observeFuel(TestHarness.ORG_A, machineId).first()
        assertEquals(1, fuel.size)
        assertEquals(18.0, fuel.first().litres, 0.001)
        assertEquals(id, fuel.first().jobId)
        assertTrue(JobEventType.FuelAdded in events(id))

        h.work.complete(TestHarness.ORG_A, id)
        assertEquals("fuel is the owner's cost, not the customer's", 2_000L, balance())
    }

    @Test
    fun `a breakdown stops the clock but keeps the job`() = runTest {
        val id = runningJob()
        h.advanceMinutes(30)
        h.work.reportBreakdown(
            orgId = TestHarness.ORG_A, jobId = id,
            category = BreakdownCategory.Hydraulics, note = "Lift not holding",
        )

        val after = job(id)
        assertEquals(JobStatus.Paused, after.status)
        assertEquals("the half hour already worked is kept", 1_800L, after.elapsedSeconds)
        assertEquals("the clock is stopped", null, after.runningSince)
        assertTrue(JobEventType.BreakdownReported in events(id))
    }

    @Test
    fun `a breakdown marks the machine down and is recorded against it`() = runTest {
        val id = runningJob()
        h.work.reportBreakdown(
            orgId = TestHarness.ORG_A, jobId = id, category = BreakdownCategory.Tyre,
        )

        assertEquals(
            MachineStatus.Down,
            h.db.machineDao().find(TestHarness.ORG_A, machineId)!!.status,
        )
        val open = h.db.machineDao().observeOpenBreakdowns(TestHarness.ORG_A).first()
        assertEquals(1, open.size)
        assertEquals(BreakdownCategory.Tyre, open.first().category)
        assertEquals(id, open.first().jobId)
    }

    @Test
    fun `work resumes after the machine is fixed, without losing the earlier time`() = runTest {
        val id = runningJob()
        h.advanceMinutes(30)
        h.work.reportBreakdown(TestHarness.ORG_A, id, BreakdownCategory.Engine)
        h.advanceMinutes(45)
        h.work.resume(TestHarness.ORG_A, id)
        h.advanceMinutes(15)

        // 30 before + 15 after; the 45 minutes under repair are not charged for.
        assertEquals(2_700L, h.work.elapsedSeconds(job(id)))
    }

    @Test
    fun `everything done in the field is queued for upload`() = runTest {
        val id = runningJob()
        h.work.addFuel(TestHarness.ORG_A, id, litres = 10.0, costRupees = 950)
        h.work.reportBreakdown(TestHarness.ORG_A, id, BreakdownCategory.Electrical)

        val tables = h.db.outboxDao().nextBatch(200).map { it.tableName }
        assertTrue(SyncQueue.TABLE_FUEL in tables)
        assertTrue(SyncQueue.TABLE_BREAKDOWNS in tables)
    }

    @Test
    fun `handing the job over changes who is driving it`() = runTest {
        val id = runningJob()
        h.work.handOver(TestHarness.ORG_A, id, TestHarness.OWNER)

        assertEquals(TestHarness.OWNER, job(id).operatorPersonId)
        assertTrue(JobEventType.HandedOver in events(id))
        assertNotNull("the job keeps running through the swap", job(id).runningSince)
    }

    private suspend fun balance() =
        h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
}
