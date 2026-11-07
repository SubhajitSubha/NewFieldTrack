package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.financialYear
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.data.region.Region
import com.example.fieldtrack.data.settings.WeekRules
import com.example.fieldtrack.feature.customer.financialYearStart
import com.example.fieldtrack.feature.datePattern
import com.example.fieldtrack.feature.fleet.startOfWeek
import com.example.fieldtrack.feature.fleet.weekdayIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

/**
 * How a business counts days, weeks and years, wherever the phone is.
 *
 * A day is the business's day, a year starts when the business says, a week starts
 * on the day the person counts from, and a date is written the way their region
 * writes it. None of those may depend on what the phone's own zone happens to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BusinessCalendarTest {

    @After fun backToThePhone() {
        BusinessClock.use(null, 4)
        WeekRules.apply(com.example.fieldtrack.data.settings.WeekStartChoice.Auto)
    }

    private fun at(zone: String, y: Int, m: Int, d: Int, hour: Int = 0): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.of(zone)).plusHours(hour.toLong()).toInstant().toEpochMilli()

    // ----------------------------------------------------------- accounting year

    @Test
    fun `a year starting in January is the calendar year`() {
        val utc = ZoneId.of("UTC")
        assertEquals("2026", financialYear(at("UTC", 2026, 1, 1), utc, 1))
        assertEquals("2026", financialYear(at("UTC", 2026, 12, 31), utc, 1))
        assertEquals("2027", financialYear(at("UTC", 2027, 1, 1), utc, 1))
    }

    @Test
    fun `a year starting in July runs July to June`() {
        val utc = ZoneId.of("UTC")
        assertEquals("2025-26", financialYear(at("UTC", 2026, 6, 30), utc, 7))
        assertEquals("2026-27", financialYear(at("UTC", 2026, 7, 1), utc, 7))
        assertEquals("2026-27", financialYear(at("UTC", 2027, 6, 30), utc, 7))
    }

    @Test
    fun `a statement over the year begins where the business's year begins`() {
        val now = at("UTC", 2026, 11, 7)
        val utc = ZoneId.of("UTC")
        assertEquals(LocalDate.of(2026, 4, 1), financialYearStart(now, utc, 4))
        assertEquals(LocalDate.of(2026, 1, 1), financialYearStart(now, utc, 1))
        assertEquals(LocalDate.of(2026, 7, 1), financialYearStart(now, utc, 7))
        assertEquals("before the month comes round, the year is last year's", LocalDate.of(2025, 12, 1), financialYearStart(at("UTC", 2026, 3, 1), utc, 12))
    }

    // ----------------------------------------------------------------- time zone

    @Test
    fun `the plan's day is the business's day, not the phone's`() {
        // 22:00 on 6 November in London is 03:30 on the 7th in Kolkata.
        val moment = at("Europe/London", 2026, 11, 6, 22)
        BusinessClock.use("Europe/London", 1)
        val london = PlanDay.today(moment, 0)
        BusinessClock.use("Asia/Kolkata", 4)
        val kolkata = PlanDay.today(moment, 0)
        assertEquals(LocalDate.of(2026, 11, 6).toEpochDay(), london)
        assertEquals(LocalDate.of(2026, 11, 7).toEpochDay(), kolkata)
    }

    @Test
    fun `an unknown or empty zone follows the phone`() {
        BusinessClock.use("Not/AZone", 4)
        assertEquals(ZoneId.systemDefault(), BusinessClock.zone)
        BusinessClock.use(null, 4)
        assertEquals(ZoneId.systemDefault(), BusinessClock.zone)
        BusinessClock.use("", 4)
        assertEquals(ZoneId.systemDefault(), BusinessClock.zone)
    }

    @Test
    fun `a calendar is read in the business's zone`() {
        BusinessClock.use("Pacific/Auckland", 4)
        val moment = at("UTC", 2026, 11, 6, 20) // already the 7th in Auckland
        assertEquals(7, BusinessClock.calendar(moment).get(Calendar.DAY_OF_MONTH))
    }

    // ---------------------------------------------------------------------- week

    @Test
    fun `the week is counted from the day the person starts it`() {
        val wednesday = at("UTC", 2026, 11, 4) // a Wednesday
        BusinessClock.use("UTC", 4)
        assertEquals(LocalDate.of(2026, 11, 2), LocalDate.ofEpochDay(startOfWeek(wednesday, Calendar.MONDAY) / 86_400_000L))
        assertEquals(LocalDate.of(2026, 11, 1), LocalDate.ofEpochDay(startOfWeek(wednesday, Calendar.SUNDAY) / 86_400_000L))
        assertEquals(LocalDate.of(2026, 10, 31), LocalDate.ofEpochDay(startOfWeek(wednesday, Calendar.SATURDAY) / 86_400_000L))
        assertEquals("Wednesday is the third day from Monday", 2, weekdayIndex(wednesday, Calendar.MONDAY))
        assertEquals(3, weekdayIndex(wednesday, Calendar.SUNDAY))
        assertEquals(4, weekdayIndex(wednesday, Calendar.SATURDAY))
    }

    @Test
    fun `the bars are drawn from the first day of the week round to the last`() {
        assertEquals(
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY),
            WeekRules.order(Calendar.MONDAY),
        )
        assertEquals(Calendar.SUNDAY, WeekRules.order(Calendar.SUNDAY).first())
        assertEquals(Calendar.SATURDAY, WeekRules.order(Calendar.SUNDAY).last())
        assertEquals(Calendar.SATURDAY, WeekRules.order(Calendar.SATURDAY).first())
        assertEquals(7, WeekRules.order(Calendar.SATURDAY).distinct().size)
    }

    // ----------------------------------------------------------------- date style

    @Test
    fun `a date is ordered the way the region writes it`() {
        val us = datePattern("dMMM", Locale.US)
        val uk = datePattern("dMMM", Locale.UK)
        assertTrue("US puts the month first: $us", us.indexOf('M') < us.indexOf('d'))
        assertTrue("UK puts the day first: $uk", uk.indexOf('d') < uk.indexOf('M'))
    }

    @Test
    fun `a clock reads in twelve or twenty-four hours as the region does`() {
        assertTrue(datePattern("jmm", Locale.US).contains('a'))
        assertTrue(datePattern("jmm", Locale.GERMANY).contains('H'))
    }

    // ------------------------------------------------------------------ guessing

    @Test
    fun `the accounting year a country keeps is guessed, and is only a guess`() {
        assertEquals(4, Region.fiscalStartMonthOf("IN"))
        assertEquals(7, Region.fiscalStartMonthOf("AU"))
        assertEquals(1, Region.fiscalStartMonthOf("US"))
        assertEquals(1, Region.fiscalStartMonthOf(null))
        assertEquals(1, Region.guess("us", null, Locale.US).fiscalStartMonth)
        assertEquals(4, Region.guess("in", null, Locale("hi", "IN")).fiscalStartMonth)
    }

    @Test
    fun `land is guessed in hectares outside the countries that keep the acre`() {
        assertEquals(com.example.fieldtrack.data.db.MeasureUnit.Acre, Region.landUnitOf("IN"))
        assertEquals(com.example.fieldtrack.data.db.MeasureUnit.Acre, Region.landUnitOf("US"))
        assertEquals(com.example.fieldtrack.data.db.MeasureUnit.Hectare, Region.landUnitOf("BR"))
        assertEquals(com.example.fieldtrack.data.db.MeasureUnit.Hectare, Region.landUnitOf("KE"))
        assertEquals(com.example.fieldtrack.data.db.MeasureUnit.Acre, Region.landUnitOf(null))
    }

    @Test
    fun `time zones are listed by city in order of the clock`() {
        val zones = Region.timeZones()
        assertTrue(zones.any { it.id == "Asia/Kolkata" && it.offset == "UTC+05:30" })
        assertTrue(zones.none { it.id.startsWith("Etc/") })
        assertEquals(zones.sortedBy { it.offsetSeconds }.map { it.offsetSeconds }, zones.map { it.offsetSeconds })
        assertEquals("Kolkata", zones.first { it.id == "Asia/Kolkata" }.city)
    }
}
