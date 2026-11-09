package com.example.fieldtrack.data.repository

import com.example.fieldtrack.data.db.dao.LastCharge
import com.example.fieldtrack.data.db.dao.WorkUse

/**
 * What a business's machines have actually been used for, and how it charged for it.
 *
 * The app's own table of what a rotavator or a tanker is for is a guess about every business
 * on earth; this is the business itself. Where the two disagree, this wins.
 */
data class WorkHistory(
    /** Each machine's work, most done first, as the machine or as the implement behind one. */
    val byMachine: Map<String, List<String>> = emptyMap(),
    /** The business's work as a whole, most done first. */
    val overall: List<String> = emptyList(),
    /** How each kind of work was last charged. */
    val lastCharge: Map<String, LastCharge> = emptyMap(),
    /** Every work name the business has used anywhere, its own names included. */
    val names: List<String> = emptyList(),
) {
    /** What this machine is usually used for; with no machine, what the business usually does. */
    fun doneOn(machineId: String?): List<String> =
        if (machineId == null) overall.take(OVERALL_SHOWN) else byMachine[machineId].orEmpty()

    companion object {
        /** Enough of a business's usual work to start from, without listing everything it ever did once. */
        private const val OVERALL_SHOWN = 6

        fun of(use: List<WorkUse>, last: List<LastCharge>, names: List<String>): WorkHistory {
            val order = compareByDescending<Pair<String, WorkUse>> { it.second.jobs }.thenByDescending { it.second.lastAt }
            val byMachine = use.filter { it.machineId != null }
                .groupBy { it.machineId!! }
                .mapValues { (_, rows) -> rows.sortedWith(compareByDescending<WorkUse> { it.jobs }.thenByDescending { it.lastAt }).map { it.workType }.distinct() }
            // A job with an implement is counted twice above, once for each machine; once here.
            val overall = use.filter { !it.pulled }
                .groupBy { it.workType }
                .map { (work, rows) -> work to WorkUse(null, work, rows.sumOf { it.jobs }, rows.maxOf { it.lastAt }) }
                .sortedWith(order)
                .map { it.first }
            return WorkHistory(byMachine, overall, last.associateBy { it.workType }, names.distinct())
        }
    }
}
