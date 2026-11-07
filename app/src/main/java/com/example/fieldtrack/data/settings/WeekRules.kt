package com.example.fieldtrack.data.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/** Which day the week starts on for this person: the phone's own custom, or chosen. */
enum class WeekStartChoice(val day: Int?) {
    Auto(null),
    Monday(Calendar.MONDAY),
    Sunday(Calendar.SUNDAY),
    Saturday(Calendar.SATURDAY),
}

/**
 * The first day of the week as this person counts it.
 *
 * "This week", the week's bars and a driver's weekly figure all begin here. It was fixed
 * on Monday, which is right for some places and wrong for others; now it follows the
 * phone's own calendar unless the person says otherwise. Held as Compose state so a
 * change redraws the charts at once.
 */
object WeekRules {
    var firstDay: Int by mutableIntStateOf(Calendar.getInstance().firstDayOfWeek)

    fun apply(choice: WeekStartChoice) {
        firstDay = choice.day ?: Calendar.getInstance().firstDayOfWeek
    }

    /** 0 for the first day of the week through 6 for the last, for a `Calendar.DAY_OF_WEEK` value. */
    fun indexOf(calendarDay: Int, first: Int = firstDay): Int = (calendarDay - first + 7) % 7

    /** `Calendar.DAY_OF_WEEK` values in the order the week is drawn. */
    fun order(first: Int = firstDay): List<Int> = List(7) { (first - 1 + it) % 7 + 1 }
}
