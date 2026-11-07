package com.example.fieldtrack.data.update

import android.content.Context
import com.example.fieldtrack.data.sync.Firestore
import com.example.fieldtrack.data.sync.SyncChange
import com.example.fieldtrack.data.sync.SyncOutcome
import com.example.fieldtrack.data.sync.SyncTransport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** What the server says about versions: the oldest still allowed, and the newest there is. */
data class VersionRules(val minCode: Long, val latestCode: Long)

/** What an installed version should do about it. */
enum class UpdateNeed {
    /** Nothing to say. */
    Current,

    /** A newer version exists; the person can carry on and be reminded later. */
    Available,

    /**
     * This version is too old to share data safely. It stops sending and asking, and
     * says so, until it is updated. Everything already on the phone stays where it is.
     */
    Required,
}

fun needFor(installedCode: Long, rules: VersionRules?, snoozed: Boolean): UpdateNeed = when {
    rules == null -> UpdateNeed.Current
    rules.minCode > installedCode -> UpdateNeed.Required
    rules.latestCode > installedCode && !snoozed -> UpdateNeed.Available
    else -> UpdateNeed.Current
}

/** Where the version rules are read from. */
interface VersionRulesSource {
    /** Null when they cannot be read now; the last known ones then stay in force. */
    suspend fun fetch(): VersionRules?
}

/** A document the owner of the project edits by hand in the console: `appConfig/android`. */
class FirestoreVersionRules(
    private val firestore: FirebaseFirestore = Firestore.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : VersionRulesSource {
    override suspend fun fetch(): VersionRules? = try {
        withTimeout(TIMEOUT_MILLIS) {
            // The rules want a signed-in reader. Nobody is asked to sign in for this.
            if (auth.currentUser == null) null
            else {
                val doc = firestore.collection(COLLECTION).document(DOCUMENT).get().await()
                if (!doc.exists()) null
                else VersionRules(
                    minCode = doc.getLong("minVersionCode") ?: 0L,
                    latestCode = doc.getLong("latestVersionCode") ?: 0L,
                )
            }
        }
    } catch (failure: Exception) {
        null
    }

    companion object {
        const val COLLECTION = "appConfig"
        const val DOCUMENT = "android"
        private const val TIMEOUT_MILLIS = 15_000L
    }
}

/**
 * Knows whether this install is too old, and remembers it.
 *
 * What the server last said is kept on the phone, so a version found to be too old stays
 * blocked without a connection rather than the block lifting the moment the signal does.
 * A failed read changes nothing: no answer is never read as "all clear" or "blocked".
 *
 * "Later" hides the nudge for a few days or until something newer comes out, whichever is
 * first; a required update cannot be put off, because that is the whole point of it.
 */
class UpdateGate(
    context: Context,
    private val installedCode: Long,
    private val source: VersionRulesSource,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun saved() = if (prefs.contains(KEY_MIN)) VersionRules(prefs.getLong(KEY_MIN, 0), prefs.getLong(KEY_LATEST, 0)) else null

    private fun snoozed(rules: VersionRules?) =
        rules != null && prefs.getLong(KEY_LATER_FOR, -1L) == rules.latestCode && now() < prefs.getLong(KEY_LATER_UNTIL, 0L)

    private val _need = MutableStateFlow(saved().let { needFor(installedCode, it, snoozed(it)) })
    val need: StateFlow<UpdateNeed> = _need.asStateFlow()

    val required: Boolean get() = _need.value == UpdateNeed.Required

    /**
     * Asks the server; best effort and quiet. Not more than every few hours, since every
     * change a person makes wakes the sync and none of them needs a read to be told the
     * same thing; a blocked install asks every time, so it is let back in as soon as it can be.
     */
    suspend fun refresh() {
        if (!required && now() - prefs.getLong(KEY_FETCHED, 0L) < STALE_MILLIS) return
        val rules = source.fetch() ?: return
        prefs.edit().putLong(KEY_MIN, rules.minCode).putLong(KEY_LATEST, rules.latestCode).putLong(KEY_FETCHED, now()).apply()
        _need.value = needFor(installedCode, rules, snoozed(rules))
    }

    /** "Not now" on the nudge. */
    fun later() {
        val rules = saved() ?: return
        prefs.edit().putLong(KEY_LATER_FOR, rules.latestCode).putLong(KEY_LATER_UNTIL, now() + LATER_MILLIS).apply()
        _need.value = needFor(installedCode, rules, snoozed(rules))
    }

    companion object {
        const val PREFS = "fieldtrack.update"
        private const val KEY_MIN = "min"
        private const val KEY_LATEST = "latest"
        private const val KEY_FETCHED = "fetched"
        private const val STALE_MILLIS = 6L * 60 * 60 * 1000
        private const val KEY_LATER_FOR = "laterFor"
        private const val KEY_LATER_UNTIL = "laterUntil"
        const val LATER_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}

/**
 * Holds every change back, quietly, while this install is too old to be trusted with
 * the shared records. They stay in the queue exactly as they are and go when the app is
 * updated; nothing is lost, and nothing is sent in a shape the server no longer expects.
 */
class GatedTransport(
    private val inner: SyncTransport,
    private val blocked: () -> Boolean,
    /** The practice business lives on this phone alone: whatever it queues is taken as sent, and goes nowhere. */
    private val isSample: (String) -> Boolean = { false },
) : SyncTransport by inner {
    override suspend fun send(batch: List<SyncChange>): List<SyncOutcome> {
        if (blocked()) return batch.map { SyncOutcome.Deferred("Update needed") }
        val real = batch.filter { !isSample(it.orgId) }
        if (real.size == batch.size) return inner.send(batch)
        val sent = if (real.isEmpty()) emptyList() else inner.send(real)
        val iterator = sent.iterator()
        // One outcome per change, in the order given, as the engine requires.
        return batch.map { if (isSample(it.orgId)) SyncOutcome.Accepted else iterator.next() }
    }
}
