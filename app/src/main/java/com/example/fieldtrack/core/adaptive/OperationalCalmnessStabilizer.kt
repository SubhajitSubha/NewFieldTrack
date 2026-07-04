package com.example.fieldtrack.core.adaptive

/**
 * PHASE K4 — PART 7: Operational Calmness Stabilizer.
 *
 * Prevents operational overload.
 *
 * Detects:
 * - Too many workspace items
 * - Repeated interruptions
 * - Excessive quick actions
 * - Duplicate continuation suggestions
 * - Excessive organizational visibility
 *
 * Enforces:
 * - Max 1 primary operational focus
 * - Max 2 contextual items
 * - Interruptions consolidated
 * - No urgency language
 *
 * Pure deterministic logic.
 */
object OperationalCalmnessStabilizer {

    /** Threshold beyond which workspace is considered overloaded. */
    private const val MAX_VISIBLE_ITEMS = 4
    private const val MAX_QUICK_ACTIONS = 2
    private const val MAX_INTERRUPTIONS_BEFORE_CONSOLIDATE = 2
    private const val MAX_ORG_VISIBILITY_ITEMS = 3

    /**
     * Input context for calmness evaluation.
     */
    data class CalmnessContext(
        val workspaceItemCount: Int = 0,
        val interruptionCount: Int = 0,
        val quickActionCount: Int = 0,
        val continuationSuggestionCount: Int = 0,
        val organizationalVisibilityItemCount: Int = 0,
        val hasPrimaryFocus: Boolean = true,
        val duplicateSuggestions: Int = 0
    )

    /**
     * Evaluate workspace calmness.
     */
    fun calmWorkspaceState(context: CalmnessContext): CalmWorkspaceState {
        val violations = mutableListOf<CalmnessViolation>()

        if (context.workspaceItemCount > MAX_VISIBLE_ITEMS) {
            violations += CalmnessViolation(
                kind = CalmnessIssue.TOO_MANY_ITEMS,
                description = "${context.workspaceItemCount} items visible (max $MAX_VISIBLE_ITEMS)"
            )
        }
        if (context.interruptionCount > MAX_INTERRUPTIONS_BEFORE_CONSOLIDATE) {
            violations += CalmnessViolation(
                kind = CalmnessIssue.REPEATED_INTERRUPTIONS,
                description = "${context.interruptionCount} interruptions need consolidation"
            )
        }
        if (context.quickActionCount > MAX_QUICK_ACTIONS) {
            violations += CalmnessViolation(
                kind = CalmnessIssue.EXCESSIVE_QUICK_ACTIONS,
                description = "${context.quickActionCount} quick actions (max $MAX_QUICK_ACTIONS)"
            )
        }
        if (context.duplicateSuggestions > 0) {
            violations += CalmnessViolation(
                kind = CalmnessIssue.DUPLICATE_SUGGESTIONS,
                description = "${context.duplicateSuggestions} duplicate suggestions"
            )
        }
        if (context.organizationalVisibilityItemCount > MAX_ORG_VISIBILITY_ITEMS) {
            violations += CalmnessViolation(
                kind = CalmnessIssue.EXCESSIVE_ORG_VISIBILITY,
                description = "${context.organizationalVisibilityItemCount} org items (max $MAX_ORG_VISIBILITY_ITEMS)"
            )
        }

        val level = when {
            violations.isEmpty() -> CalmnessLevel.CALM
            violations.size <= 2 -> CalmnessLevel.SLIGHTLY_BUSY
            else -> CalmnessLevel.OVERLOADED
        }

        return CalmWorkspaceState(
            level = level,
            violations = violations,
            shouldSuppressSecondaryInfo = shouldSuppressSecondaryInfo(context),
            shouldReduceVisibilityNoise = shouldReduceVisibilityNoise(context),
            summary = summaryFor(level, violations.size)
        )
    }

    /**
     * Should secondary info be suppressed to reduce cognitive load?
     */
    fun shouldSuppressSecondaryInfo(context: CalmnessContext): Boolean {
        return context.workspaceItemCount > MAX_VISIBLE_ITEMS ||
            context.interruptionCount > MAX_INTERRUPTIONS_BEFORE_CONSOLIDATE
    }

    /**
     * Should organizational visibility noise be reduced?
     */
    fun shouldReduceVisibilityNoise(context: CalmnessContext): Boolean {
        return context.organizationalVisibilityItemCount > MAX_ORG_VISIBILITY_ITEMS ||
            context.duplicateSuggestions > 0
    }

    private fun summaryFor(level: CalmnessLevel, violationCount: Int): String = when (level) {
        CalmnessLevel.CALM -> "Workspace is calm"
        CalmnessLevel.SLIGHTLY_BUSY -> "Slightly busy"
        CalmnessLevel.OVERLOADED -> "Simplifying view"
    }
}

/** Workspace calmness level. */
enum class CalmnessLevel(val displayLabel: String) {
    /** Everything is fine — minimal items. */
    CALM("Calm"),
    /** A bit busy — still operational. */
    SLIGHTLY_BUSY("Slightly busy"),
    /** Too much going on — suppression triggered. */
    OVERLOADED("Simplifying")
}

/** Types of calmness violations. */
enum class CalmnessIssue(val displayLabel: String) {
    TOO_MANY_ITEMS("Too many items"),
    REPEATED_INTERRUPTIONS("Repeated interruptions"),
    EXCESSIVE_QUICK_ACTIONS("Too many quick actions"),
    DUPLICATE_SUGGESTIONS("Duplicate suggestions"),
    EXCESSIVE_ORG_VISIBILITY("Too much org visibility")
}

/** A calmness violation. */
data class CalmnessViolation(
    val kind: CalmnessIssue,
    val description: String
)

/** Workspace calmness state result. */
data class CalmWorkspaceState(
    val level: CalmnessLevel,
    val violations: List<CalmnessViolation>,
    val shouldSuppressSecondaryInfo: Boolean,
    val shouldReduceVisibilityNoise: Boolean,
    val summary: String
) {
    val isCalm: Boolean get() = level == CalmnessLevel.CALM
}

