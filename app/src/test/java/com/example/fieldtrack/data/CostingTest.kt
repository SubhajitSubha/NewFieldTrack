package com.example.fieldtrack.data

import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.dao.FuelOnJobs
import com.example.fieldtrack.data.db.dao.JobMargin
import com.example.fieldtrack.data.db.dao.RunningFacts
import com.example.fieldtrack.data.db.dao.WorkKindFacts
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind "what an hour costs" and "what a bigha costs".
 *
 * Plain numbers in, plain numbers out. The point of testing it this way is that
 * every figure here can be checked on the back of an envelope, which is what a
 * contractor will do with it.
 */
class CostingTest {

    private val hour = 3_600L

    private fun facts(hours: Int, fuel: Long, service: Long, yearHours: Int = hours) =
        RunningFacts("m", hours * hour, fuel, service, yearHours * hour)

    private fun finance(monthly: Long?, hoursPerYear: Int? = null) =
        MachineFinanceEntity("m", "o", monthly, hoursPerYear, 0)

    // -------------------------------------------------------------- per hour

    @Test
    fun `an hour costs its diesel, its repairs and its loan`() {
        // 100 h: ₹30,000 diesel, ₹12,000 repairs. Loan ₹10,000 a month over 800 h a year.
        val cost = Costing.running(facts(100, 30_000, 12_000), finance(10_000, hoursPerYear = 800))
        val r = cost.rounded()
        assertEquals(300L, r.diesel)
        assertEquals(120L, r.upkeep)
        assertEquals("120,000 a year over 800 hours", 150L, r.loan)
        assertEquals(570L, r.total)
        assertTrue(cost.enough)
    }

    @Test
    fun `a total is the sum of its rounded parts, so it adds up on paper`() {
        // 10 h: ₹1,004 diesel and ₹1,004 repairs — ₹100.4 an hour each. Rounded alone
        // each is ₹100; rounding the ₹200.8 total alone would say ₹201, and the
        // screen would show 100 and 100 over a total of 201.
        val r = Costing.running(facts(10, 1_004, 1_004), null).rounded()
        assertEquals(100L, r.diesel)
        assertEquals(100L, r.upkeep)
        assertEquals(200L, r.total)
    }

    @Test
    fun `too few hours says nothing rather than something silly`() {
        val cost = Costing.running(facts(3, 3_800, 0), null)
        assertFalse(cost.enough)
        assertEquals("one fill over three hours is not a rate", 0L, cost.rounded().diesel)
    }

    @Test
    fun `no facts at all is not a cost of nought`() {
        val cost = Costing.running(null, null)
        assertFalse(cost.enough)
        assertEquals(0.0, cost.hours, 0.0)
    }

    // ------------------------------------------------------------------ loan

    @Test
    fun `no loan entered is said so rather than counted as nought`() {
        assertEquals(Costing.Loan.NotEntered, Costing.loanShare(null, 900.0))
        assertEquals(Costing.Loan.NotEntered, Costing.loanShare(finance(null), 900.0))
        assertEquals("a paid-off loan stops counting", Costing.Loan.NotEntered,
            Costing.loanShare(finance(0), 900.0))
    }

    @Test
    fun `the owner's hours a year win over what was recorded`() {
        val loan = Costing.loanShare(finance(5_000, hoursPerYear = 600), hoursLastYear = 120.0)
        assertEquals(100.0, (loan as Costing.Loan.Counted).perHour, 0.001)
    }

    @Test
    fun `without hours a year the last twelve months stand in, once there are enough`() {
        val enough = Costing.loanShare(finance(5_000), hoursLastYear = 400.0)
        assertEquals(150.0, (enough as Costing.Loan.Counted).perHour, 0.001)

        // A machine added last month has no year behind it. Guessing one would put an
        // invented figure into every cost that follows.
        assertEquals(Costing.Loan.CannotSpreadYet, Costing.loanShare(finance(5_000), hoursLastYear = 30.0))
    }

    // -------------------------------------------------------------- per unit

    private fun kind(quantity: Double, hours: Int, earned: Long, jobs: Int = 4) = WorkKindFacts(
        workType = "Rotavator", unit = MeasureUnit.Bigha,
        quantity = quantity, seconds = hours * hour, earnedMinor = earned, jobs = jobs,
    )

    @Test
    fun `work entered afterwards with no times does not make the machine look faster`() {
        val cost = Costing.running(facts(100, 30_000, 12_000), finance(10_000, 800))
        // 18 bigha in 18 timed hours, and a 20 bigha job typed in later with no times:
        // 38 bigha over 18 hours is two an hour; the truth the clock knows is one.
        val withUntimed = WorkKindFacts(
            workType = "Rotavator", unit = MeasureUnit.Bigha,
            quantity = 38.0, timedQuantity = 18.0, seconds = 18 * hour, earnedMinor = 38_000, jobs = 3,
        )

        val unit = Costing.perUnit(cost, withUntimed)!!

        assertEquals(1.0, unit.unitsPerHour, 0.001)
        assertEquals("what it billed per bigha still counts every job", 1_000L, unit.billedMinor)
    }

    @Test
    fun `a bigha costs the hour's cost over the bigha an hour gets through`() {
        val cost = Costing.running(facts(100, 30_000, 12_000), finance(10_000, 800))  // ₹570 an hour
        // 40 bigha in 20 hours: 2 an hour. Billed ₹40,000 over 40: ₹1,000 each.
        val unit = Costing.perUnit(cost, kind(quantity = 40.0, hours = 20, earned = 40_000))!!
        assertEquals(2.0, unit.unitsPerHour, 0.001)
        assertEquals(285L, unit.costMinor)
        assertEquals(1_000L, unit.billedMinor)
        assertEquals("billed less cost, from the two figures shown", 715L, unit.leftMinor)
    }

    @Test
    fun `work that loses money shows a negative margin`() {
        val cost = Costing.running(facts(100, 30_000, 12_000), finance(10_000, 800))
        val unit = Costing.perUnit(cost, kind(quantity = 10.0, hours = 20, earned = 3_000))!!
        assertEquals(300L, unit.billedMinor)
        assertEquals(1_140L, unit.costMinor)
        assertEquals(-840L, unit.leftMinor)
    }

    @Test
    fun `too little of one kind of work says nothing about how fast it goes`() {
        val cost = Costing.running(facts(100, 30_000, 12_000), null)
        assertNull(Costing.perUnit(cost, kind(quantity = 8.0, hours = 4, earned = 8_000)))
        assertNull("nothing measured", Costing.perUnit(cost, kind(quantity = 0.0, hours = 20, earned = 0)))
    }

    @Test
    fun `no per-unit cost while the machine itself has too few hours`() {
        val cost = Costing.running(facts(6, 2_000, 0), null)
        assertNull(Costing.perUnit(cost, kind(quantity = 12.0, hours = 6, earned = 12_000)))
    }

    // ---------------------------------------------------------- a job's share

    @Test
    fun `a job carries repairs and loan for its hours, and never the diesel`() {
        val cost = Costing.running(facts(100, 30_000, 12_000), finance(10_000, 800))
        // Repairs ₹120 + loan ₹150 an hour; a 3 h job carries ₹810. Diesel (₹300 an
        // hour) is left out: the job's own page already counts the diesel logged on it.
        assertEquals(810L, Costing.machineShare(cost, 3 * hour))
    }

    @Test
    fun `no share when there is no hourly cost to share`() {
        assertNull(Costing.machineShare(Costing.running(facts(4, 100, 0), null), hour))
        assertNull(Costing.machineShare(Costing.running(facts(100, 1, 1), null), 0))
    }

    // ------------------------------------------------------------ instalments

    @Test
    fun `instalments in a stretch of time are the months in it`() {
        val day = 24L * 3_600_000
        // Ten thousand a month, for a 183-day season: six months, near enough.
        val six = Costing.instalmentsBetween(10_000, 0, 183 * day)
        assertEquals(60_123L, six)
        assertEquals(0L, Costing.instalmentsBetween(0, 0, 183 * day))
        assertEquals("backwards time is nothing", 0L, Costing.instalmentsBetween(10_000, 5 * day, day))
    }

    // ------------------------------------------------------------------ diesel

    private fun burnt(litres: Double, hours: Int, jobs: Int) = FuelOnJobs("m", litres, hours * hour, jobs)

    @Test
    fun `a machine drinking thirty percent more is flagged`() {
        // Before: 3 L an hour over 30 h. Now: 4 L an hour over 8 h — a third more.
        val rise = Costing.fuelRise(burnt(32.0, 8, 3), burnt(90.0, 30, 6))!!
        assertEquals(4.0, rise.recentLitresPerHour, 0.001)
        assertEquals(3.0, rise.beforeLitresPerHour, 0.001)
        assertEquals(33, rise.percent)
    }

    @Test
    fun `a smaller rise is not worth an owner's attention`() {
        assertNull(Costing.fuelRise(burnt(30.0, 8, 3), burnt(90.0, 30, 6)))   // 3.75 vs 3.0: 25%
    }

    @Test
    fun `a machine drinking less is never flagged`() {
        assertNull(Costing.fuelRise(burnt(16.0, 8, 3), burnt(90.0, 30, 6)))
    }

    @Test
    fun `one or two jobs are not evidence`() {
        // A fill logged mid-job can cover the job before it.
        assertNull(Costing.fuelRise(burnt(40.0, 8, 1), burnt(90.0, 30, 6)))
        assertNull("a few hours this week", Costing.fuelRise(burnt(20.0, 3, 3), burnt(90.0, 30, 6)))
        assertNull("no history to compare with", Costing.fuelRise(burnt(40.0, 8, 3), burnt(20.0, 6, 2)))
        assertNull("nothing this week", Costing.fuelRise(null, burnt(90.0, 30, 6)))
        assertNull("nothing before", Costing.fuelRise(burnt(40.0, 8, 3), null))
    }

    // -------------------------------------------------------------- job margins

    @Test
    fun `a job that cost more than it brought in lost the difference`() {
        val margin = JobMargin("j", earnedMinor = 3_000, fuelMinor = 1_800, peopleMinor = 1_700, unpriced = 0)
        assertEquals(500L, margin.lostMinor)
    }

    @Test
    fun `a job that broke even or made money lost nothing`() {
        assertNull(JobMargin("j", 3_000, 1_500, 1_500, 0).lostMinor)
        assertNull(JobMargin("j", 3_000, 500, 500, 0).lostMinor)
    }

    @Test
    fun `a job with a crew nobody priced has not lost money yet`() {
        // Earned 3,000, diesel 2,000: it looks like a loss only because the crew's
        // wages are missing from the other side, and unpriced wages are not nought.
        val margin = JobMargin("j", 3_000, 2_000, 0, unpriced = 1)
        assertNull(margin.lostMinor)
        assertNotNull(JobMargin("j", 3_000, 2_000, 2_000, unpriced = 0).lostMinor)
    }
}
