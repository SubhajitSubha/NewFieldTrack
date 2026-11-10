package com.example.fieldtrack.feature.voice

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Speech-to-text for a single field.
 *
 * [available] is false when the device has no recogniser installed, and callers are
 * expected to hide the microphone entirely rather than show a button that does
 * nothing. A dead control is worse than no control for someone who cannot read the
 * error it would produce.
 */
data class VoiceInput(
    val available: Boolean,
    val start: () -> Unit,
)

/**
 * Wires a microphone to [onText].
 *
 * Uses the system recogniser by intent, so this app records no audio itself and needs
 * no microphone permission — the recogniser asks for whatever it needs and hands back
 * only the transcript.
 *
 * [prompt] is shown on the recogniser's own screen, so it must be a string resource
 * like everything else the user reads.
 */
@Composable
fun rememberVoiceInput(prompt: String, onText: (String) -> Unit): VoiceInput {
    val context = LocalContext.current

    val available = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        transcriptFrom(result.data)?.let(onText)
    }

    return VoiceInput(
        available = available,
        start = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                // Follow the phone's language. Most of these users have an
                // English-locale handset and speak Punjabi or Hindi names into it, so
                // this is imperfect — once the app ships Hindi, pass the app's own
                // chosen language here instead of the system one.
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Ask for on-device recognition where the handset supports it. This
                // app is used where there is no signal; if the recogniser needs the
                // network it will fall back on its own rather than fail outright.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            runCatching { launcher.launch(intent) }
        },
    )
}

/**
 * The best transcript out of a recogniser result, or null if there is nothing usable.
 *
 * Separate and pure so it can be tested: a recogniser can return no extra at all, an
 * empty list, or whitespace, and all three have to come back as "the user said
 * nothing" rather than clearing a field the person had already typed into.
 */
fun transcriptFrom(data: Intent?): String? =
    data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
