package com.example.fieldtrack.data.season

import kotlinx.serialization.Serializable
import com.example.fieldtrack.data.region.BusinessClock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a business's seasons begin.
 *
 * A season is not a fixed pair of dates for the whole country. Kharif and Rabi
 * start in June and November in the Punjab; a farmer in Kerala has three crops a
 * year on other months, and one in the south-east has a summer crop between them.
 * So the business says when each of its seasons starts — a day and a month,
 * repeating every year, with a name if it wants one — and everything that says
 * "this season" or "last season" reads it from here.
 *
 * Immutable and always valid: [of] and [parse] clean what they are given, so a
 * screen drawing a season never has to ask whether the calendar is usable. A
 * corrupt or missing value is the usual dates, never a crash on the home screen.
 */
class SeasonCalendar private constructor(val starts: List<SeasonStart>) {

    /** The season that includes [now]. */
    fun current(now: Long, zone: ZoneId = BusinessClock.zone): Season {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // This year's starts and last year's: the latest one not after today. Last
        // year's are always before it, so there is always one — which is what makes
        // January to May the back half of the season that began in November.
        val begun = (today.year - 1..today.year).flatMap { year ->
            starts.map { it.on(year) to it }
        }.filter { (date, _) -> !date.isAfter(today) }
        val (date, start) = begun.maxBy { it.first }
        return Season(date, start.name)
    }

    /**
     * The season before the one running now.
     *
     * Found by stepping back a day from this season's first morning and asking
     * again, rather than by subtracting a fixed length — seasons are not the same
     * length (June to November is five months, November to June is seven) and
     * arithmetic on months would land in the wrong one.
     */
    fun previous(now: Long, zone: ZoneId = BusinessClock.zone): Season {
        val dayBefore = current(now, zone).start.minusDays(1)
        return current(dayBefore.atStartOfDay(zone).toInstant().toEpochMilli(), zone)
    }

    /** For storing on the business: what [parse] reads back. */
    fun encode(): String = JSON.encodeToString(
        ListSerializer(Stored.serializer()),
        starts.map { Stored(it.month, it.day, it.name) },
    )

    override fun equals(other: Any?) = other is SeasonCalendar && other.starts == starts
    override fun hashCode() = starts.hashCode()
    override fun toString() = "SeasonCalendar($starts)"

    @Serializable
    private data class Stored(val m: Int, val d: Int, val n: String? = null)

    /** Why a list of starts cannot be saved as it is. */
    enum class Problem { None, Duplicate, TooMany }

    companion object {
        /**
         * Lenient about fields it does not know: a newer app may write more than this
         * one reads, and a calendar that stopped parsing because of an extra field
         * would quietly become the usual dates on the older phone.
         */
        private val JSON = Json { ignoreUnknownKeys = true }

        /** Four is enough for any cropping calendar in India, and more is a typing mistake. */
        const val MAX_SEASONS = 4

        const val MAX_NAME = 20

        /** June and November, unnamed: what the app has always meant by a season. */
        val Default: SeasonCalendar = SeasonCalendar(
            listOf(SeasonStart(6, 1), SeasonStart(11, 1))
        )

        /**
         * The last day of [month] that every year has. February stops at the 28th:
         * a season that began on the 29th would simply not exist three years in four.
         */
        fun maxDay(month: Int): Int = when (month) {
            2 -> 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        /** A start that can be used, or null: a month, a day that month has, a tidy name. */
        fun cleanStart(month: Int, day: Int, name: String?): SeasonStart? {
            if (month !in 1..12 || day !in 1..maxDay(month)) return null
            return SeasonStart(month, day, cleanName(name))
        }

        fun cleanName(name: String?): String? =
            name?.filterNot { it.isISOControl() }?.trim()?.replace(Regex("\\s+"), " ")
                ?.take(MAX_NAME)?.trim()?.ifEmpty { null }

        /**
         * A calendar from these starts, cleaned: invalid ones dropped, repeated dates
         * kept once (the first, with its name), the rest in the order the year runs.
         * Null when nothing usable is left, because a calendar with no seasons has
         * no answer to "which one is this".
         */
        fun of(starts: List<SeasonStart>): SeasonCalendar? {
            val clean = starts
                .mapNotNull { cleanStart(it.month, it.day, it.name) }
                .distinctBy { it.month to it.day }
                .sortedWith(compareBy({ it.month }, { it.day }))
                .take(MAX_SEASONS)
            return clean.takeIf { it.isNotEmpty() }?.let { SeasonCalendar(it) }
        }

        /** What stops these starts being saved, so the editor can say so. */
        fun problem(starts: List<SeasonStart>): Problem = when {
            starts.size > MAX_SEASONS -> Problem.TooMany
            starts.map { it.month to it.day }.distinct().size != starts.size -> Problem.Duplicate
            else -> Problem.None
        }

        /** Reads what [encode] wrote. Missing, blank or damaged is the usual dates. */
        fun parse(text: String?): SeasonCalendar {
            if (text.isNullOrBlank()) return Default
            return runCatching {
                JSON.decodeFromString(ListSerializer(Stored.serializer()), text)
                    .map { SeasonStart(it.m, it.d, it.n) }
            }.getOrNull()?.let(::of) ?: Default
        }
    }
}

/** A day in the year a season begins, and what it is called, if anything. */
data class SeasonStart(val month: Int, val day: Int, val name: String? = null) {
    fun on(year: Int): LocalDate = LocalDate.of(year, month, day)
}

/** One season of one year: the day it began and its name. */
data class Season(val start: LocalDate, val name: String?) {
    /** The first moment of it, in the phone's own time: what a query for "since the season began" takes. */
    fun startMillis(zone: ZoneId = BusinessClock.zone): Long =
        start.atStartOfDay(zone).toInstant().toEpochMilli()
}
