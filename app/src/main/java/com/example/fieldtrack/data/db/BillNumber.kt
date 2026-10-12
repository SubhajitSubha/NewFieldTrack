package com.example.fieldtrack.data.db

import java.time.Instant
import java.time.ZoneId

/**
 * Which financial year a date falls in, Indian style: 1 April to 31 March.
 *
 * Returned as "2026-27". A bill raised on 31 March 2027 belongs to 2026-27; one
 * raised the next morning starts 2027-28 at number one.
 */
fun financialYear(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    val startYear = if (date.monthValue >= 4) date.year else date.year - 1
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
