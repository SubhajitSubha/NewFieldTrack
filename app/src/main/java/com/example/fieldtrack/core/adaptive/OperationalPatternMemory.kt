package com.example.fieldtrack.core.adaptive

/**
 * PHASE K4 — PART 1: Operational Pattern Memory.
 *
 * Observes recurring real-world operational behavior WITHOUT enforcing it.
 * Suggestion-only — never automatic enforcement.
 *
 * Rules:
 * - Append-only
 * - Capped memory (MAX_ENTRIES)
 * - Deterministic ordering
 * - Observational only
 * - No scoring/ranking
 * - No operator profiling
 * - Thread-safe
 */
object OperationalPatternMemory {

    private const val MAX_ENTRIES = 100
    private val lock = Any()

    // ── Pattern stores ──
    private val machineCustomerPairings = ArrayDeque<PatternEntry>(MAX_ENTRIES)
    private val helperOperatorPairings = ArrayDeque<PatternEntry>(MAX_ENTRIES)
    private val workSequences = ArrayDeque<PatternEntry>(MAX_ENTRIES)
    private val continuationPaths = ArrayDeque<PatternEntry>(MAX_ENTRIES)
    private val recoveryPaths = ArrayDeque<PatternEntry>(MAX_ENTRIES)
    private val settlementTimings = ArrayDeque<PatternEntry>(MAX_ENTRIES)
    private val machineSwitches = ArrayDeque<PatternEntry>(MAX_ENTRIES)

    // ── Recording ──

    fun recordMachineCustomerPairing(machineId: String, customerId: String) {
        append(machineCustomerPairings, PatternEntry(key = machineId, value = customerId))
    }

    fun recordHelperOperatorPairing(helperId: String, operatorId: String) {
        append(helperOperatorPairings, PatternEntry(key = helperId, value = operatorId))
    }

    fun recordWorkSequence(previousWorkType: String, nextWorkType: String) {
        append(workSequences, PatternEntry(key = previousWorkType, value = nextWorkType))
    }

    fun recordContinuationPath(workId: String, path: String) {
        append(continuationPaths, PatternEntry(key = workId, value = path))
    }

    fun recordRecoveryPath(interruptionKind: String, recoveryAction: String) {
        append(recoveryPaths, PatternEntry(key = interruptionKind, value = recoveryAction))
    }

    fun recordSettlementTiming(customerId: String, timingLabel: String) {
        append(settlementTimings, PatternEntry(key = customerId, value = timingLabel))
    }

    fun recordMachineSwitch(fromMachineId: String, toMachineId: String) {
        append(machineSwitches, PatternEntry(key = fromMachineId, value = toMachineId))
    }

    // ── Suggestions (observational only) ──

    /**
     * Most likely next machine based on recent patterns.
     * Returns null if no pattern exists. Never enforced.
     */
    fun likelyNextMachine(currentCustomerId: String?): PatternSuggestion? {
        if (currentCustomerId == null) return null
        return synchronized(lock) {
            val match = machineCustomerPairings
                .filter { it.value == currentCustomerId }
                .groupingBy { it.key }
                .eachCount()
                .maxByOrNull { it.value }
            match?.let {
                PatternSuggestion(
                    value = it.key,
                    observedCount = it.value,
                    reason = "Previously used for this customer"
                )
            }
        }
    }

    /**
     * Most likely customer for a given machine.
     * Returns null if no pattern exists.
     */
    fun likelyCustomerForMachine(machineId: String): PatternSuggestion? {
        return synchronized(lock) {
            val match = machineCustomerPairings
                .filter { it.key == machineId }
                .groupingBy { it.value }
                .eachCount()
                .maxByOrNull { it.value }
            match?.let {
                PatternSuggestion(
                    value = it.key,
                    observedCount = it.value,
                    reason = "Frequently served with this machine"
                )
            }
        }
    }

    /**
     * Most likely continuation path based on recent patterns.
     */
    fun likelyContinuationPath(): PatternSuggestion? {
        return synchronized(lock) {
            val match = continuationPaths
                .groupingBy { it.value }
                .eachCount()
                .maxByOrNull { it.value }
            match?.let {
                PatternSuggestion(
                    value = it.key,
                    observedCount = it.value,
                    reason = "Common continuation choice"
                )
            }
        }
    }

    /**
     * Most likely settlement behavior for a customer.
     */
    fun likelySettlementBehavior(customerId: String): PatternSuggestion? {
        return synchronized(lock) {
            val match = settlementTimings
                .filter { it.key == customerId }
                .groupingBy { it.value }
                .eachCount()
                .maxByOrNull { it.value }
            match?.let {
                PatternSuggestion(
                    value = it.key,
                    observedCount = it.value,
                    reason = "Previous settlement pattern"
                )
            }
        }
    }

    /** Compact snapshot for debug surfaces. */
    fun snapshot(): PatternMemorySnapshot = synchronized(lock) {
        PatternMemorySnapshot(
            machineCustomerPairings = machineCustomerPairings.size,
            helperOperatorPairings = helperOperatorPairings.size,
            workSequences = workSequences.size,
            continuationPaths = continuationPaths.size,
            recoveryPaths = recoveryPaths.size,
            settlementTimings = settlementTimings.size,
            machineSwitches = machineSwitches.size
        )
    }

    fun clear() = synchronized(lock) {
        machineCustomerPairings.clear()
        helperOperatorPairings.clear()
        workSequences.clear()
        continuationPaths.clear()
        recoveryPaths.clear()
        settlementTimings.clear()
        machineSwitches.clear()
    }

    private fun append(deque: ArrayDeque<PatternEntry>, entry: PatternEntry) {
        synchronized(lock) {
            if (deque.size >= MAX_ENTRIES) deque.removeAt(0)
            deque.addLast(entry)
        }
    }
}

/** A single observed pattern entry. */
data class PatternEntry(
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** A suggestion derived from observed patterns. Never enforced. */
data class PatternSuggestion(
    val value: String,
    val observedCount: Int,
    val reason: String
)

/** Compact snapshot of pattern memory. */
data class PatternMemorySnapshot(
    val machineCustomerPairings: Int,
    val helperOperatorPairings: Int,
    val workSequences: Int,
    val continuationPaths: Int,
    val recoveryPaths: Int,
    val settlementTimings: Int,
    val machineSwitches: Int
) {
    val totalPatterns: Int get() = machineCustomerPairings + helperOperatorPairings +
        workSequences + continuationPaths + recoveryPaths + settlementTimings + machineSwitches
}

