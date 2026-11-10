package com.example.fieldtrack.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** What a search found, grouped by what it found. */
data class SearchResults(
    val customers: List<CustomerEntity> = emptyList(),
    val fields: List<FieldEntity> = emptyList(),
    val machines: List<MachineEntity> = emptyList(),
    val jobs: List<JobEntity> = emptyList(),
    /** The people on the team, for somebody who looks after the team. */
    val team: List<com.example.fieldtrack.data.db.entity.MembershipEntity> = emptyList(),
    /** Customer names for the jobs and fields found, so a row can say whose it is. */
    val customerNames: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = customers.isEmpty() && fields.isEmpty() && machines.isEmpty() && jobs.isEmpty() && team.isEmpty()
}

data class SearchState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    /** True once something has been typed, so "nothing found" is not said before a search. */
    val searched: Boolean = false,
    /** Whether this person may see money; jobs found are listed with no amounts either way. */
    val canSeeMoney: Boolean = false,
)

/**
 * One box that finds a farmer, a field, a machine or a job.
 *
 * A contractor with two seasons behind him does not remember which list a name lives in.
 * Matches are by name, village, phone, plate or the kind of work; the search runs a moment
 * after the last key so the list does not flicker while somebody is still typing.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * What is in the box, as typed. The box must follow this and not [state]: the results
     * wait for a pause in the typing, and a box fed from them would throw away every key
     * pressed in between.
     */
    val typed: StateFlow<String> = query

    val state: StateFlow<SearchState> = query.debounce(150).flatMapLatest { text ->
        flow {
            val term = text.trim()
            val s = app.sessionStore.load()
            if (term.length < MIN_CHARS || s == null) {
                emit(SearchState(query = text, searched = false, canSeeMoney = s?.canSeeMoney == true))
                return@flow
            }
            val like = "%" + term.replace("%", "").replace("_", "") + "%"
            val customers = app.db.customerDao().findByTerm(s.orgId, like)
            val fields = app.db.customerDao().findFieldsByTerm(s.orgId, like)
            val machines = app.db.machineDao().findByTerm(s.orgId, like)
            val jobs = app.db.jobDao().findByTerm(s.orgId, like)
            val names = (customers + app.db.customerDao().findByIds(s.orgId,
                (fields.map { it.customerId } + jobs.map { it.customerId }).distinct()))
                .associate { it.id to it.name }
            // Only for somebody who may open a team member: a driver is not shown a way into
            // a colleague's page by typing their name.
            val team = if (com.example.fieldtrack.data.session.Capability.EditTeam in s.can) {
                val plain = term.lowercase()
                app.teamRepository.observeTeam(s.orgId).first()
                    .filter { it.active && it.displayName.lowercase().contains(plain) }
            } else emptyList()
            emit(
                SearchState(
                    query = text,
                    searched = true,
                    canSeeMoney = s.canSeeMoney,
                    results = SearchResults(customers, fields, machines, jobs, team, names),
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun setQuery(text: String) { query.value = text.take(60) }

    private companion object {
        const val MIN_CHARS = 2
    }
}
