package com.example.fieldtrack.feature

import android.content.Intent
import android.speech.RecognizerIntent
import com.example.fieldtrack.feature.voice.transcriptFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reading a transcript back from the system recogniser.
 *
 * The happy path needs a real microphone, so what is pinned here is the part that
 * decides whether anything was said at all — getting that wrong wipes a name the
 * person already typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceInputTest {

    private fun resultOf(vararg spoken: String) = Intent().apply {
        putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(*spoken))
    }

    @Test
    fun `the best guess is taken`() {
        assertEquals("Ramesh Singh", transcriptFrom(resultOf("Ramesh Singh", "Ramesh Sing")))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Gurpreet Kaur", transcriptFrom(resultOf("  Gurpreet Kaur \n")))
    }

    @Test
    fun `nothing heard leaves the field alone`() {
        assertNull("no intent at all", transcriptFrom(null))
        assertNull("no results extra", transcriptFrom(Intent()))
        assertNull("empty list", transcriptFrom(resultOf()))
        assertNull("blank transcript", transcriptFrom(resultOf("   ")))
    }
}
