package com.example.fieldtrack.feature.season

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fieldtrack.R
import com.example.fieldtrack.data.season.SeasonStart
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "Jun" — a month named in the phone's language, for the chips that pick one. */
fun monthName(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/**
 * "1 Jun" — a day that repeats every year, so no year to print.
 *
 * Built on 2001, which is not a leap year: February's 28th is the last day a season
 * may begin, and a date that exists in every year is the only kind that can.
 */
fun dayMonthOf(month: Int, day: Int): String =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(LocalDate.of(2001, month, day))

/** "Kharif 1 Jun", or just "1 Jun" when the season has no name. */
@Composable
fun seasonStartLabel(start: SeasonStart): String {
    val date = dayMonthOf(start.month, start.day)
    return start.name?.let { stringResource(R.string.season_start_item, it, date) } ?: date
}

/** "Kharif 1 Jun · Rabi 1 Nov" — when the seasons begin, on one line. */
@Composable
fun seasonStartsSummary(starts: List<SeasonStart>): String =
    starts.map { seasonStartLabel(it) }
        .reduce { all, next -> stringResource(R.string.season_starts_join, all, next) }
