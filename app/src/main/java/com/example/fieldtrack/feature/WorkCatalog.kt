package com.example.fieldtrack.feature

import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.repository.WorkHistory

/**
 * Which work a machine does, and which machines work together.
 *
 * A tractor does nothing by itself: its work is whatever is on the back. A rotavator does one
 * thing, behind the business's own tractor or behind the farmer's. A combine harvests. The table
 * here is what such a machine is for anywhere; [WorkHistory] is what this business's machine has
 * actually done, and goes first.
 */
object WorkCatalog {

    const val OTHER = "Other"

    /** A name long enough for any work, short enough for a bill's line and a chip. */
    const val MAX_NAME = 40

    private val ours = workTypeOptions.associateBy { it.key }

    val other: WorkTypeOption get() = ours.getValue(OTHER)

    /** A stored work as a choice: one of ours, or a name the business gave its own work. */
    fun option(key: String): WorkTypeOption = ours[key] ?: other.copy(key = key, name = key)

    fun isOwnName(key: String): Boolean = key !in ours

    /** Machines whose work is decided by what is on the back of them. */
    fun carriesTools(kind: MachineKind): Boolean = kind == MachineKind.Tractor || kind == MachineKind.PowerTiller

    /** Machines that work behind or beside a tractor, or go out alone to a farmer who has one. */
    fun isTool(kind: MachineKind): Boolean = kind in TOOLS

    /**
     * Tools that never move by themselves, so the only tractor a business has is the one that
     * pulled them. A thresher, pump, sprayer or transplanter may have its own engine, and is
     * not assumed.
     */
    private fun alwaysPulled(kind: MachineKind): Boolean = kind in ALWAYS_PULLED

    /** What a kind of machine is for, most usual first. A tractor's list grows with what the business has to put on it. */
    fun usualFor(kind: MachineKind): List<String> = when (kind) {
        MachineKind.Tractor -> listOf("Ploughing", "Rotavator", "Hauling", "Levelling", "Sowing", "MachineHire")
        MachineKind.PowerTiller -> listOf("Rotavator", "Puddling", "Ploughing", "Hauling", "MachineHire")
        MachineKind.Harvester, MachineKind.SugarcaneHarvester, MachineKind.PotatoDigger -> listOf("Harvesting")
        MachineKind.Rotavator -> listOf("Rotavator", "Puddling")
        MachineKind.Cultivator, MachineKind.Plough, MachineKind.Harrow -> listOf("Ploughing")
        MachineKind.Leveller -> listOf("Levelling")
        MachineKind.Seeder -> listOf("Sowing")
        MachineKind.Transplanter -> listOf("Planting")
        MachineKind.Ridger -> listOf("Ridging")
        MachineKind.Mulcher -> listOf("Mulching")
        MachineKind.Sprayer, MachineKind.DroneSprayer -> listOf("Spraying")
        MachineKind.Baler -> listOf("Baling")
        MachineKind.Thresher -> listOf("Threshing")
        MachineKind.Trolley, MachineKind.GrainCart -> listOf("Hauling")
        MachineKind.WaterTanker -> listOf("WaterSupply", "Hauling")
        MachineKind.Pump -> listOf("Pumping")
        MachineKind.Excavator -> listOf("Digging", "Levelling", "Loading", "MachineHire")
        MachineKind.Other -> emptyList()
    }

    /** The tools that do this work behind a tractor. Empty when any tool, or none, could. */
    fun toolsFor(work: String): Set<MachineKind> = when (work) {
        "Rotavator", "Puddling" -> setOf(MachineKind.Rotavator)
        "Ploughing" -> setOf(MachineKind.Plough, MachineKind.Cultivator, MachineKind.Harrow)
        "Levelling" -> setOf(MachineKind.Leveller)
        "Sowing" -> setOf(MachineKind.Seeder)
        "Planting" -> setOf(MachineKind.Transplanter)
        "Ridging" -> setOf(MachineKind.Ridger)
        "Mulching" -> setOf(MachineKind.Mulcher)
        "Spraying" -> setOf(MachineKind.Sprayer)
        "Baling" -> setOf(MachineKind.Baler)
        "Threshing" -> setOf(MachineKind.Thresher)
        "Harvesting" -> setOf(MachineKind.PotatoDigger)
        "Hauling" -> setOf(MachineKind.Trolley, MachineKind.GrainCart, MachineKind.WaterTanker)
        "WaterSupply" -> setOf(MachineKind.WaterTanker)
        "Pumping" -> setOf(MachineKind.Pump)
        else -> emptySet()
    }

    /** Every work there is to pick: ours, then the business's own names, then "Other". */
    fun all(names: List<String>): List<WorkTypeOption> =
        (workTypeOptions.map { it.key }.filter { it != OTHER } +
            names.filter { isOwnName(it) && it.isNotBlank() }.sortedBy { it.lowercase() } +
            OTHER).distinct().map(::option)

    /** The work to offer first, and the rest behind "More work". */
    data class Menu(val usual: List<WorkTypeOption>, val rest: List<WorkTypeOption>)

    /**
     * The work list for [machine] with [tool] on the back of it.
     *
     * Order: what the tool on the back does, what this machine has done, what the business's
     * tools let a tractor do, what such a machine is for. Everything else is still there, after;
     * "Other" is always last. With no machine, the business's usual work leads.
     */
    fun menu(
        machine: MachineEntity?,
        tool: MachineEntity?,
        fleet: List<MachineEntity>,
        history: WorkHistory,
    ): Menu {
        val all = all(history.names).map { it.key }
        val usual = buildList {
            tool?.let { addAll(usualFor(it.kind)) }
            addAll(history.doneOn(machine?.id))
            if (machine == null) {
                fleet.forEach { addAll(usualFor(it.kind)) }
            } else {
                if (carriesTools(machine.kind)) fleet.filter { isTool(it.kind) }.forEach { addAll(usualFor(it.kind)) }
                addAll(usualFor(machine.kind))
            }
        }.distinct().filter { it in all && it != OTHER }
        if (usual.isEmpty()) return Menu(all.map(::option), emptyList())
        return Menu(usual.map(::option), all.filter { it !in usual }.map(::option))
    }

    /**
     * What else goes out with [machine]: the tools a tractor could have on the back, or the
     * tractors a tool could be behind. Fitting ones first. Empty when the question does not apply.
     */
    fun partners(machine: MachineEntity?, work: String, fleet: List<MachineEntity>): List<MachineEntity> {
        machine ?: return emptyList()
        return when {
            carriesTools(machine.kind) -> {
                val fits = toolsFor(work)
                fleet.filter { it.id != machine.id && isTool(it.kind) }
                    .sortedBy { if (it.kind in fits) 0 else 1 }
            }
            isTool(machine.kind) -> fleet.filter { it.id != machine.id && carriesTools(it.kind) }
            else -> emptyList()
        }
    }

    /** Whether [machine] is a tool, so the question is who pulled it rather than what is on it. */
    fun asksPuller(machine: MachineEntity?): Boolean = machine != null && isTool(machine.kind)

    /**
     * The partner to fill in without asking, when there is exactly one sensible answer: the one
     * rotavator for rotavator work, the one tractor for a rotavator. [now] leaves out machines out
     * on other work or broken down, which cannot be the one going out today.
     */
    fun autoPartner(machine: MachineEntity?, work: String, fleet: List<MachineEntity>, now: Boolean): MachineEntity? {
        machine ?: return null
        fun free(m: MachineEntity) = !now || m.status == MachineStatus.Idle
        val choices = partners(machine, work, fleet)
        return when {
            carriesTools(machine.kind) -> {
                val fits = toolsFor(work)
                if (fits.isEmpty()) null else choices.filter { it.kind in fits && free(it) }.singleOrNull()
            }
            alwaysPulled(machine.kind) -> choices.filter(::free).singleOrNull()
            else -> null
        }
    }

    /**
     * The key to save for a work named by hand.
     *
     * Empty is "Other". A name that is one of ours, in any language the reader sees it in, is
     * ours, so "Rotavator" typed by hand does not start a second rotavator on the rate card. A
     * name the business has used before keeps the spelling it was first given.
     */
    fun keyForName(typed: String, ownNames: List<String>, labelOf: (WorkTypeOption) -> String): String {
        val name = typed.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)
        if (name.isEmpty()) return OTHER
        workTypeOptions.firstOrNull { it.key.equals(name, ignoreCase = true) || labelOf(it).equals(name, ignoreCase = true) }
            ?.let { return it.key }
        return ownNames.firstOrNull { isOwnName(it) && it.equals(name, ignoreCase = true) } ?: name
    }

    private val TOOLS = setOf(
        MachineKind.Rotavator, MachineKind.Cultivator, MachineKind.Plough, MachineKind.Harrow,
        MachineKind.Leveller, MachineKind.Seeder, MachineKind.Transplanter, MachineKind.Ridger,
        MachineKind.Mulcher, MachineKind.Sprayer, MachineKind.Baler, MachineKind.Thresher,
        MachineKind.PotatoDigger, MachineKind.Trolley, MachineKind.GrainCart, MachineKind.WaterTanker,
        MachineKind.Pump,
    )

    private val ALWAYS_PULLED = setOf(
        MachineKind.Rotavator, MachineKind.Cultivator, MachineKind.Plough, MachineKind.Harrow,
        MachineKind.Leveller, MachineKind.Seeder, MachineKind.Ridger, MachineKind.Mulcher,
        MachineKind.Baler, MachineKind.PotatoDigger, MachineKind.Trolley, MachineKind.GrainCart,
    )
}
