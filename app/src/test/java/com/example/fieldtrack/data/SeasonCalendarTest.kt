package com.example.fieldtrack.data

import com.example.fieldtrack.data.season.SeasonCalendar
import com.example.fieldtrack.data.season.SeasonStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a season begins, for any business.
 *
 * The usual dates (June and November) are checked to behave exactly as the fixed
 * ones did, so nobody's season moves the day they update; the rest checks the
 * calendars a farmer in another part of the country would write.
 */
class SeasonCalendarTest {

    private val india = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, zone: ZoneId = india) =
        LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun cal(vararg s: SeasonStart) = SeasonCalendar.of(s.toList())!!
    private fun SeasonCalendar.startOn(y: Int, m: Int, d: Int) = current(at(y, m, d), india).start
    private fun SeasonCalendar.beforeOn(y: Int, m: Int, d: Int) = previous(at(y, m, d), india).start

    // ------------------------------------------------------- the usual dates

    @Test
    fun `the usual dates are June and November`() {
        val usual = SeasonCalendar.Default
        assertEquals(listOf(6 to 1, 11 to 1), usual.starts.map { it.month to it.day })
        assertEquals(listOf(null, null), usual.starts.map { it.name })
    }

    @Test
    fun `the usual dates behave exactly as the fixed ones did`() {
        val usual = SeasonCalendar.Default
        assertEquals(LocalDate.of(2026, 6, 1), usual.startOn(2026, 8, 14))
        assertEquals(LocalDate.of(2026, 11, 1), usual.startOn(2026, 12, 2))
        assertEquals("the back half of last November's season", LocalDate.of(2025, 11, 1), usual.startOn(2026, 3, 9))
        assertEquals(LocalDate.of(2025, 11, 1), usual.beforeOn(2026, 8, 14))
        assertEquals(LocalDate.of(2026, 6, 1), usual.beforeOn(2026, 12, 2))
    }

    @Test
    fun `a season begins on its first day, not the day after`() {
        val usual = SeasonCalendar.Default
        assertEquals(LocalDate.of(2026, 6, 1), usual.startOn(2026, 6, 1))
        assertEquals("the day before is still the old one", LocalDate.of(2025, 11, 1), usual.startOn(2026, 5, 31))
        assertEquals(LocalDate.of(2026, 11, 1), usual.startOn(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 6, 1), usual.startOn(2026, 10, 31))
        assertEquals(LocalDate.of(2025, 11, 1), usual.startOn(2026, 1, 1))
        assertEquals(LocalDate.of(2026, 11, 1), usual.startOn(2026, 12, 31))
    }

    // ------------------------------------------------------- other calendars

    @Test
    fun `three seasons a year, wrapping over New Year`() {
        val south = cal(SeasonStart(4, 1, "Virippu"), SeasonStart(8, 15, "Mundakan"), SeasonStart(12, 1, "Puncha"))
        assertEquals(LocalDate.of(2026, 4, 1), south.startOn(2026, 6, 30))
        assertEquals(LocalDate.of(2026, 8, 15), south.startOn(2026, 9, 1))
        assertEquals(LocalDate.of(2026, 12, 1), south.startOn(2026, 12, 25))
        assertEquals("January belongs to the one that began last December", LocalDate.of(2025, 12, 1), south.startOn(2026, 2, 3))
        assertEquals("Puncha", south.current(at(2026, 2, 3), india).name)
        assertEquals("Virippu", south.current(at(2026, 5, 3), india).name)
        // Each one's previous is the one before it, across the year's end.
        assertEquals(LocalDate.of(2025, 8, 15), south.beforeOn(2025, 12, 5))
        assertEquals("Mundakan", south.previous(at(2025, 12, 5), india).name)
        assertEquals(LocalDate.of(2025, 12, 1), south.beforeOn(2026, 5, 3))
        assertEquals("Puncha", south.previous(at(2026, 5, 3), india).name)
    }

    @Test
    fun `one season a year is a year`() {
        val year = cal(SeasonStart(4, 1, "Crop year"))
        assertEquals(LocalDate.of(2026, 4, 1), year.startOn(2026, 4, 1))
        assertEquals(LocalDate.of(2026, 4, 1), year.startOn(2026, 12, 31))
        assertEquals(LocalDate.of(2025, 4, 1), year.startOn(2026, 3, 31))
        assertEquals("the year before", LocalDate.of(2025, 4, 1), year.beforeOn(2026, 6, 1))
        assertEquals(LocalDate.of(2024, 4, 1), year.beforeOn(2026, 3, 1))
    }

    @Test
    fun `a season that begins on the 28th of February exists every year, leap or not`() {
        val feb = cal(SeasonStart(2, 28), SeasonStart(9, 1))
        assertEquals(LocalDate.of(2028, 2, 28), feb.startOn(2028, 3, 1))
        assertEquals(LocalDate.of(2027, 2, 28), feb.startOn(2027, 3, 1))
    }

    @Test
    fun `the day is the phone's day, not the server's`() {
        val usual = SeasonCalendar.Default
        // Half past midnight on 1 November in India is still 31 October in London.
        val ist = LocalDate.of(2026, 11, 1).atTime(0, 30).atZone(india).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 1), usual.current(ist, india).start)
        assertEquals(LocalDate.of(2026, 6, 1), usual.current(ist, ZoneId.of("Europe/London")).start)
    }

    // ------------------------------------------------------------ cleaning up

    @Test
    fun `starts are put in the order the year runs`() {
        val c = SeasonCalendar.of(listOf(SeasonStart(11, 1), SeasonStart(3, 15), SeasonStart(7, 1)))!!
        assertEquals(listOf(3 to 15, 7 to 1, 11 to 1), c.starts.map { it.month to it.day })
    }

    @Test
    fun `a date that does not exist is dropped`() {
        val c = SeasonCalendar.of(
            listOf(
                SeasonStart(2, 29), SeasonStart(2, 30), SeasonStart(4, 31), SeasonStart(13, 1),
                SeasonStart(0, 5), SeasonStart(5, 0), SeasonStart(6, 1),
            )
        )!!
        assertEquals(listOf(6 to 1), c.starts.map { it.month to it.day })
    }

    @Test
    fun `the same day twice is kept once, with the first one's name`() {
        val c = SeasonCalendar.of(listOf(SeasonStart(6, 1, "Kharif"), SeasonStart(6, 1, "Summer")))!!
        assertEquals(listOf("Kharif"), c.starts.map { it.name })
    }

    @Test
    fun `no more than four seasons`() {
        val many = (1..8).map { SeasonStart(it, 1) }
        assertEquals(4, SeasonCalendar.of(many)!!.starts.size)
        assertEquals(SeasonCalendar.Problem.TooMany, SeasonCalendar.problem(many))
    }

    @Test
    fun `with nothing usable there is no calendar`() {
        assertNull(SeasonCalendar.of(emptyList()))
        assertNull(SeasonCalendar.of(listOf(SeasonStart(2, 30), SeasonStart(14, 3))))
    }

    @Test
    fun `a repeat is a problem to say so about`() {
        assertEquals(
            SeasonCalendar.Problem.Duplicate,
            SeasonCalendar.problem(listOf(SeasonStart(6, 1), SeasonStart(6, 1))),
        )
        assertEquals(
            SeasonCalendar.Problem.None,
            SeasonCalendar.problem(listOf(SeasonStart(6, 1), SeasonStart(6, 2))),
        )
    }

    @Test
    fun `the last day of each month`() {
        assertEquals(28, SeasonCalendar.maxDay(2))
        assertEquals(30, SeasonCalendar.maxDay(4))
        assertEquals(30, SeasonCalendar.maxDay(6))
        assertEquals(30, SeasonCalendar.maxDay(9))
        assertEquals(30, SeasonCalendar.maxDay(11))
        assertEquals(31, SeasonCalendar.maxDay(1))
        assertEquals(31, SeasonCalendar.maxDay(12))
    }

    @Test
    fun `a name is tidied, and nothing is not a name`() {
        assertEquals("Kharif", SeasonCalendar.cleanName("  Kharif  "))
        assertEquals("Late Rabi", SeasonCalendar.cleanName("Late   \t Rabi"))
        assertEquals("AB", SeasonCalendar.cleanName("A\u0000B"))
        assertNull(SeasonCalendar.cleanName("   "))
        assertNull(SeasonCalendar.cleanName(null))
        assertEquals(20, SeasonCalendar.cleanName("x".repeat(50))!!.length)
    }

    // ---------------------------------------------------------------- storage

    @Test
    fun `what was written is read back`() {
        val c = cal(SeasonStart(6, 15, "Kharif"), SeasonStart(11, 1), SeasonStart(3, 1, "Zaid"))
        assertEquals(c, SeasonCalendar.parse(c.encode()))
    }

    @Test
    fun `names in any script and with awkward characters survive`() {
        val c = cal(SeasonStart(6, 1, "ਹਾੜ੍ਹੀ"), SeasonStart(11, 1, "\"Rabi\" & {late}, 'x'"))
        val back = SeasonCalendar.parse(c.encode())
        assertEquals(c, back)
        assertEquals("ਹਾੜ੍ਹੀ", back.starts[0].name)
    }

    @Test
    fun `nothing stored is the usual dates`() {
        assertEquals(SeasonCalendar.Default, SeasonCalendar.parse(null))
        assertEquals(SeasonCalendar.Default, SeasonCalendar.parse(""))
        assertEquals(SeasonCalendar.Default, SeasonCalendar.parse("   "))
    }

    @Test
    fun `damaged storage never crashes a screen, it is the usual dates`() {
        listOf(
            "not json", "{", "[", "[]", "[{}]", "[{\"m\":13,\"d\":1}]", "[{\"m\":2,\"d\":30}]",
            "[1,2,3]", "null", "{\"m\":6,\"d\":1}", "[{\"m\":\"six\",\"d\":1}]",
        ).forEach {
            assertEquals("for $it", SeasonCalendar.Default, SeasonCalendar.parse(it))
        }
    }

    @Test
    fun `a stored value from a newer app with fields this one does not know is still read`() {
        val c = SeasonCalendar.parse("""[{"m":9,"d":1,"n":"Sep","colour":"green"}]""")
        assertEquals(listOf(9 to 1), c.starts.map { it.month to it.day })
        assertEquals("Sep", c.starts.single().name)
    }

    @Test
    fun `valid starts among damaged ones are kept`() {
        val c = SeasonCalendar.parse("""[{"m":9,"d":1},{"m":2,"d":30},{"m":1,"d":1}]""")
        assertEquals(listOf(1 to 1, 9 to 1), c.starts.map { it.month to it.day })
    }

    @Test
    fun `chosen dates are not the usual ones, even when they look the same`() {
        // A business that saved June and November has said so; it is told apart from
        // one that never was asked by the stored value, not by these dates.
        val saved = SeasonCalendar.parse(SeasonCalendar.Default.encode())
        assertEquals(SeasonCalendar.Default, saved)
        assertNotEquals("[]", SeasonCalendar.Default.encode())
    }

    @Test
    fun `a season's first moment is midnight in the phone's zone`() {
        val s = SeasonCalendar.Default.current(at(2026, 8, 1), india)
        assertEquals(at(2026, 6, 1), s.startMillis(india))
    }
}
