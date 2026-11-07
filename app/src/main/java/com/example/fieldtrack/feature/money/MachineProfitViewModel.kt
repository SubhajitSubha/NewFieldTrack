package com.example.fieldtrack.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MachineProfitState(
    val loaded: Boolean = false,
    val machineName: String = "",
    val earnedMinor: Long = 0,
    val fuelMinor: Long = 0,
    val serviceMinor: Long = 0,
    /** What this machine's jobs cost in people, whether or not the cash has gone. */
    val peopleMinor: Long = 0,
    /** What the owner of this machine has earned from its work, when it is not the business's own. */
    val hireMinor: Long = 0,
    /** Of that, what is still owed. Shown, never taken off the figure. */
    val owedMinor: Long = 0,
    /** Paid out for a stretch of time, so no machine can carry it. */
    val leftOutMinor: Long = 0,
    /** False when this person may not see what people were paid. */
    val seesPeopleCost: Boolean = false,
    /**
     * What an hour on this machine costs, from what has been spent on it.
     * Null until it has loaded; see [Costing.RunningCost.enough] for whether it
     * says anything yet.
     */
    val running: Costing.RunningCost? = null,
    /** What each kind of work costs per unit, where there is enough of it to say. */
    val kinds: List<Costing.UnitCost> = emptyList(),
    /** Whether this person may fill in the loan, for the link that offers to. */
    val canEditMachine: Boolean = false,
) {
    val costsMinor: Long get() = fuelMinor + serviceMinor + peopleMinor + hireMinor
    val leftMinor: Long get() = earnedMinor - costsMinor
}

/**
 * What one machine brought in, and what it cost to run.
 *
 * Only what can honestly be laid against it. A month's wages belong to no
 * machine, and quietly spreading them would be an estimate wearing the clothes
 * of a fact — so they are left out and the screen says the figure out loud,
 * which is the only way the number above it stays trustworthy.
 *
 * People cost comes from the jobs' own cost sheets and never from `payouts`.
 * Those two are tied by [PayoutEntity.jobCostId], and totalling both would
 * count every settled cost twice. It also means the figure counts work that has
 * not been paid for yet: what a job cost does not depend on whether Saturday
 * has come round, and a machine that looks profitable only because its crew is
 * still waiting is exactly the lie this screen exists to prevent. What is owed
 * is said beside it rather than taken off it.
 *
 * Somebody without sight of people costs sees the machine's own costs and no
 * total at all. A total with a third of the costs missing is worse than no
 * total: it reads as profit, and somebody might act on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MachineProfitViewModel(
    private val app: FieldFlowApplication,
    private val machineId: String,
) : ViewModel() {

    val state: StateFlow<MachineProfitState> = app.sessionStore.session.flatMapLatest { s ->
        val seesPeople = Capability.SeePeopleCost in s.can

        // What it costs to run, worked out once here from the same figures that
        // sit in the card above it, so the two can be checked against each other.
        // Only for somebody who sees what the business pays: the loan is part of
        // it, and an hourly cost with the loan quietly missing reads as cheaper
        // than the machine is.
        val costs = combine(
            app.directoryRepository.observeRunningCosts(s.orgId),
            app.directoryRepository.observeWorkKinds(s.orgId, machineId),
        ) { all, kinds ->
            val running = all[machineId]
            running to (if (running == null) emptyList() else kinds.mapNotNull { Costing.perUnit(running, it) }
                .sortedByDescending { it.jobs })
        }

        combine(
            combine(
            app.directoryRepository.observeMachines(s.orgId)
                .map { all -> all.firstOrNull { it.id == machineId }?.name.orEmpty() },
            app.workRepository.observeEarnedOnMachine(s.orgId, machineId),
            app.workRepository.observeFuelSpendOnMachine(s.orgId, machineId),
            app.db.machineDao().observeServiceSpendOnMachine(s.orgId, machineId),
            combine(
                app.db.orgDao().observeJobCostOnMachine(s.orgId, machineId),
                app.db.orgDao().observeOwedOnMachine(s.orgId, machineId),
                app.db.orgDao().observeUnattributablePayouts(s.orgId),
            ) { onMachine, owed, leftOut -> Triple(onMachine, owed, leftOut) },
        ) { name, earned, fuel, service, people ->
            MachineProfitState(
                loaded = true,
                machineName = name,
                earnedMinor = earned,
                fuelMinor = fuel,
                serviceMinor = service,
                peopleMinor = if (seesPeople) people.first else 0,
                owedMinor = if (seesPeople) people.second else 0,
                leftOutMinor = if (seesPeople) people.third else 0,
                seesPeopleCost = seesPeople,
            )
        }, costs, app.hireRepository.observe(s.orgId)) { base, (running, kinds), hire ->
            base.copy(
                hireMinor = if (seesPeople) hire.flatMap { it.machines }.firstOrNull { it.machineId == machineId }?.owedMinor ?: 0 else 0,
                running = if (seesPeople) running else null,
                kinds = if (seesPeople) kinds else emptyList(),
                canEditMachine = Capability.EditMachines in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachineProfitState())
}
