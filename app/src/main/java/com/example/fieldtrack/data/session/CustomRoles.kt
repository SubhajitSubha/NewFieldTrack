package com.example.fieldtrack.data.session

import com.example.fieldtrack.data.db.MemberRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A role the owner made: a name and what it lets somebody do.
 *
 * The same switches a member carries, under a name that can be given to anybody and changed
 * in one place. Like a built-in preset it is only a starting point written onto the member:
 * every capability check still reads the member's own switches.
 */
@Serializable
data class CustomRole(val id: String, val name: String, val grants: List<String>) {

    fun toGrants() = Grants(
        seeMoney = "seeMoney" in grants,
        addCustomers = "addCustomers" in grants,
        closeBills = "closeBills" in grants,
        editTeam = "editTeam" in grants,
        editMachines = "editMachines" in grants,
        payOut = "payOut" in grants,
        seePeopleCost = "seePeopleCost" in grants,
        planWork = "planWork" in grants,
    )

    companion object {
        fun of(id: String, name: String, g: Grants) = CustomRole(
            id = id,
            name = name.trim(),
            grants = buildList {
                if (g.seeMoney) add("seeMoney")
                if (g.addCustomers) add("addCustomers")
                if (g.closeBills) add("closeBills")
                if (g.editTeam) add("editTeam")
                if (g.editMachines) add("editMachines")
                if (g.payOut) add("payOut")
                if (g.seePeopleCost) add("seePeopleCost")
                if (g.planWork) add("planWork")
            },
        )
    }
}

/** Reading and writing the list of roles an owner has made. */
object CustomRoles {
    private val json = Json { ignoreUnknownKeys = true }

    const val MAX_ROLES = 12
    const val MAX_NAME = 30

    fun parse(stored: String?): List<CustomRole> =
        if (stored.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<CustomRole>>(stored) }.getOrDefault(emptyList())

    fun encode(roles: List<CustomRole>): String? = roles.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    /**
     * The role an ordinary member's switches describe, if one the owner made does.
     * Like the built-in presets, a member whose switches have since been changed by hand matches none.
     */
    fun matching(roles: List<CustomRole>, role: MemberRole, grants: Grants): CustomRole? {
        if (role != MemberRole.Operator) return null
        return roles.firstOrNull { it.toGrants() == grants }
    }

    /**
     * What a new set of switches would be mistaken for: a built-in job or another role of the
     * owner's with exactly the same switches. Two names for one thing would make the label on
     * a person's page a coin toss.
     */
    fun clashWith(roles: List<CustomRole>, grants: Grants, exceptId: String?): String? {
        RolePreset.entries.firstOrNull { it != RolePreset.Owner && it.grants == grants }?.let { return it.name }
        return roles.firstOrNull { it.id != exceptId && it.toGrants() == grants }?.name
    }
}

/** What this member holds, as the same value a preset or a role is written in. */
fun com.example.fieldtrack.data.db.entity.MembershipEntity.grants() = Grants(
    seeMoney = canSeeMoney, addCustomers = canAddCustomers, closeBills = canCloseBills,
    editTeam = canEditTeam, editMachines = canEditMachines, payOut = canPayOut,
    seePeopleCost = canSeePeopleCost, planWork = canPlanWork,
)
