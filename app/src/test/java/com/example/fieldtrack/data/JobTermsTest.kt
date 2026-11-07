package com.example.fieldtrack.data

import com.example.fieldtrack.data.billing.JobTerms
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.FuelTerms
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.JobEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a job is agreed on beyond its rate: a minimum, an arrival charge, who pays for the
 * diesel, waiting, a discount. The bill, the khata and every total must all say the same
 * thing, so the same job is read three ways and the answers compared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobTermsTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private fun job(
        id: String = "j", mode: BillingMode = BillingMode.PerAcre, quantity: Double = 2.0, rate: Long = 100_000,
        minimum: Long = 0, arrival: Long = 0, fuel: Long = 0, waiting: Long = 0, discount: Long = 0, step: Long = 100,
        trips: Long = 0,
    ) = JobEntity(
        id = id, orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator",
        billingMode = mode, unit = MeasureUnit.Acre, rateMinor = rate, stepMinor = step, quantity = quantity,
        tripTotalMinor = trips, minimumMinor = minimum, arrivalMinor = arrival, fuelBilledMinor = fuel,
        waitingMinor = waiting, discountMinor = discount, status = JobStatus.Completed,
        workedAt = 10, recordedAt = 10, createdAt = 10, updatedAt = 10,
    )

    // ------------------------------------------------------------------- the formula

    @Test
    fun `a plain job bills exactly as before`() {
        val j = job()
        assertEquals(200_000L, j.amountMinor)
        assertEquals(j.amountMinor, j.workAmountMinor)
    }

    @Test
    fun `the work is raised to the minimum when it comes to less`() {
        assertEquals("half an acre at 1,000 is 500, the minimum is 800", 80_000L, job(quantity = 0.5, minimum = 80_000).amountMinor)
        assertEquals("a bigger job is not capped", 200_000L, job(minimum = 80_000).amountMinor)
    }

    @Test
    fun `arrival, waiting and diesel are added and the discount taken off`() {
        val j = job(arrival = 30_000, waiting = 10_000, fuel = 25_000, discount = 5_000)
        assertEquals(200_000L + 30_000 + 10_000 + 25_000 - 5_000, j.amountMinor)
    }

    @Test
    fun `the minimum applies to the work, then the extras go on top`() {
        // Work 500 raised to 800; arrival 300 on top.
        assertEquals(110_000L, job(quantity = 0.5, minimum = 80_000, arrival = 30_000).amountMinor)
    }

    @Test
    fun `a discount can never make the bill negative`() {
        assertEquals(0L, job(discount = 999_999).amountMinor)
    }

    @Test
    fun `a fixed price and a trip total take the extras too`() {
        assertEquals(500_000L + 20_000, job(mode = BillingMode.Fixed, rate = 500_000, arrival = 20_000).amountMinor)
        assertEquals(412_300L + 20_000, job(mode = BillingMode.PerTrip, rate = 90_000, trips = 412_300, arrival = 20_000).amountMinor)
    }

    // ------------------------------------------------------------- SQL says the same

    @Test
    fun `the totals in SQL are the sum of the bills in Kotlin, extras and all`() = runTest {
        val jobs = listOf(
            job("a"), job("b", quantity = 0.5, minimum = 80_000), job("c", arrival = 30_000, fuel = 25_000),
            job("d", waiting = 10_000, discount = 5_000), job("e", mode = BillingMode.Fixed, rate = 500_000, arrival = 1),
            job("f", quantity = 0.333, rate = 123_400, minimum = 50_000, arrival = 10_000, step = 1),
            job("g", mode = BillingMode.PerTrip, rate = 90_000, trips = 412_300, discount = 12_300),
            job("h", discount = 999_999),
        )
        jobs.forEach { h.db.jobDao().upsert(it) }
        assertEquals(jobs.sumOf { it.amountMinor }, h.db.orgDao().observeEarnedBetween(org, 0, 100).first())
        val onMachine = jobs.map { it.copy(id = it.id + "-m", machineId = "m9") }
        onMachine.forEach { h.db.jobDao().upsert(it) }
        assertEquals("the machine's own total says the same", onMachine.sumOf { it.amountMinor }, h.db.jobDao().observeEarnedOnMachine(org, "m9").first())
    }

    // ----------------------------------------------------------------------- a real job

    private suspend fun start(terms: JobTerms, mode: BillingMode = BillingMode.PerAcre, rate: Long = 100_000, machine: String? = "m1") =
        h.work.startJob(
            orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator", billingMode = mode,
            unit = if (mode == BillingMode.PerHour) MeasureUnit.Hour else MeasureUnit.Acre,
            rateMinor = rate, machineId = machine, operatorPersonId = TestHarness.DRIVER, terms = terms,
        )

    @Test
    fun `finishing a job with terms bills the total and the khata agrees`() = runTest {
        val id = start(JobTerms(minimumMinor = 80_000, arrivalMinor = 30_000))
        h.work.setQuantity(org, id, 0.5)
        val charged = h.work.complete(org, id)
        assertEquals("work 500 raised to 800, plus 300 arrival", 110_000L, charged)
        val charge = h.db.ledgerDao().observeFor(org, TestHarness.CUSTOMER_A).first().single { it.kind == LedgerKind.Charge }
        assertEquals(110_000L, charge.amountMinor)
        assertEquals(110_000L, h.work.observeJob(org, id).first()!!.amountMinor)
    }

    @Test
    fun `a discount given at settlement is on the bill and in the khata`() = runTest {
        val id = start(JobTerms())
        h.work.setQuantity(org, id, 2.0)
        val charged = h.work.complete(org, id, discountMinor = 10_000)
        assertEquals(190_000L, charged)
        assertEquals(10_000L, h.work.observeJob(org, id).first()!!.discountMinor)
    }

    @Test
    fun `diesel billed extra passes on what was put in for this job and only this job`() = runTest {
        val id = start(JobTerms(fuelTerms = FuelTerms.BilledExtra))
        val other = start(JobTerms(fuelTerms = FuelTerms.BilledExtra), machine = "m2")
        h.work.addFuel(org, id, 20.0, 190_000)
        h.work.addFuel(org, other, 10.0, 95_000)
        h.work.setQuantity(org, id, 1.0)
        assertEquals("the quote before finishing says the same as the bill after", 100_000L + 190_000, h.work.quote(org, id)!!.totalMinor)
        assertEquals(100_000L + 190_000, h.work.complete(org, id))
        assertEquals(190_000L, h.work.observeJob(org, id).first()!!.fuelBilledMinor)
    }

    @Test
    fun `diesel the customer supplies or the rate includes is never added`() = runTest {
        val a = start(JobTerms(fuelTerms = FuelTerms.CustomerSupplies))
        val b = start(JobTerms(fuelTerms = FuelTerms.Included), machine = "m2")
        h.work.addFuel(org, a, 20.0, 190_000)
        h.work.addFuel(org, b, 20.0, 190_000)
        h.work.setQuantity(org, a, 1.0); h.work.setQuantity(org, b, 1.0)
        assertEquals(100_000L, h.work.complete(org, a))
        assertEquals(100_000L, h.work.complete(org, b))
    }

    @Test
    fun `time spent paused is waiting, billed at the waiting rate`() = runTest {
        val id = start(JobTerms(waitingRateMinor = 60_000), mode = BillingMode.PerHour, rate = 100_000)
        h.advanceMinutes(60)
        h.work.pause(org, id)
        h.advanceMinutes(90)            // an hour and a half waiting for the farmer
        h.work.resume(org, id)
        h.advanceMinutes(60)
        h.work.pause(org, id)
        h.advanceMinutes(30)            // half an hour more, finished while still paused
        val quote = h.work.quote(org, id)!!
        assertEquals("two hours waiting at 600 an hour", 120_000L, quote.waitingMinor)
        assertEquals("two hours worked at 1,000", 200_000L, quote.workMinor)
        assertEquals(320_000L, h.work.complete(org, id))
        assertEquals(120_000L, h.work.observeJob(org, id).first()!!.waitingMinor)
    }

    @Test
    fun `no waiting rate means waiting is not billed`() = runTest {
        val id = start(JobTerms(), mode = BillingMode.PerHour, rate = 100_000)
        h.advanceMinutes(60); h.work.pause(org, id); h.advanceMinutes(120); h.work.resume(org, id)
        h.advanceMinutes(60)
        assertEquals(0L, h.work.quote(org, id)!!.waitingMinor)
        assertEquals(200_000L, h.work.complete(org, id))
    }

    @Test
    fun `the quote names every line and adds up to the total`() = runTest {
        val id = start(JobTerms(minimumMinor = 80_000, arrivalMinor = 30_000))
        h.work.setQuantity(org, id, 0.5)
        val q = h.work.quote(org, id, discountMinor = 5_000)!!
        assertEquals(50_000L, q.workMinor)
        assertEquals(30_000L, q.minimumTopUpMinor)
        assertEquals(30_000L, q.arrivalMinor)
        assertEquals(5_000L, q.discountMinor)
        assertEquals(105_000L, q.totalMinor)
        assertTrue(q.hasExtras)
        assertEquals(q.workMinor + q.minimumTopUpMinor + q.arrivalMinor - q.discountMinor, q.totalMinor)
    }

    @Test
    fun `a plain job has a plain quote`() = runTest {
        val id = start(JobTerms())
        h.work.setQuantity(org, id, 2.0)
        val q = h.work.quote(org, id)!!
        assertEquals(200_000L, q.totalMinor)
        assertEquals(false, q.hasExtras)
    }

    @Test
    fun `an amended job keeps its terms and the khata moves by what the total moved`() = runTest {
        val id = start(JobTerms(minimumMinor = 80_000, arrivalMinor = 30_000))
        h.work.setQuantity(org, id, 0.5)
        h.work.complete(org, id)                       // 800 + 300 = 1,100
        val delta = h.work.amendCompletedJob(
            org, id, com.example.fieldtrack.data.session.Capabilities.ofOwner(), quantity = 2.0, reason = "Measured again",
        )
        assertEquals("2 acres at 1,000 is 2,000, plus 300 arrival: 2,300, up 1,200", 120_000L, delta)
    }

    @Test
    fun `the terms a job was started with are kept on the rate for the next one`() = runTest {
        val terms = JobTerms(minimumMinor = 80_000, arrivalMinor = 30_000, fuelTerms = FuelTerms.BilledExtra, waitingRateMinor = 5_000)
        h.directory.rememberRate(org, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 100_000, TestHarness.CUSTOMER_A, terms)
        val rate = h.directory.ratesFor(org, "Rotavator", TestHarness.CUSTOMER_A, BillingMode.PerAcre, MeasureUnit.Acre).first()
        assertEquals(80_000L, rate.minimumMinor)
        assertEquals(30_000L, rate.arrivalMinor)
        assertEquals(FuelTerms.BilledExtra, rate.fuelTerms)
        assertEquals(5_000L, rate.waitingRateMinor)
    }

    @Test
    fun `a per-day job is days times the day rate`() = runTest {
        val id = h.work.startJob(
            orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Hire", billingMode = BillingMode.PerDay,
            unit = MeasureUnit.Day, rateMinor = 600_000,
        )
        h.work.setQuantity(org, id, 2.5)
        assertEquals(1_500_000L, h.work.complete(org, id))
    }
}
