package com.example.fieldtrack.data

import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.CancelReason
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.db.entity.BookingEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.plan.PlanDay
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.repository.PlanRepository
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.sync.RemoteRow
import com.example.fieldtrack.data.sync.RestoreApplier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Work booked for a day.
 *
 * The things worth proving are the ones a real week turns up: the plan's day turning
 * over at the hour the business chose, a machine or driver double-booked only when the
 * hours really overlap, "started" coming from the job rather than from anything a
 * driver's phone wrote on the plan, and work that was promised and never happened
 * staying in front of somebody until it is decided.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanTest {

    private lateinit var h: TestHarness
    private lateinit var plan: PlanRepository
    private val owner = Capabilities.ofOwner()
    private val org = TestHarness.ORG_A
    private val customer = TestHarness.CUSTOMER_A

    @Before fun setUp() = runTest {
        h = TestHarness()
        plan = PlanRepository(h.db, h.syncQueue, h.clock, h.ids)
        h.seed()
    }

    @After fun tearDown() = h.close()

    private val kolkata = ZoneId.of("Asia/Kolkata")

    private suspend fun book(
        day: Long = 20_000,
        part: DayPart = DayPart.Morning,
        machine: String? = null,
        driver: String? = null,
        id: String? = null,
        minute: Int? = null,
        note: String? = null,
    ) = plan.save(
        can = owner, orgId = org, id = id, customerId = customer, fieldId = null, workType = "Rotavator",
        day = day, part = part, startMinute = minute, machineId = machine, operatorPersonId = driver, note = note,
    )

    private suspend fun row(id: String): BookingRow = plan.observe(org, id).first()!!

    private suspend fun startFrom(bookingId: String): String = h.work.startJob(
        orgId = org, customerId = customer, workType = "Rotavator", billingMode = BillingMode.PerAcre,
        unit = MeasureUnit.Acre, rateMinor = 800, operatorPersonId = TestHarness.DRIVER, bookingId = bookingId,
    )

    // ------------------------------------------------------------ the day turns over

    @Test
    fun `the early hours still belong to the day before`() {
        // 03:43 on 15 Nov in India. A harvest crew finishing the night is on the 14th.
        val at = 1_700_000_000_000L
        val fourth = PlanDay.today(at, 4, kolkata)
        assertEquals(java.time.LocalDate.of(2023, 11, 14).toEpochDay(), fourth)
    }

    @Test
    fun `a business that starts earlier moves the day on earlier`() {
        val at = 1_700_000_000_000L
        assertEquals(java.time.LocalDate.of(2023, 11, 15).toEpochDay(), PlanDay.today(at, 3, kolkata))
        assertEquals(java.time.LocalDate.of(2023, 11, 15).toEpochDay(), PlanDay.today(at, 0, kolkata))
    }

    @Test
    fun `exactly at the hour is the new day`() {
        val fourAm = java.time.ZonedDateTime.of(2023, 11, 15, 4, 0, 0, 0, kolkata).toInstant().toEpochMilli()
        val justBefore = fourAm - 1
        assertEquals(java.time.LocalDate.of(2023, 11, 15).toEpochDay(), PlanDay.today(fourAm, 4, kolkata))
        assertEquals(java.time.LocalDate.of(2023, 11, 14).toEpochDay(), PlanDay.today(justBefore, 4, kolkata))
    }

    @Test
    fun `an hour that cannot be used is the default and the business's own is kept`() {
        assertEquals(4, PlanSettings.cleanHour(null))
        assertEquals(4, PlanSettings.cleanHour(-1))
        assertEquals(4, PlanSettings.cleanHour(12))
        assertEquals(0, PlanSettings.cleanHour(0))
        assertEquals(11, PlanSettings.cleanHour(11))
        assertEquals(2, PlanSettings.cleanHour(2))
    }

    // ------------------------------------------------------------------ clashes

    private fun machine(id: String, status: MachineStatus = MachineStatus.Idle, gone: Boolean = false) = MachineEntity(
        id = id, orgId = org, name = id, kind = MachineKind.Tractor, status = status,
        createdAt = 1, updatedAt = 1, deletedAt = if (gone) 5 else null,
    )

    private fun rowOf(
        id: String, part: DayPart = DayPart.Morning, machine: String? = null, driver: String? = null,
        status: BookingStatus = BookingStatus.Planned, job: JobStatus? = null, day: Long = 100,
    ) = BookingRow(
        BookingEntity(
            id = id, orgId = org, customerId = customer, workType = "Rotavator", day = day, part = part,
            machineId = machine, operatorPersonId = driver, status = status, createdAt = 1, updatedAt = 1,
        ),
        job,
    )

    private val tractors = mapOf("t1" to machine("t1"), "t2" to machine("t2"))
    private val crew = setOf("d1", "d2")

    private fun flags(subject: BookingRow, vararg others: BookingRow, machines: Map<String, MachineEntity> = tractors, today: Long = 100) =
        PlanDay.flags(subject, listOf(subject) + others, machines, crew, today)

    @Test
    fun `morning and afternoon on one machine are not a clash`() {
        val a = rowOf("a", DayPart.Morning, machine = "t1")
        val b = rowOf("b", DayPart.Afternoon, machine = "t1")
        assertTrue(flags(a, b).isEmpty())
    }

    @Test
    fun `a whole day clashes with either half, and two mornings clash`() {
        val whole = rowOf("w", DayPart.WholeDay, machine = "t1")
        assertEquals(listOf(PlanDay.Flag.MachineBooked), flags(whole, rowOf("m", DayPart.Morning, machine = "t1")))
        assertEquals(listOf(PlanDay.Flag.MachineBooked), flags(rowOf("p", DayPart.Afternoon, machine = "t1"), whole))
        assertEquals(
            listOf(PlanDay.Flag.MachineBooked),
            flags(rowOf("m1", DayPart.Morning, machine = "t1"), rowOf("m2", DayPart.Morning, machine = "t1")),
        )
    }

    @Test
    fun `the same driver on two machines at once is caught, a different driver is not`() {
        val a = rowOf("a", machine = "t1", driver = "d1")
        assertEquals(listOf(PlanDay.Flag.DriverBooked), flags(a, rowOf("b", machine = "t2", driver = "d1")))
        assertTrue(flags(a, rowOf("c", machine = "t2", driver = "d2")).isEmpty())
    }

    @Test
    fun `a cancelled, done or finished booking frees its machine and driver`() {
        val a = rowOf("a", machine = "t1", driver = "d1")
        assertTrue(flags(a, rowOf("x", machine = "t1", driver = "d1", status = BookingStatus.Cancelled)).isEmpty())
        assertTrue(flags(a, rowOf("y", machine = "t1", driver = "d1", status = BookingStatus.Done)).isEmpty())
        assertTrue(flags(a, rowOf("z", machine = "t1", driver = "d1", job = JobStatus.Completed)).isEmpty())
        // But work running right now still holds them.
        assertEquals(
            listOf(PlanDay.Flag.MachineBooked, PlanDay.Flag.DriverBooked),
            flags(a, rowOf("r", machine = "t1", driver = "d1", job = JobStatus.Running)),
        )
    }

    @Test
    fun `a booking that is itself cancelled is not flagged for anything`() {
        val broken = mapOf("t1" to machine("t1", MachineStatus.Down))
        assertTrue(flags(rowOf("a", machine = "t1", status = BookingStatus.Cancelled), machines = broken).isEmpty())
    }

    @Test
    fun `a machine that is down is said so for any day not yet past, and not for a day that is`() {
        val broken = mapOf("t1" to machine("t1", MachineStatus.Down))
        assertEquals(listOf(PlanDay.Flag.MachineDown), flags(rowOf("a", machine = "t1", day = 100), machines = broken, today = 100))
        // Next week: nothing says it will be mended by then, so it is not assumed to be.
        assertEquals(listOf(PlanDay.Flag.MachineDown), flags(rowOf("a", machine = "t1", day = 107), machines = broken, today = 100))
        assertTrue(flags(rowOf("a", machine = "t1", day = 99), machines = broken, today = 100).isEmpty())
    }

    @Test
    fun `a machine or driver that has gone is said so`() {
        assertEquals(listOf(PlanDay.Flag.MachineGone), flags(rowOf("a", machine = "t9")))
        assertEquals(listOf(PlanDay.Flag.MachineGone), flags(rowOf("a", machine = "t1"), machines = mapOf("t1" to machine("t1", gone = true))))
        assertEquals(listOf(PlanDay.Flag.DriverGone), flags(rowOf("a", driver = "left")))
    }

    @Test
    fun `free machines and drivers leave out what is booked or broken`() {
        val machines = listOf(machine("t1"), machine("t2"), machine("t3", MachineStatus.Down), machine("t4", gone = true))
        val day = listOf(rowOf("a", machine = "t1", driver = "d1"), rowOf("b", machine = "t2", status = BookingStatus.Cancelled))
        assertEquals(listOf("t2"), PlanDay.freeMachines(machines, day).map { it.id })
        assertEquals(listOf("d2"), PlanDay.freeDrivers(listOf("d1", "d2"), day))
    }

    @Test
    fun `the day reads morning, whole day, afternoon, then by the hour named`() {
        val rows = listOf(
            rowOf("pm", DayPart.Afternoon), rowOf("all", DayPart.WholeDay), rowOf("am", DayPart.Morning),
        )
        assertEquals(listOf("am", "all", "pm"), PlanDay.inOrder(rows).map { it.booking.id })
        val timed = listOf(
            rowOf("late", DayPart.Morning).let { it.copy(booking = it.booking.copy(startMinute = 9 * 60)) },
            rowOf("early", DayPart.Morning).let { it.copy(booking = it.booking.copy(startMinute = 6 * 60)) },
            rowOf("unsaid", DayPart.Morning),
        )
        assertEquals(listOf("early", "late", "unsaid"), PlanDay.inOrder(timed).map { it.booking.id })
    }

    // --------------------------------------------------------------- the repository

    @Test
    fun `a booking is saved and queued to reach the other phones`() = runTest {
        val id = book(day = 20_001, part = DayPart.Afternoon, machine = "t1", driver = TestHarness.DRIVER, minute = 14 * 60, note = "  gate by the canal  ")
        val saved = row(id).booking
        assertEquals(BookingStatus.Planned, saved.status)
        assertEquals(20_001L, saved.day)
        assertEquals(DayPart.Afternoon, saved.part)
        assertEquals(14 * 60, saved.startMinute)
        assertEquals("gate by the canal", saved.note)
        assertEquals(0, saved.movedCount)
        val queued = h.db.outboxDao().pendingFor(SyncQueue.TABLE_BOOKINGS, id)
        assertEquals(1, queued)
    }

    @Test
    fun `somebody who may not plan cannot`() = runTest {
        val driver = Capabilities.of(
            MembershipEntity(
                id = "m", orgId = org, personId = TestHarness.DRIVER, displayName = "Jaswant",
                role = MemberRole.Operator, joinedAt = 1, updatedAt = 1,
            )
        )
        assertFalse(Capability.PlanWork in driver)
        assertThrows(Capabilities.NotAllowed::class.java) {
            kotlinx.coroutines.runBlocking {
                plan.save(driver, org, null, customer, null, "Rotavator", 1, DayPart.Morning, null, null, null, null)
            }
        }
        val id = book()
        assertThrows(Capabilities.NotAllowed::class.java) {
            kotlinx.coroutines.runBlocking { plan.cancel(driver, org, id, CancelReason.Rain) }
        }
        assertEquals(BookingStatus.Planned, row(id).booking.status)
    }

    @Test
    fun `the switch grants it, and an owner always has it`() {
        fun member(plan: Boolean) = MembershipEntity(
            id = "m", orgId = org, personId = "p", displayName = "P", role = MemberRole.Operator,
            canPlanWork = plan, joinedAt = 1, updatedAt = 1,
        )
        assertTrue(Capability.PlanWork in Capabilities.of(member(true)))
        assertFalse(Capability.PlanWork in Capabilities.of(member(false)))
        assertTrue(Capability.PlanWork in Capabilities.ofOwner())
    }

    @Test
    fun `moving it to another day is counted and correcting it is not`() = runTest {
        val id = book(day = 20_000)
        book(id = id, day = 20_000, note = "second field")
        assertEquals(0, row(id).booking.movedCount)
        book(id = id, day = 20_003)
        book(id = id, day = 20_004)
        assertEquals(2, row(id).booking.movedCount)
        assertEquals(20_004L, row(id).booking.day)
    }

    @Test
    fun `an hour that is not on a clock is not kept, and a long note is cut`() = runTest {
        val id = book(minute = 24 * 60, note = "x".repeat(500))
        assertNull(row(id).booking.startMinute)
        assertEquals(300, row(id).booking.note!!.length)
        assertNull(row(book(note = "   ")).booking.note)
    }

    @Test
    fun `cancelling keeps the reason, and reopening puts it back on the plan`() = runTest {
        val id = book()
        plan.cancel(owner, org, id, CancelReason.Rain)
        assertEquals(BookingStatus.Cancelled, row(id).booking.status)
        assertEquals(CancelReason.Rain, row(id).booking.cancelReason)
        plan.reopen(owner, org, id)
        assertEquals(BookingStatus.Planned, row(id).booking.status)
        assertNull(row(id).booking.cancelReason)
        plan.markDone(owner, org, id)
        assertEquals(BookingStatus.Done, row(id).booking.status)
    }

    @Test
    fun `an edit sends its own fields and never the status`() = runTest {
        // Two planners. One phone has been offline holding "Planned"; the other cancelled
        // the work for rain. Sending the whole row put the cancelled booking back.
        val id = book(note = "first")
        plan.cancel(owner, org, id, CancelReason.Rain)
        val cancelled = h.db.outboxDao().nextBatch(500).last { it.tableName == SyncQueue.TABLE_BOOKINGS }
        val statusKeys = kotlinx.serialization.json.Json.parseToJsonElement(cancelled.payloadJson)
            .let { (it as kotlinx.serialization.json.JsonObject).keys }
        assertEquals(setOf("id", "orgId", "status", "cancelReason", "updatedAt"), statusKeys)

        plan.reopen(owner, org, id)
        book(id = id, note = "second", machine = "t1")
        val edit = h.db.outboxDao().nextBatch(500).last { it.tableName == SyncQueue.TABLE_BOOKINGS }
        val editKeys = (kotlinx.serialization.json.Json.parseToJsonElement(edit.payloadJson)
            as kotlinx.serialization.json.JsonObject).keys
        assertTrue("note" in editKeys && "machineId" in editKeys)
        assertFalse("an edit leaves the status to whoever last set it", "status" in editKeys)
        assertFalse("status" in editKeys || "cancelReason" in editKeys || "createdAt" in editKeys)
    }

    @Test
    fun `a new booking still goes up whole`() = runTest {
        val id = book()
        val created = h.db.outboxDao().nextBatch(500).last { it.tableName == SyncQueue.TABLE_BOOKINGS && it.entityId == id }
        val keys = (kotlinx.serialization.json.Json.parseToJsonElement(created.payloadJson)
            as kotlinx.serialization.json.JsonObject).keys
        assertTrue("status" in keys && "createdAt" in keys && "customerId" in keys)
    }

    @Test
    fun `work that has been started can no longer be cancelled, finished by hand or edited`() = runTest {
        // The planner opens the booking at seven; the driver starts it at five past.
        val id = book(machine = "t1")
        startFrom(id)

        assertThrows(com.example.fieldtrack.data.repository.BookingAlreadyStarted::class.java) {
            kotlinx.coroutines.runBlocking { plan.cancel(owner, org, id, CancelReason.Rain) }
        }
        assertThrows(com.example.fieldtrack.data.repository.BookingAlreadyStarted::class.java) {
            kotlinx.coroutines.runBlocking { plan.markDone(owner, org, id) }
        }
        assertThrows(com.example.fieldtrack.data.repository.BookingAlreadyStarted::class.java) {
            kotlinx.coroutines.runBlocking { book(id = id, note = "late change") }
        }
        assertEquals("the booking is as it was", BookingStatus.Planned, row(id).booking.status)
    }

    @Test
    fun `once the job is thrown away the booking can be decided again`() = runTest {
        val id = book()
        val job = startFrom(id)
        h.work.discard(org, job)

        plan.cancel(owner, org, id, CancelReason.Farmer)

        assertEquals(BookingStatus.Cancelled, row(id).booking.status)
    }

    @Test
    fun `a flow left open while the booking was cancelled does not tie a job to it`() = runTest {
        val id = book()
        plan.cancel(owner, org, id, CancelReason.Rain)

        val job = startFrom(id)

        assertNull("the work still starts, as work nobody planned", h.work.observeJob(org, job).first()!!.bookingId)
        assertNull(row(id).jobStatus)
    }

    @Test
    fun `a second phone starting the same booking does not make two jobs claim it`() = runTest {
        val id = book()
        val first = startFrom(id)
        val second = startFrom(id)

        assertEquals(id, h.work.observeJob(org, first).first()!!.bookingId)
        assertNull(h.work.observeJob(org, second).first()!!.bookingId)
    }

    @Test
    fun `a booking is not tied to work for somebody else`() = runTest {
        val id = book()
        val job = h.work.startJob(
            orgId = org, customerId = TestHarness.CUSTOMER_B, workType = "Rotavator",
            billingMode = BillingMode.PerAcre, unit = MeasureUnit.Acre, rateMinor = 800, bookingId = id,
        )

        assertNull(h.work.observeJob(org, job).first()!!.bookingId)
    }

    @Test
    fun `a machine on a job right now is not free today, and is free on another day`() {
        val busy = machine("busy", MachineStatus.Working)
        val idle = machine("idle")
        assertEquals(listOf("idle"), PlanDay.freeMachines(listOf(busy, idle), emptyList(), isToday = true).map { it.id })
        assertEquals(
            "what is running today says nothing about next week",
            listOf("busy", "idle"), PlanDay.freeMachines(listOf(busy, idle), emptyList(), isToday = false).map { it.id },
        )
    }

    @Test
    fun `a time that is not on a clock does not bring the screen down`() {
        // Only this phone's own writes are checked, so one can arrive from the server.
        assertEquals("11:59 PM", com.example.fieldtrack.feature.plan.timeOfDay(1_500).uppercase())
        assertEquals("12:00 AM", com.example.fieldtrack.feature.plan.timeOfDay(-5).uppercase())
    }

    // ---------------------------------------------------- started comes from the job

    @Test
    fun `starting the work from a booking starts the booking without anyone writing it`() = runTest {
        val id = book()
        assertNull(row(id).jobStatus)
        val before = h.db.outboxDao().pendingFor(SyncQueue.TABLE_BOOKINGS, id)

        val job = startFrom(id)
        assertEquals(JobStatus.Running, row(id).jobStatus)
        assertEquals("the plan itself was not rewritten", before, h.db.outboxDao().pendingFor(SyncQueue.TABLE_BOOKINGS, id))
        assertEquals(id, h.work.observeJob(org, job).first()!!.bookingId)

        h.work.complete(org, job, byPersonId = TestHarness.DRIVER)
        assertEquals(JobStatus.Completed, row(id).jobStatus)
    }

    @Test
    fun `throwing the job away opens the booking again`() = runTest {
        val id = book(day = 100)
        val job = startFrom(id)
        assertTrue(plan.observeOverdue(org, 200).first().isEmpty())
        h.work.discard(org, job)
        assertNull(row(id).jobStatus)
        assertEquals(listOf(id), plan.observeOverdue(org, 200).first().map { it.booking.id })
    }

    @Test
    fun `work promised for a day that has passed stays in front of somebody until decided`() = runTest {
        val missed = book(day = 100)
        val future = book(day = 200)
        val started = book(day = 100).also { startFrom(it) }
        val cancelled = book(day = 100).also { plan.cancel(owner, org, it, CancelReason.Farmer) }
        val done = book(day = 100).also { plan.markDone(owner, org, it) }

        val overdue = plan.observeOverdue(org, today = 150).first().map { it.booking.id }
        assertEquals("only what was promised and did not happen", listOf(missed), overdue)
        assertFalse(future in overdue || started in overdue || cancelled in overdue || done in overdue)
        // On its own day it is not overdue yet.
        assertTrue(plan.observeOverdue(org, today = 100).first().isEmpty())
    }

    @Test
    fun `a customer's open booking is offered when their work is started`() = runTest {
        val today = book(day = 150)
        val yesterday = book(day = 149)
        val tomorrow = book(day = 151)
        val started = book(day = 150).also { startFrom(it) }
        val open = plan.observeOpenFor(org, customer, 150).first().map { it.booking.id }
        assertEquals(listOf(today, yesterday), open)
        assertFalse(tomorrow in open || started in open)
        assertTrue(plan.observeOpenFor(org, TestHarness.CUSTOMER_B, 150).first().isEmpty())
    }

    @Test
    fun `bookings are only ever shown for the business that made them`() = runTest {
        val mine = book(day = 100)
        h.db.bookingDao().upsert(
            BookingEntity(
                id = "theirs", orgId = TestHarness.ORG_B, customerId = TestHarness.CUSTOMER_B, workType = "Harvesting",
                day = 100, createdAt = 1, updatedAt = 1,
            )
        )
        assertEquals(listOf(mine), plan.observeDays(org, 100, 100).first().map { it.booking.id })
        assertNull(plan.find(org, "theirs"))
    }

    @Test
    fun `days are read by range and in order`() = runTest {
        val a = book(day = 101)
        val b = book(day = 100)
        book(day = 103)
        assertEquals(listOf(b, a), plan.observeDays(org, 100, 102).first().map { it.booking.id })
    }

    // ------------------------------------------------------------------------ sync

    private fun remote(id: String, day: Long = 100, status: String = "Planned", extra: Map<String, Any?> = emptyMap()) =
        RemoteRow(
            SyncQueue.TABLE_BOOKINGS, id,
            mapOf(
                "id" to id, "orgId" to org, "customerId" to customer, "workType" to "Rotavator", "day" to day,
                "part" to "Morning", "status" to status, "movedCount" to 0L, "createdAt" to 1L, "updatedAt" to 1L,
                "syncState" to "Pending",
            ) + extra,
        )

    @Test
    fun `a booking from another phone lands, and is not shown as unsent here`() = runTest {
        RestoreApplier(h.db).apply(listOf(remote("far", extra = mapOf("machineId" to "t1", "somethingNewer" to "x"))))
        val got = row("far").booking
        assertEquals("t1", got.machineId)
        assertEquals(com.example.fieldtrack.data.db.SyncState.Synced, got.syncState)
    }

    @Test
    fun `what this phone has not sent yet is not overwritten by a pull`() = runTest {
        val id = book(day = 100, note = "mine")
        RestoreApplier(h.db).apply(listOf(remote(id, day = 999, extra = mapOf("note" to "theirs"))))
        assertEquals(100L, row(id).booking.day)
        assertEquals("mine", row(id).booking.note)
    }

    @Test
    fun `a booking that arrives for a person or machine this phone has never heard of is kept`() = runTest {
        RestoreApplier(h.db).apply(
            listOf(remote("far", extra = mapOf("machineId" to "unknown-machine", "operatorPersonId" to "unknown-person")))
        )
        assertNotNull(plan.find(org, "far"))
    }
}
