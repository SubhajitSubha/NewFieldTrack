package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.repository.DirectoryRepository
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
 * A farmer's plots.
 *
 * The figures on a field come from the jobs that named it, so most of what is
 * worth testing is that the naming survives the round trip and that the sums
 * count the right jobs — a field crediting itself with work still running, or
 * with another plot's acres, would be worse than no figure at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FieldTest {

    private lateinit var h: TestHarness
    private lateinit var directory: DirectoryRepository
    private val owner = Capabilities.ofOwner()

    @Before fun setUp() = runTest {
        h = TestHarness()
        directory = DirectoryRepository(
            db = h.db, syncQueue = h.syncQueue, ledger = h.ledger,
            clock = h.clock, ids = h.ids,
        )
        h.seed()
    }

    @After fun tearDown() = h.close()

    private suspend fun aField(name: String = "Canal-side plot", area: Double = 5.0) =
        directory.addField(
            orgId = TestHarness.ORG_A, can = owner, customerId = TestHarness.CUSTOMER_A,
            name = name, area = area, unit = MeasureUnit.Acre, crop = "Wheat",
        )

    private suspend fun jobOnField(fieldId: String?, acres: Double, finish: Boolean): String {
        val jobId = h.work.startJob(
            orgId = TestHarness.ORG_A,
            customerId = TestHarness.CUSTOMER_A,
            workType = "Harvesting",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            rateRupees = 1_000,
            machineId = "machine-1",
            operatorPersonId = TestHarness.DRIVER,
            fieldId = fieldId,
        )
        h.work.setQuantity(TestHarness.ORG_A, jobId, acres)
        if (finish) h.work.complete(TestHarness.ORG_A, jobId, byPersonId = TestHarness.OWNER)
        return jobId
    }

    @Test
    fun `a field is written down under the name the farmer uses`() = runTest {
        val field = aField()
        val fields = directory.observeFields(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()

        assertEquals(listOf("Canal-side plot"), fields.map { it.name })
        assertEquals(5.0, field.area, 0.0)
        assertNull("nothing has pinned it yet", field.latitude)
    }

    @Test
    fun `a field is sent up as its own row`() = runTest {
        val field = aField()
        val queued = h.db.outboxDao().nextBatch(100)
        assertTrue(queued.any { it.tableName == "fields" && it.entityId == field.id })
    }

    @Test
    fun `what a field earned counts only finished work that named it`() = runTest {
        val canal = aField()
        val behind = aField(name = "Behind the house", area = 3.0)

        jobOnField(canal.id, acres = 4.0, finish = true)      // ₹4,000
        jobOnField(canal.id, acres = 2.0, finish = true)      // ₹2,000
        jobOnField(canal.id, acres = 9.0, finish = false)     // still running
        jobOnField(behind.id, acres = 3.0, finish = true)     // another plot
        jobOnField(null, acres = 7.0, finish = true)          // nobody said where

        val totals = directory.observeFieldWork(TestHarness.ORG_A, TestHarness.CUSTOMER_A)
            .first()
            .associateBy { it.fieldId }

        // Running work has earned nothing yet, the other plot is not this one,
        // and a job that named no field belongs to none of them.
        assertEquals(6_000, totals.getValue(canal.id).earnedRupees)
        assertEquals(2, totals.getValue(canal.id).jobCount)
        assertEquals(3_000, totals.getValue(behind.id).earnedRupees)
    }

    @Test
    fun `a job can still be recorded without saying which field`() = runTest {
        aField()
        val jobId = jobOnField(null, acres = 4.0, finish = true)

        // The question is optional on purpose. A contractor who does not know
        // the plot must not be blocked from recording that the work happened.
        assertNull(h.db.jobDao().find(TestHarness.ORG_A, jobId)?.fieldId)
        val totals = directory.observeFieldWork(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
        assertTrue("a job with no field belongs to no field", totals.isEmpty())
    }

    @Test
    fun `taking a field off the list keeps the work that happened on it`() = runTest {
        val field = aField()
        jobOnField(field.id, acres = 4.0, finish = true)

        directory.removeField(TestHarness.ORG_A, owner, field.id)

        // Gone from the list offered when starting a job...
        assertTrue(
            directory.observeFields(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first().isEmpty()
        )
        // ...but the row survives, so last season's jobs can still say where
        // they were. A hard delete would leave the work nameless.
        val still = directory.observeField(TestHarness.ORG_A, field.id).first()
        assertEquals("Canal-side plot", still?.name)
    }

    @Test
    fun `correcting a field does not disturb the work already filed against it`() = runTest {
        val field = aField()
        jobOnField(field.id, acres = 4.0, finish = true)

        directory.updateField(
            orgId = TestHarness.ORG_A, can = owner, fieldId = field.id,
            name = "Canal plot", area = 6.0, unit = MeasureUnit.Acre,
            crop = "Mustard", latitude = 29.5, longitude = 75.4,
        )

        val totals = directory.observeFieldWork(TestHarness.ORG_A, TestHarness.CUSTOMER_A).first()
        assertEquals(4_000, totals.single().earnedRupees)
        val updated = directory.observeField(TestHarness.ORG_A, field.id).first()
        assertEquals("Canal plot", updated?.name)
        assertEquals(29.5, updated?.latitude)
    }

    @Test
    fun `a field needs a name and a size`() = runTest {
        assertTrue(
            runCatching {
                directory.addField(
                    orgId = TestHarness.ORG_A, can = owner,
                    customerId = TestHarness.CUSTOMER_A,
                    name = "   ", area = 5.0, unit = MeasureUnit.Acre,
                )
            }.isFailure
        )
        assertTrue(
            runCatching {
                directory.addField(
                    orgId = TestHarness.ORG_A, can = owner,
                    customerId = TestHarness.CUSTOMER_A,
                    name = "Canal-side plot", area = 0.0, unit = MeasureUnit.Acre,
                )
            }.isFailure
        )
    }

    @Test
    fun `one business cannot see another's fields`() = runTest {
        aField()
        assertTrue(
            directory.observeFields(TestHarness.ORG_B, TestHarness.CUSTOMER_A).first().isEmpty()
        )
    }
}
