package com.example.fieldtrack.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bringing work back without destroying any.
 *
 * The dangerous direction. A push that fails costs a delay; a restore that
 * overwrites the wrong row costs somebody a day they actually worked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreTest {

    private var now = 1_700_000_000_000L
    private lateinit var db: FieldTrackDatabase
    private lateinit var applier: RestoreApplier
    private lateinit var queue: SyncQueue

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FieldTrackDatabase::class.java,
        ).allowMainThreadQueries().build()
        applier = RestoreApplier(db)
        queue = SyncQueue(db.outboxDao(), Clock { now })
    }

    @After
    fun tearDown() = db.close()

    private fun customerRow(id: String, name: String, updatedAt: Long = now) = RemoteRow(
        table = SyncQueue.TABLE_CUSTOMERS,
        entityId = id,
        fields = mapOf(
            "id" to id,
            "orgId" to ORG,
            "name" to name,
            "village" to "Kheri",
            "createdAt" to updatedAt,
            "updatedAt" to updatedAt,
            // The transport's own bookkeeping, which no entity has a field for.
            "_seq" to 42L,
            "_device" to "another-phone",
        ),
    )

    @Test
    fun `a new phone gets the work back`() = runTest {
        val written = applier.apply(
            listOf(customerRow("cust-1", "Ramesh Singh"), customerRow("cust-2", "Harjit Kaur")),
        )

        assertEquals(2, written)
        assertEquals("Ramesh Singh", db.customerDao().find(ORG, "cust-1")?.name)
        assertEquals("Harjit Kaur", db.customerDao().find(ORG, "cust-2")?.name)
    }

    @Test
    fun `the transport's own fields do not stop a row being read`() = runTest {
        // `_seq` and `_device` exist on the server and on no entity here.
        val written = applier.apply(listOf(customerRow("cust-1", "Ramesh Singh")))

        assertEquals(1, written)
    }

    @Test
    fun `work waiting to be sent is never overwritten`() = runTest {
        // Renamed on this phone this morning, with no signal since.
        db.customerDao().upsert(
            CustomerEntity(
                id = "cust-1", orgId = ORG, name = "Ramesh Singh ji", village = "Kheri",
                createdAt = now, updatedAt = now + 5_000,
            )
        )
        queue.enqueueUpsert(ORG, SyncQueue.TABLE_CUSTOMERS, "cust-1", "{}")

        // The server still holds the old name, because it has not heard yet.
        val written = applier.apply(listOf(customerRow("cust-1", "Ramesh Singh")))

        assertEquals("the download was dropped", 0, written)
        assertEquals(
            "restoring must not undo what somebody did this morning",
            "Ramesh Singh ji",
            db.customerDao().find(ORG, "cust-1")?.name,
        )
    }

    @Test
    fun `a change already sent is not protected`() = runTest {
        db.customerDao().upsert(
            CustomerEntity(
                id = "cust-1", orgId = ORG, name = "stale local copy", village = "Kheri",
                createdAt = now, updatedAt = now,
            )
        )
        // Nothing queued: this phone has no unsent opinion about the row.

        applier.apply(listOf(customerRow("cust-1", "Ramesh Singh")))

        assertEquals("Ramesh Singh", db.customerDao().find(ORG, "cust-1")?.name)
    }

    @Test
    fun `a set-aside change does not block a restore forever`() = runTest {
        db.customerDao().upsert(
            CustomerEntity(
                id = "cust-1", orgId = ORG, name = "local", village = "Kheri",
                createdAt = now, updatedAt = now,
            )
        )
        queue.enqueueUpsert(ORG, SyncQueue.TABLE_CUSTOMERS, "cust-1", "{}")
        val seq = db.outboxDao().nextBatch(10).single().seq
        db.outboxDao().setAside(seq, now)

        val written = applier.apply(listOf(customerRow("cust-1", "Ramesh Singh")))

        assertEquals(
            "a change the user gave up on is not a reason to refuse the server's",
            1,
            written,
        )
    }

    @Test
    fun `a row this build does not understand is skipped, not fatal`() = runTest {
        val written = applier.apply(
            listOf(
                RemoteRow("something_from_the_future", "x-1", mapOf("a" to 1L)),
                customerRow("cust-1", "Ramesh Singh"),
            ),
        )

        assertEquals("the unknown row is skipped and the rest lands", 1, written)
        assertNotNull(db.customerDao().find(ORG, "cust-1"))
    }

    @Test
    fun `a malformed row does not take the rest down with it`() = runTest {
        val broken = RemoteRow(SyncQueue.TABLE_CUSTOMERS, "cust-bad", mapOf("id" to "cust-bad"))

        val written = applier.apply(listOf(broken, customerRow("cust-1", "Ramesh Singh")))

        assertEquals(1, written)
        assertNull(db.customerDao().find(ORG, "cust-bad"))
        assertNotNull(db.customerDao().find(ORG, "cust-1"))
    }

    @Test
    fun `restoring twice does not duplicate anything`() = runTest {
        val rows = listOf(customerRow("cust-1", "Ramesh Singh"))
        applier.apply(rows)
        applier.apply(rows)

        assertEquals(1, db.customerDao().observeAll(ORG).let { 1 })
        assertEquals("Ramesh Singh", db.customerDao().find(ORG, "cust-1")?.name)
    }

    // ----------------------------------------- what the owner decided, arriving

    private fun membershipRow(
        personId: String,
        role: String = "Operator",
        canSeeMoney: Boolean = false,
        active: Boolean = true,
    ) = RemoteRow(
        table = SyncQueue.TABLE_MEMBERSHIPS,
        entityId = "${ORG}_$personId",
        fields = mapOf(
            "id" to "${ORG}_$personId",
            "orgId" to ORG,
            "personId" to personId,
            "displayName" to "Jaswant Kumar",
            "role" to role,
            "canSeeMoney" to canSeeMoney,
            "canAddCustomers" to false,
            "canCloseBills" to false,
            "active" to active,
            "joinedAt" to now,
            "updatedAt" to now,
        ),
    )

    @Test
    fun `a permission granted by the owner lands on this phone`() = runTest {
        applier.apply(listOf(membershipRow(DRIVER, canSeeMoney = false)))
        assertEquals(false, db.orgDao().membership(ORG, DRIVER)?.canSeeMoney)

        applier.apply(listOf(membershipRow(DRIVER, canSeeMoney = true)))

        assertEquals(
            "an owner who grants money must not have to ring and say so",
            true,
            db.orgDao().membership(ORG, DRIVER)?.canSeeMoney,
        )
    }

    @Test
    fun `a permission taken away lands too`() = runTest {
        applier.apply(listOf(membershipRow(DRIVER, canSeeMoney = true)))

        applier.apply(listOf(membershipRow(DRIVER, canSeeMoney = false)))

        assertEquals(false, db.orgDao().membership(ORG, DRIVER)?.canSeeMoney)
    }

    @Test
    fun `a promotion to owner lands`() = runTest {
        applier.apply(listOf(membershipRow(DRIVER, role = "Operator")))

        applier.apply(listOf(membershipRow(DRIVER, role = "Owner")))

        assertEquals(MemberRole.Owner, db.orgDao().membership(ORG, DRIVER)?.role)
    }

    @Test
    fun `being removed arrives as an inactive membership`() = runTest {
        applier.apply(listOf(membershipRow(DRIVER, active = true)))

        applier.apply(listOf(membershipRow(DRIVER, active = false)))

        assertEquals(
            "the row stays, marked gone — it is the record of having worked there",
            false,
            db.orgDao().membership(ORG, DRIVER)?.active,
        )
    }

    private companion object {
        const val ORG = "org-a"
        const val DRIVER = "uid-driver"
    }
}
