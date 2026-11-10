package com.example.fieldtrack.feature

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.billing.Billing
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.feature.calculator.PriceInput
import com.example.fieldtrack.feature.calculator.PriceState
import com.example.fieldtrack.feature.calculator.PriceViewModel
import com.example.fieldtrack.feature.calculator.recleaned
import com.example.fieldtrack.feature.calculator.cleanQuantity
import com.example.fieldtrack.feature.calculator.exactQuantity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The price calculator: the sum, the rate it starts from, and what it will not say.
 *
 * The sum is checked against the real thing — a job's own amount — because the
 * calculator exists to predict a bill, and a prediction that rounds differently
 * from the bill is a wrong answer however sensible it looks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PriceCalculatorTest {

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val me = "person-owner"
    private val org = "org-bigha"

    private fun work(key: String) = workTypeOptions.first { it.key == key }

    private fun state(
        work: String = "Rotavator",
        mode: BillingMode = BillingMode.PerAcre,
        unit: MeasureUnit = MeasureUnit.Bigha,
        rate: String = "",
        quantity: String = "",
        hours: String = "",
        minutes: String = "",
    ) = PriceState(
        loaded = true,
        input = PriceInput(
            workType = work(work), billingMode = mode, unit = unit, rateText = rate,
            quantityText = quantity, hoursText = hours, minutesText = minutes,
        ),
    )

    // ----------------------------------------------------------------- the sum

    @Test
    fun `work at a rate`() {
        assertEquals(400_000L, state(rate = "1000", quantity = "4").amountMinor)
        assertEquals(270_000L, state(rate = "1200", quantity = "2.25").amountMinor)
    }

    @Test
    fun `there is no answer until there is a question`() {
        assertNull(state().amountMinor)
        assertNull("no rate", state(quantity = "4").amountMinor)
        assertNull("no work", state(rate = "1000").amountMinor)
        assertNull("nought of it", state(rate = "1000", quantity = "0").amountMinor)
        assertNull("a lone point", state(rate = "1000", quantity = ".").amountMinor)
    }

    @Test
    fun `hourly work is said as hours and minutes`() {
        val s = state("Ploughing", BillingMode.PerHour, MeasureUnit.Hour, rate = "1000", hours = "2", minutes = "30")
        assertEquals(250_000L, s.amountMinor)
        assertEquals(9_000L, s.seconds)
        assertEquals("minutes only", 50_000L, state("Ploughing", BillingMode.PerHour, MeasureUnit.Hour, rate = "1000", minutes = "30").amountMinor)
        assertEquals(
            "ninety minutes is an hour and a half, however it was typed",
            state("Ploughing", BillingMode.PerHour, MeasureUnit.Hour, rate = "1000", hours = "1", minutes = "90").amountMinor,
            s.copy(input = s.input.copy(hoursText = "2", minutesText = "30")).amountMinor,
        )
    }

    @Test
    fun `a fixed price is the amount, however much work`() {
        val s = state("Other", BillingMode.Fixed, MeasureUnit.Piece, rate = "6000")
        assertEquals(600_000L, s.amountMinor)
        assertEquals(600_000L, s.copy(input = s.input.copy(quantityText = "9")).amountMinor)
        assertNull(state("Other", BillingMode.Fixed, MeasureUnit.Piece).amountMinor)
    }

    @Test
    fun `trips, loads and kilometres`() {
        assertEquals(400_000L, state("Hauling", BillingMode.PerTrip, MeasureUnit.Trip, rate = "800", quantity = "5").amountMinor)
        assertEquals(187_500L, state("Hauling", BillingMode.PerUnit, MeasureUnit.Quintal, rate = "75", quantity = "25").amountMinor)
        assertEquals(126_000L, state("Hauling", BillingMode.PerKm, MeasureUnit.Kilometre, rate = "35", quantity = "36").amountMinor)
    }

    @Test
    fun `it rounds to the rupee exactly as a bill does`() {
        // 2.5 × 1,001 is 2,502.5, and a bill says ₹2,503.
        assertEquals(250_300L, state(rate = "1001", quantity = "2.5").amountMinor)
        for (mode in BillingMode.entries.filter { it != BillingMode.PerTrip }) {
            for (quantity in listOf(0.25, 1.0, 2.5, 2.75, 3.33, 10.0, 12.5, 99.99)) {
                for (rate in listOf(1L, 99L, 333L, 1_001L, 1_250L, 12_345L)) {
                    val job = JobEntity(
                        id = "j", orgId = "o", customerId = "c", workType = "Rotavator",
                        billingMode = mode, unit = unitsFor(mode).first(), rateMinor = rate,
                        quantity = quantity, status = JobStatus.Completed,
                        workedAt = 1, recordedAt = 1, createdAt = 1, updatedAt = 1,
                    )
                    assertEquals(
                        "$quantity at ₹$rate by $mode",
                        job.amountMinor, Billing.amount(mode, quantity, rate, job.stepMinor),
                    )
                }
            }
        }
    }

    @Test
    fun `an hourly figure matches the bill a running clock would make`() {
        // Two hours thirty on the clock, at ₹1,000: the calculator and the job agree.
        val job = JobEntity(
            id = "j", orgId = "o", customerId = "c", workType = "Ploughing",
            billingMode = BillingMode.PerHour, unit = MeasureUnit.Hour, rateMinor = 100_000,
            elapsedSeconds = 9_000, status = JobStatus.Paused,
            workedAt = 1, recordedAt = 1, createdAt = 1, updatedAt = 1,
        )
        val calc = state("Ploughing", BillingMode.PerHour, MeasureUnit.Hour, rate = "1000", hours = "2", minutes = "30")
        val h = com.example.fieldtrack.data.TestHarness()
        try { assertEquals(h.work.billableMinor(job), calc.amountMinor) } finally { h.close() }
    }

    // ------------------------------------------------------------- what is typed

    @Test
    fun `switching to trips drops the half a trip that was typed under acres`() {
        // 2.5 acres, then "per trip": nobody carries two and a half loads.
        val acres = state(mode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rate = "800", quantity = "2.5")
        val trips = acres.input.copy(billingMode = BillingMode.PerTrip, unit = MeasureUnit.Trip).recleaned()

        assertEquals("2", trips.quantityText)
        assertEquals(160_000L, acres.copy(input = trips).amountMinor)
    }

    @Test
    fun `going between units that take fractions keeps what was typed`() {
        val bigha = state(unit = MeasureUnit.Bigha, rate = "1000", quantity = "2.5").input

        assertEquals("2.5", bigha.copy(unit = MeasureUnit.Acre).recleaned().quantityText)
    }

    @Test
    fun `a quantity keeps digits, one point and two decimals`() {
        assertEquals("2.5", cleanQuantity("2,5", whole = false))
        assertEquals("2.55", cleanQuantity("2.555", whole = false))
        assertEquals("", cleanQuantity("abc", whole = false))
        assertEquals("12", cleanQuantity("1x2", whole = false))
        assertEquals("123456", cleanQuantity("1234567", whole = false))
        assertEquals("1.23", cleanQuantity("1.2.3", whole = false))
        assertEquals("a whole count has no point", "25", cleanQuantity("2.5", whole = true))
    }

    @Test
    fun `a quantity is shown as it was typed`() {
        assertEquals("4", exactQuantity(4.0))
        assertEquals("2.5", exactQuantity(2.5))
        assertEquals("the one-decimal formatter would say 2.3", "2.25", exactQuantity(2.25))
        assertEquals("0.05", exactQuantity(0.05))
    }

    // ---------------------------------------------------- the rate it starts from

    @Before fun setUp() {
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("AIzaScreenshotsOnly000000000000000000")
                    .setProjectId("fieldtrack-tests")
                    .build(),
            )
        }
    }

    @After fun tearDown() = Unit

    private fun business(role: MemberRole = MemberRole.Owner, canSeeMoney: Boolean = true) = runBlocking {
        val now = System.currentTimeMillis()
        app.db.orgDao().upsertOrg(
            OrgEntity(id = org, name = "Bigha Works", defaultLandUnit = MeasureUnit.Bigha, createdAt = now, updatedAt = now)
        )
        app.db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(org, me), orgId = org, personId = me, displayName = "Me",
                role = role, canSeeMoney = canSeeMoney, joinedAt = now, updatedAt = now,
            )
        )
        app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit()
            .putString("personId", me).putString("orgId", org).commit()
        app.sessionStore.load()
    }

    private suspend fun rate(work: String, mode: BillingMode, unit: MeasureUnit, amountMinor: Long, label: String? = null) {
        app.directoryRepository.setRate(org, work, mode, unit, amountMinor * 100, label)
    }

    /** Runs [block] with the view model's state being collected, and the main looper turning. */
    private fun withModel(block: (PriceViewModel, () -> PriceState) -> Unit) = runBlocking {
        val vm = PriceViewModel(app)
        val collecting = launch(Dispatchers.Default) { vm.state.collect { } }
        fun settle(): PriceState {
            repeat(100) {
                shadowOf(Looper.getMainLooper()).idle()
                if (vm.state.value.loaded) return vm.state.value
                Thread.sleep(20)
            }
            error("the calculator never loaded")
        }
        settle()
        block(vm) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(40); shadowOf(Looper.getMainLooper()).idle(); vm.state.value }
        collecting.cancel()
    }

    @Test
    fun `it starts in the business's own land unit, with the card's rate for it`() {
        business()
        runBlocking {
            rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_200)
            rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 3_000)
            rate("Rotavator", BillingMode.PerHour, MeasureUnit.Hour, 900)
        }
        withModel { _, state ->
            val s = state()
            assertEquals(MeasureUnit.Bigha, s.input.unit)
            assertEquals("not the acre rate, not the hourly one", "1200", s.input.rateText)
            assertEquals(listOf(120_000L), s.usualRates.map { it.amountMinor })
        }
    }

    @Test
    fun `changing the question changes the rate with it`() {
        business()
        runBlocking {
            rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_200)
            rate("Rotavator", BillingMode.PerHour, MeasureUnit.Hour, 900)
            rate("Ploughing", BillingMode.PerHour, MeasureUnit.Hour, 700)
        }
        withModel { vm, state ->
            vm.setBillingMode(BillingMode.PerHour)
            assertEquals("900", state().input.rateText)
            vm.selectWorkType(work("Ploughing"))
            assertEquals("ploughing defaults to hours", BillingMode.PerHour, state().input.billingMode)
            assertEquals("700", state().input.rateText)
        }
    }

    @Test
    fun `a rate the card gave goes when the card has nothing for the new question`() {
        business()
        runBlocking { rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_200) }
        withModel { vm, state ->
            assertEquals("1200", state().input.rateText)
            vm.setBillingMode(BillingMode.PerHour)
            assertEquals("a per-bigha rate is not a per-hour one", "", state().input.rateText)
            assertNull(state().amountMinor)
        }
    }

    @Test
    fun `a rate somebody typed stays, because what-if is the point`() {
        business()
        runBlocking { rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_200) }
        withModel { vm, state ->
            vm.setRate("1500")
            vm.setBillingMode(BillingMode.PerHour)
            assertEquals("1500", state().input.rateText)
            vm.selectWorkType(work("Harvesting"))
            assertEquals("1500", state().input.rateText)
        }
    }

    @Test
    fun `tapping a usual rate fills it, and it then follows the card again`() {
        business()
        runBlocking {
            rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_200, "wheat")
            rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_400, "paddy")
        }
        withModel { vm, state ->
            vm.setRate("999")
            assertEquals(2, state().usualRates.size)
            vm.pickRate(140_000)
            assertEquals("1400", state().input.rateText)
            vm.setBillingMode(BillingMode.PerHour)
            assertEquals("a tapped rate belongs to its question", "", state().input.rateText)
        }
    }

    @Test
    fun `a way of charging the work is not sold by is refused`() {
        business()
        withModel { vm, state ->
            vm.selectWorkType(work("Hauling"))
            assertEquals(BillingMode.PerTrip, state().input.billingMode)
            vm.setBillingMode(BillingMode.PerAcre)
            assertEquals("hauling is never by the bigha", BillingMode.PerTrip, state().input.billingMode)
            vm.setUnit(MeasureUnit.Bigha)
            assertEquals("nor is a trip counted in bigha", MeasureUnit.Trip, state().input.unit)
        }
    }

    @Test
    fun `trips are counted whole`() {
        business()
        withModel { vm, state ->
            vm.selectWorkType(work("Hauling"))
            vm.setQuantity("2.5")
            assertEquals("25", state().input.quantityText)
            assertTrue(state().countsWhole)
        }
    }

    @Test
    fun `nothing here is saved`() {
        business()
        runBlocking {
            rate("Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_200)
            val before = app.db.outboxDao().pendingCount()
            fun rows(table: String) = app.db.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }
            val jobsBefore = rows("jobs") to rows("rates")
            withModel { vm, _ ->
                vm.setQuantity("4"); vm.setRate("1000"); vm.selectWorkType(work("Harvesting"))
            }
            assertEquals("nothing queued to send", before, app.db.outboxDao().pendingCount())
            assertEquals("no job, no rate remembered", jobsBefore, rows("jobs") to rows("rates"))
        }
    }

    @Test
    fun `somebody who may not see money is not shown the sum`() {
        business(role = MemberRole.Operator, canSeeMoney = false)
        withModel { _, state -> assertFalse(state().allowed) }
    }

    @Test
    fun `somebody who may see money is`() {
        business(role = MemberRole.Operator, canSeeMoney = true)
        withModel { _, state -> assertTrue(state().allowed) }
    }
}
