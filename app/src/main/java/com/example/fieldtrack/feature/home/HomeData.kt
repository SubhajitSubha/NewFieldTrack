package com.example.fieldtrack.feature.home

import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.feature.customer.outstandingCharges
import com.example.fieldtrack.feature.startOfToday
import java.util.Calendar

/**
 * One thing a home screen asks somebody to look at.
 *
 * Every one of these is a fact the app already holds, phrased as the thing to do
 * about it. None is a score or a target: a contractor shown "efficiency 72%"
 * learns nothing he can act on, and one shown "Sonalika is down since 9:40"
 * knows exactly where to go.
 */
sealed interface HomeAlert {
    /** Lower comes first. A machine standing in a field costs money by the hour. */
    val rank: Int

    /**
     * The server refused a change, and everything queued behind it is waiting.
     *
     * First of all, because it is the one problem that grows on its own: every
     * job recorded from now on joins the queue behind it, and none of it reaches
     * the owner until somebody decides. Retrying will not help, so the row says
     * so and leads to where the decision is made.
     */
    data class Stuck(val waiting: Int) : HomeAlert {
        override val rank get() = -2
    }

    /** Somebody's clock has run through the night, or past a working day, unpaused. */
    data class ClockLeftOn(
        val jobId: String,
        val personName: String,
        val customerName: String,
        val sinceMillis: Long,
    ) : HomeAlert {
        override val rank get() = -1
    }

    data class MachineDown(
        val machineId: String,
        val machineName: String,
        val kind: MachineKind,
        val sinceMillis: Long,
    ) : HomeAlert {
        override val rank get() = 0
    }

    data class ServiceOverdue(
        val machineId: String,
        val machineName: String,
        val kind: MachineKind,
        val hoursOver: Double,
    ) : HomeAlert {
        override val rank get() = 1
    }

    /**
     * A machine using clearly more diesel per hour than it has been.
     *
     * Often the first sign of something wrong that no breakdown has reported yet:
     * a leak, a blocked filter, an implement dragging. Or of diesel going where it
     * should not. Either way a glance is worth it, and it is not something anybody
     * would notice from a list of fills.
     */
    data class FuelHigh(
        val machineId: String,
        val machineName: String,
        val recentLitresPerHour: Double,
        val beforeLitresPerHour: Double,
        val percent: Int,
    ) : HomeAlert {
        override val rank get() = 3
    }

    /** Money owed for longer than [OVERDUE_DAYS]. One customer is named; several are counted. */
    data class Overdue(
        val customers: Int,
        val amountMinor: Long,
        /** Set when exactly one customer is late, so the row can open their khata. */
        val customerId: String?,
        val customerName: String?,
        val sinceMillis: Long,
    ) : HomeAlert {
        override val rank get() = 2
    }

    /** A customer the owner asked to be reminded to chase, now that the day has come. */
    data class Chase(
        val customerId: String,
        val customerName: String,
        val amountMinor: Long,
    ) : HomeAlert {
        override val rank get() = 2
    }

    /** Crew who worked on a job and nobody has said what they were paid. */
    data class Unpriced(val count: Int) : HomeAlert {
        override val rank get() = 4
    }

    data class ServiceSoon(
        val machineId: String,
        val machineName: String,
        val kind: MachineKind,
        val hoursLeft: Double,
    ) : HomeAlert {
        override val rank get() = 5
    }

    /** Work kept on this phone that has not reached the server yet, and will once it can. */
    data class Waiting(val count: Int) : HomeAlert {
        override val rank get() = 6
    }
}

/** How long money can be out before it is late. A season's credit is normal; a month unpaid is not. */
const val OVERDUE_DAYS = 30

/** Hours of use left at which a service starts being mentioned. */
const val SERVICE_WARNING_HOURS = 50.0

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Breakdowns and services, for whichever machines this person looks after. */
fun machineAlerts(
    machines: List<MachineEntity>,
    breakdowns: List<BreakdownEntity>,
): List<HomeAlert> {
    val byId = machines.associateBy { it.id }
    val down = breakdowns.mapNotNull { breakdown ->
        val machine = byId[breakdown.machineId] ?: return@mapNotNull null
        HomeAlert.MachineDown(machine.id, machine.name, machine.kind, breakdown.reportedAt)
    }
    // A machine already broken down is not also nagged about its service.
    val downIds = down.map { it.machineId }.toSet()
    val service = machines.filter { it.id !in downIds }.mapNotNull { machine ->
        val interval = machine.serviceIntervalHours ?: return@mapNotNull null
        val left = (machine.lastServiceAtHours ?: 0.0) + interval - machine.hoursUsed
        when {
            left <= 0 -> HomeAlert.ServiceOverdue(machine.id, machine.name, machine.kind, -left)
            left <= SERVICE_WARNING_HOURS ->
                HomeAlert.ServiceSoon(machine.id, machine.name, machine.kind, left)
            else -> null
        }
    }
    return down + service
}

/**
 * Land worked, said in one unit.
 *
 * Acres, bigha and kanal are different sizes, and adding their counts together made
 * a number that was none of them. The business's own unit is used when any of the
 * work was in it; otherwise the unit of what was done. Work in another unit is left
 * out of the total, which is the honest figure, rather than summed into a wrong one.
 */
internal fun landWorked(jobs: List<JobEntity>, own: MeasureUnit): Pair<Double, MeasureUnit> {
    val land = jobs.filter { it.unit.isLand }
    val unit = own.takeIf { u -> land.any { it.unit == u } } ?: land.firstOrNull()?.unit ?: own
    return land.filter { it.unit == unit }.sumOf { it.quantity } to unit
}

/** A customer owing money, and since when the oldest of it has been owed. */
data class Debt(
    val customerId: String,
    val amountMinor: Long,
    val oldestMillis: Long,
)

/**
 * What every customer owes and how old the oldest of it is.
 *
 * Age comes from the khata's own rule — money in clears the oldest charge first —
 * so a farmer who paid last week against a bill from July is still late for July.
 * Reading "last paid a week ago" as "up to date" is the mistake this avoids.
 */
fun debtsByAge(entries: List<LedgerEntryEntity>): List<Debt> =
    entries.groupBy { it.customerId }.mapNotNull { (customerId, own) ->
        val dues = outstandingCharges(own)
        if (dues.isEmpty()) return@mapNotNull null
        Debt(
            customerId = customerId,
            amountMinor = dues.sumOf { it.outstandingMinor },
            oldestMillis = dues.minOf { it.entry.atMillis },
        )
    }

/**
 * Customers the owner said to chase from a given day, once that day has come and they
 * still owe. Gone as soon as they pay: a reminder to ask for money already received
 * would be the app nagging about its own bookkeeping.
 */
fun chaseAlerts(customers: List<CustomerEntity>, debts: List<Debt>, now: Long): List<HomeAlert.Chase> {
    val today = java.time.LocalDate.now(BusinessClock.zone).toEpochDay()
    val owing = debts.associateBy { it.customerId }
    return customers.mapNotNull { customer ->
        val day = customer.chaseOnDay ?: return@mapNotNull null
        val debt = owing[customer.id] ?: return@mapNotNull null
        if (day > today) return@mapNotNull null
        HomeAlert.Chase(customer.id, customer.name, debt.amountMinor)
    }.sortedByDescending { it.amountMinor }.take(3)
}

/** The late debts as one alert, or nothing when nobody is late. */
fun overdueAlert(
    debts: List<Debt>,
    namesById: Map<String, String>,
    now: Long,
): HomeAlert.Overdue? {
    val cutoff = now - OVERDUE_DAYS * DAY_MILLIS
    val late = debts.filter { it.oldestMillis < cutoff }
    if (late.isEmpty()) return null
    val single = late.singleOrNull()
    return HomeAlert.Overdue(
        customers = late.size,
        amountMinor = late.sumOf { it.amountMinor },
        customerId = single?.customerId,
        customerName = single?.let { namesById[it.customerId] },
        sinceMillis = late.minOf { it.oldestMillis },
    )
}

/**
 * Seven days ending today, oldest first, with [value] added into each.
 *
 * The last seven days rather than Monday-to-Sunday: on a Monday morning a
 * calendar week is one empty bar, and the shape of the last week is what
 * somebody glancing at a home screen actually wants.
 */
fun <T> lastSevenDays(items: List<T>, now: Long, at: (T) -> Long, value: (T) -> Double): List<Double> {
    val today = startOfToday(now)
    val first = Calendar.getInstance(BusinessClock.timeZone).apply {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -6)
    }.timeInMillis
    val days = DoubleArray(7)
    items.forEach { item ->
        val millis = at(item)
        if (millis < first) return@forEach
        // Walked by calendar day, not divided by 24 hours, so the day the clocks
        // change does not push an evening's work into the next bar.
        val index = dayIndexFrom(first, millis)
        if (index in 0..6) days[index] += value(item)
    }
    return days.toList()
}

private fun dayIndexFrom(first: Long, millis: Long): Int {
    val start = startOfToday(millis)
    val cursor = Calendar.getInstance(BusinessClock.timeZone).apply { timeInMillis = first }
    var index = 0
    while (cursor.timeInMillis < start && index < 8) {
        cursor.add(Calendar.DAY_OF_YEAR, 1)
        index++
    }
    return index
}

/** Seconds on the clock, including the stretch since it last resumed. */
fun liveSeconds(elapsedSeconds: Long, runningSince: Long?, now: Long): Long =
    elapsedSeconds + (runningSince?.let { (now - it).coerceAtLeast(0L) / 1000 } ?: 0L)

/**
 * What the upload queue says, as an alert: stuck, waiting, or nothing.
 *
 * Never both. A refused change makes everything behind it wait too, so "6
 * waiting" beside "1 refused" would be counting the same problem twice and
 * making the smaller, calmer one look like a separate thing to deal with.
 */
fun uploadAlert(pending: Int, blocked: Boolean): HomeAlert? = when {
    blocked -> HomeAlert.Stuck(pending)
    pending > 0 -> HomeAlert.Waiting(pending)
    else -> null
}
