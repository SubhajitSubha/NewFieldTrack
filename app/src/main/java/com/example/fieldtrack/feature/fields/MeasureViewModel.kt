package com.example.fieldtrack.feature.fields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.measure.Fix
import com.example.fieldtrack.data.measure.Land
import com.example.fieldtrack.data.measure.Point
import com.example.fieldtrack.data.measure.Shape
import com.example.fieldtrack.data.measure.WalkStore
import com.example.fieldtrack.data.measure.Walking
import com.example.fieldtrack.data.measure.metresBetween
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How good the GPS is right now. */
enum class Signal { None, Weak, Good }

data class MeasureState(
    val loaded: Boolean = false,
    val access: GpsAccess = GpsAccess.Allowed,
    val signal: Signal = Signal.None,
    val accuracyMetres: Int? = null,
    /** The walk so far. */
    val points: List<Point> = emptyList(),
    /** Where the phone is now, for the dot on the path. */
    val here: Point? = null,
    val walking: Boolean = false,
    /** Readings stopped for a while mid-walk, so the path may have a gap in it. */
    val interrupted: Boolean = false,
    val finished: Boolean = false,
    val unit: MeasureUnit = MeasureUnit.Acre,
    /** How many of a bigha or katha make an acre, as the farmer typed it. */
    val perAcreText: String = "",
    val shape: Shape? = null,
) {
    val walkedMetres: Double
        get() = points.zipWithNext().sumOf { (a, b) -> metresBetween(a.latitude, a.longitude, b.latitude, b.longitude) }

    val perAcre: Double?
        get() = Land.fixedPerAcre(unit) ?: perAcreText.toDoubleOrNull()?.takeIf(Land::plausiblePerAcre)

    /** The size in [unit] to two places, or null while it cannot honestly be said. */
    val area: Double?
        get() = shape?.takeIf { it.enough }?.let { s ->
            perAcre?.let { Math.round(Land.inUnit(s.areaSquareMetres, it) * 100) / 100.0 }
        }

    val canUse: Boolean get() = finished && (area ?: 0.0) > 0.0
}

/**
 * Measuring a plot by walking round its edge.
 *
 * The phone's own GPS, a reading a second, kept only when it is good and a few
 * steps on from the last. Nothing leaves the phone, and nothing is kept once the
 * size has been used or the walk thrown away.
 */
class MeasureViewModel(
    private val app: FieldFlowApplication,
    startUnit: MeasureUnit,
    private val access: () -> GpsAccess = { gpsAccess(app) },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val store = WalkStore(app)
    private var orgId = ""
    private val _state = MutableStateFlow(MeasureState(unit = startUnit))
    val state: StateFlow<MeasureState> = _state.asStateFlow()

    private var listening: Job? = null
    private var lastFixAt = 0L
    private var lastAccuracy: Float? = null

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            orgId = s.orgId
            // A walk the phone was still holding when the app was closed under it:
            // picked up again, with the gap it left said out loud.
            val saved = store.track(orgId)
            _state.update {
                it.copy(
                    loaded = true,
                    points = saved,
                    walking = saved.isNotEmpty(),
                    interrupted = saved.isNotEmpty(),
                    perAcreText = perAcreFor(startUnit),
                )
            }
        }
    }

    private fun perAcreFor(unit: MeasureUnit) = store.perAcre(orgId, unit)?.toString().orEmpty()

    /** Takes readings from [fixes] until [stop], and keeps the picture of the signal fresh. */
    fun listen(fixes: Flow<Fix>) {
        stop()
        listening = viewModelScope.launch {
            launch { fixes.collect(::onFix) }
            while (true) {
                refresh()
                delay(TICK_MILLIS)
            }
        }
    }

    /** Readings stop. Mid-walk that leaves a gap, and the path says so. */
    fun stop() {
        if (listening != null && _state.value.walking) _state.update { it.copy(interrupted = true) }
        listening?.cancel()
        listening = null
    }

    fun refresh() = _state.update {
        val age = now() - lastFixAt
        val accuracy = lastAccuracy
        it.copy(
            access = access(),
            accuracyMetres = accuracy?.toInt(),
            signal = when {
                accuracy == null || age > STALE_MILLIS || it.access != GpsAccess.Allowed -> Signal.None
                accuracy > Walking.MAX_ACCURACY_METRES -> Signal.Weak
                else -> Signal.Good
            },
        )
    }

    internal fun onFix(fix: Fix) {
        // The GPS hands over the last place it knew the moment it is asked; that is
        // where the phone was, not where it is.
        if (now() - fix.atMillis > STALE_MILLIS) return
        lastFixAt = now()
        lastAccuracy = fix.accuracyMetres
        val here = Point(fix.latitude, fix.longitude, fix.atMillis)
        _state.update { st ->
            val keep = st.walking && Walking.accepts(st.points.lastOrNull(), fix)
            val points = if (keep) st.points + here else st.points
            if (keep && orgId.isNotEmpty()) store.saveTrack(orgId, points)
            st.copy(here = here, points = points)
        }
        refresh()
    }

    /** From where the phone is now, which should be a corner of the field. */
    fun start() {
        val st = _state.value
        val here = st.here ?: return
        if (st.signal != Signal.Good) return
        if (orgId.isNotEmpty()) store.saveTrack(orgId, listOf(here))
        _state.update {
            it.copy(points = listOf(here), walking = true, interrupted = false, finished = false, shape = null)
        }
    }

    fun finish() {
        val st = _state.value
        if (!st.walking || st.points.isEmpty()) return
        _state.update { it.copy(walking = false, finished = true, shape = Shape(it.points)) }
    }

    /** Back to walking from where the path ended, after finishing too soon. */
    fun keepWalking() = _state.update { it.copy(walking = true, finished = false, shape = null) }

    fun startOver() {
        if (orgId.isNotEmpty()) store.clearTrack(orgId)
        _state.update {
            it.copy(points = emptyList(), walking = false, interrupted = false, finished = false, shape = null)
        }
    }

    fun setUnit(unit: MeasureUnit) = _state.update { it.copy(unit = unit, perAcreText = perAcreFor(unit)) }

    /** What the farmer says a bigha or katha comes to, kept for next time once it is a real number. */
    fun setPerAcre(text: String) {
        val cleaned = text.filter { it.isDigit() || it == '.' }.let { t ->
            val dot = t.indexOf('.')
            if (dot < 0) t else t.substring(0, dot + 1) + t.substring(dot + 1).replace(".", "")
        }
        _state.update { it.copy(perAcreText = cleaned) }
        cleaned.toDoubleOrNull()?.takeIf(Land::plausiblePerAcre)?.let { store.setPerAcre(orgId, _state.value.unit, it) }
    }

    /** The size and its unit, for the form; the walk is finished with. */
    fun use(): Pair<Double, MeasureUnit>? {
        val st = _state.value
        val area = st.area?.takeIf { st.canUse } ?: return null
        if (orgId.isNotEmpty()) store.clearTrack(orgId)
        return area to st.unit
    }

    private companion object {
        const val TICK_MILLIS = 2_000L
        const val STALE_MILLIS = 10_000L
    }
}
