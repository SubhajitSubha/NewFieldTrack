package com.example.fieldtrack.feature.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.feature.startOfToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A job this driver did today. */
data class DriverJobRow(
    val jobId: String,
    val customerName: String,
    val workType: String,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val machineKind: MachineKind,
    val live: Boolean,
)

data class DriverHomeState(
    val displayName: String = "",
    val businessName: String = "",
    /** Seconds on a machine today, across every job. */
    val secondsToday: Long = 0,
    val jobsToday: Int = 0,
    /** Ground covered today, when today's work was measured by area. */
    val areaToday: Double = 0.0,
    val areaUnit: MeasureUnit = MeasureUnit.Acre,
    val machineName: String? = null,
    val machineId: String? = null,
    val machineKind: MachineKind = MachineKind.Tractor,
    val today: List<DriverJobRow> = emptyList(),
    val liveJobId: String? = null,
    val liveJobPaused: Boolean = false,
    val pendingUploads: Int = 0,
)

/**
 * A driver's own screen.
 *
 * The owner's home answers "what did the business make today". This one answers
 * "what have I done today", because those are different questions and a driver
 * given the first is being shown somebody else's business.
 *
 * There is no money on it at all — not hidden behind a capability, simply absent.
 * A driver who may see money has it on the customer and job screens where it is
 * about a specific piece of work; putting a day's takings on the screen he opens
 * every morning makes his own app feel like surveillance of the till.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriverHomeViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<DriverHomeState> = app.sessionStore.session.flatMapLatest { s ->
        val dayStart = startOfToday(System.currentTimeMillis())
        combine(
            app.workRepository.observeForOperator(s.orgId, s.personId, dayStart, Long.MAX_VALUE),
            app.workRepository.observeLiveFor(s.orgId, s.personId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.db.outboxDao().observePendingCount(),
        ) { todays, live, customers, machines, pending ->
            val names = customers.associate { it.id to it.name }
            val rows = todays.map { job ->
                DriverJobRow(
                    jobId = job.id,
                    customerName = names[job.customerId].orEmpty(),
                    workType = job.workType,
                    amountDone = job.quantity,
                    unit = job.unit,
                    elapsedSeconds = app.workRepository.elapsedSeconds(job),
                    machineKind = machines.firstOrNull { it.id == job.machineId }?.kind
                        ?: MachineKind.Other,
                    live = job.status == JobStatus.Running || job.status == JobStatus.Paused,
                )
            }
            // The machine they are on now, or the last one they were on today —
            // which is the one they will reach for next.
            val machineId = (live ?: todays.firstOrNull { it.machineId != null })?.machineId
            val machine = machines.firstOrNull { it.id == machineId }

            DriverHomeState(
                displayName = s.displayName,
                businessName = s.businessName,
                secondsToday = rows.sumOf { it.elapsedSeconds },
                jobsToday = rows.count { !it.live },
                // Only area work is added up. Totalling acres with trips and
                // quintals would produce a number that means nothing.
                areaToday = todays.filter { it.unit.isLand }.sumOf { it.quantity },
                areaUnit = todays.firstOrNull { it.unit.isLand }?.unit ?: s.landUnit,
                machineName = machine?.name,
                machineId = machine?.id,
                machineKind = machine?.kind ?: MachineKind.Tractor,
                today = rows,
                liveJobId = live?.id,
                liveJobPaused = live?.status == JobStatus.Paused,
                pendingUploads = pending,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverHomeState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}
