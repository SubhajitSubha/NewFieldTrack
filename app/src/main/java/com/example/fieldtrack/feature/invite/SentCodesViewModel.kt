package com.example.fieldtrack.feature.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One code still in somebody's hands. */
data class SentCode(
    val inviteId: String,
    val code: String,
    val expiresAtMillis: Long,
    val canSeeMoney: Boolean,
    val canCloseBills: Boolean,
    val canAddCustomers: Boolean,
)

/** One that was typed in, and who came in on it. */
data class UsedCode(
    val code: String,
    val personName: String?,
    val redeemedAtMillis: Long,
)

data class SentCodesState(
    val loaded: Boolean = false,
    val businessName: String = "",
    val open: List<SentCode> = emptyList(),
    val used: List<UsedCode> = emptyList(),
    val canInvite: Boolean = false,
)

/**
 * Every code an owner has handed out and not yet seen used.
 *
 * There was no such screen while only one code could exist: the invite screen
 * held it, and making another quietly retired it. Several at once is the
 * ordinary case — a contractor taking on a crew sends one to each driver — and
 * the thing that keeps that safe is being able to see them. A code nobody can
 * list is a code nobody can take back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SentCodesViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<SentCodesState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeOpenInvites(s.orgId),
            app.teamRepository.observeRedeemedInvites(s.orgId),
            app.teamRepository.observeEveryone(s.orgId),
        ) { open, used, everyone ->
            // Names come from everyone who has ever been here, not only who is
            // here now: a code used by somebody since let go still says who.
            val names = everyone.associate { it.personId to it.displayName }
            SentCodesState(
                loaded = true,
                businessName = s.businessName,
                open = open.map { invite ->
                    SentCode(
                        inviteId = invite.id,
                        code = invite.code,
                        expiresAtMillis = invite.expiresAt,
                        canSeeMoney = invite.canSeeMoney,
                        canCloseBills = invite.canCloseBills,
                        canAddCustomers = invite.canAddCustomers,
                    )
                },
                used = used.mapNotNull { invite ->
                    UsedCode(
                        code = invite.code,
                        personName = invite.redeemedByPersonId?.let { names[it] },
                        redeemedAtMillis = invite.redeemedAt ?: return@mapNotNull null,
                    )
                },
                canInvite = Capability.EditTeam in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SentCodesState())

    fun revoke(inviteId: String) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        app.teamRepository.revokeInvite(s.orgId, inviteId, s.can, byPersonId = s.personId)
    }

    /** For the screen's countdowns, so it does not reach for a clock of its own. */
    fun now(): Long = Clock.System.now()
}
