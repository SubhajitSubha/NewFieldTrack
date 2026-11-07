package com.example.fieldtrack.feature

import android.text.format.DateFormat
import java.util.Locale

/**
 * A date or time pattern written the way this phone's region writes it.
 *
 * The screens used to say `"d MMM"` and `"h:mm a"`, which orders the day and month one
 * way and counts hours one way whoever is reading. The platform knows how each region
 * writes them (`6 Nov` or `Nov 6`, `6:30 pm` or `18:30`); the screens give it the
 * pieces they want, as a skeleton, and use what comes back.
 */
fun datePattern(skeleton: String, locale: Locale = Locale.getDefault()): String =
    runCatching { DateFormat.getBestDateTimePattern(locale, skeleton) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: skeleton

/** [millis] written with [pattern], in the business's time zone, so a day is the business's day. */
fun formatDate(pattern: String, millis: Long, locale: Locale = Locale.getDefault()): String =
    java.text.SimpleDateFormat(pattern, locale)
        .apply { timeZone = com.example.fieldtrack.data.region.BusinessClock.timeZone }
        .format(java.util.Date(millis))
