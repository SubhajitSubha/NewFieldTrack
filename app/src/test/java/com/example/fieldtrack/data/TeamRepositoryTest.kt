package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.entity.membershipId
import org.junit.Assert.assertNotNull
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
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.TeamEventKind

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
        team = TeamRepository(
            db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids,
            pauseLiveJob = { orgId, jobId -> h.work.pause(orgId, jobId) },
        )
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

    // --------------------------------------------- promotion, demotion, leaving

    @Test
    fun `an owner can promote a driver`() = runTest {
        val id = h.db.orgDao().observeTeam(TestHarness.ORG_A).first()
            .first { it.personId == TestHarness.DRIVER }.id

        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner, membershipId = id,
            bySelfPersonId = TestHarness.OWNER, displayName = "Jaswant Kumar",
            phone = null, canSeeMoney = false, canAddCustomers = false,
            canCloseBills = false, role = MemberRole.Owner,
        )

        val after = h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        assertEquals(MemberRole.Owner, after?.role)
        assertEquals(
            "an owner holds every capability, and the row should say so",
            true,
            after?.canSeeMoney,
        )
    }

    @Test
    fun `the last owner cannot be demoted`() = runTest {
        // Promote the driver, then try to demote them back while they are alone.
        val id = h.db.orgDao().observeTeam(TestHarness.ORG_A).first()
            .first { it.personId == TestHarness.DRIVER }.id
        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner, membershipId = id,
            bySelfPersonId = TestHarness.OWNER, displayName = "Jaswant",
            phone = null, canSeeMoney = true, canAddCustomers = true,
            canCloseBills = true, role = MemberRole.Owner,
        )
        // Every other owner stands down, leaving this one alone.
        h.db.orgDao().observeTeam(TestHarness.ORG_A).first()
            .filter { it.role == MemberRole.Owner && it.personId != TestHarness.DRIVER }
            .forEach { h.db.orgDao().upsertMembership(it.copy(active = false)) }

        val refused = runCatching {
            team.updateMember(
                orgId = TestHarness.ORG_A, can = owner, membershipId = id,
                bySelfPersonId = TestHarness.OWNER, displayName = "Jaswant",
                phone = null, canSeeMoney = true, canAddCustomers = true,
                canCloseBills = true, role = MemberRole.Operator,
            )
        }

        assertTrue(
            "a business with no owner can never be changed again by anybody",
            refused.exceptionOrNull() is TeamError.LastOwner,
        )
    }

    @Test
    fun `a driver can leave without anybody's permission`() = runTest {
        team.leaveOrg(TestHarness.ORG_A, TestHarness.DRIVER)

        val after = h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        assertEquals("nobody needs their boss's agreement to stop working", false, after?.active)
        assertNotNull("and the record of having worked there stays", after)
    }

    @Test
    fun `leaving is queued so the business finds out`() = runTest {
        val before = h.db.outboxDao().nextBatch(500)
            .count { it.tableName == SyncQueue.TABLE_MEMBERSHIPS }

        team.leaveOrg(TestHarness.ORG_A, TestHarness.DRIVER)

        assertEquals(
            before + 1,
            h.db.outboxDao().nextBatch(500)
                .count { it.tableName == SyncQueue.TABLE_MEMBERSHIPS },
        )
    }

    @Test
    fun `the only owner cannot walk out`() = runTest {
        h.db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(TestHarness.ORG_A, TestHarness.OWNER),
                orgId = TestHarness.ORG_A, personId = TestHarness.OWNER,
                displayName = "Harpal", role = MemberRole.Owner,
                joinedAt = h.nowMillis, updatedAt = h.nowMillis,
            )
        )

        val refused = runCatching { team.leaveOrg(TestHarness.ORG_A, TestHarness.OWNER) }

        assertTrue(refused.exceptionOrNull() is TeamError.LastOwner)
    }

    private suspend fun startJobForDriver(): String = h.work.startJob(
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
    fun `taking somebody off stops the work they were in the middle of`() = runTest {
        val jobId = startJobForDriver()
        assertEquals(JobStatus.Running, h.db.jobDao().find(TestHarness.ORG_A, jobId)?.status)

        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )

        // Removal used to touch the membership and nothing else, so the job ran
        // on for ever: still ticking in the owner's live list, still holding the
        // machine at Working, and no finish could ever arrive — the driver's
        // phone is shut out of this business the moment the removal reaches it.
        assertEquals(JobStatus.Paused, h.db.jobDao().find(TestHarness.ORG_A, jobId)?.status)
    }

    @Test
    fun `work already stopped is left alone`() = runTest {
        val jobId = startJobForDriver()
        h.work.pause(TestHarness.ORG_A, jobId)
        val before = requireNotNull(h.db.jobDao().find(TestHarness.ORG_A, jobId))

        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )

        val after = requireNotNull(h.db.jobDao().find(TestHarness.ORG_A, jobId))
        assertEquals(JobStatus.Paused, after.status)
        assertEquals("nothing to stop, so nothing is touched", before.revision, after.revision)
    }

    @Test
    fun `somebody taken off still has a name against the work they did`() = runTest {
        startJobForDriver()

        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )

        // The jobs list puts a name against each job through this list. Reading
        // it from the active-only one sent every job a driver ever did nameless
        // the moment they were let go.
        assertTrue(
            "the row stays, so the name can still be looked up",
            team.observeEveryone(TestHarness.ORG_A).first().any { it.personId == TestHarness.DRIVER },
        )
        assertFalse(
            "but they are no longer on the team",
            team.observeTeam(TestHarness.ORG_A).first().any { it.personId == TestHarness.DRIVER },
        )
    }


    @Test
    fun `handing over makes them an owner with everything that carries`() = runTest {
        team.handOverOwnership(
            orgId = TestHarness.ORG_A,
            can = owner,
            toMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            stepBack = false,
        )

        val heir = requireNotNull(
            h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        )
        assertEquals(MemberRole.Owner, heir.role)
        // Written down rather than implied, so stepping them back later does
        // not quietly restore a driver who may do nothing.
        assertTrue(heir.canSeeMoney)
        assertTrue(heir.canCloseBills)
    }

    @Test
    fun `stepping back leaves the business with an owner, never none`() = runTest {
        team.handOverOwnership(
            orgId = TestHarness.ORG_A,
            can = owner,
            toMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            stepBack = true,
        )

        val heir = requireNotNull(
            h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        )
        val me = requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, ownerPersonId))
        assertEquals(MemberRole.Owner, heir.role)
        assertEquals(MemberRole.Operator, me.role)
        assertEquals(1, h.db.orgDao().activeOwnerCount(TestHarness.ORG_A))
    }

    @Test
    fun `the promotion lands before the step back, so there is never a gap`() = runTest {
        // The order is the safety. Were it reversed, a phone that died between
        // the two writes would leave a business with nobody able to fix it —
        // and nothing inside the app could put that right.
        team.handOverOwnership(
            orgId = TestHarness.ORG_A,
            can = owner,
            toMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            stepBack = true,
        )

        assertTrue(h.db.orgDao().activeOwnerCount(TestHarness.ORG_A) >= 1)
    }

    @Test
    fun `you cannot hand the business to yourself`() = runTest {
        val failure = runCatching {
            team.handOverOwnership(
                orgId = TestHarness.ORG_A,
                can = owner,
                toMembershipId = "m-owner",
                bySelfPersonId = ownerPersonId,
                stepBack = true,
            )
        }.exceptionOrNull()

        assertTrue(failure is TeamError.NotOnYourself)
    }

    @Test
    fun `somebody off the team cannot be made an owner`() = runTest {
        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )

        val failure = runCatching {
            team.handOverOwnership(
                orgId = TestHarness.ORG_A,
                can = owner,
                toMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
                bySelfPersonId = ownerPersonId,
                stepBack = true,
            )
        }.exceptionOrNull()

        assertTrue(failure is TeamError.NoSuchMember)
    }


    /** A driver written down by hand, before they had a phone of their own. */
    private suspend fun handWritten(name: String): MembershipEntity {
        val person = "placeholder-$name"
        val row = MembershipEntity(
            id = membershipId(TestHarness.ORG_A, person),
            orgId = TestHarness.ORG_A,
            personId = person,
            displayName = name,
            role = MemberRole.Operator,
            joinedAt = 1L,
            updatedAt = 1L,
        )
        h.db.orgDao().upsertMembership(row)
        return row
    }

    @Test
    fun `merging moves the work onto the identity they can prove`() = runTest {
        val placeholder = handWritten("Jaswant Kumar")
        val jobId = h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Rotavator",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_000,
            machineId = "machine-1",
            operatorPersonId = placeholder.personId,
        )

        team.mergePeople(
            orgId = TestHarness.ORG_A,
            can = owner,
            keepMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            absorbMembershipId = placeholder.id,
        )

        // The season stops being split across two rows wearing one name.
        assertEquals(
            TestHarness.DRIVER,
            h.db.jobDao().find(TestHarness.ORG_A, jobId)?.operatorPersonId,
        )
    }

    @Test
    fun `the hand-written row is retired, not destroyed`() = runTest {
        val placeholder = handWritten("Jaswant Kumar")

        team.mergePeople(
            orgId = TestHarness.ORG_A,
            can = owner,
            keepMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            absorbMembershipId = placeholder.id,
        )

        val after = requireNotNull(
            h.db.orgDao().membership(TestHarness.ORG_A, placeholder.personId)
        )
        assertFalse(after.active)
        assertFalse(teamNow().any { it.personId == placeholder.personId })
    }

    @Test
    fun `moved work is sent again, so the server stops holding the old split`() = runTest {
        val placeholder = handWritten("Jaswant Kumar")
        h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Rotavator",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_000,
            machineId = "machine-1",
            operatorPersonId = placeholder.personId,
        )
        h.db.outboxDao().nextBatch(500).forEach { h.db.outboxDao().remove(it) }

        team.mergePeople(
            orgId = TestHarness.ORG_A,
            can = owner,
            keepMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            absorbMembershipId = placeholder.id,
        )

        // Without this the merge is a local tidy-up: the server keeps the work
        // under the old id, and the next phone to read the business from
        // scratch finds the two of them split apart exactly as before.
        val queued = h.db.outboxDao().nextBatch(500)
        assertTrue(queued.any { it.tableName == SyncQueue.TABLE_JOBS })
        assertTrue(queued.any { it.tableName == SyncQueue.TABLE_MEMBERSHIPS })
    }

    @Test
    fun `a record cannot be merged into itself`() = runTest {
        val mine = membershipId(TestHarness.ORG_A, TestHarness.DRIVER)
        val failure = runCatching {
            team.mergePeople(
                orgId = TestHarness.ORG_A,
                can = owner,
                keepMembershipId = mine,
                absorbMembershipId = mine,
            )
        }.exceptionOrNull()

        assertTrue(failure is TeamError.NotOnYourself)
    }


    private suspend fun changes() =
        h.db.orgDao().observeTeamEvents(TestHarness.ORG_A).first()

    @Test
    fun `taking somebody off is written down, with who did it`() = runTest {
        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )

        val event = changes().single { it.kind == TeamEventKind.Removed }
        assertEquals("Harpal Singh", event.byName)
        assertEquals("Jaswant Kumar", event.aboutName)
    }

    @Test
    fun `being let at the money is written down, because it is the one people regret`() =
        runTest {
            team.updateMember(
                orgId = TestHarness.ORG_A,
                can = owner,
                membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
                bySelfPersonId = ownerPersonId,
                displayName = "Jaswant Kumar",
                phone = null,
                canSeeMoney = true,
                canAddCustomers = false,
                canCloseBills = false,
            )

            val event = changes().single { it.kind == TeamEventKind.PermissionsChanged }
            assertEquals("money", event.detail)
        }

    @Test
    fun `correcting a spelling is not worth a line in a log about trust`() = runTest {
        team.updateMember(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            displayName = "Jaswant Kumaar",
            phone = null,
            canSeeMoney = false,
            canAddCustomers = false,
            canCloseBills = false,
        )

        assertTrue(changes().none { it.kind == TeamEventKind.PermissionsChanged })
    }

    @Test
    fun `the names written down survive a later rename`() = runTest {
        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )
        team.updateMember(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            displayName = "Somebody Else Entirely",
            phone = null,
            canSeeMoney = false,
            canAddCustomers = false,
            canCloseBills = false,
        )

        // A line about who let whom see the money has to go on saying that,
        // whatever either of them is called afterwards.
        assertEquals(
            "Jaswant Kumar",
            changes().single { it.kind == TeamEventKind.Removed }.aboutName,
        )
    }

    @Test
    fun `what is written down is sent on like everything else`() = runTest {
        h.db.outboxDao().nextBatch(500).forEach { h.db.outboxDao().remove(it) }

        team.setActive(
            orgId = TestHarness.ORG_A,
            can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId,
            active = false,
        )

        assertTrue(
            h.db.outboxDao().nextBatch(500)
                .any { it.tableName == SyncQueue.TABLE_TEAM_EVENTS },
        )
    }


    @Test
    fun `taking the rank away takes away what came with it`() = runTest {
        val who = membershipId(TestHarness.ORG_A, TestHarness.DRIVER)
        // Up to owner: every capability comes with the rank.
        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner, membershipId = who,
            bySelfPersonId = ownerPersonId, displayName = "Jaswant Kumar", phone = null,
            canSeeMoney = false, canAddCustomers = false, canCloseBills = false,
            role = MemberRole.Owner,
        )
        assertTrue(requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)).canSeeMoney)

        // And back down, with the screen still showing them all on — which it
        // must, because while he was an owner they were all true.
        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner, membershipId = who,
            bySelfPersonId = ownerPersonId, displayName = "Jaswant Kumar", phone = null,
            canSeeMoney = true, canAddCustomers = true, canCloseBills = true,
            role = MemberRole.Operator,
        )

        val after = requireNotNull(
            h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        )
        assertEquals(MemberRole.Operator, after.role)
        assertFalse("demotion used to leave him full sight of the money", after.canSeeMoney)
        assertFalse(after.canCloseBills)
        assertFalse(after.canAddCustomers)
    }

    @Test
    fun `an ordinary edit of a driver still does what the switches say`() = runTest {
        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId, displayName = "Jaswant Kumar", phone = null,
            canSeeMoney = true, canAddCustomers = false, canCloseBills = false,
        )

        val after = requireNotNull(
            h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        )
        assertTrue("only a demotion resets them", after.canSeeMoney)
    }


    @Test
    fun `a manager is a driver with the switches on, not a new rank`() = runTest {
        team.updateMember(
            orgId = TestHarness.ORG_A, can = owner,
            membershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId, displayName = "Jaswant Kumar", phone = null,
            canSeeMoney = true, canAddCustomers = true, canCloseBills = true,
            canEditTeam = true, canEditMachines = true,
        )

        val them = requireNotNull(
            h.db.orgDao().membership(TestHarness.ORG_A, TestHarness.DRIVER)
        )
        val can = Capabilities.of(them)
        assertEquals("still a driver by rank", MemberRole.Operator, them.role)
        assertTrue(Capability.EditTeam in can)
        assertTrue(Capability.EditMachines in can)
        // Not handed out with the rest: an owner grants these one at a time.
        assertFalse(Capability.SeePeopleCost in can)
    }

    @Test
    fun `standing down from owner takes every switch with it`() = runTest {
        team.handOverOwnership(
            orgId = TestHarness.ORG_A, can = owner,
            toMembershipId = membershipId(TestHarness.ORG_A, TestHarness.DRIVER),
            bySelfPersonId = ownerPersonId, stepBack = true,
        )

        val me = requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, ownerPersonId))
        val can = Capabilities.of(me)
        assertEquals(MemberRole.Operator, me.role)
        assertFalse("the rank is gone, so what it carried is gone", Capability.EditTeam in can)
        assertFalse(Capability.SeePeopleCost in can)
        assertFalse(Capability.PayOut in can)
    }

}
