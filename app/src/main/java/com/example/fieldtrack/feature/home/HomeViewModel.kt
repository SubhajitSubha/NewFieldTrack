package com.example.fieldtrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One finished job as the home list shows it. */
data class TodayRow(
    val jobId: String,
    val customerName: String,
    val workType: String,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val amountRupees: Long,
    val machineKind: MachineKind,
)

data class HomeState(
    val displayName: String = "",
    /** Whether this person may correct a job after it has been billed. */
    val canAmend: Boolean = false,
    val now: Long = System.currentTimeMillis(),
    val earnedTodayRupees: Long = 0,
    val jobsToday: Int = 0,
    val owedRupees: Long = 0,
    val owingCustomers: Int = 0,
    val today: List<TodayRow> = emptyList(),
    /** Set when work is already running, so the screen can offer to go back to it. */
    /**
     * The job to go back to, running or paused.
     *
     * A paused job counts. A driver whose machine stopped pauses and walks away from
     * the screen; if the way back disappeared with it, the job would be stranded —
     * there is no jobs list on a solo owner's phone to find it in.
     */
    val liveJobId: String? = null,
    val liveJobPaused: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: FieldFlowApplication) : ViewModel() {


    val state: StateFlow<HomeState> = app.sessionStore.session.flatMapLatest { s ->
        val dayStart = com.example.fieldtrack.feature.startOfToday(System.currentTimeMillis())
        combine(
            app.workRepository.observeCompletedToday(s.orgId, dayStart),
            app.ledgerRepository.observeBalances(s.orgId),
            app.directoryRepository.observeCustomers(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.workRepository.observeLiveJob(s.orgId, s.personId),
        ) { jobs, balances, customers, machines, live ->
            val namesById = customers.associate { it.id to it.name }
            val kindById = machines.associate { it.id to it.kind }
            val owed = balances.filter { it.balanceRupees > 0 }

            HomeState(
                displayName = s.displayName,
                canAmend = Capability.AmendBilledWork in s.can,
                now = System.currentTimeMillis(),
                earnedTodayRupees = jobs.sumOf { it.amountRupees },
                jobsToday = jobs.size,
                owedRupees = owed.sumOf { it.balanceRupees },
                owingCustomers = owed.size,
                today = jobs.map { job ->
                    TodayRow(
                        jobId = job.id,
                        customerName = namesById[job.customerId].orEmpty(),
                        workType = job.workType,
                        amountDone = job.quantity,
                        unit = job.unit,
                        elapsedSeconds = job.elapsedSeconds,
                        amountRupees = job.amountRupees,
                        machineKind = job.machineId?.let { kindById[it] } ?: MachineKind.Other,
                    )
                },
                // Only Running counts as "go back to it" — a paused job is a decision
                // someone already made, and nagging about it is noise.
                liveJobId = live?.id,
                liveJobPaused = live?.status == JobStatus.Paused,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}
