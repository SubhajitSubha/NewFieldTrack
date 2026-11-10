package com.example.fieldtrack.feature.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import java.util.Locale

/** Whether this phone has a speech engine at all; without one the button is not drawn. */
fun speechAvailable(context: Context): Boolean =
    context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()

/**
 * Reads a few lines aloud with the phone's own speech engine, in the phone's language.
 *
 * For somebody who reads slowly or not at all: the same figures the screen shows, said,
 * so the app is not closed to a man who runs a machine well and a page badly. Nothing is
 * sent anywhere; the engine is the phone's.
 */
@Composable
fun ReadAloudButton(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!remember(context) { speechAvailable(context) }) return
    val engine = remember { arrayOfNulls<TextToSpeech>(1) }
    val ready = remember { booleanArrayOf(false) }
    DisposableEffect(context) {
        onDispose { engine[0]?.stop(); engine[0]?.shutdown(); engine[0] = null; ready[0] = false }
    }
    Icon(
        imageVector = FieldTrackIcons.Speaker,
        contentDescription = contentDescription,
        tint = FieldTrackTheme.colors.primary,
        modifier = modifier
            .size(44.dp)
            .clickable(role = Role.Button) {
                fun say() {
                    engine[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "home")
                }
                if (ready[0]) say() else if (engine[0] == null) {
                    engine[0] = TextToSpeech(context.applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            val tts = engine[0]
                            // The phone's own language where the engine has it, else whatever it defaults to.
                            if (tts?.isLanguageAvailable(Locale.getDefault())?.let { it >= TextToSpeech.LANG_AVAILABLE } == true) {
                                tts.language = Locale.getDefault()
                            }
                            ready[0] = true
                            say()
                        }
                    }
                }
            }
            .padding(10.dp),
    )
}
