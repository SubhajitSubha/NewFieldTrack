package com.example.fieldtrack.data.sync

import android.content.Context

/**
 * How far down the server's changes each business has been read.
 *
 * Kept per business rather than per phone, because a driver who works for two
 * contractors reads them independently — and because leaving one must not cost
 * them their place in the other.
 *
 * Deliberately not in the database. A watermark is a fact about this installation,
 * not a record of anybody's work: it must never be restored onto a new handset,
 * where it would claim rows had already been read that this phone has never seen.
 */
class PullWatermarks(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Server write time of the newest change already applied. Zero means none. */
    fun watermark(orgId: String): Long = prefs.getLong(mark(orgId), 0L)

    /** When a pass was last attempted, successful or not. */
    fun lastAttemptAt(orgId: String): Long = prefs.getLong(attempt(orgId), 0L)

    fun recordAttempt(orgId: String, at: Long) {
        prefs.edit().putLong(attempt(orgId), at).apply()
    }

    /**
     * Moves the mark forward.
     *
     * Never backward: a pass that came back with less than it asked for must not
     * undo what an earlier one established, or the two would trade places for ever
     * and the same rows would arrive on every sync.
     */
    fun advance(orgId: String, watermark: Long) {
        if (watermark <= this.watermark(orgId)) return
        prefs.edit().putLong(mark(orgId), watermark).apply()
    }

    /**
     * Whether this business has ever been read in full.
     *
     * Tracked separately from the mark, because "no mark" and "nothing to read"
     * look identical: a business whose documents all predate the server stamp
     * leaves the mark at zero however many times it is read, and keying the
     * one-off full fetch on that alone would repeat it on every single pass.
     */
    fun hasSeeded(orgId: String): Boolean = prefs.getBoolean(seeded(orgId), false)

    /** Set only once the full read has actually landed. */
    fun markSeeded(orgId: String) {
        prefs.edit().putBoolean(seeded(orgId), true).apply()
    }

    /** Forgotten on removal, so rejoining later reads the business from scratch. */
    fun forget(orgId: String) {
        prefs.edit().remove(mark(orgId)).remove(attempt(orgId)).remove(seeded(orgId)).apply()
    }

    private fun mark(orgId: String) = "mark.$orgId"
    private fun attempt(orgId: String) = "attempt.$orgId"
    private fun seeded(orgId: String) = "seeded.$orgId"

    private companion object {
        const val PREFS = "fieldtrack.pull"
    }
}
