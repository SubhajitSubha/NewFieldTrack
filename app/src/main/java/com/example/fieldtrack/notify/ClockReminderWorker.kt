package com.example.fieldtrack.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.fieldtrack.FieldFlowApplication
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Wakes once each evening and asks about any clock still running.
 *
 * Eight o'clock because that is after any ordinary working day has ended and
 * before most people are asleep — the last moment the question can still be
 * answered from memory rather than reconstructed the next morning.
 *
 * Scheduled every day whether or not a job is open, because the job may be
 * started on a phone the app is then never reopened on; the check itself costs
 * one database read and posts nothing when there is nothing to say.
 *
 * @see com.example.fieldtrack.data.sync.SyncWorker
 */
class ClockReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FieldFlowApplication ?: return Result.success()
        runCatching { app.workClock.remindIfStillRunning() }
        return Result.success()
    }

    companion object {
        private const val NAME = "evening-clock-check"
        private const val HOUR = 20
        private const val TAG = "ClockReminder"

        /**
         * Idempotent: an evening already booked keeps its time.
         *
         * Never allowed to bring the app down, for the reason SyncWorker.start
         * gives: this runs in Application.onCreate, before WorkManager is
         * certain to be ready. A missed reminder costs one evening's question; a throw
         * here costs the whole app.
         */
        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<ClockReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(millisUntilNext(HOUR), TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }.onFailure { Log.w(TAG, "Could not schedule the evening clock check", it) }
        }

        internal fun millisUntilNext(hour: Int, now: Long = System.currentTimeMillis()): Long {
            val next = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now
        }
    }
}
