package com.example.fieldtrack.data.repository

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.CancelReason
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.db.entity.BookingEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The work has been started, so the booking is no longer the planner's to change. */
class BookingAlreadyStarted(val bookingId: String) :
    IllegalStateException("Booking $bookingId has been started")

/**
 * Work booked for a day.
 *
 * Only ever writes bookings. Nothing here starts a clock, touches a machine's status or
 * adds to a khata: a booking is a promise, and keeping the promise is the ordinary job
 * flow. Every write needs [Capability.PlanWork], checked here as well as hidden in the
 * UI, because a hidden button is not a boundary.
 */
class PlanRepository(
    private val db: FieldTrackDatabase,
    private val syncQueue: SyncQueue,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    /** Takes the token paid at booking onto the customer's account. Null where nothing is taken at booking. */
    private val ledger: LedgerRepository? = null,
) {
    private val bookings = db.bookingDao()

    fun observe(orgId: String, id: String): Flow<BookingRow?> = bookings.observe(orgId, id)

    fun observeDays(orgId: String, from: Long, to: Long): Flow<List<BookingRow>> =
        bookings.observeDays(orgId, from, to)

    /** Promised for a day that has passed, and never started. See [com.example.fieldtrack.data.db.dao.BookingDao.observeOverdue]. */
    fun observeOverdue(orgId: String, today: Long): Flow<List<BookingRow>> =
        bookings.observeOverdue(orgId, today)

    /** What a customer has booked and not yet started, for asking "is this that work?". */
    fun observeOpenFor(orgId: String, customerId: String, today: Long): Flow<List<BookingRow>> =
        bookings.observeOpenFor(orgId, customerId, today)

    suspend fun find(orgId: String, id: String): BookingEntity? = bookings.find(orgId, id)

    /** Books new work when [id] is null, otherwise changes that booking. Returns its id. */
    suspend fun save(
        can: Capabilities,
        orgId: String,
        id: String?,
        customerId: String,
        fieldId: String?,
        workType: String,
        day: Long,
        part: DayPart,
        startMinute: Int?,
        machineId: String?,
        operatorPersonId: String?,
        note: String?,
        /** A token taken with a new booking. It goes on the customer's account at once, as a payment. */
        advanceMinor: Long? = null,
        advanceMethod: PaymentMethod = PaymentMethod.Cash,
        byPersonId: String? = null,
    ): String {
        can.require(Capability.PlanWork)
        val now = clock.now()
        val minute = startMinute?.takeIf { it in 0 until MINUTES_IN_DAY }
        val cleanNote = note?.trim()?.ifBlank { null }?.take(MAX_NOTE)
        return db.withTransaction {
            val existing = id?.let { bookings.find(orgId, it) }
            // Re-checked here as well as on the form: a driver can start the work in
            // the minutes between the planner opening the booking and saving it.
            existing?.let { refuseIfStarted(orgId, it.id) }
            val booking = existing?.copy(
                customerId = customerId,
                fieldId = fieldId,
                workType = workType,
                day = day,
                part = part,
                startMinute = minute,
                machineId = machineId,
                operatorPersonId = operatorPersonId,
                note = cleanNote,
                // Moving it is the thing worth counting; correcting a typo is not.
                movedCount = existing.movedCount + if (day != existing.day) 1 else 0,
                updatedAt = now,
            ) ?: BookingEntity(
                id = ids.newId(),
                orgId = orgId,
                customerId = customerId,
                fieldId = fieldId,
                workType = workType,
                day = day,
                part = part,
                startMinute = minute,
                machineId = machineId,
                operatorPersonId = operatorPersonId,
                note = cleanNote,
                advanceMinor = advanceMinor?.takeIf { it > 0 && ledger != null },
                createdAt = now,
                updatedAt = now,
            )
            if (existing == null) write(booking) else writeEdit(booking)
            // Money taken for a booking is the customer's from that moment: it sits on the
            // account as a payment and comes off the bill when the work is done.
            if (existing == null && booking.advanceMinor != null && ledger != null) {
                ledger.recordPayment(
                    orgId = orgId,
                    customerId = customerId,
                    amountMinor = booking.advanceMinor,
                    method = advanceMethod,
                    byPersonId = byPersonId,
                    note = "Advance for $workType",
                )
            }
            booking.id
        }
    }

    suspend fun cancel(can: Capabilities, orgId: String, id: String, reason: CancelReason) =
        change(can, orgId, id, guardStarted = true) {
            it.copy(status = BookingStatus.Cancelled, cancelReason = reason)
        }

    /** The work happened without the app's clock and was entered afterwards. */
    suspend fun markDone(can: Capabilities, orgId: String, id: String) =
        change(can, orgId, id, guardStarted = true) {
            it.copy(status = BookingStatus.Done, cancelReason = null)
        }

    /** Back on the plan, for a booking cancelled or marked done by mistake. */
    suspend fun reopen(can: Capabilities, orgId: String, id: String) =
        change(can, orgId, id) { it.copy(status = BookingStatus.Planned, cancelReason = null) }

    private suspend fun change(
        can: Capabilities,
        orgId: String,
        id: String,
        guardStarted: Boolean = false,
        edit: (BookingEntity) -> BookingEntity,
    ) {
        can.require(Capability.PlanWork)
        db.withTransaction {
            val booking = bookings.find(orgId, id) ?: return@withTransaction
            if (guardStarted) refuseIfStarted(orgId, id)
            val changed = edit(booking).copy(updatedAt = clock.now())
            bookings.upsert(changed)
            // Only the status goes up. A planner whose phone has been offline would
            // otherwise send every field as it last saw it, and a cancellation made
            // meanwhile on another phone would be put back to Planned.
            syncQueue.enqueuePatch(
                changed.orgId, SyncQueue.TABLE_BOOKINGS, changed.id,
                buildJsonObject {
                    put("id", changed.id)
                    put("orgId", changed.orgId)
                    put("status", changed.status.name)
                    put("cancelReason", changed.cancelReason?.name)
                    put("updatedAt", changed.updatedAt)
                },
            )
        }
    }

    /** A job carries this booking, so it can no longer be cancelled, finished by hand or edited. */
    private suspend fun refuseIfStarted(orgId: String, id: String) {
        if (bookings.observe(orgId, id).first()?.jobStatus != null) throw BookingAlreadyStarted(id)
    }

    /** An edit sends the fields an edit can change, and leaves the status to whoever last set it. */
    private suspend fun writeEdit(booking: BookingEntity) {
        bookings.upsert(booking)
        syncQueue.enqueuePatch(
            booking.orgId, SyncQueue.TABLE_BOOKINGS, booking.id,
            buildJsonObject {
                put("id", booking.id)
                put("orgId", booking.orgId)
                put("customerId", booking.customerId)
                put("fieldId", booking.fieldId)
                put("workType", booking.workType)
                put("day", booking.day)
                put("part", booking.part.name)
                put("startMinute", booking.startMinute)
                put("machineId", booking.machineId)
                put("operatorPersonId", booking.operatorPersonId)
                put("note", booking.note)
                put("movedCount", booking.movedCount)
                put("updatedAt", booking.updatedAt)
            },
        )
    }

    private suspend fun write(booking: BookingEntity) {
        bookings.upsert(booking)
        syncQueue.enqueueUpsert(
            booking.orgId, SyncQueue.TABLE_BOOKINGS, booking.id, OutboxJson.encodeToString(booking)
        )
    }

    private companion object {
        const val MINUTES_IN_DAY = 24 * 60
        const val MAX_NOTE = 300
    }
}
