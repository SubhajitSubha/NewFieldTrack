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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun `making a new code stops the old one working`() = runTest {
        val first = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)
        h.advanceMinutes(5)

        val second = team.createInvite(TestHarness.ORG_A, ORG_NAME, owner, randomInt = fixedRandom)

        assertNull(
            "a pile of live codes is how somebody let go last season walks back in",
            h.db.orgDao().findUsableInvite(first.code, h.nowMillis),
        )
        assertNotNull(h.db.orgDao().findUsableInvite(second.code, h.nowMillis))
    }

    @Test
    fun `only one code is open at a time`() = runTest {
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
}
