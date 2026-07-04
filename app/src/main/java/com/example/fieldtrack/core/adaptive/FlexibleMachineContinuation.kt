package com.example.fieldtrack.core.adaptive

/**
 * PHASE K4 — PART 2: Flexible Machine Continuation.
 *
 * Allows real-world machine switching behavior safely.
 * Preserves attribution, financial continuity, and replay determinism.
 * Never duplicates work identity. Never hard-blocks switching.
 *
 * All wording calm. Never: "conflict", "denied", "invalid", "permission failure".
 */
object FlexibleMachineContinuation {

    /**
     * Context for a machine continuation decision.
     */
    data class MachineContinuationContext(
        /** Is the same operator continuing? */
        val isSameOperator: Boolean = false,
        /** Is this a helper continuing? */
        val isHelper: Boolean = false,
        /** Is this a temporary substitution? */
        val isTemporary: Boolean = false,
        /** Does the original machine still have active work? */
        val originalMachineActive: Boolean = false,
        /** Is the new machine available? */
        val newMachineAvailable: Boolean = true,
        /** Is this a recovery scenario? */
        val isRecovery: Boolean = false
    )

    /**
     * Resolve the safest machine continuation path.
     * Pure function — same inputs always produce same output.
     * Never returns "denied" or "blocked".
     */
    fun resolve(context: MachineContinuationContext): MachineContinuationDecision {
        val state = resolveState(context)
        return MachineContinuationDecision(
            state = state,
            preservesAttribution = true,
            preservesFinancialContinuity = true,
            operatorMessage = messageFor(state),
            continuityNote = noteFor(state, context)
        )
    }

    private fun resolveState(ctx: MachineContinuationContext): MachineContinuationState {
        // Recovery always allowed
        if (ctx.isRecovery) return MachineContinuationState.RECOVERY_CONTINUATION

        // Helper temporarily continuing
        if (ctx.isHelper) return MachineContinuationState.TEMPORARY_HELPER_CONTINUATION

        // Same operator, new machine, original still active → multi-machine
        if (ctx.isSameOperator && ctx.originalMachineActive) {
            return MachineContinuationState.MULTI_MACHINE_CONTINUATION
        }

        // Same operator, machine replaced
        if (ctx.isSameOperator && !ctx.originalMachineActive) {
            return MachineContinuationState.MACHINE_SUBSTITUTION
        }

        // Temporary substitution
        if (ctx.isTemporary) return MachineContinuationState.TEMPORARY_HELPER_CONTINUATION

        // Default: same machine continuation
        return MachineContinuationState.SAME_MACHINE_CONTINUATION
    }

    private fun messageFor(state: MachineContinuationState): String = when (state) {
        MachineContinuationState.SAME_MACHINE_CONTINUATION -> "Continuing on same machine"
        MachineContinuationState.MACHINE_SUBSTITUTION -> "Machine changed · Work continues"
        MachineContinuationState.MULTI_MACHINE_CONTINUATION -> "Additional machine joined"
        MachineContinuationState.TEMPORARY_HELPER_CONTINUATION -> "Helper continuing on this machine"
        MachineContinuationState.RECOVERY_CONTINUATION -> "Machine recovered · Work continues"
    }

    private fun noteFor(state: MachineContinuationState, ctx: MachineContinuationContext): String = when (state) {
        MachineContinuationState.SAME_MACHINE_CONTINUATION -> "No changes needed"
        MachineContinuationState.MACHINE_SUBSTITUTION -> "Previous machine work preserved"
        MachineContinuationState.MULTI_MACHINE_CONTINUATION -> "Both machines tracked separately"
        MachineContinuationState.TEMPORARY_HELPER_CONTINUATION -> "Helper actions tracked with attribution"
        MachineContinuationState.RECOVERY_CONTINUATION -> "Work restored after interruption"
    }
}

/**
 * Machine continuation states — all calm.
 */
enum class MachineContinuationState(val displayLabel: String) {
    /** Same machine, same work, normal continuation. */
    SAME_MACHINE_CONTINUATION("Same machine"),
    /** Machine replaced mid-job (e.g., breakdown). */
    MACHINE_SUBSTITUTION("Machine changed"),
    /** Second machine joins same work (e.g., two tractors). */
    MULTI_MACHINE_CONTINUATION("Multiple machines"),
    /** Helper temporarily using this machine. */
    TEMPORARY_HELPER_CONTINUATION("Helper continuing"),
    /** Machine recovered after interruption. */
    RECOVERY_CONTINUATION("Recovered")
}

/**
 * Result of machine continuation resolution.
 */
data class MachineContinuationDecision(
    val state: MachineContinuationState,
    val preservesAttribution: Boolean,
    val preservesFinancialContinuity: Boolean,
    val operatorMessage: String,
    val continuityNote: String
)


