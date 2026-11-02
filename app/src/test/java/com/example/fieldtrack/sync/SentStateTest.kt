package com.example.fieldtrack.sync

import com.example.fieldtrack.data.TestHarness
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.SentState
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.db.entity.JobCostEntity
import com.example.fieldtrack.data.db.entity.PayoutEntity
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import com.example.fieldtrack.data.sync.SyncChange
import com.example.fieldtrack.data.sync.SyncEngine
import com.example.fieldtrack.data.sync.SyncOutcome
import com.example.fieldtrack.data.sync.SyncStage
import com.example.fieldtrack.data.sync.SyncTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Whether the app may say a row has reached the server.
 *
 * The one direction that matters: it must never say "sent" about something that
 * is still only on the phone. Every test here is a way that could go wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SentStateTest {

    private lateinit var h: TestHarness

    private class Transport(var reply: (SyncChange) -> SyncOutcome = { SyncOutcome.Accepted }) :
        SyncTransport {
        override val name = "Test"
        override suspend fun send(batch: List<SyncChange>) = batch.map(reply)
    }

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private fun engine(transport: Transport) =
        SyncEngine(outbox = h.db.outboxDao(), transport = transport, clock = h.clock)

    private suspend fun startJob(): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Rotavator",
        billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre,
        rateRupees = 1_000,
        operatorPersonId = TestHarness.DRIVER,
    )

    private suspend fun jobState(id: String) = h.db.jobDao().find(TestHarness.ORG_A, id)!!.syncState

    @Test
    fun `a job is sent once the server takes it, and not before`() = runTest {
        val id = startJob()
        assertEquals(SyncState.Pending, jobState(id))

        engine(Transport()).sync()
        assertEquals(SyncState.Synced, jobState(id))
    }

    @Test
    fun `a change after sending makes it unsent again`() = runTest {
        val id = startJob()
        engine(Transport()).sync()

        h.work.setQuantity(TestHarness.ORG_A, id, 3.0)
        assertEquals(SyncState.Pending, jobState(id))
    }

    @Test
    fun `an older copy going up does not cover a newer change still queued`() = runTest {
        val id = startJob()
        h.work.setQuantity(TestHarness.ORG_A, id, 3.0)   // two changes queued for one job

        // The server takes the first job change and nothing after it.
        var jobsSeen = 0
        engine(Transport { change ->
            if (change.table == SyncQueue.TABLE_JOBS && ++jobsSeen > 1) {
                SyncOutcome.Deferred("no signal")
            } else {
                SyncOutcome.Accepted
            }
        }).sync()

        assertEquals("the 3 acres are still only on the phone", SyncState.Pending, jobState(id))
    }

    @Test
    fun `a change set aside is never called sent`() = runTest {
        val id = startJob()
        val engine = engine(Transport { change ->
            if (change.table == SyncQueue.TABLE_JOBS) SyncOutcome.Refused("rules") else SyncOutcome.Accepted
        })
        val status = engine.sync()
        assertEquals(SyncStage.Blocked, status.stage)

        engine.setAside(status.blockedSeq!!)
        engine.sync()
        assertEquals(SyncState.Pending, jobState(id))
    }

    @Test
    fun `a refused change stays called refused while it waits to be tried again`() = runTest {
        startJob()
        val engine = engine(Transport { change ->
            if (change.table == SyncQueue.TABLE_JOBS) SyncOutcome.Refused("rules") else SyncOutcome.Accepted
        })
        assertEquals(SyncStage.Blocked, engine.sync().stage)

        // The next ordinary run lands inside the back-off. It used to say
        // Waiting — "no signal" — about a change no signal will ever send.
        h.nowMillis += 1_000
        val again = engine.sync()
        assertEquals(SyncStage.Blocked, again.stage)
        assertEquals("rules", again.error)
    }

    @Test
    fun `a refusal halfway down a batch still lets go of what went before`() = runTest {
        val id = startJob()                      // queues its Started event, then the job
        engine(Transport { change ->
            if (change.table == SyncQueue.TABLE_JOBS) SyncOutcome.Refused("rules") else SyncOutcome.Accepted
        }).sync()

        val event = h.db.jobDao().observeEvents(id).first().single()
        assertEquals("taken before the refusal", SyncState.Synced, event.syncState)
        assertEquals(SyncState.Pending, jobState(id))
    }

    @Test
    fun `rows that arrive from the server are sent, whatever their author's phone said`() = runTest {
        RestoreApplier(h.db).apply(
            listOf(
                RemoteRow(
                    SyncQueue.TABLE_CUSTOMERS, "cust-new",
                    mapOf(
                        "id" to "cust-new", "orgId" to TestHarness.ORG_A, "name" to "Balbir",
                        "createdAt" to h.nowMillis, "updatedAt" to h.nowMillis,
                        "syncState" to "Pending",
                    ),
                )
            )
        )
        assertEquals(
            SyncState.Synced,
            h.db.customerDao().find(TestHarness.ORG_A, "cust-new")!!.syncState,
        )
    }

    @Test
    fun `an install from before the triggers is put right on opening`() = runTest {
        val id = startJob()
        engine(Transport()).sync()
        // As every row on an older install looks: sent, but still saying Pending.
        h.db.openHelper.writableDatabase.execSQL("UPDATE jobs SET syncState = 'Pending'")

        SentState.reconcile(h.db.openHelper.writableDatabase)
        assertEquals(SyncState.Synced, jobState(id))
    }

    // ----------------------------------------------- what version 12 never sent

    private suspend fun migratedCost() {
        // What MIGRATION_11_12 left behind: a cost and the payment pointing at
        // it, written straight to the tables with nothing queued.
        h.db.orgDao().upsertJobCost(
            JobCostEntity(
                id = "jc_pay-1", orgId = TestHarness.ORG_A, jobId = "job-x",
                kind = JobCostKind.Labour, headcount = 4, amountRupees = 2_000,
                byPersonId = TestHarness.OWNER, atMillis = h.nowMillis, createdAt = h.nowMillis,
                deviceId = TestHarness.DEVICE,
            )
        )
        h.db.orgDao().upsertPayout(
            PayoutEntity(
                id = "pay-1", orgId = TestHarness.ORG_A, amountRupees = 2_000,
                atMillis = h.nowMillis, covers = PayoutCovers.Job, headcount = 4,
                jobId = "job-x", jobCostId = "jc_pay-1", createdAt = h.nowMillis,
                deviceId = TestHarness.DEVICE,
            )
        )
        h.db.outboxDao().removeAll(h.db.outboxDao().nextBatch(100))
    }

    @Test
    fun `costs the migration made are sent up by somebody who may pay`() = runTest {
        migratedCost()
        val queued = h.ledger.requeueMigratedJobCosts(TestHarness.ORG_A, Capabilities.ofOwner())

        assertEquals("facts, amount, and the payment", 3, queued)
        val tables = h.db.outboxDao().nextBatch(100).map { it.tableName to it.entityId }
        assertEquals(
            listOf(
                SyncQueue.TABLE_JOB_COSTS to "jc_pay-1",
                SyncQueue.TABLE_JOB_COST_AMOUNTS to "jc_pay-1",
                SyncQueue.TABLE_PAYOUTS to "pay-1",
            ),
            tables,
        )
    }

    @Test
    fun `and left alone by somebody the server would refuse`() = runTest {
        migratedCost()
        val driver = Capabilities(setOf(com.example.fieldtrack.data.session.Capability.RecordWork))
        assertEquals(0, h.ledger.requeueMigratedJobCosts(TestHarness.ORG_A, driver))
        assertEquals(0, h.db.outboxDao().nextBatch(100).size)
    }

    @Test
    fun `a copy that came from another phone is not sent back up`() = runTest {
        migratedCost()
        h.db.openHelper.writableDatabase.execSQL("UPDATE job_costs SET deviceId = 'other-phone'")
        h.db.openHelper.writableDatabase.execSQL("UPDATE payouts SET deviceId = 'other-phone'")
        assertEquals(0, h.ledger.requeueMigratedJobCosts(TestHarness.ORG_A, Capabilities.ofOwner()))
    }
}
