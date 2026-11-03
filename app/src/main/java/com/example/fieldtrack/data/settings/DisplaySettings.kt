package com.example.fieldtrack.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or whatever the phone itself is set to. */
enum class ThemeChoice { System, Light, Dark }

/**
 * How this phone shows the app: light or dark, and how big the words are.
 *
 * Per phone, not per person or business. A driver whose eyes are tired at night
 * sets their own phone dark; that says nothing about the owner's, and nothing
 * here is worth sending to a server.
 *
 * Observable, so a change in settings redraws the whole app at once rather than
 * after the next restart.
 */
class DisplaySettings(context: Context) {

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _theme by lazy {
        MutableStateFlow(
            prefs.getString(KEY_THEME, null)
                ?.let { saved -> ThemeChoice.entries.firstOrNull { it.name == saved } }
                ?: ThemeChoice.System
        )
    }
    val theme: StateFlow<ThemeChoice> get() = _theme.asStateFlow()

    private val _biggerText by lazy { MutableStateFlow(prefs.getBoolean(KEY_BIGGER_TEXT, false)) }
    val biggerText: StateFlow<Boolean> get() = _biggerText.asStateFlow()

    fun setTheme(choice: ThemeChoice) {
        prefs.edit().putString(KEY_THEME, choice.name).apply()
        _theme.value = choice
    }

    fun setBiggerText(on: Boolean) {
        prefs.edit().putBoolean(KEY_BIGGER_TEXT, on).apply()
        _biggerText.value = on
    }

    companion object {
        const val PREFS = "fieldtrack.display"
        const val KEY_THEME = "theme"
        const val KEY_BIGGER_TEXT = "biggerText"

        /**
         * How much bigger "bigger text" is. On top of the phone's own font size,
         * not instead of it: somebody who has already turned that up asked for
         * more again here.
         */
        const val BIGGER_TEXT_SCALE = 1.15f
    }
}
