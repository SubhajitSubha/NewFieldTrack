package com.example.fieldtrack.data.session

import com.example.fieldtrack.data.db.MemberRole

/**
 * The seven things an owner can hand to somebody, as one value.
 *
 * Gathered into a type so a preset can be compared against what a member
 * actually holds. Spread across seven parameters that comparison is seven
 * `&&`s that nobody updates when an eighth permission arrives; here it is the
 * compiler's problem.
 */
data class Grants(
    val seeMoney: Boolean = false,
    val addCustomers: Boolean = false,
    val closeBills: Boolean = false,
    val editTeam: Boolean = false,
    val editMachines: Boolean = false,
    val payOut: Boolean = false,
    val seePeopleCost: Boolean = false,
)

/**
 * A job, offered instead of seven switches.
 *
 * The switches are the truth — every capability check reads them, and a preset
 * writes nothing the switches could not have written by hand. This exists
 * because asking a contractor which of seven permissions a man should hold is
 * asking them to think in permissions. They do not; they think "he is my
 * senior driver", and that sentence already answers all seven.
 *
 * So a preset is a starting point, never a cage. Change one switch afterwards
 * and the picker says [matching] found nothing and the screen says "set by
 * hand" — which is a state, not a mistake.
 */
enum class RolePreset {
    /** Records work, sees nothing else. What every member is before anything is given. */
    Driver,

    /**
     * Can be sent to a customer alone.
     *
     * The three customer-facing grants together: name the price, close the bill,
     * take the cash, add the farmer who was not on the list.
     */
    SeniorDriver,

    /**
     * Runs the yard.
     *
     * Holds all four of the grants an owner alone can hand on, wage-sight
     * included. A manager who may pay people but may not see what people are
     * paid is not a job anybody has — and splitting the two here would only
     * mean every owner making a manager has to find the switch afterwards.
     */
    Manager,

    /** The same as you, and able to take you off. */
    Owner,
    ;

    /** What picking this writes to the seven switches. */
    val grants: Grants
        get() = when (this) {
            Driver -> Grants()
            SeniorDriver -> Grants(
                seeMoney = true,
                addCustomers = true,
                closeBills = true,
            )
            Manager -> Grants(
                seeMoney = true,
                addCustomers = true,
                closeBills = true,
                editTeam = true,
                editMachines = true,
                payOut = true,
                seePeopleCost = true,
            )
            // An owner's capabilities do not come from these columns at all —
            // Capabilities.of short-circuits on the role. They are still written
            // true so that stepping back down to Driver has something coherent
            // to clear, rather than leaving a demoted owner holding everything.
            Owner -> Manager.grants
        }

    /** The role this preset carries. Only one of them is not an ordinary member. */
    val role: MemberRole get() = if (this == Owner) MemberRole.Owner else MemberRole.Operator

    companion object {
        /**
         * Which preset describes this member, or null for a hand-made set.
         *
         * Null is the ordinary answer for anybody whose switches an owner has
         * since adjusted, so callers must have something to say about it rather
         * than treating it as an error.
         */
        fun matching(role: MemberRole, grants: Grants): RolePreset? {
            if (role == MemberRole.Owner) return Owner
            return entries.firstOrNull { it != Owner && it.grants == grants }
        }

        /**
         * The presets this editor may hand out.
         *
         * A manager may make a driver or a senior driver and nothing more. The
         * four switches in [Manager] are the ones that let somebody make a peer,
         * and a manager who could make a peer could, between the two of them,
         * put the owner out of their own business. The rules refuse it on the
         * server too; this only keeps the screen from offering what will fail.
         */
        fun offeredBy(editorIsOwner: Boolean): List<RolePreset> =
            if (editorIsOwner) entries.toList() else listOf(Driver, SeniorDriver)
    }
}
