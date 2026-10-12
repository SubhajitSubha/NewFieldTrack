package com.example.fieldtrack.data.sync

import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.dao.OutboxDao
import com.example.fieldtrack.data.db.entity.OutboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the queue stands, in terms the backup screen can say out loud. */
enum class SyncStage {
    /** Nothing waiting. Everything on this phone is also on the server. */
    Idle,

    /** A run is going on right now. */
    Sending,

    /** Changes are waiting because the server could not be reached. */
    Offline,

    /** Backing off after a failure; the next try is at [SyncStatus.retryAtMillis]. */
    Waiting,

    /** The server refused a change. Retrying will not help; a person has to look. */
    Blocked,

    /** Nowhere to send anything. No backend is configured. */
    NoBackend,
}

data class SyncStatus(
    val stage: SyncStage = SyncStage.Idle,
    /** Changes still in the queue. */
    val pending: Int = 0,
    /** How many went up in the run that just finished. */
    val sent: Int = 0,
    /** When the queue was last fully drained, across restarts. */
    val lastSuccessAt: Long? = null,
    /** Why it stopped, in the server's words. */
    val error: String? = null,
    val retryAtMillis: Long? = null,
    /** The change holding the queue up, so the user can decide about it. */
    val blockedSeq: Long? = null,
    /** Changes the user has already set aside. Kept, never sent. */
    val setAside: Int = 0,
) {
    /** True when there is work on this phone the server has never seen. */
    val hasUnsentWork: Boolean get() = pending > 0
}

/** Survives process death so "last backup" is not reset by closing the app. */
interface SyncJournal {
    var lastSuccessAt: Long?

    class InMemory(override var lastSuccessAt: Long? = null) : SyncJournal
}

/**
 * Drains the outbox, in order, without ever losing a row.
 *
 * The rules, and why they are these rules:
 *
 * **Strictly in order, and it stops at the first change that does not go through.**
 * The queue is not a bag of independent facts. A job event has to land after the job
 * it belongs to, and the older of two edits to the same row must not be applied
 * after the newer one. Letting later changes jump a stuck one would produce a server
 * holding a job's events but not the job, or a rate that silently reverted. Stopping
 * costs a delay; carrying on costs correctness.
 *
 * **Nothing is ever discarded.** Not after a hundred failures, not when the server
 * says the change is invalid. Every row in here is work somebody did or money
 * somebody handed over, recorded on a phone that may be the only copy. A change that
 * cannot go up holds the queue and gets reported; deleting it to unblock the queue
 * would be trading the user's records for a tidier status line.
 *
 * **Backoff is held per row, in the row.** [OutboxEntity.attempts] and
 * `lastAttemptAt` are already persisted, so a device that keeps waking up with no
 * signal does not retry harder each time it wakes.
 *
 * Known limitation: one queue is shared by every organisation on the device, so a
 * driver working for two businesses has both behind one stuck change. That only
 * bites when a single org is refused while others are fine, which cannot happen
 * until there is a server that knows about orgs at all.
 */
class SyncEngine(
    private val outbox: OutboxDao,
    /** Null until a real backend exists. See [SyncTransport]. */
    private val transport: SyncTransport?,
    private val clock: Clock = Clock.System,
    private val journal: SyncJournal = SyncJournal.InMemory(),
    private val batchSize: Int = BATCH_SIZE,
) {

    private val _status = MutableStateFlow(
        SyncStatus(
            stage = if (transport == null) SyncStage.NoBackend else SyncStage.Idle,
            lastSuccessAt = journal.lastSuccessAt,
        )
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Where the user's work is going, or null when that is nowhere. */
    val destination: String? get() = transport?.name

    // One run at a time. The periodic worker and the user tapping "Back up now"
    // arrive on different threads, and two drains at once would send the same rows
    // twice and race each other to delete them.
    private val running = Mutex()

    /**
     * Sends everything waiting, stopping at the first change that does not go.
     *
     * @param force ignores the backoff delay on the next change, for when the user
     *   has asked for this directly and is watching.
     */
    suspend fun sync(force: Boolean = false): SyncStatus = running.withLock { drain(force) }

    /**
     * Lets the queue past a change the server will not take.
     *
     * The deliberate act the [SyncStage.Blocked] state was always waiting for. A
     * refusal that can never succeed would otherwise stop this phone syncing for
     * good, and the alternative — quietly dropping it — is the one thing this engine
     * will not do. So the row is set aside with its payload intact and the rest goes
     * on, and the screen keeps saying how many are sitting there.
     */
    suspend fun setAside(seq: Long): SyncStatus = running.withLock {
        outbox.setAside(seq, clock.now())
        publish(SyncStage.Idle)
    }

    private suspend fun drain(force: Boolean): SyncStatus {
        val transport = transport ?: return publish(SyncStage.NoBackend)
        _status.value = _status.value.copy(stage = SyncStage.Sending, error = null)

        var sent = 0
        while (true) {
            val batch = outbox.nextBatch(batchSize)
            if (batch.isEmpty()) {
                // A backup is something the server did, not something this device
                // observed about itself. Two conditions, both needed: the queue is
                // drained, and this run actually delivered something to get it
                // there. An empty queue on its own proves nothing — a phone that
                // has never recorded any work has an empty queue too, and so does
                // one whose queue was cleared by other means. Claiming "backed up
                // just now" in either case is the single lie this screen must never
                // tell, because it is the one that stops somebody worrying at
                // exactly the moment they should.
                if (sent > 0) journal.lastSuccessAt = clock.now()
                return publish(SyncStage.Idle, sent)
            }

            val head = batch.first()
            val readyAt = readyAt(head)
            if (!force && readyAt > clock.now()) {
                return publish(
                    stage = SyncStage.Waiting,
                    sent = sent,
                    error = head.lastError,
                    retryAt = readyAt,
                )
            }

            val outcomes = try {
                transport.send(batch.map { it.asChange() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A transport that throws is the same news as one that returns
                // Deferred: the change is still here and goes again later.
                outbox.recordFailure(head.seq, clock.now(), failure.describe())
                return publish(SyncStage.Offline, sent, failure.describe(), readyAt(head, bump = true))
            }

            var accepted = 0
            for ((index, row) in batch.withIndex()) {
                when (val outcome = outcomes.getOrNull(index)) {
                    // Short reply. Whatever was not answered for is still queued,
                    // so take what we were given and ask again.
                    null -> break

                    SyncOutcome.Accepted -> {
                        outbox.remove(row)
                        accepted++
                    }

                    is SyncOutcome.Deferred -> {
                        outbox.recordFailure(row.seq, clock.now(), outcome.reason)
                        return publish(
                            stage = SyncStage.Offline,
                            sent = sent + accepted,
                            error = outcome.reason,
                            retryAt = readyAt(row, bump = true),
                        )
                    }

                    is SyncOutcome.Refused -> {
                        outbox.recordFailure(row.seq, clock.now(), outcome.reason)
                        return publish(
                            stage = SyncStage.Blocked,
                            sent = sent + accepted,
                            error = outcome.reason,
                            blockedSeq = row.seq,
                        )
                    }
                }
            }
            sent += accepted

            if (accepted == 0) {
                // The transport answered without accepting, deferring or refusing
                // anything — it is broken. Record it against the head row so the
                // backoff grows, and stop rather than spin on the same batch.
                outbox.recordFailure(head.seq, clock.now(), EMPTY_REPLY)
                return publish(SyncStage.Offline, sent, EMPTY_REPLY, readyAt(head, bump = true))
            }
        }
    }

    private suspend fun publish(
        stage: SyncStage,
        sent: Int = 0,
        error: String? = null,
        retryAt: Long? = null,
        blockedSeq: Long? = null,
    ): SyncStatus {
        val status = SyncStatus(
            stage = stage,
            pending = outbox.pendingCount(),
            sent = sent,
            lastSuccessAt = journal.lastSuccessAt,
            error = error,
            retryAtMillis = retryAt,
            blockedSeq = blockedSeq,
            setAside = outbox.setAsideCount(),
        )
        _status.value = status
        return status
    }

    /** When this row may be tried again. */
    private fun readyAt(row: OutboxEntity, bump: Boolean = false): Long {
        val attempts = row.attempts + if (bump) 1 else 0
        // Never tried, so there is nothing to wait for.
        val lastAttempt = (if (bump) clock.now() else row.lastAttemptAt) ?: return 0L
        return lastAttempt + backoffMillis(attempts)
    }

    companion object {
        const val BATCH_SIZE = 50

        private const val EMPTY_REPLY = "Server replied without accepting anything"

        /**
         * How long to wait after a given number of failures.
         *
         * Short at first, because the common failure is a few seconds of lost
         * signal and the user may still be looking at the screen. Capped at an
         * hour, because beyond that the phone is somewhere without coverage and
         * retrying every minute only costs battery.
         */
        private val BACKOFF_STEPS = longArrayOf(
            30_000L,         // 30s
            60_000L,         // 1m
            2 * 60_000L,     // 2m
            5 * 60_000L,     // 5m
            15 * 60_000L,    // 15m
            30 * 60_000L,    // 30m
            60 * 60_000L,    // 1h
        )

        fun backoffMillis(attempts: Int): Long = when {
            attempts <= 0 -> 0L
            attempts >= BACKOFF_STEPS.size -> BACKOFF_STEPS.last()
            else -> BACKOFF_STEPS[attempts - 1]
        }
    }
}

/** A message worth storing, even from an exception that did not bring one. */
private fun Exception.describe(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
