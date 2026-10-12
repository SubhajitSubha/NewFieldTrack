package com.example.fieldtrack.feature.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.data.db.entity.InviteCodeDoc
import com.example.fieldtrack.data.repository.isWellFormedInviteCode
import com.example.fieldtrack.data.repository.normaliseInviteCode
import com.example.fieldtrack.data.sync.InviteLookup
import com.example.fieldtrack.data.sync.InviteLookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class JoinStep { Code, Preview }

/** Why a code did not work, in the terms the screen explains it. */
enum class JoinProblem { NotFound, Expired, AlreadyUsed, Unreachable }

data class JoinState(
    val step: JoinStep = JoinStep.Code,
    val code: String = "",
    val name: String = "",
    val invite: InviteCodeDoc? = null,
    val busy: Boolean = false,
    val problem: JoinProblem? = null,
) {
    val canCheck: Boolean get() = !busy && isWellFormedInviteCode(code)
    val canJoin: Boolean get() = !busy && invite != null && name.isNotBlank()
}

/**
 * A driver joining somebody's business with a code.
 *
 * The preview step exists because a code is typed off a WhatsApp message by someone
 * who has no other way to check it. Showing the business name before anything is
 * committed is the only chance they get to notice it is the wrong one.
 */
class JoinViewModel(
    private val lookup: InviteLookup,
    suggestedName: String = "",
    private val onJoin: suspend (InviteCodeDoc, String) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinState(name = suggestedName))
    val state: StateFlow<JoinState> = _state.asStateFlow()

    fun setCode(value: String) = _state.update {
        it.copy(code = normaliseInviteCode(value), problem = null)
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    /** Back to the code, keeping it so a single wrong character can be fixed. */
    fun back() = _state.update {
        it.copy(step = JoinStep.Code, invite = null, problem = null)
    }

    fun check() {
        val current = _state.value
        if (!current.canCheck) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, problem = null) }
            val result = lookup.find(current.code)
            _state.update {
                when (result) {
                    is InviteLookupResult.Found ->
                        it.copy(busy = false, step = JoinStep.Preview, invite = result.invite)

                    is InviteLookupResult.NoLongerValid -> it.copy(
                        busy = false,
                        problem = if (result.invite.redeemedAt != null) JoinProblem.AlreadyUsed
                        else JoinProblem.Expired,
                    )

                    InviteLookupResult.NotFound ->
                        it.copy(busy = false, problem = JoinProblem.NotFound)

                    is InviteLookupResult.Unreachable ->
                        it.copy(busy = false, problem = JoinProblem.Unreachable)
                }
            }
        }
    }

    fun join() {
        val current = _state.value
        val invite = current.invite ?: return
        if (!current.canJoin) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { onJoin(invite, current.name.trim()) }
                .onFailure { _state.update { s -> s.copy(busy = false) } }
        }
    }
}
