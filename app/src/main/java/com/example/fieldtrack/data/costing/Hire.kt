package com.example.fieldtrack.data.costing

import com.example.fieldtrack.data.db.HireBasis
import com.example.fieldtrack.data.db.MachineOwner
import com.example.fieldtrack.data.db.dao.HireFacts
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity

/**
 * What is owed for the use of a machine that belongs to somebody else.
 *
 * Worked out from the finished work and the terms, never typed in: the amount builds up as
 * the machine works, and what was paid comes off it. Changing the terms changes what is
 * owed for all the work since the hire began, which is why the form says so.
 */
object Hire {

    /** Somebody else's machine with a way of paying for it set. Anything else has nothing owed. */
    fun applies(finance: MachineFinanceEntity?): Boolean =
        finance != null && finance.ownerKind in setOf(MachineOwner.Member, MachineOwner.Outsider) &&
            finance.hireBasis != null && finance.hireFrom != null

    /** What the work in [facts] earns the owner, in minor units, under the terms in [finance]. */
    fun owedMinor(finance: MachineFinanceEntity?, facts: HireFacts?): Long {
        if (!applies(finance) || facts == null) return 0
        val rate = finance!!.hireRateMinor ?: 0L
        return when (finance.hireBasis!!) {
            HireBasis.PerHour -> Math.round(facts.seconds / 3600.0 * rate)
            HireBasis.PerDay -> facts.days.toLong() * rate
            HireBasis.PerAcre -> Math.round(facts.landQuantity * rate)
            HireBasis.Share -> Math.round(facts.amountMinor * (finance.hireSharePercent ?: 0) / 100.0)
        }.coerceAtLeast(0)
    }

    /**
     * Who the money is for, as one key: a team member by their id, an outsider by their name
     * with the case and the spaces ignored, so "Gurmeet Singh " and "gurmeet singh" are one
     * person and what is paid to either comes off the same bill.
     */
    fun payeeKey(finance: MachineFinanceEntity): String? = when (finance.ownerKind) {
        MachineOwner.Member -> finance.ownerPersonId?.let { "p:$it" }
        MachineOwner.Outsider -> finance.ownerName?.let { nameKey(it) }
        else -> null
    }

    fun nameKey(name: String): String = "n:" + name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** The key a payment was made to. */
    fun payeeKey(toPersonId: String?, toName: String?): String? =
        toPersonId?.let { "p:$it" } ?: toName?.takeIf { it.isNotBlank() }?.let { nameKey(it) }
}
