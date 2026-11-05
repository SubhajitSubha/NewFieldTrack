package com.example.fieldtrack.data.repository

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.CancelReason
import com.example.fieldtrack.data.db.Clock
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
import kotlinx.serialization.encodeToString

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
    ): String {
        can.require(Capability.PlanWork)
        val now = clock.now()
        val minute = startMinute?.takeIf { it in 0 until MINUTES_IN_DAY }
        val cleanNote = note?.trim()?.ifBlank { null }?.take(MAX_NOTE)
        return db.withTransaction {
            val existing = id?.let { bookings.find(orgId, it) }
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
                createdAt = now,
                updatedAt = now,
            )
            write(booking)
            booking.id
        }
    }

    suspend fun cancel(can: Capabilities, orgId: String, id: String, reason: CancelReason) =
        change(can, orgId, id) { it.copy(status = BookingStatus.Cancelled, cancelReason = reason) }

    /** The work happened without the app's clock and was entered afterwards. */
    suspend fun markDone(can: Capabilities, orgId: String, id: String) =
        change(can, orgId, id) { it.copy(status = BookingStatus.Done, cancelReason = null) }

    /** Back on the plan, for a booking cancelled or marked done by mistake. */
    suspend fun reopen(can: Capabilities, orgId: String, id: String) =
        change(can, orgId, id) { it.copy(status = BookingStatus.Planned, cancelReason = null) }

    private suspend fun change(
        can: Capabilities,
        orgId: String,
        id: String,
        edit: (BookingEntity) -> BookingEntity,
    ) {
        can.require(Capability.PlanWork)
        db.withTransaction {
            val booking = bookings.find(orgId, id) ?: return@withTransaction
            write(edit(booking).copy(updatedAt = clock.now()))
        }
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
