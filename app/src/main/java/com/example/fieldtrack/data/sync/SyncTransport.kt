package com.example.fieldtrack.data.sync

import com.example.fieldtrack.data.db.OutboxOp
import com.example.fieldtrack.data.db.entity.OutboxEntity

/**
 * When the server wrote a document, stamped by the server on every write.
 *
 * Lives here because both halves of the sync have to agree on it to the letter:
 * the transport writes it, and a pull orders and pages on it. The two reading
 * different strings would not fail — the pull would simply find nothing, for ever.
 */
const val FIELD_SERVER_AT = "_serverAt"


/**
 * One local change, on its way out.
 *
 * [seq] is this device's own number for the change and travels all the way to the
 * server. An acknowledgement lost after the write already landed is not a rare case
 * on a patchy rural connection — it is the normal way a sync fails — so the same
 * change will arrive twice and the far end needs something to recognise it by.
 */
data class SyncChange(
    val seq: Long,
    val orgId: String,
    val table: String,
    val entityId: String,
    val operation: OutboxOp,
    val payloadJson: String,
    val recordedAt: Long,
    /** How many times this one has already been tried. */
    val attempts: Int,
)

/** What the far end did with a single change. */
sealed interface SyncOutcome {

    /** Stored. The change leaves the queue for good. */
    data object Accepted : SyncOutcome

    /**
     * Not now: no signal, server down, request timed out.
     *
     * The change stays exactly where it is and goes again later. This is the
     * ordinary case, not an error — a tractor in the middle of a field is the
     * situation this app was built for.
     */
    data class Deferred(val reason: String) : SyncOutcome

    /**
     * The server will never accept this one — the payload is malformed, or this
     * device is no longer allowed to write to that organisation.
     *
     * The change still stays in the queue. Dropping it would be the app quietly
     * deciding that a day's work, or money somebody collected in cash, was not
     * worth keeping; no answer from a server makes that the app's call. It holds
     * the queue and gets reported so that a person decides what happens to it.
     */
    data class Refused(val reason: String) : SyncOutcome
}

/**
 * Where changes go. The only thing in this app that knows a server exists.
 *
 * Everything above it — what order changes leave in, when a failure is retried,
 * what the user is told — is decided on the device and tested with no network
 * anywhere near it.
 *
 * **There is no implementation of this yet, and that is the honest state of the
 * app.** The `google-services.json` checked in here points at `dummy-project-id`:
 * a placeholder, not a project. Until a real backend exists, [SyncEngine] is
 * constructed with no transport, the queue holds everything, and the backup screen
 * says so plainly rather than showing a spinner that can never finish.
 */
interface SyncTransport {

    /** Shown to the user as where their work is being backed up to. */
    val name: String

    /**
     * Sends changes in the order given and returns one outcome per change, in that
     * same order. Implementations must not reorder: a job has to reach the server
     * before the events that belong to it do.
     */
    suspend fun send(batch: List<SyncChange>): List<SyncOutcome>

    /**
     * Says how much this phone is still carrying, on this phone's own membership.
     *
     * Not part of [send], and deliberately outside the outbox. A queued beacon
     * would be one more unsent change, so the count it reports could never fall
     * to zero and each sync would raise another — a loop that writes for ever
     * and never tells the truth. Written directly instead, best effort: it is a
     * description of a handset, not a record of anybody's work, and losing one
     * costs nothing but a staler number on the owner's screen.
     *
     * Default does nothing, for builds with nowhere to send it.
     */
    suspend fun reportUnsent(orgId: String, personId: String, count: Int, at: Long) = Unit
}

/** Reads an outbox row as the change it represents. */
internal fun OutboxEntity.asChange() = SyncChange(
    seq = seq,
    orgId = orgId,
    table = tableName,
    entityId = entityId,
    operation = operation,
    payloadJson = payloadJson,
    recordedAt = createdAt,
    attempts = attempts,
)
