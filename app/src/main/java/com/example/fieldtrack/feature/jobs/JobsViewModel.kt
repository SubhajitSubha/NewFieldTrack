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
enum class JobFilter { Running, Today, Week, All }

data class JobCard(
    val id: String,
    val customerName: String,
    val operatorName: String?,
    val workType: String,
    val quantity: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val amountMinor: Long,
    val machineKind: MachineKind,
    val status: FtStatus,
    /** True while the job is still open, which changes what the row leads with. */
    val live: Boolean,
    /** How much this job lost, when it did and every cost is priced. */
    val lostMinor: Long? = null,
    val workedAt: Long = 0,
)

data class JobsState(
    val loaded: Boolean = false,
    val filter: JobFilter = JobFilter.Running,
    val jobs: List<JobCard> = emptyList(),
    /** Whether this person may correct a job after it has been billed. */
    val canAmend: Boolean = false,
    /**
     * Whether this person may see what a job came to. The list used to show every
     * amount to everybody who could reach it, which was harmless while only owners
     * could; a manager given the machines but not the money can reach it now.
     */
    val canSeeMoney: Boolean = true,
    /** What is typed in the list's own search box. */
    val query: String = "",
    /** Whether this person may start a job: everybody but somebody who may only look. */
    val canStart: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class JobsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val filter = MutableStateFlow(JobFilter.Running)
    private val query = MutableStateFlow("")

    val state: StateFlow<JobsState> = app.sessionStore.session.flatMapLatest { s ->
        filter.flatMapLatest { chosen ->
            val now = System.currentTimeMillis()
            val source = when (chosen) {
                JobFilter.Running -> app.workRepository.observeLive(s.orgId)
                JobFilter.Today -> app.workRepository
                    .observeCompletedToday(s.orgId, startOfToday(now))
                JobFilter.Week -> app.workRepository
                    .observeCompletedToday(s.orgId, startOfToday(now) - 6 * DAY_MILLIS)
                // Every finished job: the season's work, found again with the search box.
                JobFilter.All -> app.workRepository.observeCompletedToday(s.orgId, 0L)
            }
            val margins = if (Capability.SeeMoney in s.can && Capability.SeePeopleCost in s.can) {
                app.db.orgDao().observeJobMargins(s.orgId, startOfToday(now) - 6 * DAY_MILLIS, Long.MAX_VALUE)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
            combine(
                source,
                app.directoryRepository.observeCustomers(s.orgId),
                app.directoryRepository.observeMachines(s.orgId),
                // Everyone who has ever been on the team, not only who is on it
                // today. This map puts a name against the person who did each
                // job, and the active-only list silently dropped it: every job a
                // driver ever did went nameless the moment they were let go.
                app.teamRepository.observeEveryone(s.orgId),
                kotlinx.coroutines.flow.combine(margins, query) { m, q -> m to q },
            ) { jobs, customers, machines, team, (jobMargins, typed) ->
                val customerNames = customers.associate { it.id to it.name }
                val machineKinds = machines.associate { it.id to it.kind }
                val memberNames = team.associate { it.personId to it.displayName }
                val lost = jobMargins.mapNotNull { m -> m.lostMinor?.let { m.jobId to it } }.toMap()

                val q = typed.trim()
                JobsState(
                    loaded = true,
                    filter = chosen,
                    query = typed,
                    canStart = Capability.RecordWork in s.can,
                    canAmend = Capability.AmendBilledWork in s.can,
                    canSeeMoney = s.canSeeMoney,
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
                            amountMinor = job.amountMinor,
                            machineKind = job.machineId?.let { machineKinds[it] }
                                ?: MachineKind.Other,
                            status = when (job.status) {
                                JobStatus.Running -> FtStatus.Running
                                JobStatus.Paused -> FtStatus.Paused
                                else -> FtStatus.Completed
                            },
                            live = live,
                            lostMinor = lost[job.id],
                            workedAt = job.workedAt,
                        )
                    }.filter { card ->
                        q.isEmpty() || card.customerName.contains(q, ignoreCase = true) ||
                            card.operatorName.orEmpty().contains(q, ignoreCase = true) ||
                            card.workType.contains(q, ignoreCase = true) ||
                            // Found by the name the reader sees, not only the stored key.
                            com.example.fieldtrack.feature.workLabel(app, card.workType).contains(q, ignoreCase = true)
                    },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobsState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    fun setFilter(value: JobFilter) = filter.update { value }
    fun setQuery(value: String) = query.update { value.take(40) }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
