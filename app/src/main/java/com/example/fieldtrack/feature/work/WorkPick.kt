package com.example.fieldtrack.feature.work

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.repository.WorkHistory
import com.example.fieldtrack.feature.WorkCatalog
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.unitsFor

/**
 * The machine, what goes out with it, and the work, chosen together.
 *
 * Each answer moves the others until the person has said otherwise: picking the tractor offers
 * its work and fills in the one rotavator; picking a rotavator as the attachment makes the work
 * rotavator work. Once a person has picked a work or a partner it stays theirs, unless a later
 * pick makes it impossible (a trolley does not stay on the back for ploughing).
 *
 * [now] is true when the work starts now, so a machine out on other work is not filled in.
 */
data class WorkPick(
    val machines: List<MachineEntity> = emptyList(),
    val history: WorkHistory = WorkHistory(),
    val machineId: String? = null,
    /** The tool on the back when the machine is a tractor; the tractor in front when it is a tool. */
    val partnerId: String? = null,
    val work: WorkTypeOption = WorkCatalog.option("Rotavator"),
    /** What the person called "Other" work. Empty keeps "Other". */
    val otherName: String = "",
    val workPicked: Boolean = false,
    val partnerPicked: Boolean = false,
    val now: Boolean = true,
) {
    val machine: MachineEntity? get() = machines.firstOrNull { it.id == machineId }
    val partner: MachineEntity? get() = machines.firstOrNull { it.id == partnerId }

    val asksPuller: Boolean get() = WorkCatalog.asksPuller(machine)

    val partners: List<MachineEntity> get() = WorkCatalog.partners(machine, work.key, machines)

    /**
     * Only a tool the person put on reorders the list. One filled in for the work they just
     * tapped would move that card from under their finger.
     */
    val menu: WorkCatalog.Menu
        get() = WorkCatalog.menu(machine, partner.takeIf { !asksPuller && partnerPicked }, machines, history)

    /** The machine the job is recorded on: the tractor, whichever of the two was picked first. */
    val jobMachineId: String? get() = if (asksPuller && partnerId != null) partnerId else machineId

    /** The implement the job is recorded with. */
    val implementId: String? get() = if (asksPuller) machineId.takeIf { partnerId != null } else partnerId

    val isOther: Boolean get() = work.key == WorkCatalog.OTHER

    /** The work as it is saved: ours, the business's own name, or what was typed for "Other". */
    fun workKey(labelOf: (WorkTypeOption) -> String): String =
        if (isOther) WorkCatalog.keyForName(otherName, history.names, labelOf) else work.key

    fun withMachines(list: List<MachineEntity>) = copy(machines = list).settled()

    fun withHistory(value: WorkHistory) = copy(history = value).settled()

    fun pickMachine(id: String?) = copy(machineId = id, partnerId = null, partnerPicked = false).settled()

    fun pickWork(option: WorkTypeOption): WorkPick {
        // A tool the person put on stays only while it can do the work.
        val keepPartner = partnerPicked && (asksPuller || partner?.kind?.let { kind ->
            WorkCatalog.toolsFor(option.key).let { it.isEmpty() || kind in it }
        } ?: true)
        return copy(work = option, workPicked = true, partnerPicked = keepPartner).settled()
    }

    fun pickPartner(id: String?): WorkPick {
        var next = copy(partnerId = id, partnerPicked = true)
        val tool = next.partner?.takeIf { !asksPuller }
        if (tool != null) {
            val its = WorkCatalog.usualFor(tool.kind)
            if (its.isNotEmpty() && work.key !in its) next = next.copy(work = WorkCatalog.option(its.first()))
        }
        return next.settled()
    }

    fun nameOther(text: String) = copy(otherName = text.take(WorkCatalog.MAX_NAME))

    /** Taken from a booking: the person's own answer, kept as theirs. */
    fun fromBooking(machineId: String?, work: String) =
        copy(machineId = machineId, partnerId = null, partnerPicked = false, work = WorkCatalog.option(work), workPicked = true).settled()

    private fun settled(): WorkPick {
        var s = this
        if (s.partnerId != null && s.partners.none { it.id == s.partnerId }) s = s.copy(partnerId = null, partnerPicked = false)
        if (!s.workPicked) s = s.copy(work = s.menu.usual.firstOrNull() ?: WorkCatalog.option(s.work.key))
        if (!s.partnerPicked) s = s.copy(partnerId = WorkCatalog.autoPartner(s.machine, s.work.key, s.machines, s.now)?.id)
        return s
    }
}

/**
 * How a kind of work is charged when it is picked: the way it was last charged, if that is
 * still a way it can be charged, else its usual way in the business's land unit.
 */
fun chargeFor(work: WorkTypeOption, history: WorkHistory, landUnit: MeasureUnit): Pair<BillingMode, MeasureUnit> {
    val last = history.lastCharge[work.key]
    if (last != null && last.billingMode in work.billingModes && last.unit in unitsFor(last.billingMode)) {
        // Land in what the business measures in now, if it has changed since.
        return last.billingMode to if (last.unit.isLand) resolveUnit(last.billingMode, landUnit) else last.unit
    }
    return work.defaultMode to resolveUnit(work.defaultMode, landUnit)
}

/** The rates offered as chips, best first. */
object SuggestedRates {
    /**
     * The rate cards' rates with the machine's own last rate put where it belongs.
     *
     * What this customer paid on this machine beats everything; what this customer paid at all
     * comes next; then what this machine goes out at; then the business's card. A machine rate
     * that is already one of the card's is that chip moved up, not a second chip.
     */
    fun order(
        candidates: List<com.example.fieldtrack.data.db.entity.RateEntity>,
        machineRate: Long?,
        machineRateForCustomer: Boolean,
        machineName: String?,
    ): List<RateSuggestion> {
        val chips = candidates.map { RateSuggestion(it.amountMinor, it.label, it) }.distinctBy { it.amountMinor to it.label }
        if (machineRate == null || machineRate <= 0) return chips
        val same = chips.firstOrNull { it.amountMinor == machineRate }
        val machineChip = same ?: RateSuggestion(machineRate, machineName)
        val others = chips.filter { it !== same }
        val customerFirst = !machineRateForCustomer && chips.firstOrNull()?.let { it !== same && it.rate?.customerId != null } == true
        return if (customerFirst) listOf(others.first(), machineChip) + others.drop(1)
        else listOf(machineChip) + others
    }
}
