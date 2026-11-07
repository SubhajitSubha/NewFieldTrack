package com.example.fieldtrack.data.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * The language the app is shown in.
 *
 * By default it is whatever the phone is set to, and the app falls back to English for
 * any text not yet translated. A person who wants the app in another language than the
 * phone can choose one here; the choice belongs to this phone and nothing else.
 *
 * Done with the platform's own locale handling, not a library: the chosen language is
 * wrapped around the context every screen reads its text from, and the layout direction
 * follows the language (a right-to-left language mirrors the whole app).
 */
object AppLanguage {

    /**
     * Languages the app has been translated into, as language tags. English is the text
     * everything else falls back to, so it is always there. A language is added here when
     * its translation is in the app and has been read by somebody who speaks it.
     */
    val SUPPORTED: List<String> = listOf("en")

    /** A language written in itself, which is the only way a person who needs it can find it. */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /**
     * The locale for a chosen language, kept in the phone's own region so that dates,
     * numbers and digit grouping still follow where the person is: Hindi on a phone in
     * India is `hi-IN`, which groups in lakhs.
     */
    fun localeFor(tag: String?, system: Locale = systemLocale()): Locale {
        if (tag.isNullOrBlank()) return system
        val chosen = Locale.forLanguageTag(tag)
        return if (chosen.country.isNotEmpty() || system.country.isEmpty()) chosen
        else Locale.Builder().setLocale(chosen).setRegion(system.country).build()
    }

    /** What the phone itself is set to, untouched by anything this app has chosen. */
    fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]

    /** The saved choice, read straight from storage because it is needed before the app exists. */
    fun saved(context: Context): String? =
        context.getSharedPreferences(DisplaySettings.PREFS, Context.MODE_PRIVATE)
            .getString(DisplaySettings.KEY_LANGUAGE, null)
            ?.takeIf { it.isNotBlank() && it in SUPPORTED }

    /**
     * [base] with the chosen language laid over it, which is the context to hand to an
     * activity or the application. Also sets the process's default locale, so every
     * formatter that asks for it writes in the same language and region.
     */
    fun wrap(base: Context, tag: String? = saved(base)): Context {
        val locale = localeFor(tag)
        Locale.setDefault(locale)
        if (tag == null) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(android.os.LocaleList(locale))
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
