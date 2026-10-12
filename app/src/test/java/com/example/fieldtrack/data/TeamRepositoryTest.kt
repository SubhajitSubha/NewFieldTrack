package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.repository.TeamError
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.first
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
 * Who works for a business, and what they are trusted with.
 *
 * The rules worth testing are the ones that would lock somebody out of the only app
 * holding their season's work, and the ones that decide whether a driver can see
 * money.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TeamRepositoryTest {

    private lateinit var h: TestHarness
    private lateinit var team: TeamRepository
    private val owner = Capabilities.ofOwner()
    private val ownerPersonId = TestHarness.OWNER

    /** The harness seeds a driver of its own, so counts are measured from here. */
    private var baseline = 0

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
        team = TeamRepository(db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids)
        h.db.orgDao().upsertMembership(
            MembershipEntity(
                id = "m-owner", orgId = TestHarness.ORG_A, personId = ownerPersonId,
                displayName = "Harpal Singh", role = MemberRole.Owner,
                joinedAt = 0, updatedAt = 0,
            )
        )
        baseline = team.observeTeam(TestHarness.ORG_A).first().size
    }

    @After fun tearDown() = h.close()

    private suspend fun teamNow() = team.observeTeam(TestHarness.ORG_A).first()

    @Test
    fun `a driver added with no permissions may record work and nothing else`() = runTest {
        val id = team.addMember(TestHarness.ORG_A, owner, displayName = "Jaswant Kumar")
        val member = team.find(TestHarness.ORG_A, id)!!

        val can = Capabilities.of(member)
        assertTrue(Capability.RecordWork in can)
        assertFalse(Capability.SeeMoney in can)
        assertFalse(Capability.TakePayment in can)
        assertFalse(Capability.EditCustomers in can)
        assertFalse("a driver never manages the team", Capability.EditTeam in can)
    }

    @Test
    fun `trusting a driver with bills lets them see the money on them`() = runTest {
        val id = team.addMember(
            TestHarness.ORG_A, owner, displayName = "Jaswant", canCloseBills = true,
        )
        val can = Capabilities.of(team.find(TestHarness.ORG_A, id)!!)

        assertTrue(Capability.TakePayment in can)
        assertTrue("closing a bill means seeing the amount on it", Capability.SeeMoney in can)
        assertTrue(Capability.AmendBilledWork in can)
    }

    @Test
    fun `a driver cannot add drivers`() = runTest {
        val id = team.addMember(TestHarness.ORG_A, owner, displayName = "Jaswant")
        val driver = Capabilities.of(team.find(TestHarness.ORG_A, id)!!)

        val failure = runCatching {
            team.addMember(TestHarness.ORG_A, driver, displayName = "Someone else")
        }.exceptionOrNull()

        assertTrue(failure is Capabilities.NotAllowed)
        assertEquals("and nobody was added", baseline + 1, teamNow().size)
    }

    @Test
    fun `permissions can be changed afterwards`() = runTest {
        val id = team.addMember(TestHarness.ORG_A, owner, displayName = "Jaswant")
        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner, membershipId = id,
            bySelfPersonId = ownerPersonId,
            displayName = "Jaswant Kumar", phone = "9988776655",
            canSeeMoney = true, canAddCustomers = true, canCloseBills = false,
        )

        val member = team.find(TestHarness.ORG_A, id)!!
        assertEquals("Jaswant Kumar", member.displayName)
        assertEquals("9988776655", member.phone)
        assertTrue(Capability.SeeMoney in Capabilities.of(member))
        assertFalse(Capability.TakePayment in Capabilities.of(member))
    }

    @Test
    fun `you cannot change your own access from here`() = runTest {
        // An owner who could switch off their own permissions would lock themselves
        // out of the only app holding their season's work.
        val failure = runCatching {
            team.updateMember(
                orgId = TestHarness.ORG_A, can = owner, membershipId = "m-owner",
                bySelfPersonId = ownerPersonId,
                displayName = "Harpal", phone = null,
                canSeeMoney = false, canAddCustomers = false, canCloseBills = false,
            )
        }.exceptionOrNull()

        assertTrue(failure is TeamError.NotOnYourself)
        assertTrue(
            "and nothing changed",
            Capability.SeeMoney in Capabilities.of(team.find(TestHarness.ORG_A, "m-owner")!!),
        )
    }

    @Test
    fun `you cannot remove yourself either`() = runTest {
        val failure = runCatching {
            team.setActive(TestHarness.ORG_A, owner, "m-owner", ownerPersonId, active = false)
        }.exceptionOrNull()

        assertTrue(failure is TeamError.NotOnYourself)
    }

    @Test
    fun `the last owner cannot be removed`() = runTest {
        val second = team.addMember(TestHarness.ORG_A, owner, displayName = "Second owner")
        // Promote by hand; the app has no demote-an-owner flow yet.
        val promoted = team.find(TestHarness.ORG_A, second)!!.copy(role = MemberRole.Owner)
        h.db.orgDao().upsertMembership(promoted)

        // Two owners: removing one is fine, done by the other.
        team.setActive(TestHarness.ORG_A, owner, "m-owner", promoted.personId, active = false)

        val failure = runCatching {
            team.setActive(TestHarness.ORG_A, owner, second, ownerPersonId, active = false)
        }.exceptionOrNull()

        assertTrue("expected LastOwner, got $failure", failure is TeamError.LastOwner)
    }

    @Test
    fun `removing a driver keeps the work they did`() = runTest {
        val id = team.addMember(TestHarness.ORG_A, owner, displayName = "Jaswant")
        team.setActive(TestHarness.ORG_A, owner, id, ownerPersonId, active = false)

        assertEquals("gone from the working team", baseline, teamNow().size)
        val everyone = team.observeEveryone(TestHarness.ORG_A).first()
        assertEquals("but still on the books", baseline + 1, everyone.size)
        assertFalse(everyone.first { it.id == id }.active)
    }

    @Test
    fun `a driver who left can be taken back on`() = runTest {
        val id = team.addMember(TestHarness.ORG_A, owner, displayName = "Jaswant")
        team.setActive(TestHarness.ORG_A, owner, id, ownerPersonId, active = false)
        team.setActive(TestHarness.ORG_A, owner, id, ownerPersonId, active = true)

        assertEquals(baseline + 1, teamNow().size)
        assertEquals(null, team.find(TestHarness.ORG_A, id)!!.leftAt)
    }

    @Test
    fun `every change to the team is queued for upload`() = runTest {
        val id = team.addMember(TestHarness.ORG_A, owner, displayName = "Jaswant")
        team.setActive(TestHarness.ORG_A, owner, id, ownerPersonId, active = false)

        val queued = h.db.outboxDao().nextBatch(200)
            .filter { it.tableName == SyncQueue.TABLE_MEMBERSHIPS }
        assertEquals("the add and the removal", 2, queued.size)
    }
}
