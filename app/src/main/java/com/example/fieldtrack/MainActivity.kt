package com.example.fieldtrack

import android.os.Bundle
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
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
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

        enableEdgeToEdge()
        setContent {
            FieldTrackTheme {
                Surface(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
                    color = FieldTrackTheme.colors.paper,
                ) {
                    FieldTrackApp()
                }
            }
        }
    }
}
