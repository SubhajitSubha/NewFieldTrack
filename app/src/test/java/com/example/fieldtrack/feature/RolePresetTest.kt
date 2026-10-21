package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.Grants
import com.example.fieldtrack.data.session.RolePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The presets offered on the member screen.
 *
 * A preset writes nothing the seven switches could not write by hand, so what
 * is worth testing is not what each one grants but that the two directions
 * agree: pick a job, and the picker must still say that job afterwards.
 */
class RolePresetTest {

    private fun membership(grants: Grants, role: MemberRole = MemberRole.Operator) =
        MembershipEntity(
            id = "m1",
            orgId = "o1",
            personId = "p1",
            displayName = "Jaswant",
            role = role,
            canSeeMoney = grants.seeMoney,
            canAddCustomers = grants.addCustomers,
            canCloseBills = grants.closeBills,
            canEditTeam = grants.editTeam,
            canEditMachines = grants.editMachines,
            canPayOut = grants.payOut,
            canSeePeopleCost = grants.seePeopleCost,
            joinedAt = 0,
            updatedAt = 0,
        )

    @Test
    fun `every preset is recognised from what it grants`() {
        RolePreset.entries.forEach { preset ->
            assertEquals(
                "picking $preset then reading it back lost the name",
                preset,
                RolePreset.matching(preset.role, preset.grants),
            )
        }
    }

    @Test
    fun `a hand-made set matches no preset`() {
        // A senior driver with the machines switch added: a real thing an owner
        // might do, and the picker has to be able to say it is not a job.
        val byHand = RolePreset.SeniorDriver.grants.copy(editMachines = true)
        assertNull(RolePreset.matching(MemberRole.Operator, byHand))
    }

    @Test
    fun `a driver is the empty set, so no switch is left on by picking it`() {
        assertEquals(Grants(), RolePreset.Driver.grants)
    }

    @Test
    fun `stepping an owner down to driver clears everything`() {
        // The bug this guards: the screen shows an owner's switches as on, so a
        // demotion that only changed the role wrote all seven back as true.
        val stepped = RolePreset.Driver
        assertEquals(MemberRole.Operator, stepped.role)
        val after = membership(stepped.grants, stepped.role)
        val can = Capabilities.of(after)
        assertFalse(Capability.EditTeam in can)
        assertFalse(Capability.PayOut in can)
        assertFalse(Capability.SeePeopleCost in can)
        assertFalse(Capability.SeeMoney in can)
        // Their own work is never taken away.
        assertTrue(Capability.RecordWork in can)
    }

    @Test
    fun `a senior driver can be sent to a customer alone`() {
        val can = Capabilities.of(membership(RolePreset.SeniorDriver.grants))
        assertTrue(Capability.SeeMoney in can)
        assertTrue(Capability.TakePayment in can)
        assertTrue(Capability.EditCustomers in can)
        // And no further: these are the four an owner alone hands on.
        assertFalse(Capability.EditTeam in can)
        assertFalse(Capability.EditMachines in can)
        assertFalse(Capability.PayOut in can)
        assertFalse(Capability.SeePeopleCost in can)
    }

    @Test
    fun `a manager who may pay people may also see what people are paid`() {
        val can = Capabilities.of(membership(RolePreset.Manager.grants))
        assertTrue(Capability.PayOut in can)
        assertTrue(Capability.SeePeopleCost in can)
        assertTrue(Capability.EditTeam in can)
        assertTrue(Capability.EditMachines in can)
    }

    @Test
    fun `a manager is never offered the power to make another manager`() {
        val offered = RolePreset.offeredBy(editorIsOwner = false)
        assertEquals(listOf(RolePreset.Driver, RolePreset.SeniorDriver), offered)
        assertFalse(RolePreset.Manager in offered)
        assertFalse(RolePreset.Owner in offered)
    }

    @Test
    fun `an owner is offered all four`() {
        assertEquals(RolePreset.entries.toList(), RolePreset.offeredBy(editorIsOwner = true))
    }

    @Test
    fun `an owner reads as owner whatever the columns say`() {
        // Capabilities short-circuits on the role, so the picker must too —
        // otherwise an owner whose columns were never filled in shows as a
        // driver on the very screen that could demote them.
        assertEquals(
            RolePreset.Owner,
            RolePreset.matching(MemberRole.Owner, Grants()),
        )
    }
}
