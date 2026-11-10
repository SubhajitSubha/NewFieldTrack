package com.example.fieldtrack.feature.work

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.fieldtrack.FieldFlowApplication

/**
 * Asks, once, to show the clock in the notification shade.
 *
 * On the job screen rather than at launch: a person who has just pressed
 * "Start the timer" can see exactly what the clock is, so "allow notifications"
 * is a question with an obvious reason. Asked once and never again — a refusal
 * is an answer, and the app works fully without it; the clock just stays inside.
 */
@Composable
fun AskToShowClockOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val app = context.applicationContext as? FieldFlowApplication ?: return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Granted mid-job: put the clock up now rather than at the next change.
        if (granted) app.runInBackground { app.workClock.refresh() }
    }
    LaunchedEffect(Unit) {
        if (app.workClockNotifier.canPost() || askedBefore(context)) return@LaunchedEffect
        markAsked(context)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Asks, once, to be allowed to tell this person things: work booked for them, a job
 * handed over, a reminder they chose. The same single question as the clock's, so a
 * person is never asked twice; a refusal is an answer, and the app works without it.
 * The Notifications screen says what each switch does and where to turn them on again.
 */
@Composable
fun AskForNotificationsOnce() = AskToShowClockOnce()

private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("clock_notifications", Context.MODE_PRIVATE)

private fun askedBefore(context: Context) = prefs(context).getBoolean("asked", false)

private fun markAsked(context: Context) = prefs(context).edit().putBoolean("asked", true).apply()
