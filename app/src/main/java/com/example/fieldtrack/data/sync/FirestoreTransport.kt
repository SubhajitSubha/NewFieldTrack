package com.example.fieldtrack.data.sync

import com.example.fieldtrack.data.db.OutboxOp
import com.example.fieldtrack.data.db.SyncQueue
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sends the queue to Cloud Firestore.
 *
 * Laid out so that one organisation is one subtree, which is the shape the security
 * rules want: a person is a member of an organisation, and membership is the only
 * question a rule has to answer.
 *
 * ```
 * orgs/{orgId}                          the organisation itself
 * orgs/{orgId}/jobs/{jobId}
 * orgs/{orgId}/job_events/{eventId}
 * orgs/{orgId}/ledger_entries/{entryId}
 * orgs/{orgId}/customers/{customerId}
 * orgs/{orgId}/machines/{machineId}
 * orgs/{orgId}/memberships/{membershipId}
 * orgs/{orgId}/fuel_entries/{id}
 * orgs/{orgId}/breakdowns/{id}
 *
 * inviteCodes/{code}                    read before joining, so outside any org
 * ```
 *
 * **Firestore's own offline cache is switched off here, deliberately.** Left on, a
 * commit succeeds the moment it is written to the local cache, whether or not a
 * server ever hears about it — and this app would take that as permission to delete
 * the row from its outbox. The queue would empty itself on a phone with no signal
 * and the work would exist nowhere but a cache that a reinstall clears. With the
 * cache off, a commit succeeding means the server has it. Offline handling is this
 * app's job, and it is already done properly one layer up in [SyncEngine].
 */
class FirestoreTransport(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val deviceId: String,
) : SyncTransport {

    override val name = "Firebase"

    init {
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(memoryCacheSettings {})
            .build()
    }

    override suspend fun send(batch: List<SyncChange>): List<SyncOutcome> {
        if (batch.isEmpty()) return emptyList()

        signIn()?.let { problem -> return batch.map { problem } }

        // The whole batch in one atomic commit: either the server has all of it or
        // it has none of it, which is exactly the answer the engine is asking for.
        return try {
            commit(batch)
            batch.map { SyncOutcome.Accepted }
        } catch (timeout: TimeoutCancellationException) {
            // A timeout is cancellation to Kotlin but ordinary news here: no answer
            // came back, so the changes are still ours and go again later. Letting
            // this escape would cancel the drain instead of deferring it.
            batch.map { SyncOutcome.Deferred(NO_ANSWER) }
        } catch (failure: Exception) {
            val outcome = classify(failure)
            // A refusal is about one document, but a batch fails as a whole and will
            // not say which. Going through them one at a time costs a slow round of
            // writes exactly once, and tells the engine precisely which change is
            // stuck instead of blaming the first one in the batch.
            if (outcome is SyncOutcome.Refused) sendOneAtATime(batch)
            else batch.map { outcome }
        }
    }

    private suspend fun sendOneAtATime(batch: List<SyncChange>): List<SyncOutcome> =
        batch.map { change ->
            try {
                commit(listOf(change))
                SyncOutcome.Accepted
            } catch (timeout: TimeoutCancellationException) {
                SyncOutcome.Deferred(NO_ANSWER)
            } catch (failure: Exception) {
                classify(failure)
            }
        }

    private suspend fun commit(changes: List<SyncChange>) = withTimeout(TIMEOUT_MILLIS) {
        val batch = firestore.batch()
        for (change in changes) {
            val document = documentFor(change)
            when (change.operation) {
                OutboxOp.Upsert -> batch.set(document, change.asDocument())
                OutboxOp.Delete -> batch.delete(document)
            }
        }
        batch.commit().await()
    }

    /** Where this change lives in the tree above. */
    private fun documentFor(change: SyncChange): DocumentReference {
        // A few collections sit outside any organisation, because they are read by
        // somebody who is not in one yet and cannot name the subtree to look in.
        if (change.table in SyncQueue.ROOT_COLLECTIONS) {
            return firestore.collection(change.table).document(change.entityId)
        }
        val org = firestore.collection(ORGS).document(change.orgId)
        // The organisation row is the subtree's own root, not a document inside it.
        return if (change.table == SyncQueue.TABLE_ORGS) org
        else org.collection(change.table).document(change.entityId)
    }

    /**
     * The row, plus what is needed to recognise it arriving twice.
     *
     * The payload is unpacked into real fields rather than stored as one string, so
     * the security rules can read it and a later pull does not have to re-parse
     * every document to find out what it is.
     */
    private fun SyncChange.asDocument(): Map<String, Any?> =
        buildMap {
            val parsed = runCatching { Json.parseToJsonElement(payloadJson) }.getOrNull()
            if (parsed is JsonObject) putAll(parsed.toFirestoreMap())
            put(FIELD_SEQ, seq)
            put(FIELD_DEVICE, deviceId)
            put(FIELD_RECORDED_AT, recordedAt)
        }

    /** Signs in anonymously if needed; returns the reason we could not, or null. */
    private suspend fun signIn(): SyncOutcome? {
        if (auth.currentUser != null) return null
        return try {
            withTimeout(TIMEOUT_MILLIS) { auth.signInAnonymously().await() }
            null
        } catch (timeout: TimeoutCancellationException) {
            SyncOutcome.Deferred(NO_ANSWER)
        } catch (failure: Exception) {
            // Deferred rather than refused even when sign-in is genuinely
            // misconfigured: being unable to say who you are is nearly always a
            // network problem, and treating it as permanent would wrongly park the
            // queue as "needs attention" every time a phone leaves coverage.
            SyncOutcome.Deferred(failure.message ?: "Could not sign in")
        }
    }

    /**
     * Whether this is worth trying again.
     *
     * The default is to retry. A change parked as refused stops the whole queue
     * until a person looks at it, so that verdict is only reached on codes that
     * genuinely mean "this will never work": the write is malformed, or this device
     * is not allowed to make it.
     */
    private fun classify(failure: Exception): SyncOutcome {
        val message = failure.message ?: failure::class.java.simpleName
        val code = (failure as? FirebaseFirestoreException)?.code ?: return SyncOutcome.Deferred(message)
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.INVALID_ARGUMENT,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION,
            FirebaseFirestoreException.Code.OUT_OF_RANGE,
            FirebaseFirestoreException.Code.DATA_LOSS,
            FirebaseFirestoreException.Code.UNIMPLEMENTED -> SyncOutcome.Refused(message)

            else -> SyncOutcome.Deferred(message)
        }
    }

    private companion object {
        const val ORGS = "orgs"
        const val FIELD_SEQ = "_seq"
        const val FIELD_DEVICE = "_device"
        const val FIELD_RECORDED_AT = "_recordedAt"
        const val NO_ANSWER = "No answer from the server"

        /**
         * With the local cache off, a commit made with no signal does not fail — it
         * waits for a connection that may not come. This is the point at which the
         * app stops waiting and says so.
         */
        const val TIMEOUT_MILLIS = 30_000L
    }
}

/** Awaits a Play Services task without pulling in another dependency for it. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

private fun JsonObject.toFirestoreMap(): Map<String, Any?> =
    mapValues { (_, value) -> value.toFirestoreValue() }

private fun JsonElement.toFirestoreValue(): Any? = when (this) {
    // JsonNull is itself a primitive, so it has to be ruled out first.
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else {
        content.toBooleanStrictOrNull()
            ?: content.toLongOrNull()
            ?: content.toDoubleOrNull()
            ?: content
    }
    is JsonObject -> toFirestoreMap()
    is JsonArray -> map { it.toFirestoreValue() }
}
