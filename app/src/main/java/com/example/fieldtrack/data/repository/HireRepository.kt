package com.example.fieldtrack.data.repository

import com.example.fieldtrack.data.costing.Hire
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.HireBasis
import com.example.fieldtrack.data.db.MachineOwner
import com.example.fieldtrack.data.region.BusinessClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One owner of machines, with what their machines have earned them, what was paid, and what is left. */
data class HireBalance(
    val payeeKey: String,
    val name: String,
    /** Set when the owner is on the team. */
    val personId: String?,
    val owedMinor: Long,
    val paidMinor: Long,
    val machines: List<HiredMachine>,
) {
    val balanceMinor: Long get() = owedMinor - paidMinor
}

data class HiredMachine(
    val machineId: String,
    val name: String,
    val basis: HireBasis,
    val owedMinor: Long,
)

/**
 * What is owed for machines that belong to somebody else: a team member's own tractor, or one
 * hired in from outside. Worked out live from the finished work and the terms, so it is never
 * out of step with the jobs; payments made for hire come off it.
 */
class HireRepository(private val db: FieldTrackDatabase) {

    fun observe(orgId: String): Flow<List<HireBalance>> {
        val offsetMillis = BusinessClock.zone.rules.getOffset(java.time.Instant.now()).totalSeconds * 1000L
        return combine(
            db.machineDao().observeFinance(orgId),
            db.machineDao().observeHireFacts(orgId, offsetMillis, 0L, Long.MAX_VALUE),
            db.orgDao().observeHirePayouts(orgId),
            db.machineDao().observeAll(orgId),
            db.orgDao().observeEveryone(orgId),
        ) { finances, facts, payouts, machines, people ->
            val factsOf = facts.associateBy { it.machineId }
            val machineName = machines.associate { it.id to it.name }
            val personName = people.associate { it.personId to it.displayName }
            val hired = finances.filter { Hire.applies(it) && Hire.payeeKey(it) != null }
            hired.groupBy { Hire.payeeKey(it)!! }.map { (key, group) ->
                val first = group.first()
                val lines = group.map {
                    HiredMachine(it.id, machineName[it.id].orEmpty(), it.hireBasis!!, Hire.owedMinor(it, factsOf[it.id]))
                }
                HireBalance(
                    payeeKey = key,
                    name = when (first.ownerKind) {
                        MachineOwner.Member -> personName[first.ownerPersonId].orEmpty()
                        else -> first.ownerName.orEmpty()
                    },
                    personId = first.ownerPersonId.takeIf { first.ownerKind == MachineOwner.Member },
                    owedMinor = lines.sumOf { it.owedMinor },
                    paidMinor = payouts.filter { Hire.payeeKey(it.toPersonId, it.toName) == key }.sumOf { it.amountMinor },
                    machines = lines,
                )
            }.sortedBy { it.name.lowercase() }
        }
    }

    /**
     * What the hired machines earned their owners from work finished between [from] and [to]:
     * the cost of hire in a season or a year, for a statement of what the business made.
     */
    fun observeOwedBetween(orgId: String, from: Long, to: Long): Flow<Long> {
        val offsetMillis = BusinessClock.zone.rules.getOffset(java.time.Instant.now()).totalSeconds * 1000L
        return combine(
            db.machineDao().observeFinance(orgId),
            db.machineDao().observeHireFacts(orgId, offsetMillis, from, to),
        ) { finances, facts ->
            val factsOf = facts.associateBy { it.machineId }
            finances.sumOf { Hire.owedMinor(it, factsOf[it.id]) }
        }
    }

    /** What is owed on one machine alone, for the machine's own page. */
    fun observeFor(orgId: String, machineId: String): Flow<HiredMachine?> =
        combine(observe(orgId), db.machineDao().observeAll(orgId)) { balances, _ ->
            balances.flatMap { it.machines }.firstOrNull { it.machineId == machineId }
        }
}
