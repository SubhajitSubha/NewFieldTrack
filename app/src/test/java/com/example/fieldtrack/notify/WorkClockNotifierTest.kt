package com.example.fieldtrack.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What lands in the notification shade.
 *
 * Against the real notification manager Robolectric provides, so these are the
 * notifications a phone would actually hold — not a description of them.
 */
// A plain Application, not the app's own. FieldFlowApplication starts the real clock
// watcher, which clears the notification shade whenever nobody has an open job —
// correct in the app, but in a test it cleared notifications this class had just
// posted, at whatever moment a busy machine happened to run it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkClockNotifierTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notifier = WorkClockNotifier(app)
    private val hour = 60L * 60 * 1000
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.createChannels()
    }

    private fun job(status: JobStatus, runningSince: Long?, elapsed: Long = 0) = JobEntity(
        id = "job-1", orgId = "org-a", customerId = "c1", workType = "Rotavator",
        billingMode = BillingMode.PerHour, unit = MeasureUnit.Hour, rateRupees = 1_000,
        status = status, runningSince = runningSince, elapsedSeconds = elapsed,
        workedAt = now - 10 * hour, recordedAt = now - 10 * hour,
        createdAt = now - 10 * hour, updatedAt = now - 10 * hour,
    )

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    @Test
    fun `a running clock sits in the shade, counting, and cannot be swiped away`() {
        notifier.showClock(job(JobStatus.Running, runningSince = now - 2 * hour, elapsed = 600), "Ramesh Singh")

        val clock = posted().single()
        assertEquals(WorkClockNotifier.CHANNEL_CLOCK, clock.channelId)
        assertTrue(clock.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        // The moment it would have read zero: two hours ago, less the ten banked minutes.
        assertEquals(now - 2 * hour - 600_000, clock.`when`)
        assertTrue(clock.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Pause", clock.actions.single().title)
    }

    @Test
    fun `paused, it stops counting and offers to carry on`() {
        notifier.showClock(job(JobStatus.Paused, runningSince = null, elapsed = 90), "Ramesh Singh")

        val clock = posted().single()
        assertEquals(false, clock.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals("Resume", clock.actions.single().title)
        assertEquals(
            "Rotavator · paused with 0:01:30 on the clock",
            clock.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `the evening question names the farmer and says the hours in words`() {
        notifier.remindIfRunning(job(JobStatus.Running, runningSince = now - 11 * hour - 40 * 60_000), "Ramesh Singh", now)

        val reminder = posted().single()
        assertEquals(WorkClockNotifier.CHANNEL_REMINDER, reminder.channelId)
        assertEquals(
            "Still working for Ramesh Singh?",
            reminder.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertTrue(
            reminder.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                .startsWith("The clock has run 11 h 40 min."),
        )
        assertEquals(listOf("Pause", "Open job"), reminder.actions.map { it.title.toString() })
    }

    @Test
    fun `no open job, nothing in the shade`() {
        notifier.showClock(job(JobStatus.Running, runningSince = now - hour), "Ramesh Singh")
        notifier.remindIfRunning(job(JobStatus.Running, runningSince = now - 9 * hour), "Ramesh Singh", now)
        assertEquals(2, posted().size)

        notifier.showClock(null, "")
        assertEquals(0, posted().size)
    }

    @Test
    fun `without permission nothing is posted and nothing breaks`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.showClock(job(JobStatus.Running, runningSince = now - hour), "Ramesh Singh")
        assertEquals(0, posted().size)
    }
}
