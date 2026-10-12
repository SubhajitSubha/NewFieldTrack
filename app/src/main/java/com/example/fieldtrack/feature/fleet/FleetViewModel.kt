package com.example.fieldtrack.feature.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.feature.startOfToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Something an owner should look at today. */
data class Attention(
    val machineId: String,
    val machineName: String,
    val kind: MachineKind,
    val reason: AttentionReason,
    /** Hours of use left before service, or millis since a breakdown was reported. */
    val value: Double,
)

enum class AttentionReason { Breakdown, ServiceOverdue, ServiceSoon }

data class FleetState(
    val loaded: Boolean = false,
    val working: Int = 0,
    val idle: Int = 0,
    val down: Int = 0,
    val hasMachines: Boolean = false,
    /** Hours worked per day, Monday first. Real figures from recorded jobs. */
    val weekHours: List<Double> = List(7) { 0.0 },
    val attention: List<Attention> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class FleetViewModel(private val app: FieldFlowApplication) : ViewModel() {


    val state: StateFlow<FleetState> = app.sessionStore.session.flatMapLatest { s ->
        val weekStart = startOfWeek(System.currentTimeMillis())
        combine(
            app.directoryRepository.observeMachines(s.orgId),
            app.db.machineDao().observeOpenBreakdowns(s.orgId),
            app.workRepository.observeCompletedToday(s.orgId, weekStart),
        ) { machines, breakdowns, jobs ->
            val byId = machines.associateBy { it.id }

            val fromBreakdowns = breakdowns.mapNotNull { breakdown ->
                val machine = byId[breakdown.machineId] ?: return@mapNotNull null
                Attention(
                    machineId = machine.id,
                    machineName = machine.name,
                    kind = machine.kind,
                    reason = AttentionReason.Breakdown,
                    value = breakdown.reportedAt.toDouble(),
                )
            }

            val fromService = machines.mapNotNull { machine ->
                val interval = machine.serviceIntervalHours ?: return@mapNotNull null
                val left = (machine.lastServiceAtHours ?: 0.0) + interval - machine.hoursUsed
                when {
                    left <= 0 -> AttentionReason.ServiceOverdue
                    left <= SERVICE_WARNING_HOURS -> AttentionReason.ServiceSoon
                    else -> null
                }?.let { reason ->
                    Attention(machine.id, machine.name, machine.kind, reason, left)
                }
            }

            // Hours per weekday, from jobs actually recorded rather than a target.
            val hours = DoubleArray(7)
            jobs.forEach { job ->
                val index = weekdayIndex(job.workedAt)
                hours[index] = hours[index] + job.elapsedSeconds / 3600.0
            }

            FleetState(
                loaded = true,
                working = machines.count { it.status == MachineStatus.Working },
                idle = machines.count { it.status == MachineStatus.Idle },
                down = machines.count { it.status == MachineStatus.Down },
                hasMachines = machines.isNotEmpty(),
                weekHours = hours.toList(),
                // Breakdowns first: a machine standing in a field costs more than one
                // that is merely due a service.
                attention = fromBreakdowns + fromService.sortedBy { it.value },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    private companion object {
        const val SERVICE_WARNING_HOURS = 50.0
    }
}

/** Midnight on the Monday of the week containing [now]. */
fun startOfWeek(now: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = startOfToday(now) }
    // Calendar weeks start on Sunday in many locales; the chart reads Monday first.
    val shift = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    calendar.add(Calendar.DAY_OF_YEAR, -shift)
    return calendar.timeInMillis
}

/** 0 for Monday through 6 for Sunday. */
fun weekdayIndex(millis: Long): Int {
    val day = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
    return (day - Calendar.MONDAY + 7) % 7
}
