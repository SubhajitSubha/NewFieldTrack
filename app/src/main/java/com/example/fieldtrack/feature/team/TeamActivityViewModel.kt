package com.example.fieldtrack.feature.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.TeamEventKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One line in the log, already carrying the names it was written with. */
data class TeamChange(
    val id: String,
    val atMillis: Long,
    val kind: TeamEventKind,
    val byName: String,
    val aboutName: String?,
    val detail: String?,
    /** True when somebody acted on their own record rather than another's. */
    val aboutSelf: Boolean,
)

data class TeamActivityState(
    val loaded: Boolean = false,
    val changes: List<TeamChange> = emptyList(),
)

/**
 * What was done to this team, and by whom.
 *
 * Money permissions change hands in a tap and are forgotten by the evening. The
 * log exists so that "who let him see the balances" has an answer that does not
 * depend on anybody remembering.
 *
 * It begins empty. Nothing wrote these down before the log existed, so there is
 * no past to show — only what happens from here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamActivityViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<TeamActivityState> = app.sessionStore.session.flatMapLatest { s ->
        app.db.orgDao().observeTeamEvents(s.orgId).map { events ->
            TeamActivityState(
                loaded = true,
                changes = events.map { event ->
                    TeamChange(
                        id = event.id,
                        atMillis = event.atMillis,
                        kind = event.kind,
                        byName = event.byName,
                        aboutName = event.aboutName,
                        detail = event.detail,
                        aboutSelf = event.aboutPersonId != null &&
                            event.aboutPersonId == event.byPersonId,
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamActivityState())
}
