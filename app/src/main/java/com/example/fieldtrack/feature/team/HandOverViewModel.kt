package com.example.fieldtrack.feature.team

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

/** Somebody who could be given the business. */
data class HeirRow(
    val membershipId: String,
    val displayName: String,
    val alreadyOwner: Boolean,
)

data class HandOverState(
    val loaded: Boolean = false,
    val candidates: List<HeirRow> = emptyList(),
    val chosenMembershipId: String? = null,
    val stepBack: Boolean = false,
    val ownerCount: Int = 0,
    val busy: Boolean = false,
) {
    val canConfirm: Boolean get() = !busy && chosenMembershipId != null
    val chosenName: String?
        get() = candidates.firstOrNull { it.membershipId == chosenMembershipId }?.displayName
}

/**
 * Giving somebody else the keys.
 *
 * A business with one owner is a business that lives on one handset. Lose it and
 * nothing inside the app brings the records back — the only way in is somebody
 * with console access, which no farmer has. A second owner is the whole remedy,
 * and it costs one screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HandOverViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val chosen = MutableStateFlow<String?>(null)
    private val stepBack = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<HandOverState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeTeam(s.orgId),
            chosen,
            stepBack,
            busy,
        ) { members, pick, back, working ->
            val others = members.filter { it.personId != s.personId }
            HandOverState(
                loaded = true,
                candidates = others.map {
                    HeirRow(
                        membershipId = it.id,
                        displayName = it.displayName,
                        alreadyOwner = it.role == MemberRole.Owner,
                    )
                },
                chosenMembershipId = pick,
                stepBack = back,
                ownerCount = members.count { it.role == MemberRole.Owner },
                busy = working,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HandOverState())

    fun choose(membershipId: String) { chosen.value = membershipId }

    fun setStepBack(value: Boolean) { stepBack.value = value }

    fun confirm(onDone: () -> Unit) = viewModelScope.launch {
        val pick = chosen.value ?: return@launch
        val s = app.sessionStore.load() ?: return@launch
        busy.value = true
        try {
            app.teamRepository.handOverOwnership(
                orgId = s.orgId,
                can = s.can,
                toMembershipId = pick,
                bySelfPersonId = s.personId,
                stepBack = stepBack.value,
            )
            onDone()
        } finally {
            busy.value = false
        }
    }
}
