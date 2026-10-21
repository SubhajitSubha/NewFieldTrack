package com.example.fieldtrack.data.sync

import android.util.Log
import com.example.fieldtrack.data.db.Clock

/**
 * Brings down what other people changed.
 *
 * The app has always been able to send. What it could not do was listen: once a
 * phone had signed in, its machines, customers, rates and the business's own name
 * were whatever they had been that day, for ever. An owner buying a tractor or
 * correcting the address that prints on every bill changed nothing on the drivers'
 * handsets, and there was no way for anybody to find that out except by noticing
 * the bills were wrong.
 *
 * Three rules:
 *
 * **Only forward.** Each pass asks for what the server has written since the last
 * one and remembers how far it got. Nothing is downloaded twice except deliberately.
 *
 * **Local work still wins.** Every row goes through [RestoreApplier], which refuses
 * to overwrite anything this phone has not managed to send yet. A pull arrives from
 * a server that has not heard about this morning's work in a field with no signal,
 * and it must not be allowed to erase it.
 *
 * **Quiet when it cannot.** No signal is the normal state here, not an error. A
 * failed pass leaves the mark where it was and says nothing to anybody.
 */
class PullSync(
    private val restore: RemoteRestore,
    private val applier: RestoreApplier,
    private val marks: PullWatermarks,
    private val clock: Clock,
    /**
     * How often one business is worth asking about.
     *
     * A pass costs a read against every collection, and the sync runs on each save
     * — which during a working morning is constant. Machines and rates do not
     * change by the minute, so asking more often than this would spend the user's
     * data and the project's quota to learn nothing.
     */
    private val minIntervalMillis: Long = 10 * 60_000L,
) {

    /** Returns how many rows were written. [force] ignores the interval. */
    suspend fun pull(orgId: String, force: Boolean = false): Int {
        val now = clock.now()

        // A business never read in full is never made to wait. The interval is
        // there to stop ordinary deltas running on every save; somebody who
        // joined a minute ago and is looking at an empty app is a different
        // question, and a failed first attempt must not strand them for ten
        // minutes with nothing on screen.
        val mustSeed = !marks.hasSeeded(orgId)
        if (!force && !mustSeed && now - marks.lastAttemptAt(orgId) < minIntervalMillis) return 0
        marks.recordAttempt(orgId, now)

        return try {
            var written = 0

            // The first read of a business is a full one, not a delta.
            //
            // A delta asks for what changed after a mark, and somebody who has
            // just joined has no mark — so it asks from zero and brings back
            // every document carrying a server stamp. Documents written before
            // that stamp existed are invisible to the range query, permanently: a
            // business with two years of customers behind it would hand its new
            // driver whatever had been touched lately and nothing else, with no
            // sign anything was missing. A full fetch reads the collections
            // outright and has no such blind spot.
            //
            // It runs here, inside the pull, rather than at the moment of joining
            // — which was where it went first, and it was refused every time. At
            // that moment the new membership exists only in this phone's outbox,
            // so the server does not yet know this person is allowed to read
            // anything. By the time a pull runs, the sync has already pushed the
            // membership, and the order comes out right without anybody
            // sequencing it by hand.
            if (mustSeed) {
                written += applier.apply(restore.fetchOrg(orgId))
                marks.markSeeded(orgId)
            }

            val page = restore.fetchChangesSince(orgId, marks.watermark(orgId))
            written += applier.apply(page.rows)
            // Only after the rows are safely down. Moving the mark first and
            // failing to write would skip those rows for good.
            marks.advance(orgId, page.watermark)
            written
        } catch (failure: Exception) {
            Log.d(TAG, "Could not read changes for $orgId", failure)
            0
        }
    }

    private companion object {
        const val TAG = "PullSync"
    }
}

/**
 * Where the next pass should resume from.
 *
 * Each collection is read separately, and any of them can fill its page while the
 * others come back complete. The mark has to respect the one that ran out: if jobs
 * stopped at ten o'clock with more behind them, it does not matter that rates were
 * read all the way to noon — resuming from noon would step over the rest of the
 * jobs and they would never be asked for again.
 *
 * So a truncated pass resumes from the earliest point any collection stopped at.
 * The complete collections get re-read over that span, which costs a few reads and
 * writes nothing new, because applying a row twice lands the same row.
 */
internal fun resumeFrom(since: Long, newest: Long, truncatedAt: List<Long>): Long =
    truncatedAt.minOrNull() ?: maxOf(since, newest)

