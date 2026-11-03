package com.example.fieldtrack.data.costing

import com.example.fieldtrack.data.db.dao.FuelOnJobs
import com.example.fieldtrack.data.db.dao.RunningFacts
import com.example.fieldtrack.data.db.dao.WorkKindFacts
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import kotlin.math.roundToLong

/**
 * What a machine costs, and what that comes to for a job.
 *
 * Pure arithmetic, in one place, because the same figures reach four screens —
 * the machine's own page, the hint under the rate when a job is started, a job's
 * page, and the season. Four copies of "diesel plus repairs over hours" is how
 * two of them end up a rupee apart, and a farmer with a calculator finds out.
 *
 * Three rules every function here keeps:
 *
 *  - **Say nothing rather than something silly.** A tractor that has worked three
 *    hours "costs" whatever its one diesel fill divides into. Below [MIN_HOURS] a
 *    cost per hour is not offered, and the screen says how far short it is.
 *  - **Nothing is invented.** Diesel and repairs are what was spent. A loan is
 *    spread only when the owner entered one, over hours they said, or over a
 *    year's recorded work once there is enough of it to mean anything.
 *  - **A total is the sum of its rounded parts.** Rounding each figure on its own
 *    and then the total on its own makes ₹312 + ₹150 + ₹178 add up to ₹641.
 */
object Costing {

    /** Hours of finished work before a cost per hour is worth saying. */
    const val MIN_HOURS = 10.0

    /** The same, for one kind of work on one machine. */
    const val MIN_KIND_HOURS = 5.0

    /** A year of recorded work needed before it can stand in for "hours a year". */
    const val MIN_YEAR_HOURS = 50.0

    /** A month, for turning a stretch of days into instalments. */
    private const val MONTH_MILLIS = 30.4375 * 24 * 60 * 60 * 1000

    /** How the loan stands in a machine's cost. */
    sealed interface Loan {
        /** Nothing entered, so nothing is counted — and the screen can say so. */
        data object NotEntered : Loan

        /** An instalment is entered but there is nothing honest to spread it over yet. */
        data object CannotSpreadYet : Loan

        data class Counted(val perHour: Double) : Loan
    }

    data class RunningCost(
        val hours: Double,
        val dieselPerHour: Double,
        val upkeepPerHour: Double,
        val loan: Loan,
    ) {
        /** Enough hours behind it to be worth saying. */
        val enough: Boolean get() = hours >= MIN_HOURS

        /** Whole rupees, each rounded alone; [Rounded.total] is their sum. */
        fun rounded() = Rounded(
            diesel = dieselPerHour.roundToLong(),
            upkeep = upkeepPerHour.roundToLong(),
            loan = (loan as? Loan.Counted)?.perHour?.roundToLong(),
        )
    }

    data class Rounded(val diesel: Long, val upkeep: Long, val loan: Long?) {
        val total: Long get() = diesel + upkeep + (loan ?: 0)
    }

    /**
     * What an hour on this machine costs, from what has been spent on it.
     *
     * [facts] null means the machine is unknown here; the result then has no hours
     * and says nothing.
     */
    fun running(facts: RunningFacts?, finance: MachineFinanceEntity?): RunningCost {
        val hours = (facts?.seconds ?: 0L) / 3600.0
        val enough = hours >= MIN_HOURS
        return RunningCost(
            hours = hours,
            dieselPerHour = if (enough) facts!!.fuelRupees / hours else 0.0,
            upkeepPerHour = if (enough) facts!!.serviceRupees / hours else 0.0,
            loan = loanShare(finance, (facts?.yearSeconds ?: 0L) / 3600.0),
        )
    }

    /**
     * The loan, spread over a year's hours.
     *
     * Over what the owner said the machine works in a year; failing that, over
     * what it actually worked in the last twelve months, if that is a real
     * number. A tractor added to the app last week has no year behind it, and
     * guessing at one would put an invented figure into every cost after it.
     */
    fun loanShare(finance: MachineFinanceEntity?, hoursLastYear: Double): Loan {
        val monthly = finance?.monthlyRupees?.takeIf { it > 0 } ?: return Loan.NotEntered
        val yearly = finance.hoursPerYear?.takeIf { it > 0 }?.toDouble()
            ?: hoursLastYear.takeIf { it >= MIN_YEAR_HOURS }
            ?: return Loan.CannotSpreadYet
        return Loan.Counted(monthly * 12 / yearly)
    }

    /** What doing one unit of a kind of work costs, against what it brings in. */
    data class UnitCost(
        val workType: String,
        val unit: com.example.fieldtrack.data.db.MeasureUnit,
        val unitsPerHour: Double,
        val costRupees: Long,
        /** What this kind of work has billed per unit, on average. */
        val billedRupees: Long,
        val jobs: Int,
    ) {
        /** Billed less cost, from the two rounded figures so they subtract on paper. */
        val leftRupees: Long get() = billedRupees - costRupees
    }

    /**
     * Cost per bigha, per trip, per quintal — whatever the work is counted in.
     *
     * The hour's cost divided by how many units an hour of this work gets
     * through, taken from the machine's own finished jobs. Null when there is not
     * enough of that work to say how fast it goes.
     */
    fun perUnit(cost: RunningCost, kind: WorkKindFacts): UnitCost? {
        if (!cost.enough) return null
        val hours = kind.seconds / 3600.0
        if (hours < MIN_KIND_HOURS || kind.quantity <= 0.0) return null
        val unitsPerHour = kind.quantity / hours
        val billed = (kind.earnedRupees / kind.quantity).roundToLong()
        return UnitCost(
            workType = kind.workType,
            unit = kind.unit,
            unitsPerHour = unitsPerHour,
            costRupees = (cost.rounded().total / unitsPerHour).roundToLong(),
            billedRupees = billed,
            jobs = kind.jobs,
        )
    }

    /**
     * A job's share of the machine's own wear and loan.
     *
     * Hours on the job times repairs-and-loan per hour. Diesel is left out
     * because a job's page already counts the diesel logged against it; adding
     * the hourly diesel as well would count it twice. Null when there is no cost
     * per hour to share.
     */
    fun machineShare(cost: RunningCost, jobSeconds: Long): Long? {
        if (!cost.enough || jobSeconds <= 0) return null
        val parts = cost.rounded()
        val perHour = parts.upkeep + (parts.loan ?: 0)
        return (perHour * jobSeconds / 3600.0).roundToLong()
    }

    /** Loan instalments falling in a stretch of time, for a season's total. */
    fun instalmentsBetween(monthlyTotal: Long, fromMillis: Long, toMillis: Long): Long {
        if (monthlyTotal <= 0 || toMillis <= fromMillis) return 0
        return (monthlyTotal * (toMillis - fromMillis) / MONTH_MILLIS).roundToLong()
    }

    /** A machine drinking more than it used to. */
    data class FuelRise(val recentLitresPerHour: Double, val beforeLitresPerHour: Double) {
        val percent: Int get() = ((recentLitresPerHour / beforeLitresPerHour - 1) * 100).roundToLong().toInt()
    }

    /** Hours of work, and jobs, needed in each window before diesel use is compared. */
    private const val RECENT_MIN_HOURS = 4.0
    private const val BEFORE_MIN_HOURS = 10.0
    private const val RECENT_MIN_JOBS = 2
    private const val BEFORE_MIN_JOBS = 3

    /** How much more, as a fraction, before it is worth an owner's attention. */
    const val FUEL_RISE_THRESHOLD = 0.30

    /**
     * Whether a machine is using clearly more diesel per hour than it did.
     *
     * Needs real work behind both sides: a fill logged mid-job can cover the job
     * before it, so one or two jobs say nothing, and the thresholds are what keep
     * this to a machine that is genuinely off. A false alarm costs an owner a
     * glance, but a few of them teach him to stop looking.
     */
    fun fuelRise(recent: FuelOnJobs?, before: FuelOnJobs?): FuelRise? {
        if (recent == null || before == null) return null
        val recentHours = recent.seconds / 3600.0
        val beforeHours = before.seconds / 3600.0
        if (recentHours < RECENT_MIN_HOURS || beforeHours < BEFORE_MIN_HOURS) return null
        if (recent.jobs < RECENT_MIN_JOBS || before.jobs < BEFORE_MIN_JOBS) return null
        val now = recent.litres / recentHours
        val then = before.litres / beforeHours
        if (then <= 0.0) return null
        return if (now >= then * (1 + FUEL_RISE_THRESHOLD)) FuelRise(now, then) else null
    }
}
