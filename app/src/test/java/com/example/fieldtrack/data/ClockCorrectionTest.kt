package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.repository.ClockCheck
import com.example.fieldtrack.data.repository.WorkError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * A clock left running overnight, and putting it right.
 *
 * The money is the point: an hourly job billed off a clock that ran through the
 * night charges a farmer for hours nobody worked. So the tests follow the hours
 * all the way to the khata, not just to the job row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockCorrectionTest {

    private lateinit var h: TestHarness
    private val hour = 60L * 60 * 1000

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun hourlyJob(rate: Long = 1_000): String = h.work.startJob(
        orgId = TestHarness.ORG_A,
        customerId = TestHarness.CUSTOMER_A,
        workType = "Ploughing",
        billingMode = BillingMode.PerHour,
        unit = MeasureUnit.Hour,
        rateRupees = rate,
        operatorPersonId = TestHarness.DRIVER,
    )

    private suspend fun job(id: String) = h.db.jobDao().find(TestHarness.ORG_A, id)!!

    @Test
    fun `stopping the clock at six bills the day and not the night`() = runTest {
        val start = h.nowMillis
        val id = hourlyJob()
        h.nowMillis = start + 23 * hour                 // opened again next morning

        val removed = h.work.stopClockAt(
            TestHarness.ORG_A, id, stoppedAt = start + 10 * hour, byPersonId = TestHarness.OWNER,
        )

        assertEquals(13 * 3600L, removed)
        val stopped = job(id)
        assertEquals(JobStatus.Paused, stopped.status)
        assertNull(stopped.runningSince)
        assertEquals(10 * 3600L, stopped.elapsedSeconds)

        // And the bill follows: ten hours at ₹1,000, not twenty-three.
        val charged = h.work.complete(TestHarness.ORG_A, id, byPersonId = TestHarness.OWNER)
        assertEquals(10_000L, charged)
        val charge = h.db.ledgerDao().observeFor(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
            .single { it.kind == LedgerKind.Charge }
        assertEquals(10_000L, charge.amountRupees)
    }

    @Test
    fun `time already banked before a pause is kept`() = runTest {
        val start = h.nowMillis
        val id = hourlyJob()
        h.nowMillis = start + 2 * hour
        h.work.pause(TestHarness.ORG_A, id)                  // two hours, then lunch
        h.nowMillis = start + 3 * hour
        h.work.resume(TestHarness.ORG_A, id)                 // back at it
        h.nowMillis = start + 20 * hour                      // and forgotten

        h.work.stopClockAt(TestHarness.ORG_A, id, start + 7 * hour, TestHarness.DRIVER)

        // Two before lunch, four after it.
        assertEquals(6 * 3600L, job(id).elapsedSeconds)
    }

    @Test
    fun `the stop time cannot fall outside the stretch it ends`() = runTest {
        val start = h.nowMillis
        val id = hourlyJob()
        h.nowMillis = start + 5 * hour

        // Before the clock started: held at the start, so nothing is counted.
        h.work.stopClockAt(TestHarness.ORG_A, id, start - 4 * hour, TestHarness.DRIVER)
        assertEquals(0L, job(id).elapsedSeconds)

        h.work.resume(TestHarness.ORG_A, id)
        val resumedAt = h.nowMillis
        h.nowMillis += hour
        // In the future: held at now, which is simply a pause.
        val removed = h.work.stopClockAt(TestHarness.ORG_A, id, h.nowMillis + 9 * hour, null)
        assertEquals(0L, removed)
        assertEquals((h.nowMillis - resumedAt) / 1000, job(id).elapsedSeconds)
    }

    @Test(expected = WorkError.NotRunning::class)
    fun `a paused clock has nothing to put right`() = runTest {
        val id = hourlyJob()
        h.work.pause(TestHarness.ORG_A, id)
        h.work.stopClockAt(TestHarness.ORG_A, id, h.nowMillis, null)
    }

    @Test
    fun `the correction is on the timeline with both halves of the story`() = runTest {
        val start = h.nowMillis
        val id = hourlyJob()
        h.nowMillis = start + 15 * hour
        h.work.stopClockAt(TestHarness.ORG_A, id, start + 9 * hour, TestHarness.OWNER)

        val event = h.db.jobDao().observeEvents(id).first()
            .single { it.type == JobEventType.ClockCorrected }
        assertEquals("written when it was put right", start + 15 * hour, event.atMillis)
        assertEquals(start + 9 * hour, event.textValue!!.toLong())
        assertEquals(6 * 3600.0, event.numericValue!!, 0.0)
        assertEquals(TestHarness.OWNER, event.byPersonId)
    }

    @Test
    fun `resuming after the correction leaves the night out`() = runTest {
        val start = h.nowMillis
        val id = hourlyJob()
        h.nowMillis = start + 23 * hour
        h.work.stopClockAt(TestHarness.ORG_A, id, start + 10 * hour, null)
        h.work.resume(TestHarness.ORG_A, id)            // carrying on the same field
        h.nowMillis += 2 * hour

        assertEquals(12 * 3600L, h.work.elapsedSeconds(job(id), h.nowMillis))
    }

    // ------------------------------------------------------------ when to ask

    private fun at(day: Int, hourOfDay: Int, minute: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hourOfDay, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `a clock is asked about once it runs through midnight or past twelve hours`() {
        // A normal working day: not asked.
        assertFalse(ClockCheck.looksForgotten(at(2, 7), at(2, 18)))
        // Past midnight: asked, even though it is only a few hours.
        assertTrue(ClockCheck.looksForgotten(at(2, 22), at(3, 1)))
        // A very long day without a pause: asked.
        assertTrue(ClockCheck.looksForgotten(at(2, 5), at(2, 17, 30)))
        // Paused (no running stretch): never asked.
        assertFalse(ClockCheck.looksForgotten(null, at(3, 9)))
    }
}
