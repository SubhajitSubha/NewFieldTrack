package com.example.fieldtrack.feature.home

import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
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

    /** Money owed for longer than [OVERDUE_DAYS]. One customer is named; several are counted. */
    data class Overdue(
        val customers: Int,
        val rupees: Long,
        /** Set when exactly one customer is late, so the row can open their khata. */
        val customerId: String?,
        val customerName: String?,
        val sinceMillis: Long,
    ) : HomeAlert {
        override val rank get() = 2
    }

    /** Crew who worked on a job and nobody has said what they were paid. */
    data class Unpriced(val count: Int) : HomeAlert {
        override val rank get() = 3
    }

    data class ServiceSoon(
        val machineId: String,
        val machineName: String,
        val kind: MachineKind,
        val hoursLeft: Double,
    ) : HomeAlert {
        override val rank get() = 4
    }

    /** Work kept on this phone that has not reached the server yet, and will once it can. */
    data class Waiting(val count: Int) : HomeAlert {
        override val rank get() = 5
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

/** A customer owing money, and since when the oldest of it has been owed. */
data class Debt(
    val customerId: String,
    val rupees: Long,
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
            rupees = dues.sumOf { it.outstandingRupees },
            oldestMillis = dues.minOf { it.entry.atMillis },
        )
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
        rupees = late.sumOf { it.rupees },
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
    val first = Calendar.getInstance().apply {
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
    val cursor = Calendar.getInstance().apply { timeInMillis = first }
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
