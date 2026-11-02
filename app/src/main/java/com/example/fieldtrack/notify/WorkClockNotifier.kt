package com.example.fieldtrack.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fieldtrack.MainActivity
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.feature.spokenDuration

/**
 * The clock, outside the app.
 *
 * A driver locks the phone and puts it in a pocket the moment the tractor moves,
 * and that is exactly when a running clock gets forgotten. So the job sits in the
 * notification shade for as long as it is open: the clock ticking (the system
 * counts it, so nothing here has to stay awake), and Pause one press away
 * without unlocking into the app.
 *
 * Silent. It is there to be glanced at, not to interrupt anybody. The one that
 * does make a sound is the evening reminder, on a channel of its own, so a person
 * who turns that off has not also turned off the clock.
 */
class WorkClockNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CLOCK,
                context.getString(R.string.notify_channel_clock),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notify_channel_clock_note)
                setShowBadge(false)
            }
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.notify_channel_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_reminder_note)
            }
        )
    }

    /** Whether anything posted here would actually be seen. */
    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Puts the open job in the shade, or takes it away when there is none. */
    fun showClock(job: JobEntity?, customerName: String) {
        if (job == null || (job.status != JobStatus.Running && job.status != JobStatus.Paused)) {
            manager.cancel(ID_CLOCK)
            manager.cancel(ID_REMINDER)
            return
        }
        if (!canPost()) return

        val running = job.status == JobStatus.Running
        val builder = NotificationCompat.Builder(context, CHANNEL_CLOCK)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(customerName)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openJob(job.id))
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))

        if (running) {
            // The system draws the moving clock from this one number: the moment
            // it would have read zero. Banked time before the last resume is
            // simply that much earlier.
            val zero = (job.runningSince ?: System.currentTimeMillis()) - job.elapsedSeconds * 1000
            builder
                .setContentText(context.getString(R.string.notify_clock_running, job.workType))
                .setUsesChronometer(true)
                .setWhen(zero)
                .setShowWhen(true)
                // Running is the state that must not be swiped away and forgotten.
                .setOngoing(true)
                .addAction(0, context.getString(R.string.home_job_pause), action(ClockActionReceiver.PAUSE, job))
        } else {
            builder
                .setContentText(
                    context.getString(
                        R.string.notify_clock_paused,
                        job.workType,
                        // With seconds: "0:01" alone reads as a minute past midnight.
                        duration(job.elapsedSeconds, withSeconds = true),
                    )
                )
                .setShowWhen(false)
                .setOngoing(false)
                .addAction(0, context.getString(R.string.home_job_resume), action(ClockActionReceiver.RESUME, job))
            // A paused clock is not running on into the night.
            manager.cancel(ID_REMINDER)
        }
        post(ID_CLOCK, builder)
    }

    /**
     * "Still working for Ramesh?" — the evening question, asked once.
     *
     * The one notification here that makes a sound, because by the time it is
     * needed the phone has been in a pocket for hours.
     */
    fun remindIfRunning(job: JobEntity?, customerName: String, now: Long) {
        if (job == null || job.status != JobStatus.Running) {
            manager.cancel(ID_REMINDER)
            return
        }
        if (!canPost()) return
        val running = job.elapsedSeconds + ((now - (job.runningSince ?: now)).coerceAtLeast(0L) / 1000)
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(context.getString(R.string.notify_reminder_title, customerName))
            .setContentText(context.getString(R.string.notify_reminder_body, spokenDuration(context, running)))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notify_reminder_body, spokenDuration(context, running)))
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openJob(job.id))
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .addAction(0, context.getString(R.string.home_job_pause), action(ClockActionReceiver.PAUSE, job))
            .addAction(0, context.getString(R.string.home_job_open), openJob(job.id))
        post(ID_REMINDER, builder)
    }

    fun clearAll() {
        manager.cancel(ID_CLOCK)
        manager.cancel(ID_REMINDER)
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        // Checked again at the moment of posting: permission can be withdrawn in
        // system settings between the check above and here.
        if (!canPost()) return
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Withdrawn in the instant between. Nothing to do; the job is safe.
        }
    }

    private fun openJob(jobId: String): PendingIntent = PendingIntent.getActivity(
        context,
        jobId.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_JOB, jobId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(which: String, job: JobEntity): PendingIntent = PendingIntent.getBroadcast(
        context,
        (which + job.id).hashCode(),
        Intent(context, ClockActionReceiver::class.java)
            .setAction(which)
            .putExtra(ClockActionReceiver.EXTRA_ORG, job.orgId)
            .putExtra(ClockActionReceiver.EXTRA_JOB, job.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_CLOCK = "work_clock"
        const val CHANNEL_REMINDER = "clock_reminders"
        private const val ID_CLOCK = 4101
        private const val ID_REMINDER = 4102
    }
}
