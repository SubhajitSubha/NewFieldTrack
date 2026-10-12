package com.example.fieldtrack.feature.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One person on the books, with whatever they happen to be doing right now. */
data class TeamMemberRow(
    val membershipId: String,
    val personId: String,
    val displayName: String,
    val role: MemberRole,
    val phone: String?,
    val isYou: Boolean,
    val active: Boolean,
    /** What they are driving, when they are on a job. */
    val workingOn: String? = null,
    val elapsedSeconds: Long = 0,
    val paused: Boolean = false,
) {
    val working: Boolean get() = workingOn != null
}

data class TeamState(
    val loaded: Boolean = false,
    val members: List<TeamMemberRow> = emptyList(),
    val canEditTeam: Boolean = false,
) {
    val working: List<TeamMemberRow> get() = members.filter { it.working && it.active }
    val idle: List<TeamMemberRow> get() = members.filter { !it.working && it.active }
    val former: List<TeamMemberRow> get() = members.filter { !it.active }
}

/**
 * Everyone who works for this business, and what each of them is doing.
 *
 * The people on a job come first. An owner opening this screen is nearly always
 * asking "who is out right now and on what", not "show me a directory".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<TeamState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeEveryone(s.orgId),
            app.workRepository.observeLive(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
        ) { members, liveJobs, machines ->
            val machineNames = machines.associate { it.id to it.name }
            TeamState(
                loaded = true,
                canEditTeam = Capability.EditTeam in s.can,
                members = members.map { member ->
                    val job = liveJobs.firstOrNull { it.operatorPersonId == member.personId }
                    TeamMemberRow(
                        membershipId = member.id,
                        personId = member.personId,
                        displayName = member.displayName,
                        role = member.role,
                        phone = member.phone,
                        isYou = member.personId == s.personId,
                        active = member.active,
                        workingOn = job?.let {
                            machineNames[it.machineId] ?: it.workType
                        },
                        elapsedSeconds = job?.let { app.workRepository.elapsedSeconds(it) } ?: 0,
                        paused = job?.status == JobStatus.Paused,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}
