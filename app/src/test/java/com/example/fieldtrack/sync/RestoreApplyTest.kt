package com.example.fieldtrack.sync

import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import com.example.fieldtrack.data.TestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreApplyTest {
    private lateinit var h: TestHarness
    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    @Test
    fun `one row that cannot be written does not discard the rest`() = runTest {
        val applier = RestoreApplier(h.db)
        // A job event this phone already has, which is the ordinary state of a
        // pull re-reading a span it has seen. It used to raise a UNIQUE
        // constraint, and because the whole restore ran in one transaction it
        // took every other row in the pass down with it.
        val event = mapOf(
            "id" to "ev-1",
            "jobId" to "job-1",
            "orgId" to TestHarness.ORG_A,
            "type" to "Started",
            "atMillis" to 1L,
        )
        applier.apply(listOf(RemoteRow(SyncQueue.TABLE_JOB_EVENTS, "ev-1", event)))

        applier.apply(
            listOf(
                RemoteRow(SyncQueue.TABLE_JOB_EVENTS, "ev-1", event),
                RemoteRow(
                    SyncQueue.TABLE_JOB_COSTS,
                    "jc-9",
                    mapOf(
                        "id" to "jc-9",
                        "orgId" to TestHarness.ORG_A,
                        "jobId" to "job-1",
                        "kind" to "Labour",
                        "headcount" to 2L,
                        "atMillis" to 1L,
                        "createdAt" to 1L,
                    ),
                ),
            )
        )

        assertEquals(
            listOf("jc-9"),
            h.db.orgDao().observeJobCosts(TestHarness.ORG_A, "job-1").first().map { it.cost.id },
        )
    }

    @Test
    fun `a job cost document lands as a row`() = runTest {
        val applier = RestoreApplier(h.db)
        val written = applier.apply(
            listOf(
                RemoteRow(
                    SyncQueue.TABLE_JOB_COSTS,
                    "jc-1",
                    mapOf(
                        "id" to "jc-1",
                        "orgId" to TestHarness.ORG_A,
                        "jobId" to "job-1",
                        "kind" to "Labour",
                        "headcount" to 4L,
                        "atMillis" to 1L,
                        "createdAt" to 1L,
                    ),
                )
            )
        )
        assertEquals(1, written)
        assertEquals(
            listOf("jc-1"),
            h.db.orgDao().observeJobCosts(TestHarness.ORG_A, "job-1").first().map { it.cost.id },
        )
    }
}
