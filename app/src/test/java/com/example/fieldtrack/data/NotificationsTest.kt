package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.notify.PushEvent
import com.example.fieldtrack.notify.PushKind
import com.example.fieldtrack.notify.PushSettings
import com.example.fieldtrack.notify.PushText
import com.example.fieldtrack.notify.ReminderKind
import com.example.fieldtrack.notify.ReminderSettings
import com.example.fieldtrack.notify.ReminderText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the phone says without being opened, and who gets to switch it off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationsTest {

    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PushSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(ReminderSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `a message from the server is understood by its event, and an unknown one is ignored`() {
        assertEquals(PushEvent.BookingNew, PushEvent.parse(mapOf("event" to "booking_new", "orgId" to "o")))
        assertEquals(PushKind.Payment, PushEvent.parse(mapOf("event" to "payment_recorded"))!!.kind)
        assertNull("a newer server may send something this version has never heard of", PushEvent.parse(mapOf("event" to "tractor_sold")))
        assertNull(PushEvent.parse(emptyMap()))
    }

    @Test fun `every event has words, and none carries a figure`() {
        for (event in PushEvent.entries) {
            val title = PushText.title(context, event)
            assertTrue(title.isNotBlank())
            assertFalse("$event", title.any { it.isDigit() })
        }
    }

    @Test fun `instant news is on until a person turns a kind off, and each kind is its own switch`() {
        val settings = PushSettings(context)
        assertTrue(PushKind.entries.all { settings.isOn(it) })
        settings.set(PushKind.Payment, false)
        val again = PushSettings(context)
        assertFalse(again.isOn(PushKind.Payment))
        assertTrue(again.isOn(PushKind.Booking))
        assertTrue(again.isOn(PushKind.Job))
    }

    @Test fun `reminders are all off until asked for, and remember the hour chosen`() {
        val settings = ReminderSettings(context)
        assertFalse(settings.any)
        settings.set(ReminderKind.Plan, true)
        settings.setEvening(20)
        settings.setMorning(7)
        val again = ReminderSettings(context)
        assertTrue(again.isOn(ReminderKind.Plan))
        assertFalse(again.isOn(ReminderKind.Dues))
        assertEquals(20, again.hourOf(ReminderKind.Plan))
        assertEquals("money and services are told in the morning", 7, again.hourOf(ReminderKind.Dues))
        assertEquals(7, again.hourOf(ReminderKind.Service))
    }

    @Test fun `a reminder says nothing when there is nothing to say`() {
        assertNull(ReminderText.plan(context, 0))
        assertNull(ReminderText.dues(context, 0))
        assertNull(ReminderText.service(context, emptyList()))
    }

    @Test fun `a reminder counts, in the right number, and names a single machine`() {
        assertEquals("1 job is booked for tomorrow.", ReminderText.plan(context, 1)!!.second)
        assertEquals("3 jobs are booked for tomorrow.", ReminderText.plan(context, 3)!!.second)
        assertEquals("2 farmers to ask for money today.", ReminderText.dues(context, 2)!!.second)
        assertEquals("Mahindra 575 is due for its service.", ReminderText.service(context, listOf("Mahindra 575"))!!.second)
        assertEquals("2 machines are due for service.", ReminderText.service(context, listOf("A", "B"))!!.second)
    }

    @Test fun `a reminder about money names no amount`() {
        val text = ReminderText.dues(context, 4)!!
        assertNotNull(text)
        assertFalse((text.first + text.second).contains("₹"))
    }
}
