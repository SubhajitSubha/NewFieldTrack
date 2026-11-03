package com.example.fieldtrack.feature

import android.app.Activity
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.settings.DisplaySettings
import com.example.fieldtrack.data.settings.ThemeChoice
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme

/**
 * The theme as this person has set it in Display.
 *
 * Also keeps the system bars honest. The status bar's icons are drawn by Android,
 * not by us, and left alone they follow the phone's own dark setting — so someone
 * who chose Dark on a phone set to light would get black clock and battery icons
 * on a black ground. Each change of theme tells the window which it now is.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as? FieldFlowApplication)?.displaySettings
    val choice = settings?.theme?.collectAsStateWithLifecycle()?.value ?: ThemeChoice.System
    val bigger = settings?.biggerText?.collectAsStateWithLifecycle()?.value ?: false
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }

    FieldTrackTheme(
        dark = dark,
        textScale = if (bigger) DisplaySettings.BIGGER_TEXT_SCALE else 1f,
    ) {
        val paper = FieldTrackTheme.colors.paper.toArgb()
        LaunchedEffect(dark, paper) {
            val activity = context as? ComponentActivity ?: return@LaunchedEffect
            val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            // Behind the app, for the instant between screens and on rotation.
            (activity as Activity).window.decorView.setBackgroundColor(paper)
        }
        content()
    }
}
