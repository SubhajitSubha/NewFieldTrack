package com.example.fieldtrack.data.plan

import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.PlanTier
import com.example.fieldtrack.data.db.entity.EntitlementEntity
import com.example.fieldtrack.data.region.BusinessClock
import java.time.LocalDate

/**
 * What the free plan covers and what a paid one adds.
 *
 * Free is a real plan, not a trial: one person with a few machines, and everything a
 * contractor needs to run the work and keep the khata. Paying adds a team, any number of
 * machines, and the extras that serve other people. Kept in one place so the owner can see and
 * change the lines without reading the app.
 *
 * **Limits only ever stop adding.** What a business already has stays and works: a lapsed plan
 * never hides a record, never stops anybody recording work, and never stops an export. See
 * `docs/pricing.md`.
 */
object PlanLimits {
    /** Machines on the free plan. */
    const val FREE_MACHINES = 3

    /** People on the free plan: the owner alone. */
    const val FREE_PEOPLE = 1

    /** Days after a plan runs out before the extras stop, so a slow renewal or a month with no signal costs nothing. */
    const val GRACE_DAYS = 14L
}

/** The things the free plan will not add. */
enum class Need { MoreMachines, TeamMember, BookingLink, ProofOfWork }

sealed interface Verdict {
    data object Allowed : Verdict

    /** Needs the paid plan, and says which thing asked. */
    data class NeedsBusiness(val need: Need) : Verdict
}

/** Where the plan stands today. */
sealed interface PlanStatus {
    data object Free : PlanStatus

    /** Paid and running; [daysLeft] is null when the server gave no end. */
    data class Active(val daysLeft: Long?, val founding: Boolean) : PlanStatus

    /** Ran out recently; the extras still work for [graceLeft] more days. */
    data class Grace(val graceLeft: Long, val founding: Boolean) : PlanStatus

    /** Ran out and past the grace: back to the free plan, with nothing taken away. */
    data object Lapsed : PlanStatus
}

object Plans {

    fun status(entitlement: EntitlementEntity?, today: Long): PlanStatus {
        if (entitlement == null || entitlement.tier != PlanTier.Business) return PlanStatus.Free
        val until = entitlement.untilDay ?: return PlanStatus.Active(null, entitlement.founding)
        val over = today - until
        return when {
            over <= 0 -> PlanStatus.Active(until - today, entitlement.founding)
            over <= PlanLimits.GRACE_DAYS -> PlanStatus.Grace(PlanLimits.GRACE_DAYS - over, entitlement.founding)
            else -> PlanStatus.Lapsed
        }
    }

    /** The tier the extras follow: paid while active or in grace, free otherwise. */
    fun tier(entitlement: EntitlementEntity?, today: Long): PlanTier = when (status(entitlement, today)) {
        is PlanStatus.Active, is PlanStatus.Grace -> PlanTier.Business
        else -> PlanTier.Free
    }

    /** Whether one more of [need] may be added, given what there already is. */
    fun verdict(need: Need, tier: PlanTier, machines: Int, people: Int): Verdict {
        if (tier == PlanTier.Business) return Verdict.Allowed
        val over = when (need) {
            Need.MoreMachines -> machines >= PlanLimits.FREE_MACHINES
            Need.TeamMember -> people >= PlanLimits.FREE_PEOPLE
            Need.BookingLink, Need.ProofOfWork -> true
        }
        return if (over) Verdict.NeedsBusiness(need) else Verdict.Allowed
    }
}

/** Asks the database what the plan allows right now. Reads only what is on the phone, so it answers offline. */
class PlanGate(private val db: FieldTrackDatabase, private val today: () -> Long = { LocalDate.now(BusinessClock.zone).toEpochDay() }) {

    suspend fun status(orgId: String): PlanStatus = Plans.status(db.orgDao().entitlement(orgId), today())

    suspend fun verdict(orgId: String, need: Need): Verdict {
        val tier = Plans.tier(db.orgDao().entitlement(orgId), today())
        return Plans.verdict(need, tier, db.machineDao().liveMachineCount(orgId), db.orgDao().activeMemberCount(orgId))
    }

    suspend fun allows(orgId: String, need: Need): Boolean = verdict(orgId, need) == Verdict.Allowed
}

/** Raised when something that needs the paid plan is attempted without it. Callers that checked first never see it. */
class PlanLimit(val need: Need) : Exception("This needs the Business plan: $need")
