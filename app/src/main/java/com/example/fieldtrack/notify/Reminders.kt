package com.example.fieldtrack.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.MainActivity
import com.example.fieldtrack.R
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.Session
import com.example.fieldtrack.feature.home.HomeAlert
import com.example.fieldtrack.feature.home.chaseAlerts
import com.example.fieldtrack.feature.home.debtsByAge
import com.example.fieldtrack.feature.home.machineAlerts
import com.example.fieldtrack.feature.plan.visibleTo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** The three things the app can tell somebody about without being opened. */
enum class ReminderKind { Plan, Dues, Service, Papers }

/**
 * Which reminders this phone gives, and when. Per phone and per person: a driver wants
 * tomorrow's work and nothing about money, an owner may want all three, and none is on
 * until somebody asks for it, because the first one asked for is also the moment the
 * phone is asked for permission to show anything.
 */
class ReminderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val on = ReminderKind.entries.associateWith { MutableStateFlow(prefs.getBoolean(it.name, false)) }
    private val _evening = MutableStateFlow(prefs.getInt(KEY_EVENING, DEFAULT_EVENING))
    private val _morning = MutableStateFlow(prefs.getInt(KEY_MORNING, DEFAULT_MORNING))
    private val _paperDays = MutableStateFlow(prefs.getInt(KEY_PAPER_DAYS, com.example.fieldtrack.data.costing.Papers.DEFAULT_WARN_DAYS))

    fun enabled(kind: ReminderKind): StateFlow<Boolean> = on.getValue(kind).asStateFlow()
    val eveningHour: StateFlow<Int> get() = _evening.asStateFlow()
    val morningHour: StateFlow<Int> get() = _morning.asStateFlow()

    /** How many days before a machine's paper runs out it starts being mentioned. */
    val paperWarnDays: StateFlow<Int> get() = _paperDays.asStateFlow()

    fun setPaperWarnDays(days: Int) {
        prefs.edit().putInt(KEY_PAPER_DAYS, days).apply(); _paperDays.value = days
    }

    fun isOn(kind: ReminderKind) = on.getValue(kind).value
    val any: Boolean get() = ReminderKind.entries.any { isOn(it) }

    fun set(kind: ReminderKind, enabled: Boolean) {
        prefs.edit().putBoolean(kind.name, enabled).apply()
        on.getValue(kind).value = enabled
    }

    fun setEvening(hour: Int) {
        prefs.edit().putInt(KEY_EVENING, hour).apply(); _evening.value = hour
    }

    fun setMorning(hour: Int) {
        prefs.edit().putInt(KEY_MORNING, hour).apply(); _morning.value = hour
    }

    /** The hour of day a kind is given at: the plan the evening before, the rest in the morning. */
    fun hourOf(kind: ReminderKind) = if (kind == ReminderKind.Plan) _evening.value else _morning.value

    companion object {
        const val PREFS = "fieldtrack.reminders"
        private const val KEY_EVENING = "eveningHour"
        private const val KEY_MORNING = "morningHour"
        private const val KEY_PAPER_DAYS = "paperDays"
        const val DEFAULT_EVENING = 19
        const val DEFAULT_MORNING = 8
        val EVENING_HOURS = listOf(17, 18, 19, 20, 21)
        val MORNING_HOURS = listOf(6, 7, 8, 9)
    }
}

/** What each reminder says, from the facts. Counts only: nothing on a lock screen should be money. */
object ReminderText {
    fun plan(context: Context, jobs: Int): Pair<String, String>? =
        if (jobs <= 0) null
        else context.getString(R.string.remind_plan_title) to context.resources.getQuantityString(R.plurals.remind_plan_body, jobs, jobs)

    fun dues(context: Context, farmers: Int): Pair<String, String>? =
        if (farmers <= 0) null
        else context.getString(R.string.remind_dues_title) to context.resources.getQuantityString(R.plurals.remind_dues_body, farmers, farmers)

    fun papers(context: Context, count: Int, firstMachine: String?): Pair<String, String>? = when {
        count <= 0 -> null
        count == 1 && firstMachine != null -> context.getString(R.string.remind_papers_title) to context.getString(R.string.remind_papers_one, firstMachine)
        else -> context.getString(R.string.remind_papers_title) to context.resources.getQuantityString(R.plurals.remind_papers_many, count, count)
    }

    fun service(context: Context, machines: List<String>): Pair<String, String>? = when {
        machines.isEmpty() -> null
        machines.size == 1 -> context.getString(R.string.remind_service_title) to context.getString(R.string.remind_service_one, machines[0])
        else -> context.getString(R.string.remind_service_title) to context.resources.getQuantityString(R.plurals.remind_service_many, machines.size, machines.size)
    }
}

/** Gives one reminder and books the next one for tomorrow. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FieldFlowApplication ?: return Result.success()
        val kind = inputData.getString(KEY_KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return Result.success()
        // Switched off since this was booked: nothing to say, and nothing to book.
        if (!app.reminderSettings.isOn(kind)) return Result.success()
        runCatching { give(app, kind) }
        Reminders.schedule(applicationContext, app.reminderSettings)
        return Result.success()
    }

    private suspend fun give(app: FieldFlowApplication, kind: ReminderKind) {
        val s = app.sessionStore.load() ?: return
        val context = applicationContext
        val now = System.currentTimeMillis()
        val message = when (kind) {
            ReminderKind.Plan -> ReminderText.plan(context, tomorrowsJobs(app, s, now))
            ReminderKind.Dues -> if (Capability.SeeMoney !in s.can) null else ReminderText.dues(context, farmersToChase(app, s, now))
            ReminderKind.Service ->
                if (Capability.EditMachines !in s.can) null else ReminderText.service(context, machinesDueForService(app, s))
            ReminderKind.Papers ->
                if (Capability.EditMachines !in s.can) null else papersMessage(context, app, s)
        } ?: return
        Reminders.post(context, kind, message.first, message.second)
    }

    private suspend fun papersMessage(context: Context, app: FieldFlowApplication, s: Session): Pair<String, String>? {
        val today = java.time.LocalDate.now(com.example.fieldtrack.data.region.BusinessClock.zone).toEpochDay()
        val due = com.example.fieldtrack.data.costing.Papers.needingAttention(
            app.db.machineDao().papersOnce(s.orgId), today, app.reminderSettings.paperWarnDays.value,
        )
        val first = due.firstOrNull()?.let { paper -> app.db.machineDao().findMachineName(s.orgId, paper.machineId) }
        return ReminderText.papers(context, due.size, first)
    }

    private suspend fun tomorrowsJobs(app: FieldFlowApplication, s: Session, now: Long): Int {
        val settings = app.directoryRepository.observePlanSettings(s.orgId).first()
        val day = PlanDay.today(now, settings.dayStartHour) + 1
        return app.db.bookingDao().observeDays(s.orgId, day, day).first()
            .filter { PlanDay.holds(it) }
            .let { it.visibleTo(s, settings) }
            .size
    }

    private suspend fun farmersToChase(app: FieldFlowApplication, s: Session, now: Long): Int {
        val customers = app.db.customerDao().observeAll(s.orgId).first()
        val debts = debtsByAge(app.db.ledgerDao().observeAll(s.orgId).first())
        return chaseAlerts(customers, debts, now, limit = Int.MAX_VALUE).size
    }

    private suspend fun machinesDueForService(app: FieldFlowApplication, s: Session): List<String> =
        machineAlerts(app.db.machineDao().observeAllOnce(s.orgId), emptyList()).mapNotNull {
            when (it) {
                is HomeAlert.ServiceOverdue -> it.machineName
                is HomeAlert.ServiceSoon -> it.machineName
                else -> null
            }
        }

    companion object {
        const val KEY_KIND = "kind"
    }
}

object Reminders {
    private const val CHANNEL = "daily_reminders"
    private const val TAG = "Reminders"

    private fun workName(kind: ReminderKind) = "reminder-${kind.name}"

    /**
     * Books each reminder that is on for its next hour, and cancels each that is off.
     * Idempotent, and safe to call whenever a setting changes or the app starts.
     * Never allowed to throw: a missed reminder costs one reminder, a throw costs the app.
     */
    fun schedule(context: Context, settings: ReminderSettings) {
        runCatching {
            val work = WorkManager.getInstance(context)
            ReminderKind.entries.forEach { kind ->
                if (settings.isOn(kind)) {
                    val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(ClockReminderWorker.millisUntilNext(settings.hourOf(kind)), TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
                        .build()
                    work.enqueueUniqueWork(workName(kind), ExistingWorkPolicy.REPLACE, request)
                } else {
                    work.cancelUniqueWork(workName(kind))
                }
            }
        }.onFailure { Log.w(TAG, "Could not book the reminders", it) }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notify_channel_daily), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notify_channel_daily_note)
            }
        )
    }

    fun post(context: Context, kind: ReminderKind, title: String, text: String) {
        val app = context.applicationContext as? FieldFlowApplication
        if (app?.workClockNotifier?.canPost() == false) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, kind.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            // On a locked screen, only that there is something; the words show once unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_clock)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.remind_public))
                    .build()
            )
        try {
            NotificationManagerCompat.from(context).notify(5100 + kind.ordinal, builder.build())
        } catch (_: SecurityException) {
            // Permission withdrawn in the instant between the check and here.
        }
    }
}
