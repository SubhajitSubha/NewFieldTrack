package com.example.fieldtrack.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.fieldtrack.FieldFlowApplication
import java.util.concurrent.TimeUnit
import com.example.fieldtrack.data.db.Clock

/**
 * Sends the queue up while nobody is looking.
 *
 * Constrained to a connected network, so the common case — a phone that was in a
 * field all morning and has just come back into coverage — is handled by the system
 * waking this, rather than by the app polling for signal it does not have.
 *
 * Runs in a fixed order, and the order is the design: find out whether this person
 * is still allowed to be here, send what they did, then find out what everybody
 * else did. Each step is allowed to fail on its own without stopping the others,
 * because no signal is the normal state in a field, not an error.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FieldFlowApplication ?: return Result.success()

        // Before sending, find out what the owner has decided. A permission
        // withdrawn or a driver removed takes effect on the next wake rather than
        // whenever somebody happens to reopen the app, which is what makes any of
        // it automatic. Failure here is ignored on purpose: no signal means they
        // keep this morning's permissions, and the rules refuse what they must not
        // do anyway.
        // Before anything else: an account turned off takes every business away
        // at once, so there is no point asking one of them about permissions.
        val personId = app.sessionStore.personIdOrNull()
        if (personId != null) {
            runCatching {
                if (app.remoteRestore.isAccountDisabled(personId)) {
                    app.sessionStore.lockOutDisabledAccount()
                    return Result.success()
                }
                app.sessionStore.clearAccountDisabled()
            }
        }

        runCatching {
            app.sessionStore.refreshMembership(app.remoteRestore, app.restoreApplier)
        }

        val status = app.syncEngine.sync()

        // Last, and only once this phone's own changes have gone up. A pull that
        // ran first would be answered with a server copy of rows this device is
        // still holding newer versions of — harmless, since the applier refuses
        // to overwrite unsent work, but a wasted read every time.
        //
        // Read after the membership check, too: somebody removed a minute ago has
        // no business pulling that org's customers down one last time.
        // load() rather than current(): current() only answers once something has
        // already resolved the session, which in practice means the UI. A worker
        // woken on its own — the six-hourly backstop, or a connection returning
        // while the app is shut — would find it null and skip the pull without a
        // word, so the one case this exists for was the one case it did nothing.
        // load() is idempotent and returns the session it resolves.
        runCatching { app.sessionStore.load() }.getOrNull()?.let { session ->
            // How much this phone is still holding for this business, written
            // onto its own membership so the owner can see it before acting on
            // somebody. After the send, so the number is the leftover rather
            // than the backlog we just cleared.
            runCatching {
                app.syncTransport.reportUnsent(
                    orgId = session.orgId,
                    personId = session.personId,
                    count = app.db.outboxDao().pendingCountFor(session.orgId),
                    at = Clock.System.now(),
                )
            }

            runCatching { app.pullSync.pull(session.orgId) }
        }
        return when (status.stage) {
            // Nothing left, or nowhere to send it. Either way, not a failure.
            SyncStage.Idle, SyncStage.NoBackend -> Result.success()

            // Could not get through for want of a network.
            //
            // Deliberately NOT a retry. This worker already runs under a
            // connected-network constraint, so WorkManager will not start it
            // without one; asking for a retry on top adds exponential backoff for
            // a condition the constraint already covers, and the two compound.
            //
            // They compounded badly. A morning in a field with no signal pushed
            // the backoff out past a quarter of an hour, and because the one-off
            // request is unique, every later trigger was dropped in favour of the
            // one already waiting. Coming back into coverage and opening the app
            // — the exact case this is built for — then did nothing at all, for
            // as long as the backoff had grown to. The queue is durable and the
            // triggers are many; being told "no network" is not a failure worth
            // punishing the next attempt for.
            SyncStage.Offline -> Result.success()

            // A real attempt that did not get through. Worth backing off.
            SyncStage.Waiting -> Result.retry()

            // The server refused something. Waking up again changes nothing — this
            // waits for a person, and the backup screen is where they will see it.
            SyncStage.Blocked -> Result.success()

            SyncStage.Sending -> Result.success()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val PERIODIC = "fieldtrack.sync.periodic"
        private const val ONE_OFF = "fieldtrack.sync.now"

        private val onConnection = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Starts the background schedule, if there is anywhere to send work.
         *
         * Without a backend this does nothing at all. Scheduling a worker that can
         * only ever wake up, find no transport and go back to sleep would burn the
         * user's battery to accomplish nothing.
         */
        fun start(context: Context, hasBackend: Boolean) {
            if (!hasBackend) return
            // Never allowed to bring the app down with it. This runs during
            // Application.onCreate, and WorkManager is initialised by a provider
            // that is not guaranteed to have run — under test it has not. Backup
            // failing to be scheduled costs a later upload; an exception here costs
            // the user the whole app.
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                        .setConstraints(onConnection)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .build(),
                )
            }.onFailure { Log.w(TAG, "Could not schedule background backup", it) }
        }

        /**
         * Runs as soon as there is a network, for when something has just changed.
         *
         * [force] replaces whatever is already queued instead of standing behind
         * it, which resets any backoff that has built up. Used when a person did
         * something — opening the app — rather than when a row was merely saved.
         * A save can wait for the run already pending; somebody looking at
         * "7 changes waiting to go up" cannot be told to come back in an hour.
         *
         * Saves keep [ExistingWorkPolicy.KEEP] on purpose: during a working
         * morning they arrive constantly, and replacing on every one would cancel
         * the send already in flight over and over, so nothing would ever finish.
         */
        fun runNow(context: Context, force: Boolean = false) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_OFF,
                    if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(onConnection)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .build(),
                )
            }.onFailure { Log.w(TAG, "Could not ask for a backup now", it) }
        }
    }
}
