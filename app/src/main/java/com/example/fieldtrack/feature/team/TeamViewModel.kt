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
import com.example.fieldtrack.data.auth.indianMobileDigits
import com.example.fieldtrack.data.db.entity.MembershipEntity
import kotlinx.coroutines.flow.MutableStateFlow

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
    /** Codes handed out and not yet used, so they can be seen and taken back. */
    val openCodeCount: Int = 0,
    /** How many people can manage this business. One is a single point of failure. */
    val ownerCount: Int = 0,
    /** Two rows that look like one person, if the team holds any. */
    val possibleDuplicate: DuplicatePair? = null,
) {
    val working: List<TeamMemberRow> get() = members.filter { it.working && it.active }
    val idle: List<TeamMemberRow> get() = members.filter { !it.working && it.active }
    val former: List<TeamMemberRow> get() = members.filter { !it.active }
}

/**
 * Two memberships that are probably one man.
 *
 * [keep] is the one he can prove — he typed a code and signed in on it.
 * [absorb] is the one an owner wrote down for him beforehand.
 */
data class DuplicatePair(
    val keepMembershipId: String,
    val absorbMembershipId: String,
    /** The name on the row he joined with. */
    val keepName: String,
    /** The name on the row somebody wrote down for him. */
    val absorbName: String,
    val phone: String,
)

/**
 * Everyone who works for this business, and what each of them is doing.
 *
 * The people on a job come first. An owner opening this screen is nearly always
 * asking "who is out right now and on what", not "show me a directory".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val dismissedDuplicates =
        MutableStateFlow(app.sessionStore.dismissedDuplicates())

    /**
     * Told once that two rows are different men, the app has to remember it.
     *
     * Two brothers on one family phone is an ordinary thing, and a prompt that
     * cannot be answered for good is a prompt that gets ignored for good.
     */
    fun notTheSamePerson(pair: DuplicatePair) {
        app.sessionStore.rememberNotDuplicates(pair.keepMembershipId, pair.absorbMembershipId)
        dismissedDuplicates.value = app.sessionStore.dismissedDuplicates()
    }

    fun merge(pair: DuplicatePair, onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        runCatching {
            app.teamRepository.mergePeople(
                orgId = s.orgId,
                can = s.can,
                keepMembershipId = pair.keepMembershipId,
                absorbMembershipId = pair.absorbMembershipId,
            )
        }.onSuccess { onDone() }
    }

    val state: StateFlow<TeamState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeEveryone(s.orgId),
            app.workRepository.observeLive(s.orgId),
            app.directoryRepository.observeMachines(s.orgId),
            app.teamRepository.observeOpenInvites(s.orgId),
            dismissedDuplicates,
        ) { members, liveJobs, machines, openCodes, dismissed ->
            val machineNames = machines.associate { it.id to it.name }
            TeamState(
                loaded = true,
                canEditTeam = Capability.EditTeam in s.can,
                openCodeCount = openCodes.size,
                ownerCount = members.count { it.active && it.role == MemberRole.Owner },
                possibleDuplicate = duplicateAmong(members, dismissed),
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

/**
 * The first pair of live memberships that look like one person.
 *
 * Matched on the phone number and nothing else. A name is a weak signal in a
 * village where half the team shares one — the number is what an owner actually
 * typed twice, once for the man and once when he joined.
 *
 * The one he joined on is kept, because it is the only one he can prove at the
 * server; the hand-written row is the one absorbed.
 */
private fun duplicateAmong(
    members: List<MembershipEntity>,
    dismissed: Set<String>,
): DuplicatePair? {
    val live = members.filter { it.active }
    for (a in live) {
        val digits = a.phone?.let { indianMobileDigits(it) }.orEmpty()
        if (digits.length < 10) continue
        for (b in live) {
            if (a.id == b.id || a.personId == b.personId) continue
            if (b.phone?.let { indianMobileDigits(it) }.orEmpty() != digits) continue

            // Whichever came in on a code is the provable one.
            val keep = if (a.joinedWithCode != null) a else b
            val absorb = if (keep.id == a.id) b else a
            if (absorb.joinedWithCode != null) continue

            if (duplicateKey(keep.id, absorb.id) in dismissed) continue
            return DuplicatePair(
                keepMembershipId = keep.id,
                absorbMembershipId = absorb.id,
                keepName = keep.displayName,
                absorbName = absorb.displayName,
                phone = digits,
            )
        }
    }
    return null
}

/** Order-independent, so answering once answers it whichever way round it is seen. */
fun duplicateKey(a: String, b: String): String = listOf(a, b).sorted().joinToString("|")
