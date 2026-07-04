package com.example.fieldtrack.core.debug

import com.example.fieldtrack.core.config.AppEnvironment

/**
 * PHASE I2.5 — Part 1
 *
 * Canonical, side-effect-free location for:
 *  - debug DTO contracts
 *  - shared compact summary structures
 *  - gating helpers
 *  - debug surface metadata
 *
 * Rules:
 *  - no business logic
 *  - no replay mutation
 *  - no operational side effects
 *
 * PROD always returns null / disallows surfaces.
 */
object OperationalDebugContracts {

    /** True when DEV/STAGING — debug surfaces allowed. */
    fun isDebugSurfaceAllowed(): Boolean =
        AppEnvironment.current().isDebugLike

    /**
     * Convenience gate: invokes the producer only in DEV/STAGING; PROD returns null safely.
     * Never throws — any internal failure folds to null.
     */
    inline fun <T> gatedOrNull(producer: () -> T): T? {
        if (!isDebugSurfaceAllowed()) return null
        return try {
            producer()
        } catch (_: Throwable) {
            null
        }
    }

    /** Decision wrapper for callers that want to log a calm reason. */
    fun decideGate(surfaceId: String): DebugGateDecision {
        val env = AppEnvironment.current()
        return if (env.isDebugLike) {
            DebugGateDecision(allowed = true, reason = "DEV/STAGING surface: $surfaceId")
        } else {
            DebugGateDecision(allowed = false, reason = "PROD — surface sealed: $surfaceId")
        }
    }

    /**
     * Forbidden UI terms — operational copy must never expose these to operators.
     * Used by UI surface consistency validation. Case-insensitive substring matching.
     */
    val FORBIDDEN_UI_TERMS: List<String> = listOf(
        "mutation",
        "replay",
        "journal",
        "transaction id",
        "operationalid",
        "operational id",
        "sequence #",
        "sequence number",
        "ack",
        "dto",
        "schema",
        "ksp",
        "stacktrace",
        "throwable",
        "exception",
        "null pointer",
        "nullpointer"
    )

    /** Compact, display-safe summary used across debug surfaces. */
    data class CompactDebugSummary(
        val section: String,
        val isHealthy: Boolean,
        val lines: List<String>,
        val generatedAt: Long = System.currentTimeMillis()
    )

    /** Metadata describing a debug surface — useful for governance. */
    data class DebugSurfaceMetadata(
        val surfaceId: String,
        val environmentLabel: String,
        val gated: Boolean
    )

    /** Result of a gate check. */
    data class DebugGateDecision(
        val allowed: Boolean,
        val reason: String?
    )
}

