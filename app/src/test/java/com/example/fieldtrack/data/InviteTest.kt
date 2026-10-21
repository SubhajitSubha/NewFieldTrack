package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.repository.isWellFormedInviteCode
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import com.example.fieldtrack.data.repository.TeamError
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.fieldtrack.data.db.OutboxOp

/** Handing a driver a code, and the ways that must not go wrong. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InviteTest {

    private lateinit var h: TestHarness
    private lateinit var team: TeamRepository

    private val owner = Capabilities.of(
        MembershipEntity(
            id = "m-owner", orgId = TestHarness.ORG_A, personId = TestHarness.OWNER,
            displayName = "Harpal", role = MemberRole.Owner,
            joinedAt = 0, updatedAt = 0,
        )
    )
    private val driver = Capabilities.of(
        MembershipEntity(
            id = "m-driver", orgId = TestHarness.ORG_A, personId = TestHarness.DRIVER,
            displayName = "Jaswant", role = MemberRole.Operator,
            joinedAt = 0, updatedAt = 0,
        )
    )

    @Before
    fun setUp() = runTest {
        h = TestHarness()
        team = TeamRepository(db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids)
        h.seed()
    }

    @After
    fun tearDown() = h.close()

    /** Fixed randomness, so a test can name the code it expects. */
    private val ORG_NAME = "Singh Agro Services"

    private var counter = 0
    private val fixedRandom: (Int) -> Int = { counter++ % it }

    @Test
    fun `an owner gets a usable code`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        assertTrue(isWellFormedInviteCode(invite.code))
        assertEquals(TestHarness.ORG_A, invite.orgId)
        assertEquals(MemberRole.Operator, invite.role)
        assertNull("a fresh code has not been used", invite.redeemedAt)
        assertTrue("and it has not already lapsed", invite.expiresAt > h.nowMillis)
    }

    @Test
    fun `a driver cannot invite anybody`() = runTest {
        val refused = runCatching {
            team.createInvite(TestHarness.ORG_A, ORG_NAME, driver, randomInt = fixedRandom)
        }

        assertTrue(
            "letting a driver mint codes would let the team grow itself",
            refused.isFailure,
        )
    }

    @Test
    fun `the permissions the owner picked travel with the code`() = runTest {
        val invite = team.createInvite(
            TestHarness.ORG_A, ORG_NAME, owner,
            canSeeMoney = true, canCloseBills = true, randomInt = fixedRandom,
        )

        assertTrue(invite.canSeeMoney)
        assertTrue(invite.canCloseBills)
        assertFalse(invite.canAddCustomers)
    }

    @Test
    fun `making a new code no longer kills the one already sent`() = runTest {
        val first = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        h.advanceMinutes(5)

        val second = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        // This reverses a deliberate rule, so the reason for the reversal is
        // worth as much as the rule was. It read: "a pile of live codes is how
        // somebody let go last season walks back in" — and that threat is real,
        // but a code dies on its own after a week, so a season-old one was never
        // the danger. What the rule actually cost was paid by the driver: a
        // second code silently retired the first, and the man still typing
        // yesterday's message was told his code was no good, with nothing
        // anywhere to tell him why.
        //
        // Exposure is now held down by sight instead of scarcity. Every live
        // code is listed with what it allows and when it dies, and any one of
        // them can be taken down on its own, which the single-code rule never
        // offered.
        assertNotNull(h.db.orgDao().findUsableInvite(first.code, h.nowMillis))
        assertNotNull(h.db.orgDao().findUsableInvite(second.code, h.nowMillis))
    }

    @Test
    fun `the newest code is the one the invite screen offers`() = runTest {
        team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        h.advanceMinutes(1)
        val second = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        val open = team.observeOpenInvite(TestHarness.ORG_A).first()
        assertEquals(second.code, open?.code)
    }

    @Test
    fun `a code stops working once it lapses`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        h.nowMillis = invite.expiresAt + 1

        assertNull(h.db.orgDao().findUsableInvite(invite.code, h.nowMillis))
        assertNull(team.observeOpenInvite(TestHarness.ORG_A).first())
    }

    @Test
    fun `revoking leaves no code open`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        h.advanceMinutes(2)

        team.revokeInvites(TestHarness.ORG_A, owner)

        assertNull(h.db.orgDao().findUsableInvite(invite.code, h.nowMillis))
        assertNull(team.observeOpenInvite(TestHarness.ORG_A).first())
    }

    @Test
    fun `a driver cannot revoke a code either`() = runTest {
        team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        val refused = runCatching { team.revokeInvites(TestHarness.ORG_A, driver) }

        assertTrue(refused.isFailure)
        assertNotNull(team.observeOpenInvite(TestHarness.ORG_A).first())
    }

    @Test
    fun `a code belongs to one business only`() = runTest {
        team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        assertNull(
            "org B must not see org A's code",
            team.observeOpenInvite(TestHarness.ORG_B).first(),
        )
    }

    @Test
    fun `a joiner's membership is queued before anything else about them`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val code = com.example.fieldtrack.data.db.entity.InviteCodeDoc(
            code = invite.code,
            orgId = invite.orgId,
            orgName = ORG_NAME,
            inviteId = invite.id,
            role = invite.role,
            expiresAt = invite.expiresAt,
        )

        // Only the rows the join itself adds. Making the code already queued
        // a team event of its own, and counting that one made this test pass
        // while the bug was still there.
        val before = h.db.outboxDao().nextBatch(200).maxOfOrNull { it.seq } ?: 0

        team.redeemInvite(
            invite = code,
            personId = "uid-driver",
            displayName = "Jaswant Kumar",
            phone = null,
        )

        // Every rule guarding this business asks the server whether the caller
        // is a member, so until the membership document lands the joiner is a
        // stranger and anything queued ahead of it is refused. The queue sends
        // in order and stops at a refusal, so getting this backwards meant a
        // driver who had just joined could never sync anything, ever.
        val queued = h.db.outboxDao().nextBatch(200)
            .filter { it.orgId == TestHarness.ORG_A && it.seq > before }
            .map { it.tableName }
        val membershipAt = queued.indexOf("memberships")
        val eventAt = queued.indexOf("team_events")
        assertTrue("the membership was never queued: $queued", membershipAt >= 0)
        assertTrue("the join event was never queued: $queued", eventAt >= 0)
        assertTrue(
            "the join event is queued ahead of the membership it depends on",
            membershipAt < eventAt,
        )
    }

    @Test
    fun `redeeming records the code the person came in on`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val code = com.example.fieldtrack.data.db.entity.InviteCodeDoc(
            code = invite.code,
            orgId = invite.orgId,
            orgName = ORG_NAME,
            inviteId = invite.id,
            role = invite.role,
            expiresAt = invite.expiresAt,
        )

        val membership = team.redeemInvite(
            invite = code,
            personId = "uid-driver",
            displayName = "Jaswant Kumar",
            phone = null,
        )

        assertEquals(
            "without this the server cannot tell an invited driver from a stranger",
            invite.code,
            membership.joinedWithCode,
        )
        assertEquals("uid-driver", membership.personId)
        assertEquals("${TestHarness.ORG_A}_uid-driver", membership.id)
    }

    @Test
    fun `somebody taken off the team can be invited back`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val code = com.example.fieldtrack.data.db.entity.InviteCodeDoc(
            code = invite.code,
            orgId = invite.orgId,
            orgName = ORG_NAME,
            inviteId = invite.id,
            role = invite.role,
            expiresAt = invite.expiresAt,
        )
        val driver = TestHarness.DRIVER
        val mine = requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, driver))
        h.db.orgDao().upsertMembership(mine.copy(active = false, leftAt = h.nowMillis))

        val rejoined = team.redeemInvite(
            invite = code, personId = driver, displayName = "Jaswant Kumar", phone = null,
        )

        // The row survives a removal so the business stays in their history. If
        // that row alone barred them, a driver let go once could never come back.
        assertTrue(rejoined.active)
        assertTrue(requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, driver)).active)
    }

    @Test
    fun `a code cannot be redeemed into a business you already belong to`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val code = com.example.fieldtrack.data.db.entity.InviteCodeDoc(
            code = invite.code,
            orgId = invite.orgId,
            orgName = ORG_NAME,
            inviteId = invite.id,
            role = invite.role,
            expiresAt = invite.expiresAt,
        )
        val alreadyIn = TestHarness.DRIVER
        val before = requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, alreadyIn))

        val failure = runCatching {
            team.redeemInvite(
                invite = code,
                personId = alreadyIn,
                displayName = "Harpal",
                phone = null,
            )
        }.exceptionOrNull()

        // Redeeming writes the membership the code describes over whatever is
        // already there. An owner who pastes their own driver code to see what it
        // looks like would be demoted to a driver in their own business — and the
        // server refuses self-promotion, so nothing in the app could undo it.
        assertTrue(failure is TeamError.AlreadyInBusiness)
        val after = requireNotNull(h.db.orgDao().membership(TestHarness.ORG_A, alreadyIn))
        assertEquals(before.role, after.role)
        assertEquals(before.displayName, after.displayName)
    }

    @Test
    fun `an owner creating a business carries no code`() = runTest {
        val mine = h.db.orgDao().observeEveryone(TestHarness.ORG_A).first()

        assertTrue(
            "an owner made the business rather than joining it",
            mine.all { it.joinedWithCode == null },
        )
    }

    @Test
    fun `the code is queued for the server`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        val queued = h.db.outboxDao().nextBatch(500)
            .filter { it.tableName == SyncQueue.TABLE_INVITES }
        assertEquals(
            "a code the server never hears about cannot be redeemed on another phone",
            1,
            queued.size,
        )
        assertEquals(invite.id, queued.single().entityId)
    }

    @Test
    fun `a second code does not kill the first`() = runTest {
        val first = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val second = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        // Issuing one used to retire every other. A contractor taking on a crew
        // hands a different code to each driver, and the second silently killed
        // the first — so the driver still typing yesterday's message was told
        // his code was no good, with nothing anywhere to say why.
        val open = team.observeOpenInvites(TestHarness.ORG_A).first().map { it.code }
        assertEquals(2, open.size)
        assertTrue(first.code in open)
        assertTrue(second.code in open)
    }

    @Test
    fun `a code withdrawn while somebody is watching leaves the list`() = runTest {
        val first = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val second = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val codes = team.observeOpenInvites(TestHarness.ORG_A)
        assertEquals(2, codes.first().size)

        team.revokeInvite(TestHarness.ORG_A, first.id, owner)

        // The cutoff has to be read again on each emission. Passed into the
        // query instead, it is fixed when the flow is built, and the withdrawn
        // code goes on looking live for as long as the screen stays open — which
        // is exactly when somebody is watching to see it go.
        assertEquals(listOf(second.code), codes.first().map { it.code })
    }

    @Test
    fun `revoking one code leaves the rest working`() = runTest {
        val first = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        val second = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        team.revokeInvite(TestHarness.ORG_A, first.id, owner)

        assertEquals(
            listOf(second.code),
            team.observeOpenInvites(TestHarness.ORG_A).first().map { it.code },
        )
    }

    @Test
    fun `taking a code down removes the public copy, not just the private row`() = runTest {
        val invite = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        team.revokeInvite(TestHarness.ORG_A, invite.id, owner)

        // The published document is the only copy anybody outside the business
        // can reach. A code that still answers is still a way in, whatever the
        // private row says about it.
        val queued = h.db.outboxDao().nextBatch(50)
        assertTrue(
            queued.any {
                it.tableName == SyncQueue.TABLE_INVITE_CODES &&
                    it.entityId == invite.code &&
                    it.operation == OutboxOp.Delete
            },
        )
    }

}
