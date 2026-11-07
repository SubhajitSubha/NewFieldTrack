package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.TeamEventKind
import com.example.fieldtrack.data.repository.TeamError
import com.example.fieldtrack.data.repository.TeamRepository
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.CustomRole
import com.example.fieldtrack.data.session.CustomRoles
import com.example.fieldtrack.data.session.Grants
import com.example.fieldtrack.data.session.RolePreset
import com.example.fieldtrack.data.session.grants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Roles an owner makes: a name and what it lets somebody do, changed in one place for everybody who has it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomRolesTest {

    private lateinit var h: TestHarness
    private lateinit var team: TeamRepository
    private val org = TestHarness.ORG_A
    private val owner = Capabilities.ofOwner()
    private val mechanic = Grants(editMachines = true, payOut = true)

    @Before fun setUp() = runTest {
        h = TestHarness()
        h.seed()
        team = TeamRepository(db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids)
    }

    @After fun tearDown() = h.close()

    private suspend fun roles() = team.observeCustomRoles(org).first()

    private suspend fun hire(name: String): String = team.addMember(org, owner, name).also { id ->
        // Given the role the way a person's own page gives it: its switches written on them.
        val role = roles().first()
        val g = role.toGrants()
        val m = team.find(org, id)!!
        h.db.orgDao().upsertMembership(
            m.copy(
                canSeeMoney = g.seeMoney, canAddCustomers = g.addCustomers, canCloseBills = g.closeBills,
                canEditTeam = g.editTeam, canEditMachines = g.editMachines, canPlanWork = g.planWork,
                canPayOut = g.payOut, canSeePeopleCost = g.seePeopleCost,
            )
        )
    }

    @Test fun `a role is stored on the business and read back by name and switches`() = runTest {
        val id = team.saveCustomRole(org, owner, TestHarness.OWNER, null, "  Mechanic ", mechanic)
        val saved = roles().single()
        assertEquals(id, saved.id)
        assertEquals("Mechanic", saved.name)
        assertEquals(mechanic, saved.toGrants())
        assertTrue("it is queued for the server with the business record", h.db.outboxDao().nextBatch(100).any { it.tableName == "organisations" })
    }

    @Test fun `a person whose switches are a role's is that role, until they are adjusted by hand`() {
        val role = CustomRole.of("r1", "Mechanic", mechanic)
        assertEquals(role, CustomRoles.matching(listOf(role), MemberRole.Operator, mechanic))
        assertNull(CustomRoles.matching(listOf(role), MemberRole.Operator, mechanic.copy(seeMoney = true)))
        assertNull("an owner or a viewer is never a custom role", CustomRoles.matching(listOf(role), MemberRole.Viewer, mechanic))
    }

    @Test fun `a role cannot repeat a name or be a built-in job under another name`() = runTest {
        team.saveCustomRole(org, owner, TestHarness.OWNER, null, "Mechanic", mechanic)
        val repeated = runCatching { team.saveCustomRole(org, owner, TestHarness.OWNER, null, "mechanic", Grants(planWork = true)) }.exceptionOrNull()
        assertTrue(repeated is TeamError.RoleNameTaken)
        val sameAsManager = runCatching { team.saveCustomRole(org, owner, TestHarness.OWNER, null, "Boss", RolePreset.Manager.grants) }.exceptionOrNull()
        assertTrue(sameAsManager is TeamError.RoleSameAs)
        val sameAsOther = runCatching { team.saveCustomRole(org, owner, TestHarness.OWNER, null, "Fitter", mechanic) }.exceptionOrNull()
        assertTrue(sameAsOther is TeamError.RoleSameAs)
        val noName = runCatching { team.saveCustomRole(org, owner, TestHarness.OWNER, null, "  ", mechanic) }.exceptionOrNull()
        assertTrue(noName is TeamError.RoleNeedsName)
        assertEquals(1, roles().size)
    }

    @Test fun `only an owner can make roles`() = runTest {
        val manager = Capabilities.of(
            com.example.fieldtrack.data.db.entity.MembershipEntity(
                id = "m", orgId = org, personId = "p", displayName = "Boss", role = MemberRole.Operator,
                canEditTeam = true, canEditMachines = true, canPlanWork = true, canPayOut = true, canSeePeopleCost = true,
                canSeeMoney = true, canAddCustomers = true, canCloseBills = true, joinedAt = 0, updatedAt = 0,
            )
        )
        val refused = runCatching { team.saveCustomRole(org, manager, "p", null, "Mine", mechanic) }.exceptionOrNull()
        assertTrue(refused is Capabilities.NotAllowed)
        assertTrue(roles().isEmpty())
    }

    @Test fun `changing a role changes everybody who has it, and not those adjusted by hand`() = runTest {
        val id = team.saveCustomRole(org, owner, TestHarness.OWNER, null, "Mechanic", mechanic)
        val a = hire("Bittu")
        val b = hire("Sukhi")
        // Sukhi was given a little more by hand, so Sukhi is no longer "the role".
        h.db.orgDao().upsertMembership(team.find(org, b)!!.let { it.copy(canPlanWork = true) })

        team.saveCustomRole(org, owner, TestHarness.OWNER, id, "Mechanic", mechanic.copy(seePeopleCost = true))

        assertEquals(mechanic.copy(seePeopleCost = true), team.find(org, a)!!.grants())
        assertEquals("adjusted by hand: left alone", mechanic.copy(planWork = true), team.find(org, b)!!.grants())
        val changed = h.db.orgDao().observeTeamEvents(org).first().filter { it.kind == TeamEventKind.PermissionsChanged }
        assertEquals("the change is written in the team's log, once for each person", 1, changed.count { it.aboutName == "Bittu" })
    }

    @Test fun `deleting a role leaves the people who had it able to do what they could`() = runTest {
        val id = team.saveCustomRole(org, owner, TestHarness.OWNER, null, "Mechanic", mechanic)
        val a = hire("Bittu")
        team.deleteCustomRole(org, owner, id)
        assertTrue(roles().isEmpty())
        assertEquals(mechanic, team.find(org, a)!!.grants())
        assertNull(CustomRoles.matching(roles(), MemberRole.Operator, team.find(org, a)!!.grants()))
    }

    @Test fun `a business can keep only so many roles`() = runTest {
        repeat(CustomRoles.MAX_ROLES) { i ->
            team.saveCustomRole(
                org, owner, TestHarness.OWNER, null, "Role $i",
                // Twelve distinct combinations of the eight switches.
                Grants(
                    seeMoney = i and 1 != 0, addCustomers = i and 2 != 0, editMachines = i and 4 != 0,
                    planWork = i and 8 != 0, payOut = true,
                ),
            )
        }
        val over = runCatching { team.saveCustomRole(org, owner, TestHarness.OWNER, null, "One more", Grants(editTeam = true, payOut = true)) }.exceptionOrNull()
        assertTrue(over is TeamError.TooManyRoles)
    }
}
