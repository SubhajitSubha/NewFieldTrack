package com.example.fieldtrack.data

import com.example.fieldtrack.data.billing.JobTerms
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
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

/** An implement that wears while the tractor earns, more hands on a job, machines the list never had. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrewAndMachinesTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun twoHourJob(terms: JobTerms, machine: String?): String {
        val id = h.work.startJob(
            orgId = org, customerId = TestHarness.CUSTOMER_A, workType = "Rotavator", billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre, rateMinor = 100_000, machineId = machine, operatorPersonId = TestHarness.DRIVER, terms = terms,
        )
        h.advanceMinutes(120)
        h.work.setQuantity(org, id, 3.0)
        h.work.complete(org, id)
        return id
    }

    @Test
    fun `the implement gets the hours and the tractor gets the money`() = runTest {
        val tractor = h.directory.addMachine(org, "Swaraj", MachineKind.Tractor)
        val rotavator = h.directory.addMachine(org, "Rotavator", MachineKind.Rotavator)
        h.advanceMinutes(1)
        twoHourJob(JobTerms(implementMachineId = rotavator), tractor)
        val hours = h.db.machineDao().observeAllWithWork(org).first().associate { it.machine.id to it.workedSeconds }
        assertEquals(7_200L, hours.getValue(tractor))
        assertEquals("the implement was behind it all that time", 7_200L, hours.getValue(rotavator))
        assertEquals(300_000L, h.db.jobDao().observeEarnedOnMachine(org, tractor).first())
        assertEquals("the implement earned nothing of its own", 0L, h.db.jobDao().observeEarnedOnMachine(org, rotavator).first())
    }

    @Test
    fun `the implement's own page lists the job it was used on`() = runTest {
        val tractor = h.directory.addMachine(org, "Swaraj", MachineKind.Tractor)
        val rotavator = h.directory.addMachine(org, "Rotavator", MachineKind.Rotavator)
        val id = twoHourJob(JobTerms(implementMachineId = rotavator), tractor)
        assertEquals(listOf(id), h.db.jobDao().observeForMachine(org, rotavator, 10).first().map { it.id })
    }

    @Test
    fun `somebody else on the job sees it among their own work`() = runTest {
        val id = twoHourJob(JobTerms(coOperatorIds = "helper-1,helper-2"), null)
        assertEquals(listOf(id), h.db.jobDao().observeForOperator(org, "helper-1").first().map { it.id })
        assertEquals(listOf(id), h.db.jobDao().observeForOperator(org, "helper-2").first().map { it.id })
        assertTrue("and a stranger does not", h.db.jobDao().observeForOperator(org, "helper-").first().isEmpty())
        assertTrue(h.db.jobDao().observeForOperator(org, "someone-else").first().isEmpty())
        assertEquals("the operator still does", listOf(id), h.db.jobDao().observeForOperator(org, TestHarness.DRIVER).first().map { it.id })
    }

    @Test
    fun `a machine the list has no name for keeps the owner's own`() = runTest {
        val id = h.directory.addMachine(org, "Laser leveller", MachineKind.Other, typeLabel = "  Laser leveller ")
        assertEquals("Laser leveller", h.directory.findMachine(org, id)!!.typeLabel)
        h.directory.updateMachine(org, Capabilities.ofOwner(), id, "Laser leveller", MachineKind.Excavator, null, null, null, null)
        assertNull("a kind from the list needs no label", h.directory.findMachine(org, id)!!.typeLabel)
    }

    @Test
    fun `the machines the list now has are all there`() {
        for (kind in listOf("Excavator", "Seeder", "Mulcher", "Plough", "Harrow", "DroneSprayer", "WaterTanker", "SugarcaneHarvester", "PotatoDigger", "Ridger", "GrainCart", "Pump")) {
            assertTrue(kind, MachineKind.entries.any { it.name == kind })
        }
    }

    @Test
    fun `grain handed over is a payment that says what it was`() = runTest {
        h.ledger.recordPayment(org, TestHarness.CUSTOMER_A, 600_000, PaymentMethod.InKind, "owner", note = "20 bags of wheat")
        val entry = h.db.ledgerDao().observeFor(org, TestHarness.CUSTOMER_A).first().single()
        assertEquals(PaymentMethod.InKind, entry.method)
        assertEquals("20 bags of wheat", entry.note)
        assertEquals(-600_000L, h.ledger.observeBalance(org, TestHarness.CUSTOMER_A).first())
    }
}
