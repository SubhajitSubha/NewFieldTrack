package com.example.fieldtrack.data.db

import java.time.Instant
import com.example.fieldtrack.data.region.BusinessClock
import java.time.ZoneId

/**
 * Which financial year a date falls in, in the business's own accounting calendar.
 *
 * With the year starting in April, returned as "2026-27": a bill raised on 31 March
 * 2027 belongs to 2026-27 and one raised the next morning starts 2027-28 at number
 * one. A business whose year is the calendar year gets plain "2026", because two
 * years joined by a dash would imply a year that straddles New Year when it does not.
 */
fun financialYear(
    atMillis: Long,
    zone: ZoneId = BusinessClock.zone,
    startMonth: Int = BusinessClock.fiscalStartMonth,
): String {
    val date = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    if (startMonth == 1) return date.year.toString()
    val startYear = if (date.monthValue >= startMonth) date.year else date.year - 1
    return "$startYear-${((startYear + 1) % 100).toString().padStart(2, '0')}"
}

/**
 * How a bill number is written out: "2026-27/0241".
 *
 * Padded to four digits so a season's bills line up in a column when they are read
 * off a printed statement.
 */
fun formatBillNumber(financialYear: String, sequence: Int): String =
    "$financialYear/${sequence.toString().padStart(4, '0')}"
