package com.example.fieldtrack.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.OutboxOp
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.sync.SyncChange
import com.example.fieldtrack.data.sync.SyncEngine
import com.example.fieldtrack.data.sync.SyncJournal
import com.example.fieldtrack.data.sync.SyncOutcome
import com.example.fieldtrack.data.sync.SyncStage
import com.example.fieldtrack.data.sync.SyncTransport
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
 * A transport we tell what to say.
 *
 * [reply] decides the outcome per change, so a test can accept the first two and
 * defer the third without the engine knowing anything is unusual.
 */
private class FakeTransport(
    var reply: (SyncChange) -> SyncOutcome = { SyncOutcome.Accepted },
) : SyncTransport {

    override val name = "Fake"

    /** Every batch handed over, in the order the engine sent them. */
    val batches = mutableListOf<List<SyncChange>>()

    val seen: List<SyncChange> get() = batches.flatten()

    /** Set to throw instead of replying, for the dropped-connection case. */
    var throws: Exception? = null

    /** Set to throw only once a given number of calls have been made. */
    var throwsOnCall: Int? = null

    /** Set to answer for fewer changes than it was given. */
    var truncateTo: Int? = null

    override suspend fun send(batch: List<SyncChange>): List<SyncOutcome> {
        batches += batch
        throws?.let { throw it }
        if (batches.size == throwsOnCall) throw java.io.IOException("connection lost")
        val answers = batch.map(reply)
        return truncateTo?.let { answers.take(it) } ?: answers
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    private var now = 1_700_000_000_000L
    private val clock = Clock { now }

    private lateinit var db: FieldTrackDatabase
    private lateinit var queue: SyncQueue
    private lateinit var journal: SyncJournal

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FieldTrackDatabase::class.java,
        ).allowMainThreadQueries().build()
        queue = SyncQueue(db.outboxDao(), clock)
        journal = SyncJournal.InMemory()
    }

    @After
    fun tearDown() = db.close()

    private fun engine(
        transport: SyncTransport?,
        batchSize: Int = SyncEngine.BATCH_SIZE,
    ) = SyncEngine(
        outbox = db.outboxDao(),
        transport = transport,
        clock = clock,
        journal = journal,
        batchSize = batchSize,
    )

    /** Queues one change and lets the clock move, so ordering is unambiguous. */
    private suspend fun enqueue(entityId: String, table: String = SyncQueue.TABLE_JOBS) {
        queue.enqueueUpsert(ORG, table, entityId, """{"id":"$entityId"}""")
        now += 1_000
    }

    private suspend fun pending() = db.outboxDao().pendingCount()

    private fun h() = db.outboxDao()

    /** Counts every row, set aside or not — what "kept, not deleted" means. */
    private fun rawOutboxCount(): Int =
        db.query("SELECT COUNT(*) FROM outbox", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    // ---------------------------------------------------------------- the easy path

    @Test
    fun `an empty queue is idle but is not a backup on its own`() = runTest {
        val status = engine(FakeTransport()).sync()

        assertEquals(SyncStage.Idle, status.stage)
        assertEquals(0, status.sent)
        assertNull(
            "a phone that has never sent anything has an empty queue too",
            status.lastSuccessAt,
        )
    }

    @Test
    fun `clearing the queue by other means does not count as a backup`() = runTest {
        repeat(3) { enqueue("job-$it") }
        db.outboxDao().nextBatch(10).forEach { db.outboxDao().remove(it) }

        val status = engine(FakeTransport()).sync()

        assertEquals(0, status.pending)
        assertNull("nothing reached a server, so nothing may be claimed", status.lastSuccessAt)
    }

    @Test
    fun `a backup is recorded once changes actually land`() = runTest {
        enqueue("job-1")

        val status = engine(FakeTransport()).sync()

        assertEquals(1, status.sent)
        assertEquals(now, status.lastSuccessAt)
    }

    @Test
    fun `a later empty run keeps the time of the real backup`() = runTest {
        enqueue("job-1")
        val engine = engine(FakeTransport())
        engine.sync()
        val realBackup = journal.lastSuccessAt

        now += 3_600_000
        val idle = engine.sync()

        assertEquals("an empty run must not move the clock forward", realBackup, idle.lastSuccessAt)
    }

    @Test
    fun `everything accepted leaves the queue empty`() = runTest {
        repeat(5) { enqueue("job-$it") }

        val status = engine(FakeTransport()).sync()

        assertEquals(SyncStage.Idle, status.stage)
        assertEquals(5, status.sent)
        assertEquals(0, status.pending)
        assertEquals(0, pending())
    }

    @Test
    fun `changes reach the transport in the order they were made`() = runTest {
        enqueue("job-1")
        enqueue("event-1", SyncQueue.TABLE_JOB_EVENTS)
        enqueue("job-2")
        enqueue("payment-1", SyncQueue.TABLE_LEDGER)
        val transport = FakeTransport()

        engine(transport).sync()

        assertEquals(
            listOf("job-1", "event-1", "job-2", "payment-1"),
            transport.seen.map { it.entityId },
        )
    }

    @Test
    fun `a change carries everything the server needs to apply it`() = runTest {
        queue.enqueueUpsert(ORG, SyncQueue.TABLE_CUSTOMERS, "cust-1", """{"name":"Ramesh"}""")
        queue.enqueueDelete(ORG, SyncQueue.TABLE_MACHINES, "machine-9")
        val transport = FakeTransport()

        engine(transport).sync()

        val upsert = transport.seen[0]
        assertEquals(ORG, upsert.orgId)
        assertEquals(SyncQueue.TABLE_CUSTOMERS, upsert.table)
        assertEquals("cust-1", upsert.entityId)
        assertEquals(OutboxOp.Upsert, upsert.operation)
        assertEquals("""{"name":"Ramesh"}""", upsert.payloadJson)
        assertTrue("the seq lets a server recognise a repeat", upsert.seq > 0)

        val delete = transport.seen[1]
        assertEquals(OutboxOp.Delete, delete.operation)
        assertEquals("machine-9", delete.entityId)
    }

    @Test
    fun `a queue longer than one batch goes up in several`() = runTest {
        repeat(7) { enqueue("job-$it") }
        val transport = FakeTransport()

        val status = engine(transport, batchSize = 3).sync()

        assertEquals(7, status.sent)
        assertEquals(0, pending())
        assertEquals("no pointless round trip for an empty batch", listOf(3, 3, 1), transport.batches.map { it.size })
    }

    // -------------------------------------------------------- nothing is ever lost

    @Test
    fun `a refused change stays in the queue`() = runTest {
        enqueue("job-1")
        val transport = FakeTransport { SyncOutcome.Refused("not your organisation") }

        val status = engine(transport).sync()

        assertEquals(SyncStage.Blocked, status.stage)
        assertEquals("not your organisation", status.error)
        assertEquals("a server saying no is not permission to delete a day's work", 1, pending())
    }

    @Test
    fun `a refused change survives any number of attempts`() = runTest {
        enqueue("payment-1", SyncQueue.TABLE_LEDGER)
        val transport = FakeTransport { SyncOutcome.Refused("rejected") }
        val engine = engine(transport)

        repeat(50) {
            engine.sync(force = true)
            now += 1_000
        }

        assertEquals("money someone collected is never dropped to tidy the queue", 1, pending())
        assertEquals(50, db.outboxDao().nextBatch(1).first().attempts)
    }

    @Test
    fun `a failure is recorded against the row that failed`() = runTest {
        enqueue("job-1")
        val transport = FakeTransport { SyncOutcome.Deferred("timed out") }

        engine(transport).sync()

        val row = db.outboxDao().nextBatch(1).first()
        assertEquals(1, row.attempts)
        assertEquals("timed out", row.lastError)
        assertEquals(now, row.lastAttemptAt)
    }

    @Test
    fun `setting a refused change aside lets the rest go`() = runTest {
        enqueue("job-1")
        enqueue("job-2")
        enqueue("job-3")
        val transport = FakeTransport { change ->
            if (change.entityId == "job-1") SyncOutcome.Refused("not allowed")
            else SyncOutcome.Accepted
        }
        val engine = engine(transport)
        val blocked = engine.sync()
        assertEquals(SyncStage.Blocked, blocked.stage)
        assertEquals(3, blocked.pending)

        engine.setAside(blocked.blockedSeq!!)
        val after = engine.sync(force = true)

        assertEquals(SyncStage.Idle, after.stage)
        assertEquals("the two behind it went up", 2, after.sent)
        assertEquals(1, after.setAside)
    }

    @Test
    fun `a change set aside is kept, not deleted`() = runTest {
        enqueue("payment-1", SyncQueue.TABLE_LEDGER)
        val engine = engine(FakeTransport { SyncOutcome.Refused("rejected") })
        val blocked = engine.sync()

        engine.setAside(blocked.blockedSeq!!)

        assertEquals("it leaves the queue", 0, h().pendingCount())
        assertEquals("but not the database", 1, rawOutboxCount())
    }

    @Test
    fun `a set aside change is never sent again on its own`() = runTest {
        enqueue("job-1")
        enqueue("job-2")
        val transport = FakeTransport { change ->
            if (change.entityId == "job-1") SyncOutcome.Refused("no") else SyncOutcome.Accepted
        }
        val engine = engine(transport)
        engine.setAside(engine.sync().blockedSeq!!)
        engine.sync(force = true)
        val seenBefore = transport.seen.size

        engine.sync(force = true)

        assertEquals("nothing left to try", seenBefore, transport.seen.size)
    }

    @Test
    fun `being removed from one business does not stop another syncing`() = runTest {
        // A driver who works for two. Changes for both are waiting.
        queue.enqueueUpsert("org-gone", SyncQueue.TABLE_JOBS, "job-a", """{"id":"job-a"}""")
        now += 1_000
        queue.enqueueUpsert("org-still", SyncQueue.TABLE_JOBS, "job-b", """{"id":"job-b"}""")
        now += 1_000

        // They are taken off the first one. Its queue can never be accepted again.
        db.outboxDao().setAsideForOrg("org-gone", now)

        val transport = FakeTransport()
        val status = engine(transport).sync()

        assertEquals(SyncStage.Idle, status.stage)
        assertEquals(
            "the business they still drive for must keep working",
            listOf("job-b"),
            transport.seen.map { it.entityId },
        )
        assertEquals(1, status.setAside)
    }

    @Test
    fun `work from a business they left is kept, not thrown away`() = runTest {
        queue.enqueueUpsert("org-gone", SyncQueue.TABLE_LEDGER, "pay-1", """{"id":"pay-1"}""")

        db.outboxDao().setAsideForOrg("org-gone", now)
        engine(FakeTransport()).sync()

        assertEquals("it leaves the queue", 0, pending())
        assertEquals(
            "money somebody collected is not deleted because they were let go",
            1,
            rawOutboxCount(),
        )
    }

    // ------------------------------------------------------- order, held under fire

    @Test
    fun `a stuck change holds back everything behind it`() = runTest {
        enqueue("job-1")
        enqueue("job-2")
        enqueue("job-3")
        // The second one will not go. The third must not overtake it: on the server
        // that would be job-3 existing while job-2, made first, does not.
        val transport = FakeTransport { change ->
            if (change.entityId == "job-2") SyncOutcome.Deferred("no signal")
            else SyncOutcome.Accepted
        }

        val status = engine(transport).sync()

        assertEquals(SyncStage.Offline, status.stage)
        assertEquals(1, status.sent)
        assertEquals(2, pending())
        assertEquals(
            listOf("job-2", "job-3"),
            db.outboxDao().nextBatch(10).map { it.entityId },
        )
    }

    @Test
    fun `a refusal in the middle also holds the queue`() = runTest {
        enqueue("job-1")
        enqueue("job-2")
        enqueue("job-3")
        val transport = FakeTransport { change ->
            if (change.entityId == "job-2") SyncOutcome.Refused("bad payload")
            else SyncOutcome.Accepted
        }

        val status = engine(transport).sync()

        assertEquals(SyncStage.Blocked, status.stage)
        assertEquals(1, status.sent)
        assertEquals(2, pending())
    }

    @Test
    fun `a dropped connection leaves the queue exactly as it was`() = runTest {
        repeat(3) { enqueue("job-$it") }
        val transport = FakeTransport().apply { throws = java.io.IOException("unreachable") }

        val status = engine(transport).sync()

        assertEquals(SyncStage.Offline, status.stage)
        assertEquals(0, status.sent)
        assertEquals(3, pending())
        assertEquals("unreachable", status.error)
        assertEquals(1, db.outboxDao().nextBatch(1).first().attempts)
    }

    @Test
    fun `a transport that answers for nothing does not spin`() = runTest {
        repeat(3) { enqueue("job-$it") }
        val transport = FakeTransport().apply { truncateTo = 0 }

        val status = engine(transport).sync()

        assertEquals(SyncStage.Offline, status.stage)
        assertEquals(3, pending())
        assertEquals("one batch tried, then stopped", 1, transport.batches.size)
    }

    @Test
    fun `a short reply keeps the changes it did not answer for`() = runTest {
        repeat(4) { enqueue("job-$it") }
        // Answers for two of the four, then the connection goes on the next round,
        // so what survives is exactly what the first reply left untouched.
        val transport = FakeTransport().apply {
            truncateTo = 2
            throwsOnCall = 2
        }

        val status = engine(transport).sync()

        assertEquals(2, status.sent)
        assertEquals("silence about a change is not acceptance of it", 2, pending())
        assertEquals(listOf("job-2", "job-3"), db.outboxDao().nextBatch(10).map { it.entityId })
    }

    // ------------------------------------------------------------------- backing off

    @Test
    fun `a change that just failed is not retried straight away`() = runTest {
        enqueue("job-1")
        val transport = FakeTransport { SyncOutcome.Deferred("no signal") }
        val engine = engine(transport)

        engine.sync()
        transport.reply = { SyncOutcome.Accepted }
        val second = engine.sync()

        assertEquals(SyncStage.Waiting, second.stage)
        assertEquals("the second run never reached the transport", 1, transport.batches.size)
        assertNotNull(second.retryAtMillis)
    }

    @Test
    fun `it goes again once the wait is over`() = runTest {
        enqueue("job-1")
        val transport = FakeTransport { SyncOutcome.Deferred("no signal") }
        val engine = engine(transport)
        engine.sync()

        now += SyncEngine.backoffMillis(1)
        transport.reply = { SyncOutcome.Accepted }
        val second = engine.sync()

        assertEquals(SyncStage.Idle, second.stage)
        assertEquals(0, pending())
    }

    @Test
    fun `asking for it directly ignores the wait`() = runTest {
        enqueue("job-1")
        val transport = FakeTransport { SyncOutcome.Deferred("no signal") }
        val engine = engine(transport)
        engine.sync()

        transport.reply = { SyncOutcome.Accepted }
        val forced = engine.sync(force = true)

        assertEquals("a user watching the screen should not be told to wait", SyncStage.Idle, forced.stage)
        assertEquals(0, pending())
    }

    @Test
    fun `waiting longer after each failure, up to an hour`() {
        assertEquals(0L, SyncEngine.backoffMillis(0))
        assertEquals(30_000L, SyncEngine.backoffMillis(1))
        assertEquals(60_000L, SyncEngine.backoffMillis(2))
        assertEquals(60 * 60_000L, SyncEngine.backoffMillis(7))
        assertEquals("capped, not growing forever", 60 * 60_000L, SyncEngine.backoffMillis(400))
    }

    // ------------------------------------------------------------ no backend at all

    @Test
    fun `with nowhere to send, the queue is kept and the app says so`() = runTest {
        repeat(3) { enqueue("job-$it") }

        val status = engine(transport = null).sync()

        assertEquals(SyncStage.NoBackend, status.stage)
        assertEquals(3, status.pending)
        assertNull("nothing was backed up, so nothing is claimed", status.lastSuccessAt)
    }

    // -------------------------------------------------- what "last backup" may mean

    @Test
    fun `a partial run is not reported as a backup`() = runTest {
        enqueue("job-1")
        enqueue("job-2")
        val transport = FakeTransport { change ->
            if (change.entityId == "job-2") SyncOutcome.Deferred("no signal")
            else SyncOutcome.Accepted
        }

        val status = engine(transport).sync()

        assertEquals(1, status.sent)
        assertNull(
            "work is still only on this phone; saying 'backed up' would be a lie",
            status.lastSuccessAt,
        )
    }

    @Test
    fun `the last backup time survives a new engine`() = runTest {
        enqueue("job-1")
        engine(FakeTransport()).sync()
        val realBackup = journal.lastSuccessAt

        now += 60_000
        // A fresh engine, as after the app was closed and reopened.
        val later = engine(FakeTransport()).sync()

        assertNotNull(realBackup)
        assertEquals(
            "it reports when the work actually went up, not when it was last asked",
            realBackup,
            later.lastSuccessAt,
        )
    }

    private companion object {
        const val ORG = "org-a"
    }
}
