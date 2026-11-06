package com.example.fieldtrack.feature.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.feature.workTypeOptions
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Today", "Tomorrow", "Yesterday", otherwise the date: how a day is named beside today. */
@Composable
fun dayName(day: Long, today: Long): String = when (day - today) {
    0L -> stringResource(R.string.plan_today)
    1L -> stringResource(R.string.plan_tomorrow)
    -1L -> stringResource(R.string.plan_yesterday)
    else -> dayDate(day)
}

/** "Thu 6 Nov". */
fun dayDate(day: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(PlanDay.date(day))

/** "6:30 am", from minutes past midnight. */
fun timeOfDay(minute: Int): String {
    // Clamped: only this phone's own writes are checked, and a booking that came down
    // from the server with a time past midnight must not bring the screen down.
    val safe = minute.coerceIn(0, 24 * 60 - 1)
    return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(LocalTime.of(safe / 60, safe % 60))
}

@Composable
fun partName(part: DayPart): String = stringResource(
    when (part) {
        DayPart.Morning -> R.string.plan_part_morning
        DayPart.Afternoon -> R.string.plan_part_afternoon
        DayPart.WholeDay -> R.string.plan_part_whole
    }
)

/** The work's name, or the stored word itself for work this build has no name for. */
@Composable
fun workName(key: String): String =
    workTypeOptions.firstOrNull { it.key == key }?.let { stringResource(it.labelRes) } ?: key
