package com.example.fieldtrack.feature

import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.dao.RunningFacts
import com.example.fieldtrack.data.db.dao.WorkKindFacts
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import com.example.fieldtrack.feature.home.Books
import com.example.fieldtrack.feature.home.HomeAlert
import com.example.fieldtrack.feature.home.Yard
import com.example.fieldtrack.feature.home.ownerAlerts
import com.example.fieldtrack.feature.jobs.JobDetailState
import com.example.fieldtrack.feature.machine.AddMachineState
import com.example.fieldtrack.feature.money.SeasonState
import com.example.fieldtrack.feature.work.CostHint
import com.example.fieldtrack.feature.work.StartWorkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions the cost screens make, tested where they are made.
 *
 * Each one is a sentence the screen says to a contractor — "your cost is", "this
 * lost", "the season left" — and each has a case where saying it would be wrong.
 */
class CostScreensTest {

    private val hour = 3_600L

    private fun cost(hours: Int = 100, fuel: Long = 30_000, service: Long = 12_000, monthly: Long? = 10_000) =
        Costing.running(
            RunningFacts("m", hours * hour, fuel, service, hours * hour),
            MachineFinanceEntity("m", "o", monthly, 800, 0),
        )                                                                  // ₹570 an hour

    private fun kind(quantity: Double = 40.0, hours: Int = 20, earned: Long = 40_000) = WorkKindFacts(
        "Rotavator", MeasureUnit.Bigha, quantity, hours * hour, earned, 4,
    )

    private fun start(
        cost: Costing.RunningCost? = cost(),
        kinds: List<WorkKindFacts> = listOf(kind()),
        rate: String = "",
        mode: BillingMode = BillingMode.PerAcre,
        unit: MeasureUnit = MeasureUnit.Bigha,
    ) = StartWorkState(
        workType = com.example.fieldtrack.feature.workTypeOptions.first { it.key == "Rotavator" },
        billingMode = mode, unit = unit, rateText = rate,
        machineCost = cost, machineKinds = kinds,
    )

    // ------------------------------------------------------ the hint under the rate

    @Test
    fun `with this machine's history the hint is by the unit`() {
        // 2 bigha an hour at ₹570 an hour: ₹285 a bigha.
        assertEquals(CostHint.PerUnit(285), start().costHint)
    }

    @Test
    fun `hourly work is compared hour to hour, with no history needed`() {
        val hint = start(kinds = emptyList(), mode = BillingMode.PerHour, unit = MeasureUnit.Hour).costHint
        assertEquals(CostHint.PerUnit(570), hint)
    }

    @Test
    fun `without enough of this kind of work only the hour is said`() {
        assertEquals(CostHint.OnlyHourly(570), start(kinds = emptyList()).costHint)
        // Work of another kind does not stand in for it.
        assertEquals(
            CostHint.OnlyHourly(570),
            start(kinds = listOf(kind().copy(workType = "Harvesting"))).costHint,
        )
        // Nor the same work counted another way.
        assertEquals(
            CostHint.OnlyHourly(570),
            start(kinds = listOf(kind().copy(unit = MeasureUnit.Acre))).costHint,
        )
    }

    @Test
    fun `no hint without a machine, without permission, or before there are hours`() {
        assertNull("no machine, or a reader who may not see the books", start(cost = null).costHint)
        assertNull(
            "three hours is not a rate",
            start(cost = Costing.running(RunningFacts("m", 3 * hour, 3_800, 0, 3 * hour), null)).costHint,
        )
    }

    // ------------------------------------------------------------------ a job's page

    private fun job(left: Long = 600, share: Long? = null, canTotal: Boolean = true) = JobDetailState(
        loaded = true, exists = true, amountRupees = 1_000 + left - 0,
        canSeeMoney = true, seesPeopleCost = canTotal,
        status = com.example.fieldtrack.data.db.JobStatus.Completed,
        fuelRupees = 1_000, machineShareRupees = share,
    )

    @Test
    fun `a job that cost more than it was billed says so, and by how much`() {
        val lost = job(left = -250)
        assertEquals(-250L, lost.leftRupees)
        assertEquals(250L, lost.lostRupees)
        assertNull(job(left = 0).lostRupees)
        assertNull(job(left = 400).lostRupees)
    }

    @Test
    fun `a loss is not claimed where a total cannot honestly be shown`() {
        assertNull("a reader who may not see wages", job(left = -250, canTotal = false).lostRupees)
    }

    @Test
    fun `the machine's share is taken off separately and the figure above it does not move`() {
        val j = job(left = 600, share = 810)
        assertEquals(600L, j.leftRupees)
        assertEquals(-210L, j.leftAfterMachineRupees)
        assertNull(job(left = 600, share = null).leftAfterMachineRupees)
    }

    // ----------------------------------------------------------------- the season

    @Test
    fun `loan instalments are a cost of the season and are in what it left`() {
        val season = SeasonState(
            loaded = true, canSeeMoney = true, seesPeopleCost = true,
            earnedRupees = 100_000, fuelRupees = 20_000, serviceRupees = 10_000,
            jobPeopleRupees = 15_000, loanRupees = 30_000,
        )
        assertEquals(75_000L, season.costsRupees)
        assertEquals(25_000L, season.leftRupees)
    }

    @Test
    fun `no loan entered changes nothing about the season`() {
        val without = SeasonState(
            loaded = true, canSeeMoney = true, seesPeopleCost = true,
            earnedRupees = 100_000, fuelRupees = 20_000, serviceRupees = 10_000,
        )
        assertEquals(30_000L, without.costsRupees)
        assertEquals(70_000L, without.leftRupees)
        assertTrue(without.canTotal)
    }

    // ---------------------------------------------------------------- the machine form

    @Test
    fun `a year only has so many hours`() {
        assertFalse(AddMachineState(name = "x", hoursPerYearText = "8760").hoursPerYearTooMany)
        val typo = AddMachineState(name = "x", hoursPerYearText = "8761")
        assertTrue(typo.hoursPerYearTooMany)
        assertFalse("and it cannot be saved like that", typo.canSave)
    }

    @Test
    fun `the form tells a typed meter reading from hours it was opened with`() {
        val untouched = AddMachineState(name = "x", hoursText = "1005", initialHoursText = "1005")
        assertFalse(untouched.hoursChanged)
        assertTrue(untouched.copy(hoursText = "1010").hoursChanged)
        assertFalse(untouched.financeChanged)
        assertTrue(untouched.copy(monthlyText = "12000").financeChanged)
    }

    // ------------------------------------------------------------- the diesel alert

    private fun machine(id: String) = MachineEntity(
        id = id, orgId = "o", name = "Swaraj $id", kind = MachineKind.Tractor, createdAt = 0, updatedAt = 0,
    )

    private fun fuelHigh(id: String) = HomeAlert.FuelHigh(id, "Swaraj $id", 4.0, 3.0, 33)

    private fun yard(rises: List<HomeAlert.FuelHigh>, broken: List<String> = emptyList()) = Yard(
        machines = listOf(machine("a"), machine("b")),
        breakdowns = broken.map {
            BreakdownEntity(id = "bd-$it", orgId = "o", machineId = it,
                category = BreakdownCategory.Engine, reportedAt = 1)
        },
        unpriced = 0, pending = 0, stuck = false, fuelRises = rises,
    )

    private val books = Books(emptyList(), emptyList(), emptyList(), 0)

    @Test
    fun `a machine drinking more is listed`() {
        val alerts = ownerAlerts(books, yard(listOf(fuelHigh("a"))), canSeeMoney = true, now = 0)
        assertEquals(listOf(fuelHigh("a")), alerts.filterIsInstance<HomeAlert.FuelHigh>())
    }

    @Test
    fun `a broken machine gets its own louder row and not a second one about diesel`() {
        val alerts = ownerAlerts(books, yard(listOf(fuelHigh("a")), broken = listOf("a")), true, 0)
        assertTrue(alerts.none { it is HomeAlert.FuelHigh })
        assertNotNull(alerts.firstOrNull { it is HomeAlert.MachineDown })
    }

    @Test
    fun `diesel ranks below a breakdown and an overdue service and above the small things`() {
        val order = listOf(
            HomeAlert.MachineDown("m", "m", MachineKind.Tractor, 0),
            HomeAlert.ServiceOverdue("m", "m", MachineKind.Tractor, 5.0),
            fuelHigh("a"),
            HomeAlert.Unpriced(2),
            HomeAlert.ServiceSoon("m", "m", MachineKind.Tractor, 20.0),
            HomeAlert.Waiting(3),
        )
        assertEquals(order, order.shuffled().sortedBy { it.rank })
    }
}
