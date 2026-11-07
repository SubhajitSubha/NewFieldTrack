package com.example.fieldtrack.data.repository

import android.content.Context
import com.example.fieldtrack.data.region.BusinessClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Whether a running clock has probably been forgotten.
 *
 * The usual way it happens: a job started at eight, the tractor parked at six, the
 * phone in a pocket, and the app opened again the next morning to a clock reading
 * twenty-three hours. Billed as it stands, that is a farmer charged for a night.
 *
 * Two signs, either one enough to ask:
 *  - the clock has run through midnight without a pause, or
 *  - it has run more than [LONG_STRETCH_MILLIS] without one.
 *
 * Only asked, never acted on. A harvester crew in wheat season really does work a
 * fourteen-hour day, and a thresher can run into the night; the person holding the
 * phone is the only one who knows, so the app offers the question and keeps the
 * answer.
 */
object ClockCheck {

    const val LONG_STRETCH_MILLIS = 12L * 60 * 60 * 1000

    fun looksForgotten(runningSince: Long?, now: Long): Boolean {
        val since = runningSince ?: return false
        if (now - since > LONG_STRETCH_MILLIS) return true
        return since < midnightOf(now)
    }

    /** Local midnight at the start of the day [millis] falls in. */
    fun midnightOf(millis: Long): Long = Calendar.getInstance(BusinessClock.timeZone).apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * The stretches somebody has already said were real work.
 *
 * Keyed by the job and the moment its clock last started, so "yes, still working"
 * is remembered for exactly that stretch: pause for lunch and resume, and a new
 * stretch begins that will be asked about on its own if it runs on too.
 *
 * Kept on the phone only. It is a courtesy about what to ask this person, not a
 * fact about the job, and nobody else needs to know it was asked.
 */
class ClockConfirmations(context: Context) {

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _changes = MutableStateFlow(0)

    /** Bumps on every answer, so a screen showing the question can drop it at once. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun isConfirmed(jobId: String, runningSince: Long?): Boolean =
        runningSince != null && key(jobId, runningSince) in confirmed()

    fun confirm(jobId: String, runningSince: Long) {
        // Only the latest few are kept, in order — a string rather than a string
        // set, because a set has no order to keep the latest by. A stretch, once
        // ended, is never asked about again, so an ever-growing list would be
        // remembering nothing useful.
        val kept = (confirmed() + key(jobId, runningSince)).distinct().takeLast(KEEP)
        prefs.edit().putString(KEY, kept.joinToString(SEPARATOR)).apply()
        _changes.value++
    }

    private fun confirmed(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private fun key(jobId: String, runningSince: Long) = "$jobId@$runningSince"

    private companion object {
        const val PREFS = "clock_check"
        const val KEY = "confirmedStretches"
        const val KEEP = 20
        const val SEPARATOR = "\n"
    }
}
