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
import kotlinx.coroutines.flow.first
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

    @Test fun `a breakdown, a call for help and somebody joining are understood and say what they were about`() {
        val breakdown = mapOf("event" to "breakdown_new", "orgId" to "o", "what" to "Swaraj 855")
        assertEquals(PushEvent.BreakdownReported, PushEvent.parse(breakdown))
        assertEquals("Swaraj 855", PushEvent.what(breakdown))
        assertEquals(PushEvent.BreakdownFixed, PushEvent.parse(mapOf("event" to "breakdown_fixed")))
        assertEquals(PushEvent.SosRaised, PushEvent.parse(mapOf("event" to "sos")))
        assertEquals(PushEvent.MemberJoined, PushEvent.parse(mapOf("event" to "member_joined")))
        assertNull(PushEvent.what(mapOf("event" to "sos", "what" to "  ")))
        assertEquals(60, PushEvent.what(mapOf("what" to "x".repeat(200)))!!.length)

        assertTrue(PushText.title(context, PushEvent.BreakdownReported, "Swaraj 855").contains("Swaraj 855"))
        assertTrue(PushText.title(context, PushEvent.SosRaised, "Gurmeet").contains("Gurmeet"))
        assertTrue(PushText.title(context, PushEvent.SosRaised).isNotBlank())
    }

    @Test fun `a call for help cannot be switched off, and everything else still can`() {
        val settings = PushSettings(context)
        settings.set(PushKind.Sos, false)
        assertTrue("an emergency nobody can hear is no emergency", PushSettings(context).isOn(PushKind.Sos))
        assertFalse(PushKind.Sos.switchable)
        settings.set(PushKind.Breakdown, false)
        assertFalse(PushSettings(context).isOn(PushKind.Breakdown))
        assertTrue(PushSettings(context).isOn(PushKind.Team))
    }

    @Test fun `what the phone was told is kept newest first, marked read per business, and emptied on sign-out`() {
        context.getSharedPreferences(com.example.fieldtrack.notify.NoticeInbox.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val inbox = com.example.fieldtrack.notify.NoticeInbox(context)
        inbox.add("a", "breakdown_new", "Swaraj 855", at = 1_000)
        inbox.add("a", "sos", "Gurmeet", at = 2_000)
        inbox.add("b", "job_handed", null, at = 3_000)

        val mine = kotlinx.coroutines.runBlocking { inbox.forOrg("a").first() }
        assertEquals(listOf("sos", "breakdown_new"), mine.map { it.event })
        assertEquals(2, kotlinx.coroutines.runBlocking { inbox.unread("a").first() })

        inbox.markAllRead("a")
        assertEquals(0, kotlinx.coroutines.runBlocking { inbox.unread("a").first() })
        assertEquals("another business's news is not touched", 1, kotlinx.coroutines.runBlocking { inbox.unread("b").first() })

        // Survives the process, as a phone that was off must still show it.
        assertEquals(3, com.example.fieldtrack.notify.NoticeInbox(context).all.value.size)
        inbox.clear()
        assertTrue(com.example.fieldtrack.notify.NoticeInbox(context).all.value.isEmpty())
    }

    @Test fun `the list keeps only the most recent`() {
        context.getSharedPreferences(com.example.fieldtrack.notify.NoticeInbox.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val inbox = com.example.fieldtrack.notify.NoticeInbox(context)
        repeat(100) { inbox.add("a", "sos", null, at = it.toLong()) }
        assertEquals(60, inbox.all.value.size)
        assertEquals(99L, inbox.all.value.first().at)
    }
}
