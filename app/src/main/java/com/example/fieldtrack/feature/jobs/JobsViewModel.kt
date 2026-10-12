package com.example.fieldtrack.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.feature.startOfToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What slice of work to show.
 *
 * There is deliberately no "Unpaid" filter. Payments in this app are recorded against
 * a customer, not against a job — that is what lets two people take money offline and
 * have it add up — so per-job payment status does not exist to filter by. Who owes
 * what is the customers tab's question.
 */
enum class JobFilter { Running, Today, Week }

data class JobCard(
    val id: String,
    val customerName: String,
    val operatorName: String?,
    val workType: String,
    val quantity: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val amountRupees: Long,
    val machineKind: MachineKind,
    val status: FtStatus,
    /** True while the job is still open, which changes what the row leads with. */
    val live: Boolean,
)

data class JobsState(
    val loaded: Boolean = false,
    val filter: JobFilter = JobFilter.Running,
    val jobs: List<JobCard> = emptyList(),
    /** Whether this person may correct a job after it has been billed. */
    val canAmend: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JobsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val filter = MutableStateFlow(JobFilter.Running)

    val state: StateFlow<JobsState> = app.sessionStore.session.flatMapLatest { s ->
        filter.flatMapLatest { chosen ->
            val now = System.currentTimeMillis()
            val source = when (chosen) {
                JobFilter.Running -> app.workRepository.observeLive(s.orgId)
                JobFilter.Today -> app.workRepository
                    .observeCompletedToday(s.orgId, startOfToday(now))
                JobFilter.Week -> app.workRepository
                    .observeCompletedToday(s.orgId, startOfToday(now) - 6 * DAY_MILLIS)
            }
            combine(
                source,
                app.directoryRepository.observeCustomers(s.orgId),
                app.directoryRepository.observeMachines(s.orgId),
                app.db.orgDao().observeTeam(s.orgId),
            ) { jobs, customers, machines, team ->
                val customerNames = customers.associate { it.id to it.name }
                val machineKinds = machines.associate { it.id to it.kind }
                val memberNames = team.associate { it.personId to it.displayName }

                JobsState(
                    loaded = true,
                    filter = chosen,
                    canAmend = Capability.AmendBilledWork in s.can,
                    jobs = jobs.map { job ->
                        val live = job.status == JobStatus.Running || job.status == JobStatus.Paused
                        JobCard(
                            id = job.id,
                            customerName = customerNames[job.customerId].orEmpty(),
                            operatorName = job.operatorPersonId?.let { memberNames[it] },
                            workType = job.workType,
                            quantity = job.quantity,
                            unit = job.unit,
                            elapsedSeconds = app.workRepository.elapsedSeconds(job),
                            amountRupees = job.amountRupees,
                            machineKind = job.machineId?.let { machineKinds[it] }
                                ?: MachineKind.Other,
                            status = when (job.status) {
                                JobStatus.Running -> FtStatus.Running
                                JobStatus.Paused -> FtStatus.Paused
                                else -> FtStatus.Completed
                            },
                            live = live,
                        )
                    },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobsState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun setFilter(value: JobFilter) = filter.update { value }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
