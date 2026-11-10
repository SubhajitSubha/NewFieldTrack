package com.example.fieldtrack.data

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import java.io.File
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
 * Three seasons of a busy contractor: about 5,000 jobs, as many bills and payments, 300
 * farmers. The screens that open on this much data must still open at once.
 *
 * Timed on a laptop JVM, which is not a phone, so the limits are loose and exist to catch
 * the query that goes from milliseconds to seconds because an index stopped being used.
 * The figures are written to build/scale.txt for reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScaleTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val report = mutableListOf<String>()

    @Before fun setUp() { h = TestHarness() }

    @After fun tearDown() {
        File("build/scale.txt").apply { parentFile?.mkdirs() }.writeText(report.joinToString("\n"))
        h.close()
    }

    private suspend fun seed() {
        h.db.withTransaction {
            repeat(300) { c ->
                h.db.customerDao().upsert(CustomerEntity(id = "c$c", orgId = org, name = "Farmer %03d".format(c), createdAt = now, updatedAt = now))
            }
            repeat(5_000) { i ->
                val workedAt = now - (i * 3L * 365 * day / 5_000)
                val customer = "c${i % 300}"
                h.db.jobDao().upsert(
                    JobEntity(
                        id = "j$i", orgId = org, customerId = customer, workType = "Ploughing",
                        billingMode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rateMinor = 150_000,
                        quantity = 4.0, status = JobStatus.Completed, workedAt = workedAt, recordedAt = workedAt,
                        createdAt = workedAt, updatedAt = workedAt,
                    )
                )
                h.db.ledgerDao().insert(
                    LedgerEntryEntity(
                        id = "l$i", orgId = org, customerId = customer, kind = LedgerKind.Charge,
                        amountMinor = 600_000, jobId = "j$i", atMillis = workedAt, createdAt = workedAt,
                    )
                )
                if (i % 2 == 0) {
                    h.db.ledgerDao().insert(
                        LedgerEntryEntity(
                            id = "p$i", orgId = org, customerId = customer, kind = LedgerKind.Payment,
                            amountMinor = 400_000, method = PaymentMethod.Cash, atMillis = workedAt, createdAt = workedAt,
                        )
                    )
                }
            }
        }
    }

    private suspend fun <T> timed(name: String, limitMillis: Long, block: suspend () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val took = (System.nanoTime() - start) / 1_000_000
        report += "$name: $took ms"
        assertTrue("$name took $took ms (limit $limitMillis)", took <= limitMillis)
        return result
    }

    @Test fun `the screens that read the most still open at once on three seasons of work`() = runTest {
        seed()
        assertEquals(5_000, h.db.exportDao().completedJobs(org))
        val season = (now - 365 * day)..now

        timed("recent jobs (50)", 500) { h.db.jobDao().observeRecent(org, 50).first() }.also { assertEquals(50, it.size) }
        timed("this season's finished jobs", 1_500) { h.db.jobDao().observeCompletedBetween(org, season.first, season.last).first() }
            .also { assertTrue(it.size in 1_600..1_700) }
        timed("one farmer's jobs", 500) { h.db.jobDao().observeForCustomer(org, "c7").first() }.also { assertTrue(it.size in 16..17) }
        timed("every farmer's balance", 1_000) { h.db.ledgerDao().observeBalances(org).first() }.also { assertEquals(300, it.size) }
        timed("what everybody owes", 1_000) { h.db.ledgerDao().observeTotalOwed(org).first() }
        timed("the whole book, for ageing debts", 1_500) { h.db.ledgerDao().observeAll(org).first() }.also { assertEquals(7_500, it.size) }
        timed("one farmer's khata", 500) { h.db.ledgerDao().observeFor(org, "c7").first() }
        timed("earned over a season", 1_000) { h.db.orgDao().observeEarnedBetween(org, season.first, season.last).first() }
        timed("export of everything", 4_000) {
            h.db.exportDao().jobs(org); h.db.exportDao().ledger(org); h.db.exportDao().customers(org)
        }
    }
}
