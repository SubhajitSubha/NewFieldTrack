package com.example.fieldtrack

import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.fieldtrack.data.session.Startup
import com.example.fieldtrack.data.sync.SyncWorker
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.AppTheme
import com.example.fieldtrack.feature.FieldTrackApp

/**
 * Entry point for the redesigned app.
 *
 * The previous MainActivity drove a 35-route graph of screens backed by repositories
 * that returned null; it is kept at MainActivity.legacy.kt.txt for reference while
 * the remaining flows are rebuilt, and deleted once nothing is left to port.
 */
/** How long the launch window may wait for the session before giving up. */
private const val MAX_SPLASH_MILLIS = 2_000L

class MainActivity : ComponentActivity() {

    /**
     * A job somebody asked to be taken to — the clock notification, pressed.
     *
     * Held here until the app has a signed-in screen to open it from; a press
     * that arrives while the session is still being read is not lost, it waits.
     */
    private val openJobRequests = MutableStateFlow<String?>(null)

    /** The chosen language is laid over everything this screen reads its text from. */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.fieldtrack.data.settings.AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, as the platform requires: this swaps the launch window's
        // theme for the app's own.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Held until the session is resolved from disk. Without this the app shows
        // a blank paper-coloured frame for the moment it takes to read who is using
        // it, which reads as a stall rather than as a launch.
        val app = application as FieldFlowApplication
        val splashStartedAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            // Time-bounded on purpose. A condition that can only be cleared by work
            // completing is a condition that can hang the app on its own splash if
            // that work ever fails; after this the startup gate takes over and says
            // something, which is always better than a frozen logo.
            app.sessionStore.startup.value is Startup.Loading &&
                SystemClock.uptimeMillis() - splashStartedAt < MAX_SPLASH_MILLIS
        }

        // Only from a fresh launch, not a restore: after the system recreates the
        // activity the intent is the old one, and the job it named was opened then.
        if (savedInstanceState == null) takeOpenJob(intent)

        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    color = FieldTrackTheme.colors.paper,
                ) {
                    FieldTrackApp(
                        openJobRequests = openJobRequests,
                        onOpenJobHandled = { openJobRequests.value = null },
                    )
                }
            }
        }
    }

    /**
     * Asks for a send every time the app is opened.
     *
     * Covers the gap the per-change trigger cannot: work recorded in a field with
     * no signal, where the phone was then locked and carried home. The request is
     * held by the system until there is a network, so opening the app in the yard
     * is enough — nobody has to go looking for a button.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeOpenJob(intent)
    }

    private fun takeOpenJob(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_JOB)?.let { openJobRequests.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Forced: a person opening the app outranks whatever is queued, including
        // a backoff left over from a morning with no signal.
        SyncWorker.runNow(this, force = true)
    }

    companion object {
        /** The job a notification wants opened. */
        const val EXTRA_OPEN_JOB = "openJobId"
    }
}
