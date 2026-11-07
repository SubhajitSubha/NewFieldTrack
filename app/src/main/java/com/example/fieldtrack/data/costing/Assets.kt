package com.example.fieldtrack.data.costing

import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import com.example.fieldtrack.data.db.entity.MachinePaperEntity

/**
 * What a machine is worth on the books today, and what is left to run out on its papers.
 *
 * Two small pieces of arithmetic kept in one place so the asset register, the machine page
 * and the bank statement all say the same figure.
 */
object Assets {

    /** Years a machine is taken to last when the owner has not said. A tractor in India or Kenya outlives this; it is only where the books start. */
    const val DEFAULT_LIFE_YEARS = 10

    private const val DAYS_PER_YEAR = 365.25

    /**
     * Worth on [today] (an epoch day), straight down from the price paid to the resale value
     * over the machine's life, and no lower than the resale value however old it is. Null
     * when there is no purchase price or date to start from.
     */
    fun bookValue(finance: MachineFinanceEntity?, today: Long): Long? {
        val price = finance?.purchaseMinor ?: return null
        val boughtOn = finance.purchasedDay ?: return null
        val resale = (finance.resaleMinor ?: 0L).coerceIn(0L, price)
        val life = (finance.lifeYears ?: DEFAULT_LIFE_YEARS).coerceAtLeast(1)
        val years = ((today - boughtOn) / DAYS_PER_YEAR).coerceIn(0.0, life.toDouble())
        return price - Math.round((price - resale) * (years / life))
    }

    /** How much of the price comes off in one year, or null when there is nothing to spread. */
    fun yearlyDepreciation(finance: MachineFinanceEntity?): Long? {
        val price = finance?.purchaseMinor ?: return null
        val resale = (finance.resaleMinor ?: 0L).coerceIn(0L, price)
        val life = (finance.lifeYears ?: DEFAULT_LIFE_YEARS).coerceAtLeast(1)
        return Math.round((price - resale).toDouble() / life)
    }
}

/** Where a paper stands against the calendar. */
sealed interface PaperStatus {
    /** No expiry date was entered. */
    data object NoDate : PaperStatus

    data class Valid(val daysLeft: Long) : PaperStatus

    /** Runs out within the warning window. */
    data class Soon(val daysLeft: Long) : PaperStatus

    data class Expired(val daysAgo: Long) : PaperStatus
}

object Papers {

    /** How many days before it runs out a paper starts being mentioned, unless the person chose otherwise. */
    const val DEFAULT_WARN_DAYS = 30
    val WARN_CHOICES = listOf(15, 30, 60)

    /** The last valid day is still valid: a paper that ends today has not ended. */
    fun status(paper: MachinePaperEntity, today: Long, warnDays: Int = DEFAULT_WARN_DAYS): PaperStatus {
        val last = paper.expiresDay ?: return PaperStatus.NoDate
        val left = last - today
        return when {
            left < 0 -> PaperStatus.Expired(-left)
            left <= warnDays -> PaperStatus.Soon(left)
            else -> PaperStatus.Valid(left)
        }
    }

    /** The papers worth saying something about: running out, or already out. Soonest first. */
    fun needingAttention(papers: List<MachinePaperEntity>, today: Long, warnDays: Int = DEFAULT_WARN_DAYS): List<MachinePaperEntity> =
        papers.filter { it.deletedAt == null && status(it, today, warnDays).let { s -> s is PaperStatus.Soon || s is PaperStatus.Expired } }
            .sortedBy { it.expiresDay }
}
