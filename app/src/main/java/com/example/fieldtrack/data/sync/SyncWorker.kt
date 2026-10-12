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

/**
 * Sends the queue up while nobody is looking.
 *
 * Constrained to a connected network, so the common case — a phone that was in a
 * field all morning and has just come back into coverage — is handled by the system
 * waking this, rather than by the app polling for signal it does not have.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FieldFlowApplication ?: return Result.success()
        val status = app.syncEngine.sync()
        return when (status.stage) {
            // Nothing left, or nowhere to send it. Either way, not a failure.
            SyncStage.Idle, SyncStage.NoBackend -> Result.success()

            // Could not get through. WorkManager's own backoff decides when the
            // next wake is; the engine's per-row backoff then decides whether the
            // row is due once we are awake.
            SyncStage.Offline, SyncStage.Waiting -> Result.retry()

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

        /** Runs as soon as there is a network, for when something has just changed. */
        fun runNow(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_OFF,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(onConnection)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .build(),
                )
            }.onFailure { Log.w(TAG, "Could not ask for a backup now", it) }
        }
    }
}
