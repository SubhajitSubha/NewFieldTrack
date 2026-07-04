package com.example.fieldtrack.core.adaptive

import com.example.fieldtrack.core.visibility.RoleVisibilityProfile
import com.example.fieldtrack.core.workspace.ActionConfidence

/**
 * PHASE K4 — PART 4: Contextual Quick Action Engine.
 *
 * Reduces friction dynamically with max 2 quick actions.
 * Role-aware, confidence-based, removable instantly if stale.
 * No incorrect aggressive assumptions.
 *
 * Rules:
 * - Max 2 quick actions total (1 primary + 1 secondary)
 * - Never noisy
 * - Role-aware
 * - Confidence-based
 * - Pure, deterministic, no side effects
 */
object ContextualQuickActionEngine {

    /**
     * Context for quick action resolution.
     */
    data class QuickActionContext(
        val role: RoleVisibilityProfile,
        /** Is there active work running? */
        val hasActiveWork: Boolean = false,
        /** Active work type label (if any). */
        val activeWorkType: String? = null,
        /** Is there interrupted work that can resume? */
        val hasInterruptedWork: Boolean = false,
        /** Interrupted work type label (if any). */
        val interruptedWorkType: String? = null,
        /** Last used machine name (if recent). */
        val lastMachineName: String? = null,
        /** Is there a pending settlement? */
        val hasPendingSettlement: Boolean = false,
        /** Pending settlement customer name. */
        val pendingSettlementCustomer: String? = null,
        /** Is a helper available? */
        val helperAvailable: Boolean = false,
        /** Last customer name (for repeat suggestion). */
        val lastCustomerName: String? = null,
        /** Whether the context is stale and should be cleared. */
        val isStale: Boolean = false
    )

    /**
     * Resolve quick actions.
     * Returns at most 1 primary + 1 secondary.
     */
    fun resolve(context: QuickActionContext): QuickActionResult {
        if (context.isStale) {
            return QuickActionResult(primary = null, secondary = null)
        }
        val primary = resolvePrimary(context)
        val secondary = resolveSecondary(context, primary)
        return QuickActionResult(primary = primary, secondary = secondary)
    }

    /** Convenience: primary quick action only. */
    fun primaryQuickAction(context: QuickActionContext): QuickAction? = resolve(context).primary

    /** Convenience: secondary quick action only. */
    fun secondaryQuickAction(context: QuickActionContext): QuickAction? = resolve(context).secondary

    private fun resolvePrimary(ctx: QuickActionContext): QuickAction? {
        // Active work → continue
        if (ctx.hasActiveWork) {
            val label = if (ctx.activeWorkType != null) "Continue ${ctx.activeWorkType.lowercase()}"
                else "Continue work"
            return QuickAction(
                label = label,
                actionId = "continue_active_work",
                confidence = ActionConfidence.HIGH,
                reason = "Active work running"
            )
        }

        // Interrupted work → resume
        if (ctx.hasInterruptedWork) {
            val label = if (ctx.interruptedWorkType != null) "Resume ${ctx.interruptedWorkType.lowercase()}"
                else "Resume interrupted work"
            return QuickAction(
                label = label,
                actionId = "resume_interrupted",
                confidence = ActionConfidence.MODERATE,
                reason = "Interrupted work available"
            )
        }

        // Pending settlement
        if (ctx.hasPendingSettlement) {
            val name = ctx.pendingSettlementCustomer ?: "customer"
            return QuickAction(
                label = "Finish settlement for $name",
                actionId = "finish_settlement",
                confidence = ActionConfidence.MODERATE,
                reason = "Pending settlement"
            )
        }

        // Last machine available
        if (ctx.lastMachineName != null) {
            return QuickAction(
                label = "Use ${ctx.lastMachineName}",
                actionId = "use_last_machine",
                confidence = ActionConfidence.LOW,
                reason = "Recently used machine"
            )
        }

        return null
    }

    private fun resolveSecondary(ctx: QuickActionContext, primary: QuickAction?): QuickAction? {
        // Don't duplicate primary
        val primaryId = primary?.actionId

        if (primaryId != "finish_settlement" && ctx.hasPendingSettlement) {
            val name = ctx.pendingSettlementCustomer ?: "customer"
            return QuickAction(
                label = "Settlement for $name",
                actionId = "finish_settlement",
                confidence = ActionConfidence.LOW,
                reason = "Pending settlement"
            )
        }

        if (primaryId != "use_last_machine" && ctx.lastMachineName != null && !ctx.hasActiveWork) {
            return QuickAction(
                label = "Use ${ctx.lastMachineName}",
                actionId = "use_last_machine",
                confidence = ActionConfidence.LOW,
                reason = "Recently used machine"
            )
        }

        if (ctx.helperAvailable && ctx.hasActiveWork) {
            return QuickAction(
                label = "Continue with helper",
                actionId = "continue_with_helper",
                confidence = ActionConfidence.LOW,
                reason = "Helper available"
            )
        }

        if (primaryId != "resume_yesterday_customer" && ctx.lastCustomerName != null && !ctx.hasActiveWork) {
            return QuickAction(
                label = "Work for ${ctx.lastCustomerName}",
                actionId = "resume_yesterday_customer",
                confidence = ActionConfidence.LOW,
                reason = "Recent customer"
            )
        }

        return null
    }
}

/** A single quick action. */
data class QuickAction(
    val label: String,
    val actionId: String,
    val confidence: ActionConfidence,
    val reason: String
)

/** Result of quick action resolution. */
data class QuickActionResult(
    val primary: QuickAction?,
    val secondary: QuickAction?
) {
    val hasActions: Boolean get() = primary != null
    val actionCount: Int get() = listOfNotNull(primary, secondary).size
}

