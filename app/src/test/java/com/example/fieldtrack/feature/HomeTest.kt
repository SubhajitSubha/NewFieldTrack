package com.example.fieldtrack.feature

import com.example.fieldtrack.data.TestHarness
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.feature.home.heardAge
import com.example.fieldtrack.feature.home.landWorked
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.RolePreset
import com.example.fieldtrack.feature.home.HomeAlert
import com.example.fieldtrack.feature.home.HomeKind
import com.example.fieldtrack.feature.home.debtsByAge
import com.example.fieldtrack.feature.home.homeKindFor
import com.example.fieldtrack.feature.home.lastSevenDays
import com.example.fieldtrack.feature.home.liveSeconds
import com.example.fieldtrack.feature.home.machineAlerts
import com.example.fieldtrack.feature.home.overdueAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * What the three homes decide.
 *
 * The screens are drawing; these are the claims they make. Who is shown which
 * home, which figures count as late, which machines are flagged, which day a
 * job lands on in the chart — each is a sentence the home says to a person,
 * and a wrong one sends them to the wrong place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeTest {

    private val day = 24L * 60 * 60 * 1000

    private fun can(preset: RolePreset): Capabilities = Capabilities.of(
        MembershipEntity(
            id = "m1", orgId = "o1", personId = "p1", displayName = "Jaswant",
            role = preset.role,
            canSeeMoney = preset.grants.seeMoney,
            canAddCustomers = preset.grants.addCustomers,
            canCloseBills = preset.grants.closeBills,
            canEditTeam = preset.grants.editTeam,
            canEditMachines = preset.grants.editMachines,
            canPayOut = preset.grants.payOut,
            canSeePeopleCost = preset.grants.seePeopleCost,
            joinedAt = 0, updatedAt = 0,
        )
    )

    // ------------------------------------------------------------ who gets which

    @Test
    fun `an owner's home follows whether they run a crew`() {
        val owner = Capabilities.ofOwner()
        assertEquals(HomeKind.Solo, homeKindFor(MemberRole.Owner, owner, business = false))
        assertEquals(HomeKind.Yard, homeKindFor(MemberRole.Owner, owner, business = true))
    }

    @Test
    fun `a manager watches the yard even though the role says operator`() {
        // The case the old rule got wrong: role alone sent a manager to a
        // driver's screen, with no crew on it and no way to see the machines.
        assertEquals(
            HomeKind.Yard,
            homeKindFor(MemberRole.Operator, can(RolePreset.Manager), business = false),
        )
    }

    @Test
    fun `drivers of both kinds are out in the field`() {
        assertEquals(
            HomeKind.Field,
            homeKindFor(MemberRole.Operator, can(RolePreset.Driver), business = true),
        )
        assertEquals(
            HomeKind.Field,
            homeKindFor(MemberRole.Operator, can(RolePreset.SeniorDriver), business = true),
        )
    }

    @Test
    fun `the customer tab goes to whoever deals at the gate`() {
        val senior = Routes.roots(HomeKind.Field, can(RolePreset.SeniorDriver))
        val plain = Routes.roots(HomeKind.Field, can(RolePreset.Driver))

        assertTrue(Routes.CUSTOMERS in senior)
        assertFalse(Routes.MACHINES in senior)
        assertFalse("a plain driver has no customer book", Routes.CUSTOMERS in plain)
        assertTrue(Routes.MACHINES in plain)
    }

    @Test
    fun `a yard without money has no customer tab to show empty`() {
        val machinesOnly = Capabilities.of(
            MembershipEntity(
                id = "m1", orgId = "o1", personId = "p1", displayName = "Bittu",
                role = MemberRole.Operator, canEditMachines = true,
                joinedAt = 0, updatedAt = 0,
            )
        )
        assertEquals(HomeKind.Yard, homeKindFor(MemberRole.Operator, machinesOnly, true))
        assertFalse(Routes.CUSTOMERS in Routes.roots(HomeKind.Yard, machinesOnly))
        assertTrue(Routes.CUSTOMERS in Routes.roots(HomeKind.Yard, can(RolePreset.Manager)))
    }

    // ------------------------------------------------------------------ debt age

    private var seq = 0
    @Test
    fun `how long ago the phone last heard is said in the words people use`() {
        val now = 1_700_000_000_000L
        val min = 60_000L
        assertEquals(com.example.fieldtrack.feature.home.HeardAge.Never, heardAge(null, now))
        assertEquals(com.example.fieldtrack.feature.home.HeardAge.Never, heardAge(0, now))
        assertEquals(com.example.fieldtrack.feature.home.HeardAge.JustNow, heardAge(now - 20_000, now))
        assertEquals(com.example.fieldtrack.feature.home.HeardAge.Minutes(3), heardAge(now - 3 * min, now))
        assertEquals(com.example.fieldtrack.feature.home.HeardAge.Minutes(59), heardAge(now - 59 * min, now))
        assertEquals(com.example.fieldtrack.feature.home.HeardAge.Hours(2), heardAge(now - 125 * min, now))
        assertEquals(
            "a clock corrected since reads as just now, not the future",
            com.example.fieldtrack.feature.home.HeardAge.JustNow, heardAge(now + 10 * min, now),
        )
    }

    @Test
    fun `it is worth warning about only when it is long, or has never happened`() {
        val now = 1_700_000_000_000L
        val hour = 60 * 60_000L
        assertTrue(heardAge(null, now).stale)
        assertFalse(heardAge(now - 5 * hour, now).stale)
        assertTrue(heardAge(now - 6 * hour, now).stale)
        assertFalse(heardAge(now - 10 * 60_000L, now).stale)
    }

    private fun landJob(unit: MeasureUnit, quantity: Double) = JobEntity(
        id = "j-$unit-$quantity", orgId = "o1", customerId = "c1", workType = "Rotavator",
        billingMode = BillingMode.PerAcre, unit = unit, rateRupees = 1_000, quantity = quantity,
        workedAt = 0, recordedAt = 0, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `land worked is not a sum of acres and bigha`() {
        val jobs = listOf(landJob(MeasureUnit.Bigha, 4.0), landJob(MeasureUnit.Acre, 2.0), landJob(MeasureUnit.Bigha, 1.5))

        val (area, unit) = landWorked(jobs, own = MeasureUnit.Bigha)

        assertEquals("only the business's own unit is counted", 5.5, area, 0.001)
        assertEquals(MeasureUnit.Bigha, unit)
    }

    @Test
    fun `with none in the business's own unit it says the unit that was worked`() {
        val (area, unit) = landWorked(listOf(landJob(MeasureUnit.Acre, 3.0)), own = MeasureUnit.Bigha)

        assertEquals(3.0, area, 0.001)
        assertEquals(MeasureUnit.Acre, unit)
        assertEquals(0.0, landWorked(emptyList(), own = MeasureUnit.Bigha).first, 0.0)
    }

    private fun entry(kind: LedgerKind, rupees: Long, at: Long, customer: String = "c1") =
        LedgerEntryEntity(
            id = "e${++seq}", orgId = "o1", customerId = customer, kind = kind,
            amountRupees = rupees, atMillis = at, createdAt = at,
        )

    @Test
    fun `a recent payment does not make an old debt young`() {
        val now = 100 * day
        val debts = debtsByAge(
            listOf(
                entry(LedgerKind.Charge, 5_000, now - 60 * day),
                entry(LedgerKind.Charge, 3_000, now - 10 * day),
                // Last week's money clears the oldest charge first, and only part of it.
                entry(LedgerKind.Payment, 2_000, now - 5 * day),
            )
        )
        val debt = debts.single()
        assertEquals(6_000, debt.rupees)
        assertEquals("still owed since the first job", now - 60 * day, debt.oldestMillis)

        val alert = overdueAlert(debts, mapOf("c1" to "Ramesh"), now)!!
        assertEquals("c1", alert.customerId)
        assertEquals("Ramesh", alert.customerName)
        assertEquals(6_000, alert.rupees)
    }

    @Test
    fun `once the oldest charge is paid off the age moves forward`() {
        val now = 100 * day
        val debts = debtsByAge(
            listOf(
                entry(LedgerKind.Charge, 5_000, now - 60 * day),
                entry(LedgerKind.Charge, 3_000, now - 10 * day),
                entry(LedgerKind.Payment, 5_000, now - 5 * day),
            )
        )
        assertEquals(now - 10 * day, debts.single().oldestMillis)
        assertNull("ten days owed is not late", overdueAlert(debts, emptyMap(), now))
    }

    @Test
    fun `several late customers are counted, not named`() {
        val now = 100 * day
        val debts = debtsByAge(
            listOf(
                entry(LedgerKind.Charge, 4_000, now - 45 * day, customer = "c1"),
                entry(LedgerKind.Charge, 1_500, now - 31 * day, customer = "c2"),
                entry(LedgerKind.Charge, 9_000, now - 2 * day, customer = "c3"),
                entry(LedgerKind.Charge, 2_000, now - 90 * day, customer = "c4"),
                entry(LedgerKind.Payment, 2_000, now - 1 * day, customer = "c4"),
            )
        )
        val alert = overdueAlert(debts, emptyMap(), now)!!
        assertEquals(2, alert.customers)
        assertEquals(5_500, alert.rupees)
        assertNull(alert.customerId)
        assertEquals(now - 45 * day, alert.sinceMillis)
    }

    // ------------------------------------------------------------------ machines

    private fun machine(id: String, hoursUsed: Double, interval: Double?, lastAt: Double? = null) =
        MachineEntity(
            id = id, orgId = "o1", name = id, kind = MachineKind.Tractor,
            hoursUsed = hoursUsed, serviceIntervalHours = interval,
            lastServiceAtHours = lastAt, createdAt = 0, updatedAt = 0,
        )

    @Test
    fun `machines are flagged by how badly they need looking at`() {
        val alerts = machineAlerts(
            machines = listOf(
                machine("over", hoursUsed = 260.0, interval = 250.0),
                machine("soon", hoursUsed = 220.0, interval = 250.0),
                machine("fine", hoursUsed = 100.0, interval = 250.0),
                machine("unset", hoursUsed = 900.0, interval = null),
            ),
            breakdowns = emptyList(),
        )
        assertEquals(2, alerts.size)
        assertEquals(10.0, (alerts.single { it is HomeAlert.ServiceOverdue } as HomeAlert.ServiceOverdue).hoursOver, 0.0)
        assertEquals(30.0, (alerts.single { it is HomeAlert.ServiceSoon } as HomeAlert.ServiceSoon).hoursLeft, 0.0)
    }

    @Test
    fun `a broken machine is not also nagged about its service`() {
        val alerts = machineAlerts(
            machines = listOf(machine("m", hoursUsed = 300.0, interval = 250.0)),
            breakdowns = listOf(
                BreakdownEntity(
                    id = "b", orgId = "o1", machineId = "m",
                    category = BreakdownCategory.Engine, reportedAt = 5,
                )
            ),
        )
        assertEquals(listOf(HomeAlert.MachineDown("m", "m", MachineKind.Tractor, 5)), alerts)
    }

    // ------------------------------------------------------------------- the week

    @Test
    fun `seven days end today and leave out the eighth`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 26, 15, 0, 0)
        }.timeInMillis
        fun daysBack(n: Int, hour: Int) = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -n)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis

        val work = listOf(
            daysBack(0, 7) to 3.0,
            daysBack(0, 23) to 1.0,
            daysBack(6, 0) to 2.0,
            daysBack(7, 23) to 99.0, // eight days ago: off the chart, not on day one
        )
        val bars = lastSevenDays(work, now, { it.first }) { it.second }
        assertEquals(listOf(2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 4.0), bars)
    }

    @Test
    fun `a paused clock does not keep counting`() {
        assertEquals(600, liveSeconds(600, runningSince = null, now = 10_000_000))
        assertEquals(660, liveSeconds(600, runningSince = 1_000_000, now = 1_060_000))
    }

    // -------------------------------------------------------------- cash in hand

    @Test
    fun `cash with you is your own cash from today and nothing else`() = runTest {
        val h = TestHarness()
        h.seed()
        try {
            val start = startOfToday(h.nowMillis)
            suspend fun pay(rupees: Long, method: PaymentMethod, by: String, at: Long = h.nowMillis) {
                h.ledger.recordPayment(
                    orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
                    amountRupees = rupees, method = method, byPersonId = by, atMillis = at,
                )
            }
            pay(1_500, PaymentMethod.Cash, TestHarness.DRIVER)
            pay(2_000, PaymentMethod.Upi, TestHarness.DRIVER)       // went to the bank
            pay(700, PaymentMethod.Cash, TestHarness.OWNER)         // someone else's pocket
            pay(400, PaymentMethod.Cash, TestHarness.DRIVER, at = start - 1) // yesterday

            assertEquals(
                1_500L,
                h.ledger.observeCashTakenBy(
                    TestHarness.ORG_A, TestHarness.DRIVER, start, Long.MAX_VALUE,
                ).first(),
            )
        } finally {
            h.close()
        }
    }

    // --------------------------------------------------------------- uploads

    @Test
    fun `a refused change is said once, as stuck, not also as waiting`() {
        assertEquals(HomeAlert.Stuck(6), com.example.fieldtrack.feature.home.uploadAlert(6, blocked = true))
        assertEquals(HomeAlert.Waiting(6), com.example.fieldtrack.feature.home.uploadAlert(6, blocked = false))
        assertNull(com.example.fieldtrack.feature.home.uploadAlert(0, blocked = false))
    }

    @Test
    fun `the evening check is booked for the next eight o'clock`() {
        val morning = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val night = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2, 21, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val hour = 60L * 60 * 1000
        assertEquals(11 * hour, com.example.fieldtrack.notify.ClockReminderWorker.millisUntilNext(20, morning))
        // Already past it: tomorrow's, not one a minute ago.
        assertEquals(23 * hour, com.example.fieldtrack.notify.ClockReminderWorker.millisUntilNext(20, night))
    }
}
