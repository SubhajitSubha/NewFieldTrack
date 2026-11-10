package com.example.fieldtrack.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.MainActivity
import com.example.fieldtrack.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** What kind of news can arrive the moment it happens. One switch for each. */
enum class PushKind(val key: String) {
    /** Work booked for you, moved, or cancelled. */
    Booking("booking"),

    /** A job handed to you. */
    Job("job"),

    /** A payment recorded in the business, for those who look after the money. */
    Payment("payment"),

    /** A farmer asked for work through the booking link, for those who plan. */
    Request("request"),

    /** A machine broke down, or is working again: news for everyone it touches. */
    Breakdown("breakdown"),

    /** Somebody joined the business, for the owners. */
    Team("team"),

    /**
     * Somebody on a job says they need help. Has no switch: an emergency that a person
     * has muted is an emergency nobody hears about.
     */
    Sos("sos");

    /** Whether a person may turn this kind off. */
    val switchable: Boolean get() = this != Sos

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** One thing that happened, as the server says it. Never carries an amount. */
enum class PushEvent(val kind: PushKind, val key: String) {
    BookingNew(PushKind.Booking, "booking_new"),
    BookingChanged(PushKind.Booking, "booking_changed"),
    BookingCancelled(PushKind.Booking, "booking_cancelled"),
    JobHanded(PushKind.Job, "job_handed"),
    PaymentRecorded(PushKind.Payment, "payment_recorded"),
    RequestNew(PushKind.Request, "request_new"),
    BreakdownReported(PushKind.Breakdown, "breakdown_new"),
    BreakdownFixed(PushKind.Breakdown, "breakdown_fixed"),
    MemberJoined(PushKind.Team, "member_joined"),
    SosRaised(PushKind.Sos, "sos");

    companion object {
        /** The event a message from the server names, or null for anything this version does not know. */
        fun parse(data: Map<String, String>): PushEvent? = entries.firstOrNull { it.key == data["event"] }

        /** What the news was about (a machine, a person), when the server said. Never an amount. */
        fun what(data: Map<String, String>): String? = data["what"]?.trim()?.takeIf { it.isNotEmpty() }?.take(60)
    }
}

/**
 * Which instant notifications this phone wants. On by default: somebody who joined a
 * business to be given work expects to hear when they are. Each kind can be switched
 * off, and the switch is also told to the server so it does not send what would be
 * thrown away.
 */
class PushSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val on = PushKind.entries.associateWith { MutableStateFlow(prefs.getBoolean(it.key, true)) }

    fun enabled(kind: PushKind): StateFlow<Boolean> = on.getValue(kind).asStateFlow()
    fun isOn(kind: PushKind) = !kind.switchable || on.getValue(kind).value

    fun set(kind: PushKind, enabled: Boolean) {
        if (!kind.switchable) return
        prefs.edit().putBoolean(kind.key, enabled).apply()
        on.getValue(kind).value = enabled
    }

    /** The token this phone was last given, so a change of switch can be told without asking again. */
    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value) }.apply() }

    /** What the server was last told, so an unchanged phone does not write again on every sync. */
    var registered: String?
        get() = prefs.getString(KEY_REGISTERED, null)
        set(value) { prefs.edit().apply { if (value == null) remove(KEY_REGISTERED) else putString(KEY_REGISTERED, value) }.apply() }

    companion object {
        const val PREFS = "fieldtrack.push"
        private const val KEY_TOKEN = "token"
        private const val KEY_REGISTERED = "registered"
    }
}

/**
 * Tells the server how to reach this phone, and what it wants to hear.
 *
 * Written under the person, so only they can read or change it, and one row per phone:
 * a person with two phones is told on both. Best effort and quiet; whatever did not get
 * through is simply sent again the next time anything changes or the app opens.
 */
class PushRegistrar(private val app: FieldFlowApplication) {

    suspend fun sync() {
        val settings = app.pushSettings
        val personId = app.sessionStore.personIdOrNull() ?: return
        val token = settings.token ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()?.also { settings.token = it } ?: return
        val wants = PushKind.entries.associate { it.key to settings.isOn(it) }
        val signature = "$personId|$token|${wants.entries.joinToString { "${it.key}=${it.value}" }}"
        if (settings.registered == signature) return
        val landed = runCatching {
            app.syncTransport.registerPush(personId, app.deviceId, token, wants, System.currentTimeMillis())
        }.getOrDefault(false)
        if (landed) settings.registered = signature
    }

    /** This phone no longer belongs to anybody: stop the server pushing to it. */
    suspend fun forget(personId: String) {
        runCatching { app.syncTransport.registerPush(personId, app.deviceId, null, emptyMap(), System.currentTimeMillis()) }
        app.pushSettings.registered = null
        // What this phone was told belonged to whoever was signed in.
        app.noticeInbox.clear()
    }
}

/** The words for each event. Counted nowhere and priced nowhere: a lock screen is not a ledger. */
object PushText {
    fun title(context: Context, event: PushEvent, what: String? = null): String {
        val machine = what ?: context.getString(R.string.push_what_machine)
        val person = what ?: context.getString(R.string.push_what_person)
        return when (event) {
            PushEvent.BookingNew -> context.getString(R.string.push_booking_new)
            PushEvent.BookingChanged -> context.getString(R.string.push_booking_changed)
            PushEvent.BookingCancelled -> context.getString(R.string.push_booking_cancelled)
            PushEvent.JobHanded -> context.getString(R.string.push_job_handed)
            PushEvent.PaymentRecorded -> context.getString(R.string.push_payment_recorded)
            PushEvent.RequestNew -> context.getString(R.string.push_request_new)
            PushEvent.BreakdownReported -> context.getString(R.string.push_breakdown_new, machine)
            PushEvent.BreakdownFixed -> context.getString(R.string.push_breakdown_fixed, machine)
            PushEvent.MemberJoined -> context.getString(R.string.push_member_joined, person)
            PushEvent.SosRaised -> context.getString(R.string.push_sos, person)
        }
    }
}

/** Where the server's messages arrive. */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = application as? FieldFlowApplication ?: return
        app.pushSettings.token = token
        app.runInBackground { PushRegistrar(app).sync() }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as? FieldFlowApplication ?: return
        val event = PushEvent.parse(message.data) ?: return
        // Switched off here as well as at the server: a message in flight when the
        // switch was turned must not still arrive.
        if (!app.pushSettings.isOn(event.kind)) return
        val what = PushEvent.what(message.data)
        // Kept even when the notification itself cannot be shown (permission withdrawn,
        // Do Not Disturb): the list in the app is how nothing gets lost.
        message.data["orgId"]?.let { app.noticeInbox.add(it, event.key, what, System.currentTimeMillis()) }
        Push.show(this, event, what)
    }
}

object Push {
    private const val CHANNEL = "instant_updates"
    private const val CHANNEL_URGENT = "urgent_help"
    /** The extra that tells the app a tap came from the news, so it opens the list. */
    const val EXTRA_OPEN_NOTICES = "open_notices"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notify_channel_instant), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notify_channel_instant_note)
            }
        )
        // Its own channel, so a call for help can be told apart from a booking and the
        // phone's settings can give it the loudest voice.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_URGENT, context.getString(R.string.notify_channel_urgent), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notify_channel_urgent_note)
                enableVibration(true)
            }
        )
    }

    fun show(context: Context, event: PushEvent, what: String? = null) {
        val app = context.applicationContext as? FieldFlowApplication
        if (app?.workClockNotifier?.canPost() == false) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 100 + event.ordinal,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_NOTICES, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = PushText.title(context, event, what)
        val urgent = event == PushEvent.SosRaised
        val builder = NotificationCompat.Builder(context, if (urgent) CHANNEL_URGENT else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.push_open))
            .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_clock)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.remind_public))
                    .build()
            )
        try {
            NotificationManagerCompat.from(context).notify(5200 + event.ordinal, builder.build())
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and here.
        }
    }
}
