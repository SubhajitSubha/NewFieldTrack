package com.example.fieldtrack.feature.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.repository.TeamError
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.Grants
import com.example.fieldtrack.data.session.RolePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.fieldtrack.data.auth.indianMobileDigits

data class MemberState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val phone: String? = null,
    val role: MemberRole = MemberRole.Operator,
    /** Set when the last save was refused, so the screen can say why. */
    val problem: Int? = null,
    val active: Boolean = true,
    val joinedAtMillis: Long? = null,
    /** Work still sitting on their phone, as of the last time it reached us. */
    val unsentCount: Int = 0,
    val unsentAtMillis: Long? = null,
    /** Set when they are in the middle of a job right now. */
    val liveWorkType: String? = null,
    val liveSeconds: Long = 0,
    val canSeeMoney: Boolean = false,
    val canAddCustomers: Boolean = false,
    val canCloseBills: Boolean = false,
    val canEditTeam: Boolean = false,
    val canEditMachines: Boolean = false,
    val canPayOut: Boolean = false,
    val canSeePeopleCost: Boolean = false,
    /** Only an owner may hand on the switches that create a peer. */
    val editableByOwnerOnly: Boolean = false,
    /** What they have done here. Only ever their work for this business. */
    val jobCount: Int = 0,
    val totalSeconds: Long = 0,
    val broughtInRupees: Long = 0,
    /** False for anyone who may not manage the team, and for your own record. */
    val editable: Boolean = false,
    val isYou: Boolean = false,
    val canSeeTheirEarnings: Boolean = true,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = editable && !saving && displayName.isNotBlank()

    /** The seven switches as one value, so a preset can be compared against them. */
    val grants: Grants
        get() = Grants(
            seeMoney = canSeeMoney,
            addCustomers = canAddCustomers,
            closeBills = canCloseBills,
            editTeam = canEditTeam,
            editMachines = canEditMachines,
            payOut = canPayOut,
            seePeopleCost = canSeePeopleCost,
        )

    /** Which job describes them, or null once an owner has adjusted a switch. */
    val preset: RolePreset? get() = RolePreset.matching(role, grants)

    /** What this editor may hand out — a manager may not make a manager. */
    val offeredPresets: List<RolePreset> get() = RolePreset.offeredBy(editableByOwnerOnly)

    /**
     * Whether their phone has gone quiet.
     *
     * The count alone hides the worst case. A beacon is written after a send, so
     * a phone that is reaching the server reports zero, and a phone that cannot
     * reach it reports nothing at all — leaving the driver who has been out of
     * signal for a week with nine jobs on him looking exactly like the driver
     * with nothing to send. Silence is the thing worth showing.
     */
    val outOfTouch: Boolean
        get() = unsentAtMillis != null &&
            System.currentTimeMillis() - unsentAtMillis > QUIET_AFTER_MILLIS

    private companion object {
        const val QUIET_AFTER_MILLIS = 24L * 60 * 60 * 1000
    }
}

/**
 * One member's record, and the permissions an owner grants them.
 *
 * The figures are deliberately scoped to this business: the same driver may work for
 * two contractors in a season, and neither should be able to see what the other pays
 * them or how much work they did elsewhere.
 */
class MemberViewModel(
    private val app: FieldFlowApplication,
    private val membershipId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MemberState())
    val state: StateFlow<MemberState> = _state.asStateFlow()

    private var orgId: String = ""
    private var selfPersonId: String = ""

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            orgId = s.orgId
            selfPersonId = s.personId
            val member = app.teamRepository.find(s.orgId, membershipId) ?: return@launch

            val jobs = app.workRepository.observeForOperator(s.orgId, member.personId).first()
            val billed = jobs.filter { it.status == JobStatus.Completed }
            // Asked here so the screen can warn before somebody is taken off
            // mid-shift: removal stops the job where it stands.
            val live = app.workRepository.observeLiveFor(s.orgId, member.personId).first()

            _state.update {
                it.copy(
                    loaded = true,
                    displayName = member.displayName,
                    // Normalised on the way in, because the column holds two
                    // spellings of the same number: ten digits when an owner
                    // typed it, full international form when it came from the
                    // signed-in identity of somebody who joined with a code. The
                    // field below renders under a "+91" prefix, so the second
                    // spelling arrives with a country code already attached and
                    // shows as "+91 +9190000...". Saving then writes the tidy one
                    // back, so the row heals the first time anybody opens it.
                    phone = member.phone?.let { indianMobileDigits(it) }?.ifBlank { null },
                    role = member.role,
                    active = member.active,
                    joinedAtMillis = member.joinedAt.takeIf { at -> at > 0 },
                    unsentCount = member.unsentCount,
                    unsentAtMillis = member.unsentAt,
                    liveWorkType = live?.workType,
                    liveSeconds = live?.let { job -> app.workRepository.elapsedSeconds(job) } ?: 0,
                    canSeeMoney = member.canSeeMoney,
                    canEditTeam = member.canEditTeam,
                    canEditMachines = member.canEditMachines,
                    canPayOut = member.canPayOut,
                    canSeePeopleCost = member.canSeePeopleCost,
                    editableByOwnerOnly = s.role == MemberRole.Owner,
                    canAddCustomers = member.canAddCustomers,
                    canCloseBills = member.canCloseBills,
                    jobCount = billed.size,
                    totalSeconds = jobs.sumOf { job -> job.elapsedSeconds },
                    broughtInRupees = billed.sumOf { job -> job.amountRupees },
                    isYou = member.personId == s.personId,
                    // Your own access is not editable from here: an owner who could
                    // switch off their own permissions would lock themselves out.
                    editable = Capability.EditTeam in s.can && member.personId != s.personId,
                    canSeeTheirEarnings = s.canSeeMoney,
                )
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(displayName = v) }
    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() }) }
    fun setSeeMoney(v: Boolean) = _state.update { it.copy(canSeeMoney = v) }
    /**
     * Promotes or demotes.
     *
     * An owner has every capability by definition, so the switches below stop
     * being choices the moment this is set to Owner — the screen disables them
     * rather than leaving three controls that quietly do nothing.
     */
    fun setRole(value: MemberRole) = _state.update { it.copy(role = value, problem = null) }

    /**
     * Picks a job, which writes all seven switches and the role at once.
     *
     * Every switch is set, the off ones included. Half-applying a preset — only
     * turning things on — would mean choosing Driver for a former manager left
     * them running the team, which is the opposite of what the word says.
     */
    fun setPreset(preset: RolePreset) = _state.update { current ->
        if (preset !in current.offeredPresets) return@update current
        val g = preset.grants
        current.copy(
            role = preset.role,
            canSeeMoney = g.seeMoney,
            canAddCustomers = g.addCustomers,
            canCloseBills = g.closeBills,
            canEditTeam = g.editTeam,
            canEditMachines = g.editMachines,
            canPayOut = g.payOut,
            canSeePeopleCost = g.seePeopleCost,
            problem = null,
        )
    }

    fun setAddCustomers(v: Boolean) = _state.update { it.copy(canAddCustomers = v) }

    /** Closing bills implies seeing money, so turning it on turns that on too. */
    fun setCloseBills(v: Boolean) = _state.update {
        it.copy(canCloseBills = v, canSeeMoney = if (v) true else it.canSeeMoney)
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        runCatching {
            app.teamRepository.updateMember(
                orgId = s.orgId,
                can = s.can,
                membershipId = membershipId,
                bySelfPersonId = selfPersonId,
                displayName = current.displayName,
                phone = current.phone,
                canSeeMoney = current.canSeeMoney,
                canAddCustomers = current.canAddCustomers,
                canCloseBills = current.canCloseBills,
                canEditTeam = current.canEditTeam,
                canEditMachines = current.canEditMachines,
                canPayOut = current.canPayOut,
                canSeePeopleCost = current.canSeePeopleCost,
                role = current.role,
            )
        }.onFailure { failure ->
            // Was: quietly switch the form to read-only. A refusal the user cannot
            // see is worse than the refusal — they are left with a screen that has
            // stopped responding and no idea what they did.
            _state.update { st ->
                st.copy(
                    saving = false,
                    problem = when (failure) {
                        is TeamError.LastOwner -> LastOwnerProblem
                        else -> null
                    },
                )
            }
            return@launch
        }
        _state.update { it.copy(saving = false) }
        onSaved()
    }

    fun setEditTeam(v: Boolean) = _state.update { it.copy(canEditTeam = v) }
    fun setEditMachines(v: Boolean) = _state.update { it.copy(canEditMachines = v) }
    fun setPayOut(v: Boolean) = _state.update { it.copy(canPayOut = v) }
    fun setSeePeopleCost(v: Boolean) = _state.update { it.copy(canSeePeopleCost = v) }

    fun setActive(active: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        runCatching {
            app.teamRepository.setActive(s.orgId, s.can, membershipId, selfPersonId, active)
        }.onSuccess { onDone() }
    }
}

/** The one refusal this screen can produce, as a string the screen can show. */
private val LastOwnerProblem = com.example.fieldtrack.R.string.account_last_owner
