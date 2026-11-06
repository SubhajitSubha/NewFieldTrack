package com.example.fieldtrack.data.settings

import android.content.Context
import com.example.fieldtrack.designsystem.format.DigitGrouping
import com.example.fieldtrack.designsystem.format.Money
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light, dark, or whatever the phone itself is set to. */
enum class ThemeChoice { System, Light, Dark }

/** How big numbers are grouped on this phone: by the phone's region, or as chosen. */
enum class GroupingChoice { Auto, Indian, International }

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

    private val _grouping by lazy {
        MutableStateFlow(
            prefs.getString(KEY_GROUPING, null)
                ?.let { saved -> GroupingChoice.entries.firstOrNull { it.name == saved } }
                ?: GroupingChoice.Auto
        )
    }
    val grouping: StateFlow<GroupingChoice> get() = _grouping.asStateFlow()

    init { applyGrouping(_grouping.value) }

    /**
     * Digit grouping is how this person reads a number, so it is kept with the phone and
     * given to the money formatter the moment it changes: every figure on screen, and
     * every bill made here from now on, is written the way they asked.
     */
    private fun applyGrouping(choice: GroupingChoice) {
        val grouping = when (choice) {
            GroupingChoice.Indian -> DigitGrouping.Indian
            GroupingChoice.International -> DigitGrouping.International
            GroupingChoice.Auto -> DigitGrouping.forLocale(Locale.getDefault())
        }
        Money.grouping = grouping
        Money.style = Money.style.withGrouping(grouping)
    }

    fun setGrouping(choice: GroupingChoice) {
        prefs.edit().putString(KEY_GROUPING, choice.name).apply()
        _grouping.value = choice
        applyGrouping(choice)
    }

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
        const val KEY_GROUPING = "grouping"

        /**
         * How much bigger "bigger text" is. On top of the phone's own font size,
         * not instead of it: somebody who has already turned that up asked for
         * more again here.
         */
        const val BIGGER_TEXT_SCALE = 1.15f
    }
}
