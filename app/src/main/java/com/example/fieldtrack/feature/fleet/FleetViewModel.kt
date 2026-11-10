package com.example.fieldtrack.feature.fleet

import com.example.fieldtrack.data.settings.WeekRules
import com.example.fieldtrack.data.region.BusinessClock
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

/** Midnight on the first day of the week containing [now], as this person counts the week. */
fun startOfWeek(now: Long, firstDay: Int = WeekRules.firstDay): Long {
    val calendar = Calendar.getInstance(BusinessClock.timeZone).apply { timeInMillis = startOfToday(now) }
    calendar.add(Calendar.DAY_OF_YEAR, -WeekRules.indexOf(calendar.get(Calendar.DAY_OF_WEEK), firstDay))
    return calendar.timeInMillis
}

/** 0 for the first day of the week through 6 for the last. */
fun weekdayIndex(millis: Long, firstDay: Int = WeekRules.firstDay): Int {
    val day = Calendar.getInstance(BusinessClock.timeZone).apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
    return WeekRules.indexOf(day, firstDay)
}
