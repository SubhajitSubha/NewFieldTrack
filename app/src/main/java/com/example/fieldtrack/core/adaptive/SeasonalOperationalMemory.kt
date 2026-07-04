package com.example.fieldtrack.core.adaptive

import java.util.Calendar

/**
 * PHASE K4 — PART 6: Seasonal Operational Memory.
 *
 * Adapts to recurring farming seasons.
 * Lightweight, observational only.
 * No forecasting. No analytics. No prediction dashboards.
 *
 * Examples:
 * - Same customer returns yearly
 * - Same machine used for same work
 * - Same settlement pattern
 * - Repeated crop cycle flow
 */
object SeasonalOperationalMemory {

    private const val MAX_ENTRIES = 200
    private val lock = Any()
    private val entries = ArrayDeque<SeasonalEntry>(MAX_ENTRIES)

    /**
     * Record a seasonal operational event.
     * Captures customer + work + machine + timestamp so the pattern
     * can be recognized when a similar season comes around.
     */
    fun record(
        customerId: String?,
        machineId: String?,
        workType: String?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (customerId == null && machineId == null && workType == null) return
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val month = cal.get(Calendar.MONTH)
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
            entries.addLast(
                SeasonalEntry(
                    customerId = customerId,
                    machineId = machineId,
                    workType = workType,
                    month = month,
                    timestamp = timestamp
                )
            )
        }
    }

    /**
     * Recurring operational patterns observed across seasons.
     * Returns the top recurring (customer, workType) pairs.
     */
    fun recurringOperationalPatterns(): List<RecurringPattern> {
        return synchronized(lock) {
            entries
                .filter { it.customerId != null && it.workType != null }
                .groupingBy { it.customerId!! to it.workType!! }
                .eachCount()
                .filter { it.value >= 2 } // Must occur at least twice to be a pattern
                .map { (key, count) ->
                    RecurringPattern(
                        customerId = key.first,
                        workType = key.second,
                        observedCount = count
                    )
                }
                .sortedByDescending { it.observedCount }
                .take(10)
        }
    }

    /**
     * Recent seasonal continuity — what's happened in the same month
     * across previous years (or in the past N days for short-term seasonal flow).
     */
    fun recentSeasonContinuity(now: Long = System.currentTimeMillis()): SeasonalContinuitySnapshot {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentMonth = cal.get(Calendar.MONTH)
        val recent30Days = now - (30L * 24 * 60 * 60 * 1000)

        return synchronized(lock) {
            val sameMonth = entries.filter { it.month == currentMonth }
            val recent = entries.filter { it.timestamp >= recent30Days }
            SeasonalContinuitySnapshot(
                sameMonthEvents = sameMonth.size,
                recentEvents = recent.size,
                distinctCustomersThisSeason = sameMonth.mapNotNull { it.customerId }.distinct().size,
                distinctWorkTypesThisSeason = sameMonth.mapNotNull { it.workType }.distinct().size
            )
        }
    }

    /**
     * Calm continuity hints — observational suggestions only.
     * Examples:
     * - "Same customer worked with last season"
     * - "Common work for this time of year"
     */
    fun continuityHints(customerId: String?, workType: String?): List<String> {
        val hints = mutableListOf<String>()
        if (customerId != null) {
            val customerCount = synchronized(lock) {
                entries.count { it.customerId == customerId }
            }
            if (customerCount >= 2) {
                hints += "Returning customer"
            }
        }
        if (workType != null) {
            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH)
            val seasonal = synchronized(lock) {
                entries.count { it.workType == workType && it.month == month }
            }
            if (seasonal >= 2) {
                hints += "Common for this season"
            }
        }
        return hints
    }

    /** Compact snapshot for debug surfaces. */
    fun snapshot(): SeasonalMemorySnapshot = synchronized(lock) {
        SeasonalMemorySnapshot(
            totalEntries = entries.size,
            distinctCustomers = entries.mapNotNull { it.customerId }.distinct().size,
            distinctMachines = entries.mapNotNull { it.machineId }.distinct().size,
            distinctWorkTypes = entries.mapNotNull { it.workType }.distinct().size,
            recurringPatternCount = recurringOperationalPatterns().size
        )
    }

    fun clear() = synchronized(lock) { entries.clear() }
}

/** A single seasonal entry. */
data class SeasonalEntry(
    val customerId: String?,
    val machineId: String?,
    val workType: String?,
    val month: Int,
    val timestamp: Long
)

/** A recurring observed pattern. */
data class RecurringPattern(
    val customerId: String,
    val workType: String,
    val observedCount: Int
)

/** Recent seasonal continuity snapshot. */
data class SeasonalContinuitySnapshot(
    val sameMonthEvents: Int,
    val recentEvents: Int,
    val distinctCustomersThisSeason: Int,
    val distinctWorkTypesThisSeason: Int
)

/** Compact debug snapshot. */
data class SeasonalMemorySnapshot(
    val totalEntries: Int,
    val distinctCustomers: Int,
    val distinctMachines: Int,
    val distinctWorkTypes: Int,
    val recurringPatternCount: Int
)

