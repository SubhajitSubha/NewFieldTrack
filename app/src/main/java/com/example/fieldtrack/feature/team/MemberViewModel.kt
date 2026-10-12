package com.example.fieldtrack.feature.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberState(
    val loaded: Boolean = false,
    val displayName: String = "",
    val phone: String? = null,
    val role: MemberRole = MemberRole.Operator,
    val active: Boolean = true,
    val joinedAtMillis: Long? = null,
    val canSeeMoney: Boolean = false,
    val canAddCustomers: Boolean = false,
    val canCloseBills: Boolean = false,
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

            _state.update {
                it.copy(
                    loaded = true,
                    displayName = member.displayName,
                    phone = member.phone,
                    role = member.role,
                    active = member.active,
                    joinedAtMillis = member.joinedAt.takeIf { at -> at > 0 },
                    canSeeMoney = member.canSeeMoney,
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
            )
        }.onFailure { _state.update { st -> st.copy(editable = false) } }
        _state.update { it.copy(saving = false) }
        onSaved()
    }

    fun setActive(active: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        runCatching {
            app.teamRepository.setActive(s.orgId, s.can, membershipId, selfPersonId, active)
        }.onSuccess { onDone() }
    }
}
