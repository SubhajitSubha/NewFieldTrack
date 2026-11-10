package com.example.fieldtrack.data

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.settings.AppLanguage
import com.example.fieldtrack.data.settings.DisplaySettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The language the app is shown in, and the direction it reads.
 *
 * A language is chosen once and then every screen, every formatter and every
 * notification has to agree on it; an Arabic or Urdu choice must also turn the whole
 * layout round, not just the words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLanguageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val phone = Locale("en", "IN")

    @After fun backToThePhone() {
        Locale.setDefault(Locale.US)
        context.getSharedPreferences(DisplaySettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `no choice follows the phone`() {
        assertEquals(phone, AppLanguage.localeFor(null, phone))
        assertEquals(phone, AppLanguage.localeFor("", phone))
    }

    @Test
    fun `a chosen language keeps the phone's region so numbers still read the local way`() {
        val locale = AppLanguage.localeFor("hi", phone)
        assertEquals("hi", locale.language)
        assertEquals("IN", locale.country)
    }

    @Test
    fun `a language chosen with its own region keeps it`() {
        val locale = AppLanguage.localeFor("pt-BR", Locale("en", "PT"))
        assertEquals("pt", locale.language)
        assertEquals("BR", locale.country)
    }

    @Test
    fun `a language is named in itself`() {
        assertEquals("हिन्दी", AppLanguage.nativeName("hi"))
        assertEquals("Español", AppLanguage.nativeName("es"))
        assertEquals("العربية", AppLanguage.nativeName("ar"))
    }

    @Test
    fun `wrapping sets the language for the screens and for every formatter`() {
        val wrapped = AppLanguage.wrap(context, "fr")
        assertEquals("fr", wrapped.resources.configuration.locales[0].language)
        assertEquals("fr", Locale.getDefault().language)
    }

    @Test
    fun `a right-to-left language turns the whole layout round`() {
        val arabic = AppLanguage.wrap(context, "ar")
        assertEquals(View.LAYOUT_DIRECTION_RTL, arabic.resources.configuration.layoutDirection)
        val urdu = AppLanguage.wrap(context, "ur")
        assertEquals(View.LAYOUT_DIRECTION_RTL, urdu.resources.configuration.layoutDirection)
        val english = AppLanguage.wrap(context, "en")
        assertEquals(View.LAYOUT_DIRECTION_LTR, english.resources.configuration.layoutDirection)
    }

    @Test
    fun `only a language the app has been translated into can be saved and read back`() {
        val settings = DisplaySettings(context)
        settings.setLanguage("en")
        assertEquals("en", AppLanguage.saved(context))
        settings.setLanguage("zz")
        assertEquals("a tag with no translation behind it is ignored", null, AppLanguage.saved(context))
        settings.setLanguage(null)
        assertEquals(null, AppLanguage.saved(context))
        assertTrue("English is always there", "en" in AppLanguage.SUPPORTED)
    }

    @Test
    fun `whatever the language, numbers on screen use the digits 0 to 9`() {
        for (tag in listOf("ar", "bn", "hi", "fr", "sw")) {
            val read = AppLanguage.wrap(context, tag)
            val shown = read.getString(com.example.fieldtrack.R.string.doc_entries, 1274)
            assertTrue("$tag: $shown", shown.any { it in '0'..'9' } && shown.none { it in '\u0660'..'\u0669' || it in '\u09E6'..'\u09EF' })
        }
    }

    @Test
    fun `a phone set to Arabic with no choice made still gets the same digits as the money`() {
        val arabicPhone = Locale("ar", "EG")
        Locale.setDefault(arabicPhone)
        val withLatin = AppLanguage.withLatinDigits(arabicPhone)
        assertEquals("1274", String.format(withLatin, "%d", 1274))
    }
}
