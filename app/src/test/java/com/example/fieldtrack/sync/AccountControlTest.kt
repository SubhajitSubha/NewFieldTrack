package com.example.fieldtrack.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.OutboxOp
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.OutboxEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.data.session.Startup
import com.example.fieldtrack.data.sync.PullPage
import com.example.fieldtrack.data.sync.RemoteOrg
import com.example.fieldtrack.data.sync.RemoteRestore
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the owner decides, and what the account itself decides.
 *
 * Two different powers, and the app has to tell them apart: a business can let
 * somebody go, which costs them that business; an account can be turned off, which
 * costs them all of them at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountControlTest {

    private lateinit var context: Context
    private lateinit var db: FieldTrackDatabase
    private lateinit var store: SessionStore
    private lateinit var restore: FakeRestore
    private lateinit var homeOrgId: String

    /** Answers about one membership, and nothing else. */
    private class FakeRestore : RemoteRestore {
        var membership: RemoteRow? = null
        var failWith: Exception? = null

        override suspend fun orgsFor(personId: String) = emptyList<RemoteOrg>()
        override suspend fun fetchOrg(orgId: String) = emptyList<RemoteRow>()
        override suspend fun fetchChangesSince(orgId: String, since: Long) =
            PullPage(emptyList(), since)

        override suspend fun isAccountDisabled(personId: String) = false

        override suspend fun fetchMyMembership(orgId: String, personId: String): RemoteRow? {
            failWith?.let { throw it }
            return membership
        }
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        for (name in listOf("fieldtrack.session", "fieldtrack.pull")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        db = Room.inMemoryDatabaseBuilder(context, FieldTrackDatabase::class.java)
            .allowMainThreadQueries().build()
        store = SessionStore(context, db)
        restore = FakeRestore()
        homeOrgId = store.completeOnboarding(
            displayName = "Ramesh",
            businessName = "Home business",
            landUnit = MeasureUnit.Acre,
            isBusiness = false,
        ).orgId
        store.activate()
    }

    @After
    fun tearDown() = db.close()

    private val personId get() = requireNotNull(store.personIdOrNull())

    private suspend fun joinOrg(orgId: String, active: Boolean = true) {
        db.orgDao().upsertOrg(
            OrgEntity(id = orgId, name = "Singh Harvesting", createdAt = 1L, updatedAt = 1L)
        )
        db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(orgId, personId),
                orgId = orgId,
                personId = personId,
                displayName = "Ramesh",
                role = MemberRole.Operator,
                joinedAt = 1L,
                updatedAt = 1L,
                active = active,
            )
        )
    }

    private suspend fun queueSomethingFor(orgId: String) = db.outboxDao().enqueue(
        OutboxEntity(
            orgId = orgId,
            tableName = "customers",
            entityId = "c1",
            operation = OutboxOp.Upsert,
            payloadJson = "{}",
            createdAt = 1L,
        )
    )

    private fun applier() = RestoreApplier(db)

    // ------------------------------------------------- removed from a business

    @Test
    fun `an empty answer takes nothing away`() = runTest {
        joinOrg("org-b")
        queueSomethingFor("org-b")
        restore.membership = null
        // Onboarding queues rows of its own, so these are measured as a change.
        val before = db.outboxDao().pendingCount()

        val allowed = store.verifyMembership("org-b", restore, applier())

        // A document that is not there is not a statement that somebody was
        // removed. Firestore returns exactly this from an empty cache whenever the
        // phone cannot reach the network, so reading it as removal threw working
        // drivers out of businesses they had never left.
        assertTrue(allowed)
        assertTrue(requireNotNull(db.orgDao().membership("org-b", personId)).active)
        assertEquals(before, db.outboxDao().pendingCount())
    }

    @Test
    fun `a membership come back inactive is refused`() = runTest {
        joinOrg("org-b")
        queueSomethingFor("org-b")
        val before = db.outboxDao().pendingCount()
        restore.membership = RemoteRow(
            table = "memberships",
            entityId = membershipId("org-b", personId),
            fields = mapOf(
                "id" to membershipId("org-b", personId),
                "orgId" to "org-b",
                "personId" to personId,
                "displayName" to "Ramesh",
                "role" to "Operator",
                "canSeeMoney" to false,
                "canAddCustomers" to false,
                "canCloseBills" to false,
                "active" to false,
                "joinedAt" to 1L,
                "updatedAt" to 2L,
                "syncState" to "Synced",
            ),
        )

        // Said out loud by the server, which is the only thing that removes anybody.
        assertFalse(store.verifyMembership("org-b", restore, applier()))
        assertEquals(before - 1, db.outboxDao().pendingCount())
    }

    @Test
    fun `no signal is not the same as being removed`() = runTest {
        joinOrg("org-b")
        queueSomethingFor("org-b")
        restore.failWith = IllegalStateException("no signal")
        val before = db.outboxDao().pendingCount()

        // Somebody standing in a field keeps working. The server refuses anything
        // they should not be doing regardless of what this phone believes.
        assertTrue(store.verifyMembership("org-b", restore, applier()))
        assertTrue(requireNotNull(db.orgDao().membership("org-b", personId)).active)
        assertEquals(before, db.outboxDao().pendingCount())
    }

    // ----------------------------------------------------- account turned off

    @Test
    fun `turning the account off stops every business at once`() = runTest {
        joinOrg("org-b")
        queueSomethingFor(homeOrgId)
        queueSomethingFor("org-b")

        assertTrue(store.lockOutDisabledAccount())

        assertEquals(Startup.NeedsSignIn, store.startup.value)
        // Both businesses, not just the one they happened to be in.
        assertEquals(0, db.outboxDao().pendingCount())
        assertTrue(store.accountDisabled())
    }

    @Test
    fun `the work itself is kept, not deleted`() = runTest {
        queueSomethingFor(homeOrgId)
        val before = db.outboxDao().pendingCount()

        store.lockOutDisabledAccount()

        // Parked, so it stops being sent — but still on the phone, because it is a
        // record of work somebody really did.
        assertEquals(0, db.outboxDao().pendingCount())
        assertEquals(before, db.outboxDao().setAsideCount())
    }

    @Test
    fun `locking out twice changes nothing the second time`() = runTest {
        assertTrue(store.lockOutDisabledAccount())
        assertFalse(store.lockOutDisabledAccount())
    }

    @Test
    fun `turning it back on lets them in again`() = runTest {
        store.lockOutDisabledAccount()
        assertEquals(Startup.NeedsSignIn, store.startup.value)

        store.clearAccountDisabled()

        assertFalse(store.accountDisabled())
        // Back where they were: the business was never deleted, only let go of.
        store.switchTo(homeOrgId)
        assertEquals(homeOrgId, store.current()?.orgId)
    }

    @Test
    fun `being taken back clears the notice about being let go`() = runTest {
        joinOrg("org-b")
        store.switchTo("org-b")
        // The server says their membership there has been ended.
        restore.membership = inactiveMembershipRow("org-b")

        store.refreshMembership(restore, applier())

        // They are out, and the next screen is told which business and why.
        assertEquals("org-b", store.removedFrom())

        // Then they are taken back — here, joining anywhere at all.
        store.adoptMembership("org-b")

        // Left behind, that notice outlives the thing it describes and the join
        // screen greets a working driver with "you are no longer with them".
        assertEquals(null, store.removedFrom())
    }

    /** The membership as the server hands it back once somebody is let go. */
    private fun inactiveMembershipRow(orgId: String) = RemoteRow(
        table = "memberships",
        entityId = membershipId(orgId, personId),
        fields = mapOf(
            "id" to membershipId(orgId, personId),
            "orgId" to orgId,
            "personId" to personId,
            "displayName" to "Ramesh",
            "role" to "Operator",
            "canSeeMoney" to false,
            "canAddCustomers" to false,
            "canCloseBills" to false,
            "active" to false,
            "joinedAt" to 1L,
            "updatedAt" to 2L,
            "syncState" to "Synced",
        ),
    )


    @Test
    fun `what is waiting is counted per business`() = runTest {
        joinOrg("org-b")
        queueSomethingFor("org-b")
        queueSomethingFor("org-b")

        // The count is written onto a membership, so it must be scoped to that
        // business. A driver who works for two contractors would otherwise tell
        // each of them how far behind the other one was.
        assertEquals(2, db.outboxDao().pendingCountFor("org-b"))
        assertTrue(db.outboxDao().pendingCount() > 2)
    }

}
