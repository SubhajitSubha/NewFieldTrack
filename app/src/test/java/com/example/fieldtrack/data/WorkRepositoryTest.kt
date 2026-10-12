package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.EntryMethod
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.SyncQueue
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkRepositoryTest {

    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
    }

    @After fun tearDown() = h.close()

    private suspend fun startRotavatorJob(): String = h.work.startJob(
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
    fun `finishing a job bills the customer in the same breath`() = runTest {
        val jobId = startRotavatorJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 3.5)

        val charged = h.work.complete(TestHarness.ORG_A, jobId, byPersonId = TestHarness.DRIVER)

        assertEquals(3_500L, charged)
        // The whole point: work done is money owed, with no window in between.
        assertEquals(
            3_500L,
            h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first(),
        )
    }

    @Test
    fun `elapsed time survives the process being killed mid-job`() = runTest {
        val jobId = startRotavatorJob()
        h.advanceMinutes(45)

        // Nothing wrote the elapsed figure during those 45 minutes — it is derived
        // from runningSince, so a cold start recovers it rather than resetting.
        val job = h.work.observeJob(TestHarness.ORG_A, jobId).first()!!
        assertEquals(45 * 60L, h.work.elapsedSeconds(job))
    }

    @Test
    fun `pausing banks the time and resuming starts a fresh stretch`() = runTest {
        val jobId = startRotavatorJob()
        h.advanceMinutes(30)
        h.work.pause(TestHarness.ORG_A, jobId)

        val paused = h.work.observeJob(TestHarness.ORG_A, jobId).first()!!
        assertEquals(JobStatus.Paused, paused.status)
        assertEquals(30 * 60L, paused.elapsedSeconds)
        assertNull("a paused job is not accruing time", paused.runningSince)

        h.advanceMinutes(20) // lunch — must not count
        h.work.resume(TestHarness.ORG_A, jobId)
        h.advanceMinutes(15)

        val resumed = h.work.observeJob(TestHarness.ORG_A, jobId).first()!!
        assertEquals(45 * 60L, h.work.elapsedSeconds(resumed))
    }

    @Test
    fun `hourly work bills from the clock without anyone retyping it`() = runTest {
        val jobId = h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Ploughing",
            billingMode = BillingMode.PerHour,
            unit = MeasureUnit.Hour,
            rateRupees = 1_100,
            operatorPersonId = TestHarness.DRIVER,
        )
        h.advanceMinutes(120)

        assertEquals(2_200L, h.work.complete(TestHarness.ORG_A, jobId))
    }

    @Test
    fun `trip work counts up and undo never goes below zero`() = runTest {
        val jobId = h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Hauling",
            billingMode = BillingMode.PerTrip,
            unit = MeasureUnit.Trip,
            rateRupees = 400,
            operatorPersonId = TestHarness.DRIVER,
        )
        repeat(6) { h.work.addTrip(TestHarness.ORG_A, jobId, distanceMetres = 8_000) }
        h.work.removeLastTrip(TestHarness.ORG_A, jobId)

        val job = h.work.observeJob(TestHarness.ORG_A, jobId).first()!!
        assertEquals(5.0, job.quantity, 0.0)
        assertEquals(48_000L, job.distanceMetres)
        assertEquals(2_000L, job.amountRupees)

        repeat(10) { h.work.removeLastTrip(TestHarness.ORG_A, jobId) }
        assertEquals(0.0, h.work.observeJob(TestHarness.ORG_A, jobId).first()!!.quantity, 0.0)
    }

    @Test
    fun `backdated work records both when it happened and when it was typed in`() = runTest {
        val yesterday = h.nowMillis - 24 * 3600_000
        val jobId = h.work.recordPastWork(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Harvesting",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_700,
            quantity = 5.0,
            workedAt = yesterday,
            startedAt = yesterday,
            completedAt = yesterday + 3 * 3600_000,
            machineId = "machine-1",
            operatorPersonId = TestHarness.DRIVER,
        )

        val job = h.work.observeJob(TestHarness.ORG_A, jobId).first()!!
        assertEquals(EntryMethod.Manual, job.entryMethod)
        assertEquals(yesterday, job.workedAt)
        assertEquals(h.nowMillis, job.recordedAt)
        assertEquals(3 * 3600L, job.elapsedSeconds)
        // Not billed on save — a manual entry is reviewed and finished like any other.
        assertEquals(0L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())
    }

    @Test
    fun `a backdated entry is warned about when the machine was already busy`() = runTest {
        val yesterday = h.nowMillis - 24 * 3600_000
        h.work.recordPastWork(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Harvesting",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_700,
            quantity = 5.0,
            workedAt = yesterday,
            startedAt = yesterday,
            completedAt = yesterday + 4 * 3600_000,
            machineId = "machine-1",
        )

        // The same machine, two hours into that window: it cannot be in two fields.
        val clash = h.work.findOverlaps(
            orgId = TestHarness.ORG_A,
            machineId = "machine-1",
            startsAt = yesterday + 2 * 3600_000,
            endsAt = yesterday + 6 * 3600_000,
        )
        assertEquals(1, clash.size)

        val clear = h.work.findOverlaps(
            orgId = TestHarness.ORG_A,
            machineId = "machine-1",
            startsAt = yesterday + 5 * 3600_000,
            endsAt = yesterday + 7 * 3600_000,
        )
        assertTrue(clear.isEmpty())
    }

    @Test
    fun `every change is queued for the server as part of the same transaction`() = runTest {
        val jobId = startRotavatorJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 2.0)
        h.work.complete(TestHarness.ORG_A, jobId)

        val queued = h.db.outboxDao().nextBatch(100)
        // Nothing reaches the network directly, so the queue is the only evidence the
        // server will ever see this work.
        assertTrue(queued.any { it.tableName == SyncQueue.TABLE_JOBS && it.entityId == jobId })
        assertTrue(queued.any { it.tableName == SyncQueue.TABLE_LEDGER })
        assertTrue(queued.all { it.orgId == TestHarness.ORG_A })
    }

    @Test
    fun `a driver's live job is scoped to the organisation they are working for`() = runTest {
        val jobA = startRotavatorJob()

        assertEquals(
            jobA,
            h.work.observeLiveJob(TestHarness.ORG_A, TestHarness.DRIVER).first()?.id,
        )
        // Same person, other employer — their current job must not appear there.
        assertNull(h.work.observeLiveJob(TestHarness.ORG_B, TestHarness.DRIVER).first())
    }

    @Test
    fun `a job billed then part-paid leaves exactly the remainder owing`() = runTest {
        val jobId = startRotavatorJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 3.5)
        h.work.complete(TestHarness.ORG_A, jobId)

        h.ledger.recordPayment(
            TestHarness.ORG_A, TestHarness.CUSTOMER_A, 2_000,
            com.example.fieldtrack.data.db.PaymentMethod.Cash,
        )

        // Regression: the finish screen once read the customer's balance live while
        // completing the job, so this job's own charge was counted a second time as
        // an "old balance" and the receipt showed 5,000 owing instead of 1,500.
        assertEquals(
            1_500L,
            h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first(),
        )
    }

    @Test
    fun `a first job leaves no old balance behind it`() = runTest {
        val jobId = startRotavatorJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 2.0)

        // Before billing there is nothing owed — the figure the bill calls "old
        // balance" must be zero for a customer's first job.
        assertEquals(0L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())

        h.work.complete(TestHarness.ORG_A, jobId)
        assertEquals(
            2_000L,
            h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first(),
        )
    }

    @Test
    fun `a machine shows as working while its job is open`() = runTest {
        h.db.machineDao().upsert(
            com.example.fieldtrack.data.db.entity.MachineEntity(
                id = "machine-1",
                orgId = TestHarness.ORG_A,
                name = "Mahindra 575",
                kind = com.example.fieldtrack.data.db.MachineKind.Tractor,
                createdAt = h.nowMillis,
                updatedAt = h.nowMillis,
            )
        )

        val jobId = startRotavatorJob()
        assertEquals(
            com.example.fieldtrack.data.db.MachineStatus.Working,
            h.db.machineDao().find(TestHarness.ORG_A, "machine-1")?.status,
        )

        // A pause is lunch, not availability — the machine is still out with a driver.
        h.work.pause(TestHarness.ORG_A, jobId)
        assertEquals(
            com.example.fieldtrack.data.db.MachineStatus.Working,
            h.db.machineDao().find(TestHarness.ORG_A, "machine-1")?.status,
        )

        h.work.complete(TestHarness.ORG_A, jobId)
        assertEquals(
            com.example.fieldtrack.data.db.MachineStatus.Idle,
            h.db.machineDao().find(TestHarness.ORG_A, "machine-1")?.status,
        )
    }

    @Test
    fun `a discarded job frees its machine`() = runTest {
        h.db.machineDao().upsert(
            com.example.fieldtrack.data.db.entity.MachineEntity(
                id = "machine-1",
                orgId = TestHarness.ORG_A,
                name = "Mahindra 575",
                kind = com.example.fieldtrack.data.db.MachineKind.Tractor,
                createdAt = h.nowMillis,
                updatedAt = h.nowMillis,
            )
        )
        val jobId = startRotavatorJob()
        h.work.discard(TestHarness.ORG_A, jobId)

        assertEquals(
            com.example.fieldtrack.data.db.MachineStatus.Idle,
            h.db.machineDao().find(TestHarness.ORG_A, "machine-1")?.status,
        )
    }

    @Test
    fun `a discarded job is billed to nobody`() = runTest {
        val jobId = startRotavatorJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 4.0)
        h.work.discard(TestHarness.ORG_A, jobId)

        assertEquals(0L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())
        assertTrue(h.work.observeLive(TestHarness.ORG_A).first().isEmpty())
    }

    @Test
    fun `a finished job cannot be billed twice`() = runTest {
        val jobId = startRotavatorJob()
        h.work.setQuantity(TestHarness.ORG_A, jobId, 1.0)
        h.work.complete(TestHarness.ORG_A, jobId)

        val second = runCatching { h.work.complete(TestHarness.ORG_A, jobId) }.exceptionOrNull()
        assertNotNull("completing twice must fail rather than double-charge", second)
        assertEquals(1_000L, h.ledger.observeBalance(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first())
    }

    @Test
    fun `the job timeline records what happened and who did it`() = runTest {
        val jobId = startRotavatorJob()
        h.advanceMinutes(10)
        h.work.pause(TestHarness.ORG_A, jobId)
        h.work.resume(TestHarness.ORG_A, jobId)
        h.work.setQuantity(TestHarness.ORG_A, jobId, 3.0)
        h.work.complete(TestHarness.ORG_A, jobId, byPersonId = TestHarness.DRIVER)

        val timeline = h.work.observeEvents(jobId).first().map { it.type.name }
        assertEquals(
            listOf("Started", "Paused", "Resumed", "QuantitySet", "Completed"),
            timeline,
        )
    }
}
