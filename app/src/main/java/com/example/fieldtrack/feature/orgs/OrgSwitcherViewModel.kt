package com.example.fieldtrack.feature.orgs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MemberRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One business this person belongs to, or used to. */
data class OrgRow(
    val orgId: String,
    val name: String,
    val role: MemberRole,
    val active: Boolean,
    val lastWorkedMillis: Long?,
    val isCurrent: Boolean,
    /** What is running there now, when this is the business they are in. */
    val liveWorkType: String? = null,
    val liveSeconds: Long = 0,
)

data class OrgSwitcherState(
    val loaded: Boolean = false,
    val personName: String = "",
    val role: MemberRole = MemberRole.Operator,
    val current: OrgRow? = null,
    val others: List<OrgRow> = emptyList(),
    /** True while the server is being asked whether a switch is still allowed. */
    val checking: String? = null,
    /** The business they have just been found to be out of, if any. */
    val refused: String? = null,
)

/**
 * The businesses one person drives for.
 *
 * A driver with two or three employers is ordinary here — a season with one
 * contractor, a season with another, sometimes both in the same month. Until this
 * screen existed the app could hold several memberships and show only whichever it
 * happened to land on, with no way to move.
 *
 * Switching changes what the whole app is looking at, which is exactly why it is
 * worth saying out loud on the screen that the businesses cannot see each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrgSwitcherViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _checking = MutableStateFlow<String?>(null)
    private val _refused = MutableStateFlow<String?>(null)

    val state: StateFlow<OrgSwitcherState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.db.orgDao().observeMembershipsOf(s.personId),
            app.workRepository.observeLiveFor(s.orgId, s.personId),
            _checking,
            _refused,
        ) { memberships, live, checking, refused ->
            val rows = memberships.map { membership ->
                val org = app.db.orgDao().findOrg(membership.orgId)
                OrgRow(
                    orgId = membership.orgId,
                    name = org?.name?.ifBlank { null } ?: membership.orgId.take(8),
                    role = membership.role,
                    active = membership.active,
                    lastWorkedMillis = app.db.jobDao()
                        .lastWorkedAt(membership.orgId, s.personId),
                    isCurrent = membership.orgId == s.orgId,
                    liveWorkType = live?.workType.takeIf { membership.orgId == s.orgId },
                    liveSeconds = live?.let { app.workRepository.elapsedSeconds(it) } ?: 0,
                )
            }
            OrgSwitcherState(
                loaded = true,
                personName = s.displayName,
                role = s.role,
                current = rows.firstOrNull { it.isCurrent },
                // Businesses they have left sort last: still worth seeing, since
                // the work is still theirs to remember, but not offered first.
                others = rows.filterNot { it.isCurrent }
                    .sortedWith(compareByDescending<OrgRow> { it.active }
                        .thenByDescending { it.lastWorkedMillis ?: 0 }),
                checking = checking,
                refused = refused,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrgSwitcherState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }

    /**
     * Moves the whole app to another business.
     *
     * Refused for one they have left: the membership is inactive, the server would
     * turn down every read, and letting them in would show a shell of a business
     * with nothing in it and no explanation.
     */
    fun switchTo(orgId: String, onSwitched: () -> Unit) = viewModelScope.launch {
        val row = state.value.others.firstOrNull { it.orgId == orgId } ?: return@launch
        if (!row.active) return@launch

        // Ask before moving, because the local row only says what was true the
        // last time anybody checked, and nothing checks a business you are not
        // working in. Somebody let go in April would otherwise walk straight back
        // into their old employer's customers in June.
        _checking.value = orgId
        val allowed = runCatching {
            app.sessionStore.verifyMembership(orgId, app.remoteRestore, app.restoreApplier)
        }.getOrDefault(true)
        _checking.value = null

        if (!allowed) {
            _refused.value = row.name
            return@launch
        }

        app.sessionStore.switchTo(orgId)
        onSwitched()
    }

    fun clearRefusal() {
        _refused.value = null
    }
}
