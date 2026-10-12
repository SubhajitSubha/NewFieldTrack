package com.example.fieldtrack.data.session

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.MembershipEntity

/**
 * What a person is allowed to change.
 *
 * Every edit in the app is gated on one of these, in two places: the UI hides the
 * affordance, and the repository refuses the call. The second check is the real one —
 * a hidden button is a courtesy, not a boundary, and a driver's phone syncing into an
 * owner's business must not be able to rewrite what it was never shown.
 */
enum class Capability {
    /** Add or correct customers and their fields. */
    EditCustomers,

    /** Add or correct machines, their hours and their service schedule. */
    EditMachines,

    /** Rename the business, change the land unit it works in. */
    EditBusiness,

    /** Invite drivers, change what they are allowed to do, remove them. */
    EditTeam,

    /** See rates, balances and what anything earned. */
    SeeMoney,

    /** Close a bill and record money received. */
    TakePayment,

    /**
     * Change a job that has already been billed.
     *
     * Separate from [TakePayment] because it rewrites what a customer owes after the
     * fact. Correcting an acre figure a week later moves real money, so it is held to
     * the same bar as taking the payment itself.
     */
    AmendBilledWork,

    /** Correct a job's own measurements while it is still open. */
    RecordWork,
}

/**
 * The set of things this person may do in this organisation.
 *
 * Derived from their membership rather than stored, so revoking a permission takes
 * effect the moment the membership row changes.
 */
@JvmInline
value class Capabilities(private val granted: Set<Capability>) {

    operator fun contains(capability: Capability): Boolean = capability in granted

    fun require(capability: Capability) {
        if (capability !in granted) throw NotAllowed(capability)
    }

    class NotAllowed(val capability: Capability) :
        Exception("This person is not allowed to ${capability.name}")

    companion object {
        /** An owner may do anything inside their own business. */
        fun ofOwner() = Capabilities(Capability.entries.toSet())

        fun of(membership: MembershipEntity): Capabilities {
            if (membership.role == MemberRole.Owner) return ofOwner()
            return Capabilities(
                buildSet {
                    // Recording work is the whole point of a driver's account.
                    add(Capability.RecordWork)
                    if (membership.canAddCustomers) add(Capability.EditCustomers)
                    if (membership.canSeeMoney) add(Capability.SeeMoney)
                    if (membership.canCloseBills) {
                        add(Capability.TakePayment)
                        add(Capability.AmendBilledWork)
                        // Closing a bill means seeing the amount on it.
                        add(Capability.SeeMoney)
                    }
                }
            )
        }
    }
}

