package com.example.fieldtrack.core.adaptive

/**
 * PHASE K4 — PART 3: Shared Device Operational Safety.
 *
 * Supports real-world shared-device usage safely.
 * Preserves attribution continuity, active work visibility,
 * prevents duplicate settlements, avoids rapid identity confusion.
 * No forced logout flows.
 *
 * Pure observational logic only.
 */
object SharedDeviceOperationalSafety {

    /**
     * Context for shared device safety evaluation.
     */
    data class SharedDeviceContext(
        /** Current operator ID on this device. */
        val currentOperatorId: String?,
        /** Previous operator ID that was active on this device. */
        val previousOperatorId: String?,
        /** How long since the operator switch (millis). Null = first use. */
        val msSinceOperatorSwitch: Long? = null,
        /** Does the previous operator have active work? */
        val previousOperatorHasActiveWork: Boolean = false,
        /** Has a settlement already been recorded in this session? */
        val recentSettlementRecorded: Boolean = false,
        /** Is the device currently showing another operator's work? */
        val showingOtherOperatorWork: Boolean = false
    )

    /**
     * Evaluate shared device safety.
     * Pure function — deterministic, no side effects.
     */
    fun evaluate(context: SharedDeviceContext): SharedDeviceSafetyResult {
        val isSameOperator = context.currentOperatorId != null &&
            context.currentOperatorId == context.previousOperatorId
        val isRapidSwitch = context.msSinceOperatorSwitch != null &&
            context.msSinceOperatorSwitch < RAPID_SWITCH_THRESHOLD_MS

        val confidence = when {
            isSameOperator -> ContinuityConfidence.HIGH
            isRapidSwitch -> ContinuityConfidence.LOW
            context.previousOperatorHasActiveWork -> ContinuityConfidence.MODERATE
            else -> ContinuityConfidence.HIGH
        }

        val visibilityState = when {
            context.showingOtherOperatorWork && !isSameOperator -> VisibilitySafetyState.SHOWING_OTHER_WORK
            isRapidSwitch -> VisibilitySafetyState.IDENTITY_TRANSITIONING
            else -> VisibilitySafetyState.CLEAR
        }

        return SharedDeviceSafetyResult(
            identityLine = buildIdentityLine(context, isSameOperator, isRapidSwitch),
            continuityConfidence = confidence,
            visibilitySafetyState = visibilityState,
            shouldPreventDuplicateSettlement = context.recentSettlementRecorded && isRapidSwitch,
            operatorMessage = buildOperatorMessage(confidence, visibilityState)
        )
    }

    private fun buildIdentityLine(
        ctx: SharedDeviceContext,
        isSame: Boolean,
        isRapid: Boolean
    ): String = when {
        isSame -> "Your device"
        isRapid -> "Switching operators"
        ctx.previousOperatorHasActiveWork -> "Another operator's work visible"
        else -> "Ready"
    }

    private fun buildOperatorMessage(
        confidence: ContinuityConfidence,
        visibility: VisibilitySafetyState
    ): String = when {
        visibility == VisibilitySafetyState.IDENTITY_TRANSITIONING -> "Switching to your workspace"
        visibility == VisibilitySafetyState.SHOWING_OTHER_WORK -> "Viewing other work"
        confidence == ContinuityConfidence.LOW -> "Continuing carefully"
        else -> "Ready"
    }

    /** Rapid switch threshold — 30 seconds. */
    private const val RAPID_SWITCH_THRESHOLD_MS = 30_000L
}

/** Shared device safety evaluation result. */
data class SharedDeviceSafetyResult(
    val identityLine: String,
    val continuityConfidence: ContinuityConfidence,
    val visibilitySafetyState: VisibilitySafetyState,
    val shouldPreventDuplicateSettlement: Boolean,
    val operatorMessage: String
)

/** How confident we are in operational continuity. */
enum class ContinuityConfidence(val displayLabel: String) {
    HIGH("Confident"),
    MODERATE("Careful"),
    LOW("Transitioning")
}

/** Visibility safety on shared device. */
enum class VisibilitySafetyState(val displayLabel: String) {
    /** Clean — showing the right operator's workspace. */
    CLEAR("Clear"),
    /** Currently showing another operator's work. */
    SHOWING_OTHER_WORK("Other work visible"),
    /** Identity is transitioning between operators. */
    IDENTITY_TRANSITIONING("Switching")
}

