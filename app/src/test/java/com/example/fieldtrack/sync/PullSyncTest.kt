package com.example.fieldtrack.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.OutboxOp
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.OutboxEntity
import com.example.fieldtrack.data.sync.PullPage
import com.example.fieldtrack.data.sync.PullSync
import com.example.fieldtrack.data.sync.PullWatermarks
import com.example.fieldtrack.data.sync.RemoteOrg
import com.example.fieldtrack.data.sync.RemoteRestore
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import com.example.fieldtrack.data.sync.resumeFrom
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
import kotlinx.coroutines.flow.first

/**
 * Bringing down what other people changed.
 *
 * The rules worth holding: never ask too often, never move the mark over rows that
 * were not written, and never let a download overwrite work this phone has not
 * managed to send yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PullSyncTest {

    private lateinit var db: FieldTrackDatabase
    private lateinit var marks: PullWatermarks
    private lateinit var restore: FakeRestore
    private var now = 1_700_000_000_000L

    private class FakeRestore : RemoteRestore {
        var pages = ArrayDeque<PullPage>()
        var calls = 0
        var failWith: Exception? = null

        var fullFetches = 0
        var fullRows = listOf<RemoteRow>()

        override suspend fun orgsFor(personId: String) = emptyList<RemoteOrg>()
        override suspend fun fetchOrg(orgId: String): List<RemoteRow> {
            fullFetches++
            failWith?.let { throw it }
            return fullRows
        }
        override suspend fun fetchMyMembership(orgId: String, personId: String): RemoteRow? = null
        override suspend fun isAccountDisabled(personId: String) = false

        override suspend fun fetchChangesSince(orgId: String, since: Long): PullPage {
            calls++
            failWith?.let { throw it }
            return pages.removeFirstOrNull() ?: PullPage(emptyList(), since)
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fieldtrack.pull", Context.MODE_PRIVATE)
            .edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, FieldTrackDatabase::class.java)
            .allowMainThreadQueries().build()
        marks = PullWatermarks(context)
        restore = FakeRestore()
    }

    @After
    fun tearDown() = db.close()

    private fun membershipRow(personId: String, name: String) = RemoteRow(
        table = "memberships",
        entityId = "${ORG}_$personId",
        fields = mapOf(
            "id" to "${ORG}_$personId",
            "orgId" to ORG,
            "personId" to personId,
            "displayName" to name,
            "role" to "Owner",
            "canSeeMoney" to true,
            "canAddCustomers" to true,
            "canCloseBills" to true,
            "active" to true,
            "joinedAt" to 1L,
            "updatedAt" to 2L,
            "syncState" to "Synced",
        ),
    )

    @Test
    fun `an identity this phone has left behind never comes back`() = runTest {
        val applier = RestoreApplier(db) { it == "old-local-id" }
        applier.apply(
            listOf(
                membershipRow("old-local-id", "Harpal Singh ji"),
                membershipRow("firebase-uid", "Harpal Singh ji"),
            )
        )

        // Setting the app up before signing in leaves a membership on the server
        // under an id the app invented. Signing in moves everything onto the
        // proven id, but that document cannot be deleted or updated by this
        // phone — so without the guard the owner meets a second copy of himself
        // on his own team, back again after every pull.
        val everyone = db.orgDao().observeEveryone(ORG).first()
        assertEquals(1, everyone.size)
        assertEquals("firebase-uid", everyone.single().personId)
    }

    private fun sync() = PullSync(
        restore = restore,
        applier = RestoreApplier(db),
        marks = marks,
        clock = Clock { now },
    )

    private fun customerRow(id: String, name: String, updatedAt: Long) = RemoteRow(
        table = "customers",
        entityId = id,
        fields = mapOf(
            "id" to id,
            "orgId" to ORG,
            "name" to name,
            "createdAt" to 1L,
            "updatedAt" to updatedAt,
            "syncState" to "Synced",
            // The transport's own bookkeeping, which the applier must drop.
            "_seq" to 7L,
            "_serverAt" to 123L,
        ),
    )

    @Test
    fun `a collection this phone may not read does not stop the rest`() = runTest {
        // A driver without sight of wages is refused `payouts` by design. The
        // refusal used to throw out of the fetch loop and take the whole pull
        // with it, so that driver received no customers, no machines and no
        // jobs either — an empty app, with nothing on screen saying why.
        restore.pages += PullPage(
            rows = listOf(customerRow("c1", "Balwinder", 10L)),
            watermark = 500L,
            refused = setOf("payouts"),
        )

        assertEquals(1, sync().pull(ORG))
        assertEquals(500L, marks.watermark(ORG))
    }

    @Test
    fun `a door that opens is read from the start, not from the mark`() = runTest {
        restore.pages += PullPage(
            rows = listOf(customerRow("c1", "Balwinder", 10L)),
            watermark = 500L,
            refused = setOf("payouts"),
        )
        sync().pull(ORG)
        assertEquals(500L, marks.watermark(ORG))

        // The owner grants sight of wages. Everything written while the door
        // was shut is older than the mark, so a delta would never ask for it —
        // the business has to be read from scratch once instead.
        restore.pages += PullPage(
            rows = emptyList(),
            watermark = 900L,
            refused = emptySet(),
        )
        sync().pull(ORG, force = true)

        assertEquals("the next pass must seed, not delta", 0L, marks.watermark(ORG))
        assertFalse(marks.hasSeeded(ORG))
    }

    @Test
    fun `writes rows down and remembers how far it got`() = runTest {
        restore.pages += PullPage(listOf(customerRow("c1", "Balwinder", 10L)), watermark = 500L)

        val written = sync().pull(ORG)

        assertEquals(1, written)
        assertEquals("Balwinder", db.customerDao().find(ORG, "c1")?.name)
        assertEquals(500L, marks.watermark(ORG))
    }

    @Test
    fun `does not ask again straight away`() = runTest {
        restore.pages += PullPage(listOf(customerRow("c1", "Balwinder", 10L)), watermark = 500L)
        sync().pull(ORG)

        now += 60_000L
        sync().pull(ORG)

        // One minute later is not worth a read against every collection.
        assertEquals(1, restore.calls)
    }

    @Test
    fun `asks again once enough time has passed`() = runTest {
        sync().pull(ORG)
        now += 11 * 60_000L
        sync().pull(ORG)

        assertEquals(2, restore.calls)
    }

    @Test
    fun `forcing ignores the interval`() = runTest {
        sync().pull(ORG)
        sync().pull(ORG, force = true)

        assertEquals(2, restore.calls)
    }

    @Test
    fun `a failed pass leaves the mark where it was`() = runTest {
        restore.pages += PullPage(listOf(customerRow("c1", "Balwinder", 10L)), watermark = 500L)
        sync().pull(ORG)

        restore.failWith = IllegalStateException("no signal")
        now += 11 * 60_000L
        val written = sync().pull(ORG)

        assertEquals(0, written)
        // Still 500: a pass that could not be read must not be recorded as read,
        // or everything written since would be skipped for good.
        assertEquals(500L, marks.watermark(ORG))
    }

    @Test
    fun `the mark never moves backward`() = runTest {
        restore.pages += PullPage(emptyList(), watermark = 900L)
        sync().pull(ORG)
        restore.pages += PullPage(emptyList(), watermark = 100L)
        now += 11 * 60_000L
        sync().pull(ORG)

        assertEquals(900L, marks.watermark(ORG))
    }

    @Test
    fun `unsent local work is never overwritten by the server's older copy`() = runTest {
        db.orgDao().upsertOrg(
            OrgEntity(id = ORG, name = "Harpal Agro", createdAt = 1L, updatedAt = 1L)
        )
        db.customerDao().upsert(
            CustomerEntity(id = "c1", orgId = ORG, name = "Renamed this morning", createdAt = 1L, updatedAt = 50L)
        )
        // Queued and not yet sent: this phone knows something the server does not.
        db.outboxDao().enqueue(
            OutboxEntity(
                orgId = ORG,
                tableName = "customers",
                entityId = "c1",
                operation = OutboxOp.Upsert,
                payloadJson = "{}",
                createdAt = 50L,
            )
        )
        restore.pages += PullPage(listOf(customerRow("c1", "Stale server name", 10L)), watermark = 500L)

        val written = sync().pull(ORG)

        assertEquals(0, written)
        assertEquals("Renamed this morning", db.customerDao().find(ORG, "c1")?.name)
    }

    @Test
    fun `the first read of a business is a full one, and only the first`() = runTest {
        restore.fullRows = listOf(customerRow("old", "Balwinder", 1L))
        restore.pages += PullPage(emptyList(), watermark = 0L)

        sync().pull(ORG)

        // Written before the server stamp existed, so a delta could never see it.
        assertEquals("Balwinder", db.customerDao().find(ORG, "old")?.name)
        assertEquals(1, restore.fullFetches)

        // And not again, even though nothing stamped came back so the mark is
        // still zero — otherwise every pass would re-read the whole business.
        now += 11 * 60_000L
        sync().pull(ORG)
        assertEquals(1, restore.fullFetches)
    }

    @Test
    fun `a business never read is not made to wait for the interval`() = runTest {
        restore.failWith = IllegalStateException("membership has not reached the server yet")
        sync().pull(ORG)
        restore.failWith = null
        restore.fullRows = listOf(customerRow("c1", "Balwinder", 1L))

        // Straight away, not in ten minutes: a driver who joined a minute ago is
        // looking at an empty app, and a failed first attempt must not strand them.
        sync().pull(ORG)

        assertEquals("Balwinder", db.customerDao().find(ORG, "c1")?.name)
    }

    @Test
    fun `a failed full read is retried rather than marked done`() = runTest {
        restore.failWith = IllegalStateException("no signal")
        sync().pull(ORG)
        assertFalse(marks.hasSeeded(ORG))

        restore.failWith = null
        restore.fullRows = listOf(customerRow("c1", "Balwinder", 1L))
        sync().pull(ORG)

        assertTrue(marks.hasSeeded(ORG))
        assertEquals("Balwinder", db.customerDao().find(ORG, "c1")?.name)
    }

    @Test
    fun `a truncated collection holds the mark back`() {
        // Rates were read to noon, jobs stopped at ten with more behind them.
        assertEquals(10L, resumeFrom(since = 0L, newest = 12L, truncatedAt = listOf(10L)))
        // The earliest of several, for the same reason.
        assertEquals(8L, resumeFrom(since = 0L, newest = 12L, truncatedAt = listOf(10L, 8L)))
        // Nothing truncated: go all the way to the newest row seen.
        assertEquals(12L, resumeFrom(since = 0L, newest = 12L, truncatedAt = emptyList()))
        // Nothing came back at all, so stay put rather than rewinding to zero.
        assertEquals(40L, resumeFrom(since = 40L, newest = 0L, truncatedAt = emptyList()))
    }

    private companion object {
        const val ORG = "org-1"
    }
}
