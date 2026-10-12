package com.example.fieldtrack.feature.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InviteState(
    val loaded: Boolean = false,
    val code: String? = null,
    val expiresAtMillis: Long? = null,
    val businessName: String = "",
    val canSeeMoney: Boolean = false,
    val canAddCustomers: Boolean = false,
    val canCloseBills: Boolean = false,
    val canInvite: Boolean = false,
    val busy: Boolean = false,
) {
    val hasCode: Boolean get() = code != null
}

/**
 * The code an owner hands a driver.
 *
 * The permissions are set here, before the code exists, because they travel with it.
 * Asking at redemption would be the driver's own phone deciding what the driver is
 * allowed to do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModel(private val app: FieldFlowApplication) : ViewModel() {

    /** Permissions for the *next* code. An issued code's terms are already fixed. */
    private val pending = MutableStateFlow(PendingPermissions())
    private val busy = MutableStateFlow(false)

    private data class PendingPermissions(
        val canSeeMoney: Boolean = false,
        val canAddCustomers: Boolean = false,
        val canCloseBills: Boolean = false,
    )

    val state: StateFlow<InviteState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeOpenInvite(s.orgId),
            pending,
            busy,
        ) { invite, wanted, working ->
            InviteState(
                loaded = true,
                code = invite?.code,
                expiresAtMillis = invite?.expiresAt,
                businessName = s.businessName,
                // A live code's terms are what they were when it was made; only the
                // unissued one follows the switches.
                canSeeMoney = invite?.canSeeMoney ?: wanted.canSeeMoney,
                canAddCustomers = invite?.canAddCustomers ?: wanted.canAddCustomers,
                canCloseBills = invite?.canCloseBills ?: wanted.canCloseBills,
                canInvite = Capability.EditTeam in s.can,
                busy = working,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InviteState())

    fun setSeeMoney(value: Boolean) = pending.update { it.copy(canSeeMoney = value) }
    fun setAddCustomers(value: Boolean) = pending.update { it.copy(canAddCustomers = value) }
    fun setCloseBills(value: Boolean) = pending.update {
        // Closing a bill means seeing its amount, so the two cannot disagree.
        it.copy(canCloseBills = value, canSeeMoney = it.canSeeMoney || value)
    }

    fun makeCode() = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        busy.value = true
        try {
            val wanted = pending.value
            app.teamRepository.createInvite(
                orgId = s.orgId,
                orgName = s.businessName,
                can = s.can,
                canSeeMoney = wanted.canSeeMoney,
                canAddCustomers = wanted.canAddCustomers,
                canCloseBills = wanted.canCloseBills,
            )
        } finally {
            busy.value = false
        }
    }

    /** Replaces the live code, carrying its terms over as the starting point. */
    fun newCode() = viewModelScope.launch {
        val current = state.value
        pending.value = PendingPermissions(
            canSeeMoney = current.canSeeMoney,
            canAddCustomers = current.canAddCustomers,
            canCloseBills = current.canCloseBills,
        )
        makeCode().join()
    }

    fun revoke() = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        busy.value = true
        try {
            app.teamRepository.revokeInvites(s.orgId, s.can)
        } finally {
            busy.value = false
        }
    }
}
