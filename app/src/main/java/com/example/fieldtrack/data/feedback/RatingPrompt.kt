package com.example.fieldtrack.data.feedback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the one-time "is it helping?" question has got to. */
enum class RatingStep { Hidden, Ask, Happy, Unhappy }

/**
 * Asks, once, whether the app is helping: and only somebody it is likely to have helped.
 *
 * Not before they have had a fortnight and finished some real work, because a rating
 * asked of somebody who has seen nothing is a rating of the splash screen. A "yes" is
 * sent to the store page, a "not really" to a note to us, so the unhappy are heard
 * instead of being sent to rate. Put off, it waits a month; answered, it is never asked
 * again. The Help screen always has the rating link for anybody who wants it.
 */
class RatingPrompt(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _step = MutableStateFlow(RatingStep.Hidden)
    val step: StateFlow<RatingStep> = _step.asStateFlow()

    init {
        // Counted from the first time this ran, so a phone that has had the app a day is not asked.
        if (!prefs.contains(KEY_SINCE)) prefs.edit().putLong(KEY_SINCE, now()).apply()
    }

    fun eligible(completedJobs: Int): Boolean =
        !prefs.getBoolean(KEY_DONE, false) &&
            now() >= prefs.getLong(KEY_LATER_UNTIL, 0L) &&
            now() - prefs.getLong(KEY_SINCE, now()) >= MIN_DAYS * DAY &&
            completedJobs >= MIN_JOBS

    /** Looks at the facts and shows the question if it is time. Never interrupts one being answered. */
    fun evaluate(completedJobs: Int) {
        if (_step.value == RatingStep.Hidden && eligible(completedJobs)) _step.value = RatingStep.Ask
    }

    fun answer(helping: Boolean) { _step.value = if (helping) RatingStep.Happy else RatingStep.Unhappy }

    /** "Not now". */
    fun later() {
        prefs.edit().putLong(KEY_LATER_UNTIL, now() + SNOOZE_DAYS * DAY).apply()
        _step.value = RatingStep.Hidden
    }

    /** Whatever was done with the follow-up, the question is finished. */
    fun finish() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
        _step.value = RatingStep.Hidden
    }

    companion object {
        const val PREFS = "fieldtrack.rating"
        private const val KEY_SINCE = "since"
        private const val KEY_DONE = "done"
        private const val KEY_LATER_UNTIL = "laterUntil"
        const val MIN_DAYS = 14L
        const val MIN_JOBS = 5
        const val SNOOZE_DAYS = 30L
        private const val DAY = 24L * 60 * 60 * 1000
    }
}
