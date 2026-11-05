package com.example.fieldtrack.data.plan

import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.DayPart
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.dao.BookingRow
import com.example.fieldtrack.data.db.entity.BookingEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a business has said about how its plan is read. Both are the business's own
 * to change, and the defaults are only where it starts.
 */
data class PlanSettings(
    /** The hour, 0 to 11, at which the plan moves on to the next day. */
    val dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    /** Whether a driver sees every booking, or only theirs and the ones nobody has been given. */
    val driversSeeAll: Boolean = false,
) {
    companion object {
        /**
         * Four in the morning. A harvest crew works past midnight, and at one o'clock
         * "tomorrow" is still the day they are finishing, not a fresh one.
         */
        const val DEFAULT_DAY_START_HOUR = 4
        const val MAX_DAY_START_HOUR = 11

        /** A stored hour that can be used: out of range, or missing, is the default. */
        fun cleanHour(hour: Int?): Int =
            hour?.takeIf { it in 0..MAX_DAY_START_HOUR } ?: DEFAULT_DAY_START_HOUR
    }
}

/**
 * Which calendar day the plan is on, and the day-by-day rules around it.
 *
 * Days are local calendar dates counted as `LocalDate.toEpochDay`, never moments, so a
 * booking for Tuesday stays Tuesday however the phone's clock or zone moves later.
 */
object PlanDay {

    /**
     * The plan's "today" at [now]: the calendar date, held back by [dayStartHour] so
     * that the early hours still belong to the day before.
     */
    fun today(now: Long, dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate().toEpochDay()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    /** Morning, then the whole day, then afternoon — the order work is met in. */
    private fun rank(part: DayPart) = when (part) {
        DayPart.Morning -> 0
        DayPart.WholeDay -> 1
        DayPart.Afternoon -> 2
    }

    /** One day's bookings in the order a day is read: by part, by the hour if one was named. */
    fun inOrder(rows: List<BookingRow>): List<BookingRow> =
        rows.sortedWith(
            compareBy<BookingRow>({ it.booking.day }, { rank(it.booking.part) })
                .thenBy { it.booking.startMinute ?: Int.MAX_VALUE }
                .thenBy { it.booking.createdAt }
        )

    /** Whether two parts of a day are the same hours. A whole day is both. */
    fun overlap(a: DayPart, b: DayPart) = a == DayPart.WholeDay || b == DayPart.WholeDay || a == b

    /**
     * Whether a booking still claims its machine and driver for the day: promised, and
     * not yet done. A cancelled booking, one already done, and one whose job is finished
     * all free them.
     */
    fun holds(row: BookingRow) =
        row.booking.status == BookingStatus.Planned && row.jobStatus != JobStatus.Completed

    /** Something to check before relying on a booking. Said, never enforced. */
    enum class Flag { MachineDown, MachineGone, MachineBooked, DriverGone, DriverBooked }

    /**
     * What is worth a second look on [row], given the rest of its day.
     *
     * Down is the machine's state *now*, so it is raised for any day that has not
     * passed: nothing says when it will be mended, and a plan that treated it as fixed
     * by Thursday would be a guess. Warnings only. Real days swap machines and drivers
     * at the gate, and a plan that refused to would be abandoned.
     */
    fun flags(
        row: BookingRow,
        sameDay: List<BookingRow>,
        machines: Map<String, MachineEntity>,
        activeDrivers: Set<String>,
        today: Long,
    ): List<Flag> = buildList {
        val b = row.booking
        if (!holds(row)) return@buildList

        b.machineId?.let { id ->
            val machine = machines[id]
            when {
                machine == null || machine.deletedAt != null -> add(Flag.MachineGone)
                machine.status == MachineStatus.Down && b.day >= today -> add(Flag.MachineDown)
            }
            if (clashes(row, sameDay) { it.machineId == id }) add(Flag.MachineBooked)
        }
        b.operatorPersonId?.let { id ->
            if (id !in activeDrivers) add(Flag.DriverGone)
            if (clashes(row, sameDay) { it.operatorPersonId == id }) add(Flag.DriverBooked)
        }
    }

    private fun clashes(row: BookingRow, sameDay: List<BookingRow>, sameResource: (BookingEntity) -> Boolean) =
        sameDay.any {
            it.booking.id != row.booking.id && it.booking.day == row.booking.day &&
                holds(it) && sameResource(it.booking) && overlap(it.booking.part, row.booking.part)
        }

    /**
     * Whom a booking being written would double-book: the other bookings on that day
     * that hold the same machine, or the same driver, for the same hours.
     */
    fun clashesFor(
        day: Long,
        part: DayPart,
        machineId: String?,
        driverId: String?,
        ignoreBookingId: String?,
        sameDay: List<BookingRow>,
    ): List<BookingRow> = sameDay.filter {
        it.booking.id != ignoreBookingId && it.booking.day == day && holds(it) &&
            overlap(it.booking.part, part) &&
            ((machineId != null && it.booking.machineId == machineId) ||
                (driverId != null && it.booking.operatorPersonId == driverId))
    }

    /** Machines nothing is booked on that day, and that are not broken: free to be given work. */
    fun freeMachines(machines: List<MachineEntity>, sameDay: List<BookingRow>): List<MachineEntity> {
        val taken = sameDay.filter(::holds).mapNotNull { it.booking.machineId }.toSet()
        return machines.filter { it.deletedAt == null && it.id !in taken && it.status != MachineStatus.Down }
    }

    /** Drivers nothing is booked on that day. */
    fun freeDrivers(drivers: List<String>, sameDay: List<BookingRow>): List<String> {
        val taken = sameDay.filter(::holds).mapNotNull { it.booking.operatorPersonId }.toSet()
        return drivers.filter { it !in taken }
    }
}
