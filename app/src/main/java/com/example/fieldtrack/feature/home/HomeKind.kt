package com.example.fieldtrack.feature.home

import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability

/**
 * Which of the three homes a person opens the app onto.
 *
 * Decided by what they are trusted with, not by the word on their membership. A
 * manager is an Operator on paper — only an owner can make an owner — but runs
 * the yard, and handing them a driver's "what have I done today" screen would be
 * showing them the wrong job. The role alone cannot tell those two apart; the
 * grants can.
 */
enum class HomeKind {
    /** Someone out on a machine: their job, their day, their machine. */
    Field,

    /** One person who is the whole business: drives, bills and collects. */
    Solo,

    /** Somebody watching other people work: the crew, the machines, the money. */
    Yard,
}

fun homeKindFor(role: MemberRole, can: Capabilities, business: Boolean): HomeKind = when {
    role == MemberRole.Owner -> if (business) HomeKind.Yard else HomeKind.Solo
    // Somebody watching: the crew, the machines and the money, with nothing to start.
    role == MemberRole.Viewer -> HomeKind.Yard
    // Either of these means looking after more than one's own machine. Neither
    // is in a senior driver's preset, so the line falls where the job does.
    Capability.EditTeam in can || Capability.EditMachines in can -> HomeKind.Yard
    else -> HomeKind.Field
}

/**
 * Whether a field worker is trusted at the customer's gate.
 *
 * A senior driver names the price, closes the bill and takes the cash, so the
 * customer book is a tool they reach for daily — it earns a tab. A driver who
 * does none of that has no use for it, and gets their machines instead.
 */
fun dealsWithCustomers(can: Capabilities): Boolean =
    Capability.TakePayment in can || Capability.EditCustomers in can
