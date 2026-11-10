package com.example.fieldtrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.data.db.RequestStatus
import com.example.fieldtrack.data.db.entity.BookingRequestEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What farmers ask for through the link, and that only somebody who plans can answer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookingLinkTest {

    private lateinit var h: TestHarness
    private val org = TestHarness.ORG_A
    private val planner = Capabilities.ofOwner()
    private val plan get() = com.example.fieldtrack.data.repository.PlanRepository(db = h.db, syncQueue = h.syncQueue, clock = h.clock, ids = h.ids, ledger = h.ledger)

    @Before fun setUp() = runTest { h = TestHarness(); h.seed() }
    @After fun tearDown() = h.close()

    private suspend fun arrives(id: String = "r1", day: Long? = 20_000) {
        // As the server writes it: no sync state of its own, and the transport's stamps on it.
        RestoreApplier(h.db).apply(
            listOf(
                RemoteRow(
                    table = "booking_requests", entityId = id,
                    fields = mapOf(
                        "id" to id, "orgId" to org, "name" to "Kamau", "phone" to "+254700000001", "village" to "Nyeri",
                        "workType" to "Ploughing", "day" to day, "note" to "Two acres", "status" to "New", "bookingId" to null,
                        "createdAt" to 5L, "updatedAt" to 5L, "_device" to "booking-page", "_seq" to 0L, "_serverAt" to 9L, "_money" to 2L,
                    ),
                )
            )
        )
    }

    @Test fun `a request as the server writes it arrives on the phone and is counted as new`() = runTest {
        arrives()
        val r = h.db.bookingDao().findRequest(org, "r1")!!
        assertEquals("Kamau", r.name); assertEquals("Ploughing", r.workType); assertEquals(20_000L, r.day)
        assertEquals(RequestStatus.New, r.status)
        assertEquals(1, h.db.bookingDao().observeNewRequestCount(org).first())
    }

    @Test fun `settling a request counts it once, and a second planner tapping too finds it already dealt with`() = runTest {
        arrives()
        assertTrue(plan.settleRequest(planner, org, "r1", RequestStatus.Accepted, bookingId = "b1"))
        assertFalse(plan.settleRequest(planner, org, "r1", RequestStatus.Declined))
        val r = h.db.bookingDao().findRequest(org, "r1")!!
        assertEquals(RequestStatus.Accepted, r.status); assertEquals("b1", r.bookingId)
        assertEquals(0, h.db.bookingDao().observeNewRequestCount(org).first())
        assertEquals("the answer goes up to be shared", 1, h.db.outboxDao().nextBatch(50).count { it.tableName == "booking_requests" })
    }

    @Test fun `somebody who does not plan cannot settle a request`() = runTest {
        arrives()
        var refused = false
        try { plan.settleRequest(Capabilities(setOf(Capability.RecordWork)), org, "r1", RequestStatus.Accepted) } catch (e: Exception) { refused = true }
        assertTrue(refused)
        assertEquals(RequestStatus.New, h.db.bookingDao().findRequest(org, "r1")!!.status)
    }

    @Test fun `the rules let a handset settle a request and nothing else, and never make or remove one`() {
        val rules = File("../firestore.rules").readText()
        val block = rules.substringAfter("match /booking_requests/{requestId} {").substringBefore("\n      }")
        assertTrue(block.contains("allow read: if mayPlan(orgId)"))
        assertTrue(block.contains("hasOnly(['status', 'bookingId'"))
        assertTrue(block.contains("allow create, delete: if false"))
        // And the catch-all for ordinary collections must not reach it.
        val generic = rules.substringAfter("match /{collection}/{document} {")
        assertEquals(2, Regex("collection != 'booking_requests'").findAll(generic).count())
    }

    @Test fun `the public page and its form are the server's, and a link switched off answers not found`() {
        val code = File("../functions/index.js").readText()
        assertTrue(code.contains("where(\"bookingLink\", \"==\", true)"))
        assertTrue("an empty hidden field is how a person is told from a script", code.contains("b.website"))
        assertTrue(code.contains("MAX_PER_PHONE_PER_DAY"))
        assertTrue("every request touches the marker phones check", code.contains("doc(\"changed\")"))
        assertNotNull(File("../firebase.json").takeIf { it.exists() })
    }
}
