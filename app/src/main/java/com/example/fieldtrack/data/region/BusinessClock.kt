package com.example.fieldtrack.data.region

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.ZoneId
import java.util.Calendar
import java.util.TimeZone

/**
 * How the business on screen counts its days and its accounting year.
 *
 * Every question of the form "what day is it", "is this today's work", "which week",
 * "which season", "which financial year" is answered in the business's own time zone,
 * not whatever the phone happens to be set to. A phone left on the wrong zone, or
 * carried across one, then changes nothing about which day a booking falls on.
 *
 * With no zone chosen it follows the phone, which is what every business did before
 * this existed, so nothing moves until somebody sets one.
 */
object BusinessClock {
    private var zoneId: String? by mutableStateOf(null)

    /** The first month of the accounting year, 1 to 12. */
    var fiscalStartMonth: Int by mutableIntStateOf(4)
        private set

    /** The zone days are counted in. */
    val zone: ZoneId get() = zoneOf(zoneId)

    val timeZone: TimeZone get() = TimeZone.getTimeZone(zone)

    /** Set when a business becomes the one on screen, or its settings change. */
    fun use(timeZoneId: String?, fiscalStartMonth: Int) {
        zoneId = timeZoneId?.takeIf { it.isNotBlank() }
        this.fiscalStartMonth = fiscalStartMonth.coerceIn(1, 12)
    }

    /** A calendar set to [millis] in the business's zone. */
    fun calendar(millis: Long): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis }

    /** A zone from its id, or the phone's own when there is none or the phone does not know it. */
    fun zoneOf(id: String?): ZoneId =
        id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
}
