package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.repository.DirectoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rate card: what is offered, what is kept, and what a deletion costs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateCardTest {

    private lateinit var h: TestHarness
    private lateinit var directory: DirectoryRepository

    @Before
    fun setUp() = runTest {
        h = TestHarness()
        directory = DirectoryRepository(
            db = h.db,
            syncQueue = h.syncQueue,
            ledger = h.ledger,
            clock = h.clock,
            ids = h.ids,
        )
        h.seed()
    }

    @After
    fun tearDown() = h.close()

    private suspend fun outboxFor(table: String) =
        h.db.outboxDao().nextBatch(500).filter { it.tableName == table }

    // ------------------------------------------------------------ setting rates

    @Test
    fun `a rate the user typed is saved and offered back`() = runTest {
        directory.setRate(
            orgId = TestHarness.ORG_A,
            workType = "Rotavator",
            billingMode = BillingMode.PerAcre,
            unit = MeasureUnit.Acre,
            amountRupees = 1_000,
        )

        val offered = directory.suggestedRate(TestHarness.ORG_A, "Rotavator", null)
        assertEquals(1_000L, offered?.amountRupees)
    }

    @Test
    fun `one kind of work can carry several rates`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre,
            1_700, label = "wheat",
        )
        directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre,
            2_100, label = "paddy",
        )

        val card = directory.observeRateCard(TestHarness.ORG_A).first()
        assertEquals(2, card.size)
        assertEquals(
            setOf("wheat", "paddy"),
            card.mapNotNull { it.label }.toSet(),
        )
    }

    @Test
    fun `adding never silently overwrites an existing rate for the same work`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 1_700,
            label = "wheat",
        )
        directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 2_100,
            label = "paddy",
        )

        val amounts = directory.ratesFor(TestHarness.ORG_A, "Harvesting", null)
            .map { it.amountRupees }.toSet()
        assertEquals("both survive", setOf(1_700L, 2_100L), amounts)
    }

    @Test
    fun `editing by id changes that row rather than adding another`() = runTest {
        val id = directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
        )
        h.advanceMinutes(10)
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_200,
            rateId = id,
        )

        val card = directory.observeRateCard(TestHarness.ORG_A).first()
        assertEquals(1, card.size)
        assertEquals(1_200L, card.first().amountRupees)
    }

    @Test
    fun `a blank label is stored as no label at all`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 900,
            label = "   ",
        )

        assertNull(directory.observeRateCard(TestHarness.ORG_A).first().first().label)
    }

    // ------------------------------------------------- every rate reaches the server

    @Test
    fun `setting a rate queues it for the server`() = runTest {
        val before = outboxFor(SyncQueue.TABLE_RATES).size

        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
        )

        assertEquals(
            "a rate card that never leaves the phone is lost with the phone",
            before + 1,
            outboxFor(SyncQueue.TABLE_RATES).size,
        )
    }

    @Test
    fun `a rate learned from a finished job also queues`() = runTest {
        val before = outboxFor(SyncQueue.TABLE_RATES).size

        directory.rememberRate(
            orgId = TestHarness.ORG_A,
            workType = "Ploughing",
            billingMode = BillingMode.PerHour,
            unit = MeasureUnit.Hour,
            amountRupees = 1_100,
        )

        assertEquals(before + 1, outboxFor(SyncQueue.TABLE_RATES).size)
    }

    @Test
    fun `deleting a rate queues the deletion`() = runTest {
        val id = directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
        )

        directory.deleteRate(TestHarness.ORG_A, id)

        val deletes = outboxFor(SyncQueue.TABLE_RATES).filter { it.entityId == id }
        assertTrue("the server must be told it went", deletes.isNotEmpty())
        assertTrue(directory.observeRateCard(TestHarness.ORG_A).first().isEmpty())
    }

    @Test
    fun `one business cannot delete another's rate`() = runTest {
        val id = directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
        )

        directory.deleteRate(TestHarness.ORG_B, id)

        assertEquals(
            "an id on its own is not permission",
            1,
            directory.observeRateCard(TestHarness.ORG_A).first().size,
        )
    }

    // --------------------------------------------------------- customer agreements

    @Test
    fun `a customer's own rate beats the general one`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
        )
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 800,
            customerId = TestHarness.CUSTOMER_A,
        )

        assertEquals(
            800L,
            directory.suggestedRate(
                TestHarness.ORG_A, "Rotavator", TestHarness.CUSTOMER_A,
            )?.amountRupees,
        )
        assertEquals(
            "another farmer still pays the list price",
            1_000L,
            directory.suggestedRate(TestHarness.ORG_A, "Rotavator", null)?.amountRupees,
        )
    }

    @Test
    fun `the general card does not show deals struck with one customer`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 800,
            customerId = TestHarness.CUSTOMER_A,
        )

        assertTrue(directory.observeRateCard(TestHarness.ORG_A).first().isEmpty())
        assertEquals(1, directory.observeCustomerRates(TestHarness.ORG_A).first().size)
    }

    @Test
    fun `learning a rate does not rename it`() = runTest {
        val id = directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 1_700,
            label = "wheat",
        )
        h.advanceMinutes(5)

        // A finished job writes the rate back. It must not wipe the contractor's word.
        directory.rememberRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 1_800,
        )

        val row = directory.rateById(id)
        assertNotNull(row)
        assertEquals("wheat", row?.label)
        assertEquals(1_800L, row?.amountRupees)
    }

    @Test
    fun `the first job of a kind fills the empty card`() = runTest {
        directory.rememberRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
            customerId = TestHarness.CUSTOMER_A,
        )

        val card = directory.observeRateCard(TestHarness.ORG_A).first()
        assertEquals("the card must not stay empty after a season of work", 1, card.size)
        assertEquals(1_000L, card.first().amountRupees)
    }

    @Test
    fun `a cheap rate for one farmer never becomes everybody's price`() = runTest {
        directory.rememberRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
            customerId = TestHarness.CUSTOMER_A,
        )
        h.advanceMinutes(30)
        // The brother-in-law rate.
        directory.rememberRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 700,
            customerId = TestHarness.CUSTOMER_B,
        )

        assertEquals(
            1_000L,
            directory.suggestedRate(TestHarness.ORG_A, "Rotavator", null)?.amountRupees,
        )
        assertEquals(
            700L,
            directory.suggestedRate(
                TestHarness.ORG_A, "Rotavator", TestHarness.CUSTOMER_B,
            )?.amountRupees,
        )
    }

    @Test
    fun `seeding the card does not overwrite a rate the user set`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_200,
        )
        h.advanceMinutes(10)

        directory.rememberRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 900,
            customerId = TestHarness.CUSTOMER_A,
        )

        val card = directory.observeRateCard(TestHarness.ORG_A).first()
        assertEquals(1, card.size)
        assertEquals("what he typed stands", 1_200L, card.first().amountRupees)
    }

    // -------------------------------------------------------------------- ordering

    @Test
    fun `the same question always gets the same answer`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 1_700,
            label = "wheat",
        )
        h.advanceMinutes(10)
        directory.setRate(
            TestHarness.ORG_A, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 2_100,
            label = "paddy",
        )

        // Newest first, deterministically — not whichever row the engine reached.
        repeat(5) {
            assertEquals(
                2_100L,
                directory.suggestedRate(TestHarness.ORG_A, "Harvesting", null)?.amountRupees,
            )
        }
        assertEquals(
            listOf(2_100L, 1_700L),
            directory.ratesFor(TestHarness.ORG_A, "Harvesting", null).map { it.amountRupees },
        )
    }

    @Test
    fun `rates belong to one business only`() = runTest {
        directory.setRate(
            TestHarness.ORG_A, "Rotavator", BillingMode.PerAcre, MeasureUnit.Acre, 1_000,
        )

        assertTrue(directory.observeRateCard(TestHarness.ORG_B).first().isEmpty())
        assertNull(directory.suggestedRate(TestHarness.ORG_B, "Rotavator", null))
    }
}
