package com.example.fieldtrack.core.documentation

import java.security.MessageDigest

/**
 * PHASE G4 PART 8 — Operational Architecture Snapshot.
 *
 * Machine-readable architecture memory. Documents authority boundaries,
 * operational flows, and architectural invariants.
 *
 * Immutable. Deterministic. Pure. Future KMP extraction friendly.
 * No Android dependencies.
 */
object OperationalArchitectureSnapshot {

    /** Current architecture schema version. */
    const val ARCHITECTURE_VERSION = 1

    /**
     * Compact architecture summary documenting all authority boundaries.
     */
    fun compactArchitectureSummary(): ArchitectureSummary = ArchitectureSummary(
        version = ARCHITECTURE_VERSION,
        authorities = listOf(
            AuthorityBoundary(
                name = "OperationalMutationExecutor",
                domain = "Mutation",
                responsibility = "Single authority for all database writes and entity version management",
                exclusiveOperations = listOf(
                    "DAO writes", "entityVersion mutations", "mutation ordering",
                    "transaction grouping", "replay frame triggering"
                )
            ),
            AuthorityBoundary(
                name = "OperationalReplayRecorder",
                domain = "Replay",
                responsibility = "Single authority for replay frame generation and sequencing",
                exclusiveOperations = listOf(
                    "Replay frame creation", "sequence numbering", "continuity markers",
                    "replay hash inputs"
                )
            ),
            AuthorityBoundary(
                name = "FinancialEventAuthority",
                domain = "Financial",
                responsibility = "Immutable financial event generation",
                exclusiveOperations = listOf(
                    "FinancialEventEntity creation", "charge derivation",
                    "balance computation inputs"
                )
            ),
            AuthorityBoundary(
                name = "SettlementAuthority",
                domain = "Financial",
                responsibility = "Settlement lifecycle management",
                exclusiveOperations = listOf(
                    "Settlement creation", "settlement state transitions",
                    "settlement finalization"
                )
            ),
            AuthorityBoundary(
                name = "CollectionAuthority",
                domain = "Financial",
                responsibility = "Collection recording and integrity",
                exclusiveOperations = listOf(
                    "Collection creation", "collection amount validation",
                    "over-collection prevention"
                )
            ),
            AuthorityBoundary(
                name = "OperationalSyncQueue",
                domain = "Sync",
                responsibility = "Mutation queuing for remote synchronization",
                exclusiveOperations = listOf(
                    "Queue insertion", "ACK processing", "retry scheduling",
                    "quarantine escalation"
                )
            ),
            AuthorityBoundary(
                name = "OperationalSessionManager",
                domain = "Session",
                responsibility = "Session lifecycle and identity continuity",
                exclusiveOperations = listOf(
                    "Session creation", "session termination", "device identity binding"
                )
            )
        ),
        flows = listOf(
            OperationalFlow(
                name = "Mutation Flow",
                steps = listOf(
                    "UI action → Executor.execute()",
                    "Executor validates + writes to DAO",
                    "Executor increments entityVersion",
                    "ReplayRecorder generates frame",
                    "SyncQueue enqueues mutation",
                    "Journal appends entry"
                )
            ),
            OperationalFlow(
                name = "Financial Flow",
                steps = listOf(
                    "Work completion → FinancialEventAuthority.generate()",
                    "Charge derivation from work parameters",
                    "Collection via CollectionAuthority",
                    "Settlement via SettlementAuthority",
                    "All events immutable + append-only"
                )
            ),
            OperationalFlow(
                name = "Sync Flow",
                steps = listOf(
                    "Mutation → SyncQueue.enqueue()",
                    "Background sync attempts upload",
                    "Success → ACK + dequeue",
                    "Failure → retry with backoff",
                    "Repeated failure → quarantine"
                )
            ),
            OperationalFlow(
                name = "Recovery Flow",
                steps = listOf(
                    "Process death/interruption detected",
                    "Workspace snapshot restored",
                    "Timer continuity verified",
                    "Replay continuity checked",
                    "Orphan detection + cleanup",
                    "Normal operation resumed"
                )
            ),
            OperationalFlow(
                name = "Certification Flow",
                steps = listOf(
                    "Harness initializes deterministic environment",
                    "Scenarios execute against authorities",
                    "Results compared to baselines",
                    "Drift detected and classified",
                    "Release certification derived",
                    "Rollout governance evaluated"
                )
            )
        ),
        invariants = listOf(
            "Executor is the SINGLE mutation authority",
            "Repositories are persistence-only — no business logic",
            "Sync layer is stateless — queue is the truth",
            "Replay generation is deterministic",
            "Financial events are immutable and append-only",
            "All mutations are replay-safe",
            "Timer truth derives from start timestamp, not ticks",
            "Ownership is explicit, never inferred",
            "Recovery never invents data — only restores",
            "Certification never mutates operational state"
        )
    )

    /**
     * Stable fingerprint of the architecture.
     * Changes only when authority boundaries or invariants change.
     */
    fun stableArchitectureFingerprint(): String {
        val summary = compactArchitectureSummary()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(summary.version.toString().toByteArray(Charsets.UTF_8))
        summary.authorities.sortedBy { it.name }.forEach { auth ->
            digest.update(auth.name.toByteArray(Charsets.UTF_8))
            digest.update(auth.domain.toByteArray(Charsets.UTF_8))
            auth.exclusiveOperations.sorted().forEach {
                digest.update(it.toByteArray(Charsets.UTF_8))
            }
        }
        summary.invariants.sorted().forEach {
            digest.update(it.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }
}

// ═══════════════════════════════════════════════════════════
// Supporting types
// ═══════════════════════════════════════════════════════════

data class AuthorityBoundary(
    val name: String,
    val domain: String,
    val responsibility: String,
    val exclusiveOperations: List<String>
)

data class OperationalFlow(
    val name: String,
    val steps: List<String>
)

data class ArchitectureSummary(
    val version: Int,
    val authorities: List<AuthorityBoundary>,
    val flows: List<OperationalFlow>,
    val invariants: List<String>
) {
    val authorityCount: Int get() = authorities.size
    val flowCount: Int get() = flows.size
    val invariantCount: Int get() = invariants.size

    fun compactDescription(): String =
        "Architecture v$version: ${authorityCount} authorities, ${flowCount} flows, ${invariantCount} invariants"
}

