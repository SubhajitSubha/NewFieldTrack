package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A machine's hours, and the reminders that count down from them.
 *
 * The meter reading was never added to: finishing a job left it where it was, so
 * "service due in 250 hr" counted down from a number that only moved when somebody
 * typed a new one. Every test here is a way that could still be true.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MachineHoursTest {

    private lateinit var h: TestHarness
    private val owner = Capabilities.ofOwner()
    private val hour = 3_600_000L

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun machine(hours: Double? = null, interval: Double? = 250.0) =
        h.directory.addMachine(
            TestHarness.ORG_A, "Swaraj 855", MachineKind.Tractor,
            serviceIntervalHours = interval, hoursUsed = hours,
        )

    /** Work an hourly job on a machine for [hours] and finish it. */
    private suspend fun work(machineId: String, hours: Int) {
        val id = h.work.startJob(
            orgId = TestHarness.ORG_A, customerId = TestHarness.CUSTOMER_A,
            workType = "Ploughing", billingMode = BillingMode.PerHour, unit = MeasureUnit.Hour,
            rateRupees = 900, machineId = machineId, operatorPersonId = TestHarness.DRIVER,
        )
        h.nowMillis += hours * hour
        h.work.complete(TestHarness.ORG_A, id, TestHarness.DRIVER)
    }

    private suspend fun hoursOf(machineId: String) =
        h.directory.observeMachine(TestHarness.ORG_A, machineId).first()!!.hoursUsed

    @Test
    fun `finishing work adds its hours to the meter reading`() = runTest {
        val id = machine(hours = 1_200.0)
        assertEquals(1_200.0, hoursOf(id), 0.0)

        work(id, 6)
        work(id, 4)

        assertEquals("1200 on the meter, plus ten hours worked", 1_210.0, hoursOf(id), 0.001)
    }

    @Test
    fun `the reading typed when adding the machine is kept`() = runTest {
        // The form always had the box; the add flow dropped what was typed in it.
        val id = machine(hours = 450.0)
        assertEquals(450.0, h.db.machineDao().find(TestHarness.ORG_A, id)!!.hoursUsed, 0.0)
    }

    @Test
    fun `a new meter reading starts the count again from now`() = runTest {
        val id = machine(hours = 1_000.0)
        work(id, 8)
        assertEquals(1_008.0, hoursOf(id), 0.001)

        // Somebody reads the meter: it says 1,020. The eight hours were already
        // in it, so they must not be added again.
        h.directory.updateMachine(
            TestHarness.ORG_A, owner, id, "Swaraj 855", MachineKind.Tractor, null,
            hoursUsed = 1_020.0, serviceIntervalHours = 250.0,
        )
        assertEquals(1_020.0, hoursOf(id), 0.001)

        work(id, 3)
        assertEquals(1_023.0, hoursOf(id), 0.001)
    }

    @Test
    fun `correcting a name leaves the hours alone`() = runTest {
        val id = machine(hours = 1_000.0)
        work(id, 5)

        // The form shows 1,005 and saves it back untouched: no reading, so no
        // change to the hours — least of all rounding them down.
        h.directory.updateMachine(
            TestHarness.ORG_A, owner, id, "Swaraj 855 (new)", MachineKind.Tractor, null,
            hoursUsed = null, serviceIntervalHours = 250.0,
        )
        assertEquals(1_005.0, hoursOf(id), 0.001)
        assertEquals("Swaraj 855 (new)",
            h.directory.observeMachine(TestHarness.ORG_A, id).first()!!.name)
    }

    @Test
    fun `work that is still running is not counted until it is finished`() = runTest {
        val id = machine(hours = 100.0)
        h.work.startJob(
            TestHarness.ORG_A, TestHarness.CUSTOMER_A, "Ploughing", BillingMode.PerHour,
            MeasureUnit.Hour, 900, machineId = id, operatorPersonId = TestHarness.DRIVER,
        )
        h.nowMillis += 5 * hour
        assertEquals(100.0, hoursOf(id), 0.0)
    }

    @Test
    fun `a machine from before this existed is counted from when it was added`() = runTest {
        val id = machine(hours = 500.0)
        // As the migration leaves it, and as a machine written by an older build
        // arrives: no date on the reading.
        h.db.openHelper.writableDatabase.execSQL("UPDATE machines SET hoursAsOf = NULL WHERE id = '$id'")
        work(id, 7)
        assertEquals(507.0, hoursOf(id), 0.001)
    }

    @Test
    fun `work on one machine does not move another`() = runTest {
        val a = machine(hours = 100.0)
        val b = h.directory.addMachine(TestHarness.ORG_A, "Kartar", MachineKind.Harvester, hoursUsed = 100.0)
        work(a, 9)
        assertEquals(109.0, hoursOf(a), 0.001)
        assertEquals(100.0, hoursOf(b), 0.0)
    }

    // ------------------------------------------------------------- the reminder

    @Test
    fun `a service falls due as the work is done`() = runTest {
        val id = machine(hours = 0.0, interval = 20.0)
        assertTrue(h.work.observeServiceDue(TestHarness.ORG_A, 50.0).first().none { it.id == id } ||
            h.work.observeServiceDue(TestHarness.ORG_A, 50.0).first().single().id == id)

        work(id, 12)
        val soon = h.work.observeServiceDue(TestHarness.ORG_A, 10.0).first()
        assertEquals("8 hours to go is inside a 10 hour warning", listOf(id), soon.map { it.id })

        work(id, 10)
        val over = h.work.observeServiceDue(TestHarness.ORG_A, 10.0).first().single()
        assertEquals(22.0, over.hoursUsed, 0.001)
        assertTrue("past due", over.hoursUsed >= (over.lastServiceAtHours ?: 0.0) + 20.0)
    }

    @Test
    fun `a machine with a high meter and no service on record is not overdue on day one`() = runTest {
        // A tractor whose meter reads 1,200. Counting its service from hour zero called
        // it 950 hours overdue before anybody had done anything.
        val id = machine(hours = 1_200.0, interval = 250.0)
        assertTrue(h.work.observeServiceDue(TestHarness.ORG_A, 50.0).first().none { it.id == id })

        // It is counted from the reading it was given: due after 250 hours of work.
        work(id, 210)
        val soon = h.work.observeServiceDue(TestHarness.ORG_A, 50.0).first().single()
        assertEquals(id, soon.id)
        assertEquals(1_200.0, soon.lastServiceAtHours!!, 0.0)
        assertEquals(40.0, soon.lastServiceAtHours!! + 250.0 - soon.hoursUsed, 0.001)
    }

    @Test
    fun `a service that was logged replaces the assumption`() = runTest {
        val id = machine(hours = 1_200.0, interval = 250.0)
        work(id, 300)                                             // 100 hours past the first 250
        h.work.recordService(TestHarness.ORG_A, id, "Oil and filters", 2_000, null)

        val m = h.directory.observeMachine(TestHarness.ORG_A, id).first()!!
        assertEquals(1_500.0, m.lastServiceAtHours!!, 0.001)
        assertTrue(h.work.observeServiceDue(TestHarness.ORG_A, 50.0).first().none { it.id == id })
    }

    @Test
    fun `a machine with no schedule is never due`() = runTest {
        val id = machine(hours = 9_999.0, interval = null)
        assertTrue(h.work.observeServiceDue(TestHarness.ORG_A, 1_000.0).first().none { it.id == id })
    }

    @Test
    fun `a service is stamped with the hours worked so far and counts from there`() = runTest {
        val id = machine(hours = 0.0, interval = 50.0)
        work(id, 30)

        h.work.recordService(TestHarness.ORG_A, id, "Oil and filters", 2_400, "Singh Motors",
            partsNote = "  Oil filter, fuel filter  ")

        val record = h.db.machineDao().observeService(TestHarness.ORG_A, id).first().single()
        assertEquals(30.0, record.atMachineHours!!, 0.001)
        assertEquals("Oil filter, fuel filter", record.partsNote)

        // Fifty hours from the service, not from the meter's old reading: 30 more
        // hours is not yet due, and 20 more is.
        work(id, 30)
        assertTrue(h.work.observeServiceDue(TestHarness.ORG_A, 0.0).first().none { it.id == id })
        work(id, 20)
        assertEquals(listOf(id), h.work.observeServiceDue(TestHarness.ORG_A, 0.0).first().map { it.id })
    }

    @Test
    fun `no parts means none are stored`() = runTest {
        val id = machine()
        h.work.recordService(TestHarness.ORG_A, id, "Greased", null, null, partsNote = "   ")
        assertNull(h.db.machineDao().observeService(TestHarness.ORG_A, id).first().single().partsNote)
    }
}
