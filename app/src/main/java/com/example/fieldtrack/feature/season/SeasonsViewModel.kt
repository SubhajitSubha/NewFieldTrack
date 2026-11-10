package com.example.fieldtrack.feature.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.season.Season
import com.example.fieldtrack.data.season.SeasonCalendar
import com.example.fieldtrack.data.season.SeasonStart
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One season being edited. The day is text, not a number, so it can be emptied and
 * retyped without the field fighting back.
 */
data class SeasonRow(
    val id: Int,
    val name: String = "",
    val month: Int = 1,
    val dayText: String = "1",
) {
    val day: Int? get() = dayText.toIntOrNull()

    /** The last day this row's month allows, which the day is checked against. */
    val maxDay: Int get() = SeasonCalendar.maxDay(month)

    val dayValid: Boolean get() = day?.let { it in 1..maxDay } == true

    fun asStart(): SeasonStart? = day?.let { SeasonCalendar.cleanStart(month, it, name) }
}

data class SeasonsState(
    val loaded: Boolean = false,
    /** False for anybody but the owner: the dates are the business's, not a driver's to change. */
    val allowed: Boolean = true,
    val rows: List<SeasonRow> = emptyList(),
    val saving: Boolean = false,
    /** Today, for saying which season the dates as they stand would put it in. */
    val nowMillis: Long = 0,
) {
    val canAdd: Boolean get() = rows.size < SeasonCalendar.MAX_SEASONS

    /** One season at least: a calendar with none has no answer to "which one is this". */
    val canRemove: Boolean get() = rows.size > 1

    val duplicate: Boolean
        get() {
            val dates = rows.mapNotNull { r -> r.day?.takeIf { r.dayValid }?.let { r.month to it } }
            return dates.distinct().size != dates.size
        }

    /** Whether the season at [index] begins on the same day as one above it. */
    fun repeatsEarlier(index: Int): Boolean {
        val row = rows.getOrNull(index) ?: return false
        val day = row.day?.takeIf { row.dayValid } ?: return false
        return rows.take(index).any { it.dayValid && it.month == row.month && it.day == day }
    }

    val canSave: Boolean
        get() = allowed && !saving && rows.isNotEmpty() && rows.all { it.dayValid } && !duplicate

    /** The calendar these rows make, when they make one — what the preview line reads from. */
    val calendar: SeasonCalendar?
        get() = if (canSave) SeasonCalendar.of(rows.mapNotNull { it.asStart() }) else null

    /** The season today falls in, if the dates are good enough to say. */
    val today: Season? get() = calendar?.current(nowMillis)
}

/** Setting when the business's seasons begin. */
class SeasonsViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _state = MutableStateFlow(SeasonsState())
    val state: StateFlow<SeasonsState> = _state.asStateFlow()

    private var nextId = 0

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val calendar = app.directoryRepository.observeSeasons(s.orgId).first()
            _state.update {
                it.copy(
                    loaded = true,
                    allowed = Capability.EditBusiness in s.can,
                    rows = calendar.starts.map(::rowOf),
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun rowOf(start: SeasonStart) = SeasonRow(
        id = nextId++, name = start.name.orEmpty(), month = start.month, dayText = start.day.toString(),
    )

    private fun change(id: Int, edit: (SeasonRow) -> SeasonRow) = _state.update { st ->
        st.copy(rows = st.rows.map { if (it.id == id) edit(it) else it })
    }

    fun setName(id: Int, text: String) = change(id) {
        it.copy(name = text.filterNot(Char::isISOControl).take(SeasonCalendar.MAX_NAME))
    }

    fun setDay(id: Int, text: String) = change(id) { it.copy(dayText = text.filter(Char::isDigit).take(2)) }

    /** Moving to a shorter month pulls the day in with it, so 31 Jan never becomes 31 Feb. */
    fun setMonth(id: Int, month: Int) = change(id) {
        val day = it.day
        val max = SeasonCalendar.maxDay(month)
        it.copy(month = month, dayText = if (day != null && day > max) max.toString() else it.dayText)
    }

    /** A new season, a quarter of a year after the last one, on a date no other season has. */
    fun add() = _state.update { st ->
        if (!st.canAdd) return@update st
        val taken = st.rows.mapNotNull { r -> r.day?.let { r.month to it } }.toSet()
        var month = ((st.rows.lastOrNull()?.month ?: 0) + 2) % 12 + 1
        repeat(12) { if ((month to 1) in taken) month = month % 12 + 1 }
        st.copy(rows = st.rows + SeasonRow(id = nextId++, month = month, dayText = "1"))
    }

    fun remove(id: Int) = _state.update { st ->
        if (!st.canRemove) st else st.copy(rows = st.rows.filterNot { it.id == id })
    }

    /** Back to June and November, unnamed. */
    fun useUsual() = _state.update { st ->
        st.copy(rows = SeasonCalendar.Default.starts.map(::rowOf))
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        val calendar = current.calendar ?: return@launch
        _state.update { it.copy(saving = true) }
        runCatching { app.sessionStore.updateSeasons(calendar) }
            .onSuccess { _state.update { it.copy(saving = false) }; onSaved() }
            // Refused: the owner's permission was taken away while this was open.
            .onFailure { _state.update { it.copy(saving = false, allowed = false) } }
    }
}
