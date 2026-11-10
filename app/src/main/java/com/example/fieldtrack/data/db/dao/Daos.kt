package com.example.fieldtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.entity.BookingEntity
import com.example.fieldtrack.data.db.entity.CashHandoverEntity
import com.example.fieldtrack.data.db.entity.MemberDetailsEntity
import com.example.fieldtrack.data.db.entity.MemberPayTermsEntity
import com.example.fieldtrack.data.db.entity.BookingRequestEntity
import com.example.fieldtrack.data.db.entity.EntitlementEntity
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.FuelEntryEntity
import com.example.fieldtrack.data.db.entity.InviteEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.JobEventEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import com.example.fieldtrack.data.db.entity.MachinePaperEntity
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.OutboxEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.db.entity.ServiceRecordEntity
import kotlinx.coroutines.flow.Flow
import com.example.fieldtrack.data.db.entity.TeamEventEntity
import androidx.room.OnConflictStrategy
import com.example.fieldtrack.data.db.entity.PayoutEntity
import com.example.fieldtrack.data.db.entity.JobCostEntity
import androidx.room.Embedded

/**
 * A job cost with what has been paid against it.
 *
 * The pair travels together because every screen that shows one wants the
 * other in the same breath — a cost without its settlement reads as paid.
 */
data class JobCostWithPaid(
    @Embedded val cost: JobCostEntity,
    val paidMinor: Long,
) {
    /** Null while nobody has priced it. Clamped, so an overpayment is not a credit. */
    val owedMinor: Long?
        get() = cost.amountMinor?.let { (it - paidMinor).coerceAtLeast(0) }
}

/*
 * Every read is scoped by orgId. That is not politeness — it is the mechanism that
 * stops one business's customers appearing in another's app on a shared driver's
 * phone. There is deliberately no "get all customers" query to misuse.
 */

/**
 * What a job is billed, as SQL: the work (or the fixed price, or the trip total) raised to
 * the minimum, plus the arrival charge, billed diesel and waiting time, less the discount,
 * never below nothing. [JobEntity.amountFor] is the same arithmetic in Kotlin and a test
 * keeps them identical. The first is for queries that read `jobs` bare, the second for
 * ones that alias it as `j`.
 */
internal const val JOB_AMOUNT_SQL = """MAX(0, MAX(
CASE billingMode
    WHEN 'Fixed' THEN rateMinor
    WHEN 'PerTrip' THEN tripTotalMinor
    ELSE CAST(ROUND(quantity * rateMinor / stepMinor) * stepMinor AS INTEGER)
END, minimumMinor) + arrivalMinor + fuelBilledMinor + waitingMinor - discountMinor)"""
internal const val JOB_AMOUNT_J_SQL = """MAX(0, MAX(
CASE j.billingMode
    WHEN 'Fixed' THEN j.rateMinor
    WHEN 'PerTrip' THEN j.tripTotalMinor
    ELSE CAST(ROUND(j.quantity * j.rateMinor / j.stepMinor) * j.stepMinor AS INTEGER)
END, j.minimumMinor) + j.arrivalMinor + j.fuelBilledMinor + j.waitingMinor - j.discountMinor)"""

@Dao
interface OrgDao {
    // ------------------------------------------------------------ cash in hand

    @Upsert suspend fun upsertHandover(handover: CashHandoverEntity)

    @Query("SELECT * FROM cash_handovers WHERE orgId = :orgId AND id = :id")
    suspend fun findHandover(orgId: String, id: String): CashHandoverEntity?

    @Query("SELECT * FROM cash_handovers WHERE orgId = :orgId ORDER BY atMillis DESC")
    fun observeHandovers(orgId: String): Flow<List<CashHandoverEntity>>

    /**
     * Cash each person took from farmers and recorded themselves, less any of it put right since.
     * A payment that was reversed is not money anybody is holding.
     */
    @Query(
        """
        SELECT l.byPersonId AS personId, SUM(l.amountMinor) AS amountMinor FROM ledger_entries l
        WHERE l.orgId = :orgId AND l.kind = 'Payment' AND l.method = 'Cash' AND l.byPersonId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM ledger_entries r WHERE r.orgId = l.orgId AND r.reversesEntryId = l.id)
        GROUP BY l.byPersonId
        """
    )
    fun observeCashTaken(orgId: String): Flow<List<PersonAmount>>

    @Query(
        """
        SELECT fromPersonId AS personId, SUM(amountMinor) AS amountMinor FROM cash_handovers
        WHERE orgId = :orgId AND voidedAt IS NULL GROUP BY fromPersonId
        """
    )
    fun observeCashHandedOver(orgId: String): Flow<List<PersonAmount>>

    // ------------------------------------------------------------ a person's particulars and pay

    @Upsert suspend fun upsertMemberDetails(details: MemberDetailsEntity)

    @Query("SELECT * FROM member_details WHERE orgId = :orgId AND personId = :personId")
    fun observeMemberDetails(orgId: String, personId: String): Flow<MemberDetailsEntity?>

    @Query("SELECT * FROM member_details WHERE orgId = :orgId")
    fun observeAllMemberDetails(orgId: String): Flow<List<MemberDetailsEntity>>

    @Upsert suspend fun upsertPayTerms(terms: MemberPayTermsEntity)

    @Query("SELECT * FROM member_pay_terms WHERE orgId = :orgId AND personId = :personId")
    fun observePayTerms(orgId: String, personId: String): Flow<MemberPayTermsEntity?>

    @Query("SELECT * FROM member_pay_terms WHERE orgId = :orgId")
    fun observeAllPayTerms(orgId: String): Flow<List<MemberPayTermsEntity>>

    /**
     * For each person with pay terms: the work they did as the operator since the terms began.
     * [offsetMillis] is the business's time zone, so a day is the business's day.
     */
    @Query(
        """
        SELECT t.personId AS personId,
            COALESCE((SELECT SUM(j.elapsedSeconds) FROM jobs j
                WHERE j.operatorPersonId = t.personId AND j.orgId = t.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= t.fromMillis), 0) AS seconds,
            COALESCE((SELECT SUM(j.quantity) FROM jobs j
                WHERE j.operatorPersonId = t.personId AND j.orgId = t.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= t.fromMillis
                  AND j.unit IN ${MeasureUnit.LAND_SQL}), 0.0) AS landQuantity,
            (SELECT COUNT(DISTINCT (j.workedAt + :offsetMillis) / 86400000) FROM jobs j
                WHERE j.operatorPersonId = t.personId AND j.orgId = t.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= t.fromMillis) AS days
        FROM member_pay_terms t
        WHERE t.orgId = :orgId
        """
    )
    fun observeWageFacts(orgId: String, offsetMillis: Long): Flow<List<WageFacts>>

    /** Wages paid to each person since their terms began: the cash, and any advance it took back. */
    @Query(
        """
        SELECT p.toPersonId AS personId, SUM(p.amountMinor + p.recoversAdvanceMinor) AS amountMinor
        FROM payouts p JOIN member_pay_terms t ON t.orgId = p.orgId AND t.personId = p.toPersonId
        WHERE p.orgId = :orgId AND p.voidedAt IS NULL AND p.covers IN ('Job', 'Day', 'Period')
          AND p.atMillis >= t.fromMillis
        GROUP BY p.toPersonId
        """
    )
    fun observeWagesPaid(orgId: String): Flow<List<PersonAmount>>

    @Upsert suspend fun upsertEntitlement(entitlement: EntitlementEntity)

    @Query("SELECT * FROM entitlements WHERE orgId = :orgId LIMIT 1")
    suspend fun entitlement(orgId: String): EntitlementEntity?

    @Query("SELECT * FROM entitlements WHERE orgId = :orgId LIMIT 1")
    fun observeEntitlement(orgId: String): Flow<EntitlementEntity?>

    @Upsert suspend fun upsertOrg(org: OrgEntity)

    @Query("SELECT * FROM organisations WHERE id = :orgId")
    fun observeOrg(orgId: String): Flow<OrgEntity?>

    @Query("SELECT * FROM organisations WHERE id = :orgId")
    suspend fun findOrg(orgId: String): OrgEntity?

    @Upsert suspend fun upsertMembership(membership: MembershipEntity)

    /** Every organisation this person belongs to, active ones first. */
    @Query(
        """
        SELECT * FROM memberships
        WHERE personId = :personId
        ORDER BY active DESC, joinedAt DESC
        """
    )
    fun observeMembershipsOf(personId: String): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND personId = :personId LIMIT 1")
    suspend fun membership(orgId: String, personId: String): MembershipEntity?

    /** Every business this person is on the books of, for telling each of them who they are now. */
    @Query("SELECT * FROM memberships WHERE personId = :personId")
    suspend fun membershipsOf(personId: String): List<MembershipEntity>

    // ------------------------------------------------------- what changed, when

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTeamEvent(event: TeamEventEntity)

    /** Newest first: the question is nearly always "what just happened". */
    @Query("SELECT * FROM team_events WHERE orgId = :orgId ORDER BY atMillis DESC LIMIT 100")
    fun observeTeamEvents(orgId: String): Flow<List<TeamEventEntity>>

    // ------------------------------------------------------------- money going out

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayout(payout: PayoutEntity)

    @Query("SELECT * FROM payouts WHERE orgId = :orgId ORDER BY atMillis DESC LIMIT 200")
    fun observePayouts(orgId: String): Flow<List<PayoutEntity>>

    @Query("SELECT * FROM payouts WHERE orgId = :orgId AND atMillis >= :from ORDER BY atMillis DESC, createdAt DESC LIMIT 3000")
    fun observePayoutsSince(orgId: String, from: Long): Flow<List<PayoutEntity>>

    @Query("SELECT * FROM payouts WHERE orgId = :orgId AND id = :id")
    suspend fun findPayout(orgId: String, id: String): PayoutEntity?

    /** Everything paid to one person, newest first, including what was taken back: the history. */
    @Query("SELECT * FROM payouts WHERE orgId = :orgId AND toPersonId = :personId ORDER BY atMillis DESC, createdAt DESC")
    fun observePayoutsTo(orgId: String, personId: String): Flow<List<PayoutEntity>>

    /** Every payment for the use of somebody's machine, so what is still owed is what was earned less this. */
    @Query("SELECT * FROM payouts WHERE orgId = :orgId AND covers = 'Hire' AND voidedAt IS NULL ORDER BY atMillis DESC")
    fun observeHirePayouts(orgId: String): Flow<List<PayoutEntity>>

    /**
     * What a team member has been advanced and not yet settled: advances given, less what
     * later pay has taken off them. Never below nothing.
     */
    @Query(
        """
        SELECT MAX(0, COALESCE(SUM(CASE WHEN covers = 'Advance' THEN amountMinor ELSE 0 END), 0) -
                      COALESCE(SUM(recoversAdvanceMinor), 0))
        FROM payouts WHERE orgId = :orgId AND toPersonId = :personId AND voidedAt IS NULL
        """
    )
    fun observeAdvanceOutstanding(orgId: String, personId: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM payouts
        WHERE orgId = :orgId AND voidedAt IS NULL AND atMillis BETWEEN :from AND :to
        """
    )
    fun observePaidOutBetween(orgId: String, from: Long, to: Long): Flow<Long>

    /**
     * What the jobs one machine did cost in people, paid or not.
     *
     * Summed from the job's own cost sheet and never from `payouts`, which is
     * the whole reason the two tables exist apart. Joining payouts to jobs here
     * as well would count every settled cost twice over.
     *
     * Unpaid costs are in. What a job cost does not depend on whether Saturday
     * has come round, and a machine that looks profitable only because its
     * crew has not been paid yet is the lie this screen exists to prevent.
     */
    @Query(
        """
        SELECT COALESCE(SUM(c.amountMinor), 0) FROM job_costs c
        JOIN jobs j ON j.id = c.jobId
        WHERE c.orgId = :orgId AND j.machineId = :machineId AND j.deletedAt IS NULL
        """
    )
    fun observeJobCostOnMachine(orgId: String, machineId: String): Flow<Long>

    /** The same, asked once, for a report over a stretch of time. */
    @Query(
        """
        SELECT COALESCE(SUM(c.amountMinor), 0) FROM job_costs c
        JOIN jobs j ON j.id = c.jobId
        WHERE c.orgId = :orgId AND j.machineId = :machineId
          AND c.atMillis BETWEEN :from AND :to
        """
    )
    suspend fun jobCostOnMachine(orgId: String, machineId: String, from: Long, to: Long): Long

    /**
     * Everything no machine can carry.
     *
     * Keyed on not settling a job cost, rather than on [PayoutCovers]. A day's
     * pay belongs to no job either, and counting only the month-long ones
     * understated what was being left out — which is the one thing this figure
     * exists to prevent. A row that somehow names a job but settles no cost
     * lands here too: better over-reported in the line that admits to it than
     * quietly absent from both.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM payouts
        WHERE orgId = :orgId AND voidedAt IS NULL AND jobCostId IS NULL AND covers != 'Hire'
        """
    )
    fun observeUnattributablePayouts(orgId: String): Flow<Long>

    /** What could not be laid against anything, so a total can admit to it. */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM payouts
        WHERE orgId = :orgId AND voidedAt IS NULL AND jobCostId IS NULL AND covers != 'Hire' AND atMillis BETWEEN :from AND :to
        """
    )
    suspend fun paidOutUnattributable(orgId: String, from: Long, to: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJobCost(cost: JobCostEntity)

    /**
     * Writes the work facts without touching the figure.
     *
     * The row that comes down from the server carries no rupees — they travel
     * in their own document — so a plain upsert would blank the amount on the
     * phone of the very person who typed it.
     */
    @Query(
        """
        UPDATE job_costs
           SET kind = :kind, headcount = :headcount, note = :note,
               byPersonId = :byPersonId, atMillis = :atMillis, syncState = 'Synced'
         WHERE orgId = :orgId AND id = :id
        """
    )
    suspend fun updateJobCostFacts(
        orgId: String,
        id: String,
        kind: JobCostKind,
        headcount: Int?,
        note: String?,
        byPersonId: String?,
        atMillis: Long,
    ): Int

    @Query("UPDATE job_costs SET amountMinor = :amount WHERE orgId = :orgId AND id = :id")
    suspend fun setJobCostAmount(orgId: String, id: String, amount: Long): Int

    @Query("SELECT * FROM job_costs WHERE orgId = :orgId AND id = :id")
    suspend fun findJobCost(orgId: String, id: String): JobCostEntity?

    /**
     * One job's costs, each with what has actually been paid against it.
     *
     * The paid figure is summed from the payments pointing here rather than
     * held as a flag, so a cost settled in two halves reads correctly and
     * nothing has to remember to flip when the second half goes.
     */
    @Query(
        """
        SELECT c.*, COALESCE(
            (SELECT SUM(p.amountMinor) FROM payouts p WHERE p.jobCostId = c.id AND p.voidedAt IS NULL), 0
        ) AS paidMinor
        FROM job_costs c
        WHERE c.orgId = :orgId AND c.jobId = :jobId
        ORDER BY c.atMillis
        """
    )
    fun observeJobCosts(orgId: String, jobId: String): Flow<List<JobCostWithPaid>>

    /** What this job cost in all, paid or not. Null amounts are not yet priced. */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM job_costs
        WHERE orgId = :orgId AND jobId = :jobId
        """
    )
    fun observeJobCostTotal(orgId: String, jobId: String): Flow<Long>

    /**
     * Money owed on work already done, across the business.
     *
     * Clamped at zero per cost. An overpayment on one job is a thing to sort
     * out on that job, not a silent discount against what is owed on another.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN c.amountMinor - COALESCE(
                (SELECT SUM(p.amountMinor) FROM payouts p WHERE p.jobCostId = c.id AND p.voidedAt IS NULL), 0
            ) > 0
            THEN c.amountMinor - COALESCE(
                (SELECT SUM(p.amountMinor) FROM payouts p WHERE p.jobCostId = c.id AND p.voidedAt IS NULL), 0
            )
            ELSE 0 END
        ), 0)
        FROM job_costs c
        WHERE c.orgId = :orgId AND c.amountMinor IS NOT NULL
        """
    )
    fun observeOwedOnJobs(orgId: String): Flow<Long>

    /** Of this machine's job costs, what is still owed. */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN c.amountMinor - COALESCE(
                (SELECT SUM(p.amountMinor) FROM payouts p WHERE p.jobCostId = c.id AND p.voidedAt IS NULL), 0
            ) > 0
            THEN c.amountMinor - COALESCE(
                (SELECT SUM(p.amountMinor) FROM payouts p WHERE p.jobCostId = c.id AND p.voidedAt IS NULL), 0
            )
            ELSE 0 END
        ), 0)
        FROM job_costs c
        JOIN jobs j ON j.id = c.jobId
        WHERE c.orgId = :orgId AND j.machineId = :machineId AND c.amountMinor IS NOT NULL
        """
    )
    fun observeOwedOnMachine(orgId: String, machineId: String): Flow<Long>

    /** Costs somebody recorded but nobody has put a figure on yet. */
    @Query(
        """
        SELECT COUNT(*) FROM job_costs
        WHERE orgId = :orgId AND amountMinor IS NULL
        """
    )
    fun observeUnpricedJobCostCount(orgId: String): Flow<Int>

    /**
     * How many amounts this business has put in its books, of any kind.
     *
     * Not zero means its currency is no longer a free choice: every one of them is
     * written in it.
     */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM ledger_entries WHERE orgId = :orgId) +
            (SELECT COUNT(*) FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL) +
            (SELECT COUNT(*) FROM payouts WHERE orgId = :orgId) +
            (SELECT COUNT(*) FROM fuel_entries WHERE orgId = :orgId) +
            (SELECT COUNT(*) FROM rates WHERE orgId = :orgId) +
            (SELECT COUNT(*) FROM service_records WHERE orgId = :orgId AND costMinor IS NOT NULL) +
            (SELECT COUNT(*) FROM job_costs WHERE orgId = :orgId AND amountMinor IS NOT NULL) +
            (SELECT COUNT(*) FROM machine_finance WHERE orgId = :orgId AND monthlyMinor IS NOT NULL)
        """
    )
    suspend fun moneyEntryCount(orgId: String): Int

    /**
     * What the business billed over a stretch of time.
     *
     * Completed work only, and the same arithmetic the job itself uses, so a
     * season total and the jobs behind it cannot disagree.
     */
    @Query(
        """
        SELECT COALESCE(SUM($JOB_AMOUNT_SQL), 0)
        FROM jobs
        WHERE orgId = :orgId AND deletedAt IS NULL AND status = 'Completed'
          AND workedAt BETWEEN :from AND :to
        """
    )
    fun observeEarnedBetween(orgId: String, from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(f.costMinor), 0) FROM fuel_entries f
        LEFT JOIN jobs j ON j.id = f.jobId
        WHERE f.orgId = :orgId AND COALESCE(j.workedAt, f.atMillis) BETWEEN :from AND :to
        """
    )
    fun observeFuelSpendBetween(orgId: String, from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(costMinor), 0) FROM service_records
        WHERE orgId = :orgId AND atMillis BETWEEN :from AND :to
        """
    )
    fun observeServiceSpendBetween(orgId: String, from: Long, to: Long): Flow<Long>

    /**
     * What people cost over a stretch of time, laid against jobs.
     *
     * Dated by the day the job was worked, not the day the cost was written down: a
     * job worked on the last day of one season with its crew entered the morning after
     * belongs wholly to the season it was worked in. Diesel put in on a job follows the
     * same rule; diesel and repairs that belong to no job keep their own date.
     *
     * Priced work, paid or not. The unpriced are counted separately so the
     * screen can refuse a total rather than quietly understate one.
     */
    @Query(
        """
        SELECT COALESCE(SUM(c.amountMinor), 0) FROM job_costs c
        JOIN jobs j ON j.id = c.jobId
        WHERE c.orgId = :orgId AND j.deletedAt IS NULL
          AND j.workedAt BETWEEN :from AND :to
        """
    )
    fun observeJobCostBetween(orgId: String, from: Long, to: Long): Flow<Long>

    @Query(
        """
        SELECT COUNT(*) FROM job_costs c
        JOIN jobs j ON j.id = c.jobId
        WHERE c.orgId = :orgId AND c.amountMinor IS NULL AND j.deletedAt IS NULL
          AND j.workedAt BETWEEN :from AND :to
        """
    )
    fun observeUnpricedBetween(orgId: String, from: Long, to: Long): Flow<Int>

    /**
     * Wages that belong to no job: a month's pay, a day's hire, a season advance.
     *
     * No machine could carry these and the machine screen says so out loud.
     * A season can carry them — it is the stretch of time they were for — which
     * is the whole reason this screen exists.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM payouts
        WHERE orgId = :orgId AND voidedAt IS NULL AND covers != 'Hire' AND jobCostId IS NULL AND atMillis BETWEEN :from AND :to
        """
    )
    fun observeLooseWagesBetween(orgId: String, from: Long, to: Long): Flow<Long>

    /**
     * What each finished job brought in and what is known to have gone into it.
     *
     * Diesel logged against the job and the priced costs on its sheet — the same
     * two things the job's own page adds up, so a loss flagged on a list is a loss
     * the job's page will show when it is opened. Diesel put in at the yard and a
     * machine's wear are in neither, which makes this the least a job cost, never
     * the most.
     */
    @Query(
        """
        SELECT j.id AS jobId,
            $JOB_AMOUNT_J_SQL AS earnedMinor,
            COALESCE((SELECT SUM(f.costMinor) FROM fuel_entries f WHERE f.jobId = j.id), 0) AS fuelMinor,
            COALESCE((SELECT SUM(c.amountMinor) FROM job_costs c WHERE c.jobId = j.id), 0) AS peopleMinor,
            (SELECT COUNT(*) FROM job_costs c WHERE c.jobId = j.id AND c.amountMinor IS NULL) AS unpriced
        FROM jobs j
        WHERE j.orgId = :orgId AND j.deletedAt IS NULL AND j.status = 'Completed'
          AND j.workedAt BETWEEN :from AND :to
        """
    )
    fun observeJobMargins(orgId: String, from: Long, to: Long): Flow<List<JobMargin>>

    /**
     * Costs the version-12 migration made out of job payments on this phone.
     *
     * That migration wrote them and never queued them, so they and the payments
     * now pointing at them never left the handset. Found by the id the migration
     * gave them and the device that recorded the original payment, so a copy that
     * arrived from the server is not sent back up as though it were new.
     */
    @Query(
        """
        SELECT * FROM job_costs
        WHERE orgId = :orgId AND id LIKE 'jc!_%' ESCAPE '!' AND deviceId = :deviceId
        """
    )
    suspend fun migratedJobCosts(orgId: String, deviceId: String): List<JobCostEntity>

    @Query(
        """
        SELECT * FROM payouts
        WHERE orgId = :orgId AND jobCostId LIKE 'jc!_%' ESCAPE '!' AND deviceId = :deviceId
        """
    )
    suspend fun payoutsOnMigratedCosts(orgId: String, deviceId: String): List<PayoutEntity>

    /** The team list: everyone in one business. */
    @Query(
        """
        SELECT * FROM memberships WHERE orgId = :orgId AND active = 1
          AND personId NOT IN (SELECT r.replacesPersonId FROM memberships r WHERE r.orgId = :orgId AND r.replacesPersonId IS NOT NULL)
        ORDER BY role, displayName
        """
    )
    fun observeTeam(orgId: String): Flow<List<MembershipEntity>>

    /** Everyone ever on the books here, including people who have left. */
    @Query(
        """
        SELECT * FROM memberships WHERE orgId = :orgId
          AND personId NOT IN (SELECT r.replacesPersonId FROM memberships r WHERE r.orgId = :orgId AND r.replacesPersonId IS NOT NULL)
        ORDER BY active DESC, role, displayName
        """
    )
    fun observeEveryone(orgId: String): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND id = :membershipId LIMIT 1")
    suspend fun membershipById(orgId: String, membershipId: String): MembershipEntity?

    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND id = :membershipId LIMIT 1")
    fun observeMembershipById(orgId: String, membershipId: String): Flow<MembershipEntity?>

    /** Everybody working in the business now, for what the plan allows. */
    @Query("SELECT COUNT(*) FROM memberships WHERE orgId = :orgId AND active = 1")
    suspend fun activeMemberCount(orgId: String): Int

    /** Guards the last-owner rule: a business must always have someone in charge. */
    @Query("SELECT COUNT(*) FROM memberships WHERE orgId = :orgId AND active = 1 AND role = 'Owner'")
    suspend fun activeOwnerCount(orgId: String): Int

    /** Every business this person has ever belonged to, ended ones included. */
    @Query("SELECT COUNT(*) FROM memberships WHERE personId = :personId")
    suspend fun membershipCountFor(personId: String): Int

    /**
     * Any business they still work for, for putting a session back together.
     *
     * Used when the app knows who somebody is but not which business to show —
     * after an account is turned back on, for instance, where the alternative is
     * presenting a person who has three employers with a wizard asking them to
     * set up their first one.
     */
    @Query("SELECT * FROM memberships WHERE personId = :personId AND active = 1 ORDER BY joinedAt ASC LIMIT 1")
    suspend fun firstActiveMembershipFor(personId: String): MembershipEntity?

    /** Only the ones they work for today — what the profile row counts. */
    @Query("SELECT COUNT(*) FROM memberships WHERE personId = :personId AND active = 1")
    suspend fun activeMembershipCountFor(personId: String): Int

    // ---------------------------------------------------- adopting an identity
    //
    // Every column that names a person, rewritten together. Split across the DAO
    // rather than one statement because they are different tables, but they are
    // only ever called inside a single transaction: an identity half-changed is
    // worse than one not changed at all, since the rows would disagree about who
    // did the work.

    @Query("UPDATE memberships SET personId = :to, id = orgId || '_' || :to WHERE personId = :from")
    suspend fun adoptMemberships(from: String, to: String)

    @Query("UPDATE invites SET redeemedByPersonId = :to WHERE redeemedByPersonId = :from")
    suspend fun adoptInvites(from: String, to: String)

    @Upsert suspend fun upsertInvite(invite: InviteEntity)

    @Query("SELECT * FROM invites WHERE code = :code AND redeemedAt IS NULL AND expiresAt > :now LIMIT 1")
    suspend fun findUsableInvite(code: String, now: Long): InviteEntity?

    /** The code an owner is currently handing out, if it is still good. */
    @Query(
        """
        SELECT * FROM invites
        WHERE orgId = :orgId AND redeemedAt IS NULL AND expiresAt > :now
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    fun observeOpenInvite(orgId: String, now: Long): Flow<InviteEntity?>

    /**
     * Every code not yet used, newest first, whether or not it has run out.
     *
     * Deliberately without a time bound. A `now` passed in here is read once,
     * when the query is built, and a flow then goes on answering against that
     * instant for as long as anybody watches it — so a code withdrawn a minute
     * later still looked live until the screen was thrown away and remade. The
     * caller drops the expired ones against a clock read per emission.
     */
    @Query(
        """
        SELECT * FROM invites
        WHERE orgId = :orgId AND redeemedAt IS NULL
        ORDER BY createdAt DESC
        """
    )
    fun observeUnusedInvites(orgId: String): Flow<List<InviteEntity>>

    /** The last few that were used, so an owner can see who came in on what. */
    @Query(
        """
        SELECT * FROM invites
        WHERE orgId = :orgId AND redeemedAt IS NOT NULL
        ORDER BY redeemedAt DESC LIMIT 5
        """
    )
    fun observeRedeemedInvites(orgId: String): Flow<List<InviteEntity>>

    @Query("SELECT * FROM invites WHERE id = :id")
    suspend fun inviteById(id: String): InviteEntity?

    /** Retires every open code for this organisation, so a new one stands alone. */
    @Query(
        """
        UPDATE invites SET expiresAt = :now
        WHERE orgId = :orgId AND redeemedAt IS NULL AND expiresAt > :now
        """
    )
    suspend fun closeOpenInvites(orgId: String, now: Long)
}

@Dao
interface CustomerDao {
    @Upsert suspend fun upsert(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY name")
    fun observeAll(orgId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id AND orgId = :orgId")
    fun observe(orgId: String, id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id AND orgId = :orgId")
    suspend fun find(orgId: String, id: String): CustomerEntity?

    @Query(
        """
        SELECT * FROM customers
        WHERE orgId = :orgId AND deletedAt IS NULL
          AND (name LIKE '%' || :term || '%' OR village LIKE '%' || :term || '%')
        ORDER BY name
        """
    )
    fun search(orgId: String, term: String): Flow<List<CustomerEntity>>

    /** Customers whose name, village or phone contains the term, for the search box. */
    @Query(
        """
        SELECT * FROM customers
        WHERE orgId = :orgId AND deletedAt IS NULL
          AND (name LIKE :like OR village LIKE :like OR phone LIKE :like)
        ORDER BY name LIMIT 20
        """
    )
    suspend fun findByTerm(orgId: String, like: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE orgId = :orgId AND id IN (:ids)")
    suspend fun findByIds(orgId: String, ids: List<String>): List<CustomerEntity>

    @Query(
        """
        SELECT * FROM fields WHERE orgId = :orgId AND deletedAt IS NULL AND name LIKE :like
        ORDER BY name LIMIT 20
        """
    )
    suspend fun findFieldsByTerm(orgId: String, like: String): List<FieldEntity>

    @Upsert suspend fun upsertField(field: FieldEntity)

    @Query("SELECT * FROM fields WHERE orgId = :orgId AND customerId = :customerId AND deletedAt IS NULL ORDER BY name")
    fun observeFields(orgId: String, customerId: String): Flow<List<FieldEntity>>

    @Query("SELECT * FROM fields WHERE orgId = :orgId AND deletedAt IS NULL")
    fun observeAllFields(orgId: String): Flow<List<FieldEntity>>

    @Query("SELECT * FROM fields WHERE orgId = :orgId AND id = :id")
    fun observeField(orgId: String, id: String): Flow<FieldEntity?>

    @Query("SELECT * FROM fields WHERE orgId = :orgId AND id = :id")
    suspend fun findField(orgId: String, id: String): FieldEntity?

    /**
     * What each of a customer's fields has brought in, and when it was last worked.
     *
     * One query rather than one per card, because a customer with nine plots
     * would otherwise mean nine round trips behind a list that has to scroll.
     * Completed jobs only: a job still running has not earned anything yet, and
     * showing its amount would make a field look paid for work in progress.
     */
    @Query(
        """
        SELECT j.fieldId AS fieldId,
               COUNT(*) AS jobCount,
               COALESCE(SUM($JOB_AMOUNT_J_SQL), 0) AS earnedMinor,
               MAX(j.workedAt) AS lastWorkedAt
        FROM jobs j
        WHERE j.orgId = :orgId AND j.customerId = :customerId
          AND j.fieldId IS NOT NULL AND j.deletedAt IS NULL
          AND j.status = 'Completed'
        GROUP BY j.fieldId
        """
    )
    fun observeFieldWork(orgId: String, customerId: String): Flow<List<FieldWorkTotals>>
}

/** What one field has had done on it, for the list and the detail. */
data class FieldWorkTotals(
    val fieldId: String,
    val jobCount: Int,
    val earnedMinor: Long,
    val lastWorkedAt: Long?,
)

@Dao
interface MachineDao {
    @Upsert suspend fun upsert(machine: MachineEntity)

    /** Machines whose name, plate or own kind-name contains the term. */
    @Query(
        """
        SELECT * FROM machines
        WHERE orgId = :orgId AND deletedAt IS NULL
          AND (name LIKE :like OR plate LIKE :like OR typeLabel LIKE :like OR kind LIKE :like)
        ORDER BY name LIMIT 20
        """
    )
    suspend fun findByTerm(orgId: String, like: String): List<MachineEntity>

    @Query("SELECT * FROM machines WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY name")
    fun observeAll(orgId: String): Flow<List<MachineEntity>>

    /** Every machine with the hours worked since its meter was set — see [MachineWithWork]. */
    @Query(
        """
        SELECT m.*, COALESCE((
            SELECT SUM(j.elapsedSeconds) FROM jobs j
            WHERE (j.machineId = m.id OR j.implementMachineId = m.id) AND j.orgId = m.orgId AND j.status = 'Completed'
              AND j.deletedAt IS NULL AND j.completedAt > COALESCE(m.hoursAsOf, m.createdAt)
        ), 0) AS workedSeconds
        FROM machines m
        WHERE m.orgId = :orgId AND m.deletedAt IS NULL
        ORDER BY m.name
        """
    )
    fun observeAllWithWork(orgId: String): Flow<List<MachineWithWork>>

    @Query(
        """
        SELECT m.*, COALESCE((
            SELECT SUM(j.elapsedSeconds) FROM jobs j
            WHERE (j.machineId = m.id OR j.implementMachineId = m.id) AND j.orgId = m.orgId AND j.status = 'Completed'
              AND j.deletedAt IS NULL AND j.completedAt > COALESCE(m.hoursAsOf, m.createdAt)
        ), 0) AS workedSeconds
        FROM machines m
        WHERE m.orgId = :orgId AND m.id = :id
        """
    )
    fun observeWithWork(orgId: String, id: String): Flow<MachineWithWork?>

    @Query(
        """
        SELECT m.*, COALESCE((
            SELECT SUM(j.elapsedSeconds) FROM jobs j
            WHERE (j.machineId = m.id OR j.implementMachineId = m.id) AND j.orgId = m.orgId AND j.status = 'Completed'
              AND j.deletedAt IS NULL AND j.completedAt > COALESCE(m.hoursAsOf, m.createdAt)
        ), 0) AS workedSeconds
        FROM machines m
        WHERE m.orgId = :orgId AND m.id = :id
        """
    )
    suspend fun findWithWork(orgId: String, id: String): MachineWithWork?

    /**
     * For each machine that is somebody else's: what it has done since the hire began.
     * [offsetMillis] is the business's time zone, so a day is the business's day.
     */
    @Query(
        """
        SELECT f.id AS machineId,
            COALESCE((SELECT SUM(j.elapsedSeconds) FROM jobs j
                WHERE j.machineId = f.id AND j.orgId = f.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= MAX(f.hireFrom, :from) AND j.completedAt <= :to), 0) AS seconds,
            COALESCE((SELECT SUM(j.quantity) FROM jobs j
                WHERE j.machineId = f.id AND j.orgId = f.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= MAX(f.hireFrom, :from) AND j.completedAt <= :to
                  AND j.unit IN ${MeasureUnit.LAND_SQL}), 0.0) AS landQuantity,
            (SELECT COUNT(DISTINCT (j.workedAt + :offsetMillis) / 86400000) FROM jobs j
                WHERE j.machineId = f.id AND j.orgId = f.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= MAX(f.hireFrom, :from) AND j.completedAt <= :to) AS days,
            COALESCE((SELECT SUM($JOB_AMOUNT_J_SQL) FROM jobs j
                WHERE j.machineId = f.id AND j.orgId = f.orgId AND j.status = 'Completed'
                  AND j.deletedAt IS NULL AND j.completedAt >= MAX(f.hireFrom, :from) AND j.completedAt <= :to), 0) AS amountMinor
        FROM machine_finance f
        WHERE f.orgId = :orgId AND f.ownerKind IN ('Member', 'Outsider') AND f.hireFrom IS NOT NULL
        """
    )
    fun observeHireFacts(orgId: String, offsetMillis: Long, from: Long, to: Long): Flow<List<HireFacts>>

    @Upsert suspend fun upsertFinance(finance: MachineFinanceEntity)

    @Upsert suspend fun upsertPaper(paper: MachinePaperEntity)

    @Query("SELECT COUNT(*) FROM machines WHERE orgId = :orgId AND deletedAt IS NULL")
    suspend fun liveMachineCount(orgId: String): Int

    @Query("SELECT name FROM machines WHERE orgId = :orgId AND id = :machineId")
    suspend fun findMachineName(orgId: String, machineId: String): String?

    @Query("SELECT * FROM machine_papers WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY expiresDay IS NULL, expiresDay")
    fun observePapers(orgId: String): Flow<List<MachinePaperEntity>>

    @Query("SELECT * FROM machine_papers WHERE orgId = :orgId AND machineId = :machineId AND deletedAt IS NULL ORDER BY expiresDay IS NULL, expiresDay")
    fun observePapersFor(orgId: String, machineId: String): Flow<List<MachinePaperEntity>>

    @Query("SELECT * FROM machine_papers WHERE orgId = :orgId AND id = :id")
    suspend fun findPaper(orgId: String, id: String): MachinePaperEntity?

    @Query("SELECT * FROM machine_papers WHERE orgId = :orgId AND deletedAt IS NULL")
    suspend fun papersOnce(orgId: String): List<MachinePaperEntity>

    @Query("SELECT * FROM machine_finance WHERE orgId = :orgId")
    fun observeFinance(orgId: String): Flow<List<MachineFinanceEntity>>

    @Query("SELECT * FROM machine_finance WHERE orgId = :orgId AND id = :machineId")
    suspend fun findFinance(orgId: String, machineId: String): MachineFinanceEntity?

    /** Loan instalments a month, across the machines still in the yard. */
    @Query(
        """
        SELECT COALESCE(SUM(f.monthlyMinor), 0) FROM machine_finance f
        JOIN machines m ON m.id = f.id AND m.orgId = f.orgId
        WHERE f.orgId = :orgId AND m.deletedAt IS NULL
        """
    )
    fun observeMonthlyLoans(orgId: String): Flow<Long>

    /**
     * What it took to run each machine, in one pass.
     *
     * Hours come from finished jobs only — work still running has not been
     * written down yet — and diesel and repairs are what was actually spent, so a
     * cost per hour built from this can be traced to receipts.
     */
    @Query(
        """
        SELECT m.id AS machineId,
            COALESCE((SELECT SUM(j.elapsedSeconds) FROM jobs j
                WHERE (j.machineId = m.id OR j.implementMachineId = m.id) AND j.orgId = m.orgId
                  AND j.status = 'Completed' AND j.deletedAt IS NULL), 0) AS seconds,
            COALESCE((SELECT SUM(f.costMinor) FROM fuel_entries f
                WHERE f.machineId = m.id AND f.orgId = m.orgId), 0) AS fuelMinor,
            COALESCE((SELECT SUM(s.costMinor) FROM service_records s
                WHERE s.machineId = m.id AND s.orgId = m.orgId), 0) AS serviceMinor,
            COALESCE((SELECT SUM(j.elapsedSeconds) FROM jobs j
                WHERE (j.machineId = m.id OR j.implementMachineId = m.id) AND j.orgId = m.orgId
                  AND j.status = 'Completed' AND j.deletedAt IS NULL
                  AND j.completedAt >= :yearAgo), 0) AS yearSeconds
        FROM machines m
        WHERE m.orgId = :orgId AND m.deletedAt IS NULL
        """
    )
    fun observeRunningFacts(orgId: String, yearAgo: Long): Flow<List<RunningFacts>>

    /** The same machine's finished work, grouped by what was done and how it was measured. */
    @Query(
        """
        SELECT j.workType AS workType, j.unit AS unit,
            COALESCE(SUM(j.quantity), 0.0) AS quantity,
            COALESCE(SUM(CASE WHEN j.elapsedSeconds > 0 THEN j.quantity ELSE 0.0 END), 0.0) AS timedQuantity,
            COALESCE(SUM(j.elapsedSeconds), 0) AS seconds,
            COALESCE(SUM($JOB_AMOUNT_J_SQL), 0) AS earnedMinor,
            COUNT(*) AS jobs
        FROM jobs j
        WHERE j.orgId = :orgId AND j.machineId = :machineId AND j.deletedAt IS NULL
          AND j.status = 'Completed' AND j.billingMode != 'Fixed'
        GROUP BY j.workType, j.unit
        """
    )
    fun observeWorkKinds(orgId: String, machineId: String): Flow<List<WorkKindFacts>>

    /**
     * Litres burnt on jobs, and the time those same jobs took, per machine in a window.
     *
     * Only fuel logged against a job, over only those jobs' time. Diesel put in at
     * the yard belongs to no stretch of work, and dividing it by hours it did not
     * cover is how a tractor topped up twice in one day comes to look like it
     * drinks a hundred litres an hour.
     */
    @Query(
        """
        SELECT j.machineId AS machineId,
            COALESCE(SUM(f.litres), 0.0) AS litres,
            COALESCE(SUM(j.elapsedSeconds), 0) AS seconds,
            COUNT(DISTINCT j.id) AS jobs
        FROM jobs j
        JOIN (SELECT jobId, SUM(litres) AS litres FROM fuel_entries
              WHERE jobId IS NOT NULL GROUP BY jobId) f ON f.jobId = j.id
        WHERE j.orgId = :orgId AND j.machineId IS NOT NULL AND j.deletedAt IS NULL
          AND j.status = 'Completed' AND j.workedAt >= :from AND j.workedAt < :to
        GROUP BY j.machineId
        """
    )
    fun observeFuelOnJobs(orgId: String, from: Long, to: Long): Flow<List<FuelOnJobs>>

    @Query("SELECT * FROM machines WHERE id = :id AND orgId = :orgId")
    fun observe(orgId: String, id: String): Flow<MachineEntity?>

    @Query("SELECT * FROM machines WHERE id = :id AND orgId = :orgId")
    suspend fun find(orgId: String, id: String): MachineEntity?

    // There used to be a service-due query here, reading the stored meter reading in
    // SQL. That reading is not the machine's hours (see MachineWithWork), so the
    // question is answered in WorkRepository.observeServiceDue from hours as they stand.

    @Upsert suspend fun upsertFuel(entry: FuelEntryEntity)

    @Query("SELECT * FROM fuel_entries WHERE orgId = :orgId AND atMillis >= :from ORDER BY atMillis DESC LIMIT 3000")
    fun observeFuelSince(orgId: String, from: Long): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM service_records WHERE orgId = :orgId AND costMinor IS NOT NULL AND atMillis >= :from ORDER BY atMillis DESC LIMIT 3000")
    fun observeServiceSince(orgId: String, from: Long): Flow<List<ServiceRecordEntity>>

    @Query("SELECT * FROM fuel_entries WHERE orgId = :orgId AND machineId = :machineId ORDER BY atMillis DESC")
    fun observeFuel(orgId: String, machineId: String): Flow<List<FuelEntryEntity>>

    @Query("SELECT COALESCE(SUM(costMinor), 0) FROM fuel_entries WHERE orgId = :orgId AND atMillis >= :since")
    fun observeFuelSpendSince(orgId: String, since: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(costMinor), 0) FROM fuel_entries
        WHERE orgId = :orgId AND machineId = :machineId
        """
    )
    fun observeFuelSpendOnMachine(orgId: String, machineId: String): Flow<Long>

    /** Diesel put in while this job was running, when it was logged against it. */
    @Query(
        """
        SELECT COALESCE(SUM(costMinor), 0) FROM fuel_entries
        WHERE orgId = :orgId AND jobId = :jobId
        """
    )
    fun observeFuelSpendOnJob(orgId: String, jobId: String): Flow<Long>

    /** The same in litres, for somebody who may not see what it cost. */
    @Query(
        """
        SELECT COALESCE(SUM(litres), 0.0) FROM fuel_entries
        WHERE orgId = :orgId AND jobId = :jobId
        """
    )
    fun observeFuelLitresOnJob(orgId: String, jobId: String): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(costMinor), 0) FROM service_records
        WHERE orgId = :orgId AND machineId = :machineId
        """
    )
    fun observeServiceSpendOnMachine(orgId: String, machineId: String): Flow<Long>

    @Upsert suspend fun upsertService(record: ServiceRecordEntity)

    @Query("SELECT * FROM service_records WHERE orgId = :orgId AND machineId = :machineId ORDER BY atMillis DESC")
    fun observeService(orgId: String, machineId: String): Flow<List<ServiceRecordEntity>>

    @Upsert suspend fun upsertBreakdown(breakdown: BreakdownEntity)

    /** Every machine, once, for a pass that puts stored statuses right. */
    @Query("SELECT * FROM machines WHERE orgId = :orgId AND deletedAt IS NULL")
    suspend fun observeAllOnce(orgId: String): List<MachineEntity>

    @Query("SELECT * FROM breakdowns WHERE orgId = :orgId AND resolvedAt IS NULL ORDER BY reportedAt DESC")
    fun observeOpenBreakdowns(orgId: String): Flow<List<BreakdownEntity>>

    @Query("SELECT * FROM breakdowns WHERE orgId = :orgId AND machineId = :machineId ORDER BY reportedAt DESC")
    fun observeBreakdowns(orgId: String, machineId: String): Flow<List<BreakdownEntity>>

    @Query("SELECT * FROM breakdowns WHERE id = :id")
    suspend fun breakdownById(id: String): BreakdownEntity?

    @Query("UPDATE fuel_entries SET byPersonId = :to WHERE byPersonId = :from")
    suspend fun adoptFuel(from: String, to: String)

    @Query("SELECT * FROM fuel_entries WHERE byPersonId = :personId")
    suspend fun fuelBy(personId: String): List<FuelEntryEntity>

    @Query("UPDATE breakdowns SET reportedByPersonId = :to WHERE reportedByPersonId = :from")
    suspend fun adoptBreakdowns(from: String, to: String)

    @Query("SELECT * FROM breakdowns WHERE reportedByPersonId = :personId")
    suspend fun breakdownsBy(personId: String): List<BreakdownEntity>

    /** Whether this machine is still broken, as opposed to merely having been. */
    @Query(
        """
        SELECT COUNT(*) FROM breakdowns
        WHERE orgId = :orgId AND machineId = :machineId AND resolvedAt IS NULL
        """
    )
    suspend fun openBreakdownCount(orgId: String, machineId: String): Int

    @Query("SELECT * FROM service_records WHERE orgId = :orgId ORDER BY atMillis DESC LIMIT :limit")
    fun observeRecentService(orgId: String, limit: Int): Flow<List<ServiceRecordEntity>>
}

@Dao
interface JobDao {
    @Upsert suspend fun upsert(job: JobEntity)

    /** Jobs whose kind of work, note or customer's name contains the term, newest first. */
    @Query(
        """
        SELECT j.* FROM jobs j
        LEFT JOIN customers c ON c.id = j.customerId
        WHERE j.orgId = :orgId AND j.deletedAt IS NULL AND j.status != 'Discarded'
          AND (j.workType LIKE :like OR j.note LIKE :like OR c.name LIKE :like)
        ORDER BY j.workedAt DESC LIMIT 20
        """
    )
    suspend fun findByTerm(orgId: String, like: String): List<JobEntity>

    /**
     * Jobs still open on this machine. A paused job counts: the machine is sitting in
     * somebody's field, not back in the yard waiting to be sent out again.
     */
    @Query(
        """
        SELECT COUNT(*) FROM jobs
        WHERE orgId = :orgId AND (machineId = :machineId OR implementMachineId = :machineId) AND deletedAt IS NULL
          AND status IN ('Running', 'Paused')
        """
    )
    suspend fun liveJobCountOnMachine(orgId: String, machineId: String): Int

    /** When this person last did anything for this business. */
    @Query(
        """
        SELECT MAX(workedAt) FROM jobs
        WHERE orgId = :orgId AND operatorPersonId = :personId AND deletedAt IS NULL
        """
    )
    suspend fun lastWorkedAt(orgId: String, personId: String): Long?

    @Query("UPDATE jobs SET operatorPersonId = :to WHERE operatorPersonId = :from")
    suspend fun adoptJobs(from: String, to: String)

    /** Read back after a move, so the rows can be sent on in their new shape. */
    @Query("SELECT * FROM jobs WHERE operatorPersonId = :personId")
    suspend fun jobsBy(personId: String): List<JobEntity>

    @Query("UPDATE job_events SET byPersonId = :to WHERE byPersonId = :from")
    suspend fun adoptJobEvents(from: String, to: String)

    @Query("SELECT * FROM job_events WHERE byPersonId = :personId")
    suspend fun eventsBy(personId: String): List<JobEventEntity>

    /** Which machines are out on a job right now, for screens that say so. */
    @Query(
        """
        SELECT DISTINCT machineId FROM jobs
        WHERE orgId = :orgId AND deletedAt IS NULL AND machineId IS NOT NULL
          AND status IN ('Running', 'Paused')
        """
    )
    fun observeMachinesWithLiveJobs(orgId: String): Flow<List<String>>

    /** What this machine has been doing, newest first. */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND (machineId = :machineId OR implementMachineId = :machineId) AND deletedAt IS NULL
          AND status != 'Discarded'
        ORDER BY workedAt DESC LIMIT :limit
        """
    )
    fun observeForMachine(orgId: String, machineId: String, limit: Int): Flow<List<JobEntity>>

    /**
     * What this machine has brought in, billed work only.
     *
     * A running job has not earned anything yet — the quantity is still moving and
     * nobody has agreed the figure. Counting it would make the number jump about
     * while somebody watches it.
     */
    @Query(
        """
        SELECT COALESCE(SUM($JOB_AMOUNT_SQL), 0)
        FROM jobs
        WHERE orgId = :orgId AND machineId = :machineId AND deletedAt IS NULL
          AND status = 'Completed'
        """
    )
    fun observeEarnedOnMachine(orgId: String, machineId: String): Flow<Long>

    @Query("SELECT * FROM jobs WHERE id = :id AND orgId = :orgId")
    suspend fun find(orgId: String, id: String): JobEntity?

    /**
     * The highest bill number used in a financial year, or 0 if none.
     *
     * Read inside the completing transaction, so two jobs finished in the same second
     * cannot take the same number.
     */
    @Query("SELECT COALESCE(MAX(billSeq), 0) FROM jobs WHERE orgId = :orgId AND billFy = :financialYear")
    suspend fun lastBillSeq(orgId: String, financialYear: String): Int

    /**
     * What the most recent trip on a job cost.
     *
     * Null when the job has no trips yet, or when its trips predate per-trip pricing;
     * callers fall back to the job's own rate, which for those jobs is what every
     * trip was charged at anyway.
     */
    @Query(
        """
        SELECT numericValue FROM job_events
        WHERE jobId = :jobId AND type = 'TripAdded' AND numericValue IS NOT NULL
        ORDER BY atMillis DESC, id DESC LIMIT 1
        """
    )
    suspend fun lastTripMinor(jobId: String): Double?

    /**
     * Every trip made and every trip taken back, oldest first.
     *
     * Insertion order breaks a tie in the clock, so two taps in one millisecond still
     * come back in the order they were made.
     */
    @Query(
        """
        SELECT * FROM job_events
        WHERE jobId = :jobId AND type IN ('TripAdded', 'TripRemoved')
        ORDER BY atMillis ASC, rowid ASC
        """
    )
    suspend fun tripEvents(jobId: String): List<JobEventEntity>

    @Query("SELECT * FROM jobs WHERE id = :id AND orgId = :orgId")
    fun observe(orgId: String, id: String): Flow<JobEntity?>

    /** Everything currently on a machine somewhere, for the owner's home screen. */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND deletedAt IS NULL AND status IN ('Running', 'Paused')
        ORDER BY startedAt DESC
        """
    )
    fun observeLive(orgId: String): Flow<List<JobEntity>>

    /**
     * The last handful of jobs, finished or not.
     *
     * For laying a payment against work that was just done, which is when
     * labour is usually paid — in the field, with the job still in mind.
     */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND deletedAt IS NULL
        ORDER BY workedAt DESC LIMIT :limit
        """
    )
    fun observeRecent(orgId: String, limit: Int): Flow<List<JobEntity>>

    /** Everything one driver has ever done here, for their own record. */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND (operatorPersonId = :personId OR (coOperatorIds IS NOT NULL
               AND (',' || coOperatorIds || ',') LIKE '%,' || :personId || ',%')) AND deletedAt IS NULL
        ORDER BY workedAt DESC
        """
    )
    fun observeForOperator(orgId: String, personId: String): Flow<List<JobEntity>>

    /**
     * One operator's live job. An operator has at most one — the app will not let a
     * driver start a second job while the first is running, because in the field that
     * is always a mistake rather than an intention.
     */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND operatorPersonId = :personId
          AND deletedAt IS NULL AND status IN ('Running', 'Paused')
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    fun observeLiveFor(orgId: String, personId: String): Flow<JobEntity?>

    /** The same question asked once, for code that acts rather than watches. */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND operatorPersonId = :personId
          AND deletedAt IS NULL AND status IN ('Running', 'Paused')
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    suspend fun liveJobFor(orgId: String, personId: String): JobEntity?

    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND deletedAt IS NULL AND status = 'Completed'
          AND workedAt BETWEEN :from AND :to
        ORDER BY workedAt DESC
        """
    )
    fun observeCompletedBetween(orgId: String, from: Long, to: Long): Flow<List<JobEntity>>

    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND customerId = :customerId AND deletedAt IS NULL
        ORDER BY workedAt DESC
        """
    )
    fun observeForCustomer(orgId: String, customerId: String): Flow<List<JobEntity>>

    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND (operatorPersonId = :personId OR (coOperatorIds IS NOT NULL
               AND (',' || coOperatorIds || ',') LIKE '%,' || :personId || ',%')) AND deletedAt IS NULL
          AND workedAt BETWEEN :from AND :to
        ORDER BY workedAt DESC
        """
    )
    fun observeForOperator(orgId: String, personId: String, from: Long, to: Long): Flow<List<JobEntity>>

    /**
     * Jobs that overlap a proposed time window, so a backdated entry can warn before
     * it double-books a machine that was demonstrably somewhere else.
     */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND deletedAt IS NULL AND id != :ignoreJobId
          AND machineId = :machineId
          AND startedAt IS NOT NULL AND completedAt IS NOT NULL
          AND startedAt < :endsAt AND completedAt > :startsAt
        """
    )
    suspend fun overlapping(
        orgId: String,
        machineId: String,
        startsAt: Long,
        endsAt: Long,
        ignoreJobId: String,
    ): List<JobEntity>

    @Query("SELECT COALESCE(SUM(elapsedSeconds), 0) FROM jobs WHERE orgId = :orgId AND operatorPersonId = :personId AND workedAt BETWEEN :from AND :to AND deletedAt IS NULL")
    fun observeSecondsWorked(orgId: String, personId: String, from: Long, to: Long): Flow<Long>

    /**
     * Ignores one that is already here rather than throwing.
     *
     * A job event never changes, so the same id arriving twice is the ordinary
     * course of a pull re-reading a span it has seen. With a plain insert that
     * second arrival raised a UNIQUE constraint — and because the whole restore
     * runs in one transaction, the failure took every other row down with it.
     * The symptom was a handset that pulled once and then silently stopped
     * taking anything in, with a log line per event and nothing else to show
     * for it.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: JobEventEntity)

    @Query("SELECT * FROM job_events WHERE jobId = :jobId ORDER BY atMillis ASC")
    fun observeEvents(jobId: String): Flow<List<JobEventEntity>>

    /** The same timeline, once, in the order it happened. */
    @Query("SELECT * FROM job_events WHERE jobId = :jobId ORDER BY atMillis ASC, rowid ASC")
    suspend fun eventsOnce(jobId: String): List<JobEventEntity>

    /** What the diesel put in against this job cost, which a job on "diesel billed extra" passes on. */
    @Query("SELECT COALESCE(SUM(costMinor), 0) FROM fuel_entries WHERE orgId = :orgId AND jobId = :jobId")
    suspend fun fuelCostForJob(orgId: String, jobId: String): Long

    @Upsert suspend fun upsertRate(rate: RateEntity)

    /**
     * The single best rate to use, preferring one agreed with this customer.
     *
     * `customerId IS NULL` sorts 0 before 1, so a customer's own rate wins over the
     * general one. Beyond that the tie-break is explicit: once a work type can carry
     * several labelled rates, "whichever row SQLite happened to reach first" is a
     * number that changes under the user for no reason they can see. Newest wins,
     * then id, so the same question always gets the same answer.
     */
    @Query(
        """
        SELECT * FROM rates
        WHERE orgId = :orgId AND workType = :workType
          AND (customerId = :customerId OR customerId IS NULL)
          AND (:billingMode IS NULL OR billingMode = :billingMode)
          AND (:unit IS NULL OR unit = :unit)
        ORDER BY customerId IS NULL, updatedAt DESC, id ASC
        LIMIT 1
        """
    )
    suspend fun rateFor(
        orgId: String,
        workType: String,
        customerId: String?,
        billingMode: BillingMode?,
        unit: MeasureUnit?,
    ): RateEntity?

    /**
     * Every rate that could apply to this work, best first.
     *
     * The caller offers these as choices rather than picking one, which is the whole
     * point of letting a work type carry more than one rate.
     */
    @Query(
        """
        SELECT * FROM rates
        WHERE orgId = :orgId AND workType = :workType
          AND (customerId = :customerId OR customerId IS NULL)
          AND (:billingMode IS NULL OR billingMode = :billingMode)
          AND (:unit IS NULL OR unit = :unit)
        ORDER BY customerId IS NULL, updatedAt DESC, id ASC
        """
    )
    suspend fun ratesFor(
        orgId: String,
        workType: String,
        customerId: String?,
        billingMode: BillingMode?,
        unit: MeasureUnit?,
    ): List<RateEntity>

    /** The general rate card: what this business charges, before any customer deal. */
    @Query(
        """
        SELECT * FROM rates WHERE orgId = :orgId AND customerId IS NULL
        ORDER BY workType ASC, label IS NULL DESC, label ASC
        """
    )
    fun observeRateCard(orgId: String): Flow<List<RateEntity>>

    /** The deals struck with individual customers, which the card above hides. */
    @Query("SELECT * FROM rates WHERE orgId = :orgId AND customerId IS NOT NULL")
    fun observeCustomerRates(orgId: String): Flow<List<RateEntity>>

    @Query("SELECT * FROM rates WHERE id = :rateId")
    suspend fun rateById(rateId: String): RateEntity?

    /** Scoped to the organisation: an id alone is not permission to delete a row. */
    @Query("DELETE FROM rates WHERE id = :rateId AND orgId = :orgId")
    suspend fun deleteRate(orgId: String, rateId: String)

    /**
     * What each machine has done, counted the same whether it drove or was pulled.
     *
     * The work a machine has really been used for is a better first offer than any table
     * of what such a machine is for, so this decides the top of the work list. A machine
     * of null is the work done with no machine named.
     */
    @Query(
        """
        SELECT machineId AS machineId, workType AS workType, COUNT(*) AS jobs, MAX(workedAt) AS lastAt, 0 AS pulled
        FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL AND status != 'Discarded'
        GROUP BY machineId, workType
        UNION ALL
        SELECT implementMachineId AS machineId, workType AS workType, COUNT(*) AS jobs, MAX(workedAt) AS lastAt, 1 AS pulled
        FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL AND status != 'Discarded'
          AND implementMachineId IS NOT NULL
        GROUP BY implementMachineId, workType
        """
    )
    fun observeWorkUse(orgId: String): Flow<List<WorkUse>>

    /**
     * How each kind of work was last charged, so the next job of it starts the same way.
     * SQLite takes the other columns from the row that holds the MAX, which is the point.
     */
    @Query(
        """
        SELECT workType AS workType, billingMode AS billingMode, unit AS unit, MAX(workedAt) AS lastAt
        FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL AND status != 'Discarded'
        GROUP BY workType
        """
    )
    fun observeLastCharge(orgId: String): Flow<List<LastCharge>>

    /** Every name this business has given its work: on jobs, bookings and the rate card. */
    @Query(
        """
        SELECT workType FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL AND status != 'Discarded'
        UNION SELECT workType FROM rates WHERE orgId = :orgId
        UNION SELECT workType FROM bookings WHERE orgId = :orgId AND deletedAt IS NULL
        """
    )
    fun observeWorkNames(orgId: String): Flow<List<String>>

    /**
     * The rate last charged for this work on this machine, the given customer's first.
     *
     * Two combines in one business are often two prices; the rate card knows the work but not
     * the machine, and the jobs do.
     */
    @Query(
        """
        SELECT rateMinor AS rateMinor, (customerId = :customerId) AS sameCustomer FROM jobs
        WHERE orgId = :orgId AND machineId = :machineId AND workType = :workType
          AND billingMode = :billingMode AND unit = :unit AND rateMinor > 0
          AND deletedAt IS NULL AND status != 'Discarded'
        ORDER BY sameCustomer DESC, workedAt DESC
        LIMIT 1
        """
    )
    suspend fun lastRateOn(
        orgId: String,
        machineId: String,
        workType: String,
        billingMode: BillingMode,
        unit: MeasureUnit,
        customerId: String?,
    ): MachineRate?
}

/** How often one machine did one kind of work, and when it last did. */
data class WorkUse(
    val machineId: String?,
    val workType: String,
    val jobs: Int,
    val lastAt: Long,
    /** Counted as the implement behind another machine; the same job is also counted for that machine. */
    val pulled: Boolean = false,
)

/** How one kind of work was last charged. */
data class LastCharge(val workType: String, val billingMode: BillingMode, val unit: MeasureUnit, val lastAt: Long)

/** A rate a machine was last used at, and whether it was for the same customer. */
data class MachineRate(val rateMinor: Long, val sameCustomer: Boolean?)

/**
 * The khata.
 *
 * Reads only ever aggregate; there is no update or delete, because a ledger that can
 * be edited is a ledger nobody can argue from.
 */
@Dao
interface LedgerDao {
    /**
     * Ignores one already here, for the same reason job events do.
     *
     * The khata is append-only and a pull re-reads spans it has seen, so the
     * same entry arriving twice is routine. Left as a plain insert it raised a
     * UNIQUE constraint inside the restore's single transaction and discarded
     * every other row in the same pass.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LedgerEntryEntity)

    @Query("UPDATE ledger_entries SET byPersonId = :to WHERE byPersonId = :from")
    suspend fun adoptEntries(from: String, to: String)

    @Query("SELECT * FROM ledger_entries WHERE byPersonId = :personId")
    suspend fun entriesBy(personId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE orgId = :orgId AND customerId = :customerId ORDER BY atMillis DESC, createdAt DESC")
    fun observeFor(orgId: String, customerId: String): Flow<List<LedgerEntryEntity>>

    /** The book since a day, newest first: what the money history lists. */
    @Query("SELECT * FROM ledger_entries WHERE orgId = :orgId AND atMillis >= :from ORDER BY atMillis DESC, createdAt DESC LIMIT 3000")
    fun observeSince(orgId: String, from: Long): Flow<List<LedgerEntryEntity>>

    /** Every line in the book, for working out how old each customer's debt is. */
    @Query("SELECT * FROM ledger_entries WHERE orgId = :orgId")
    fun observeAll(orgId: String): Flow<List<LedgerEntryEntity>>

    /**
     * Cash one person took at the gate in a window — what they are carrying.
     *
     * Cash only. UPI and bank transfers land in the owner's account on their own;
     * only notes in a pocket need handing over.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM ledger_entries
        WHERE orgId = :orgId AND kind = 'Payment' AND method = 'Cash'
          AND byPersonId = :personId AND atMillis BETWEEN :from AND :to
        """
    )
    fun observeCashTakenBy(orgId: String, personId: String, from: Long, to: Long): Flow<Long>

    /** What one customer owes. Positive means they owe the business. */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE kind
                WHEN 'Charge' THEN amountMinor
                WHEN 'Opening' THEN amountMinor
                WHEN 'Payment' THEN -amountMinor
                WHEN 'Adjustment' THEN -amountMinor
                ELSE 0
            END), 0)
        FROM ledger_entries WHERE orgId = :orgId AND customerId = :customerId
        """
    )
    fun observeBalance(orgId: String, customerId: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE kind
                WHEN 'Charge' THEN amountMinor
                WHEN 'Opening' THEN amountMinor
                WHEN 'Payment' THEN -amountMinor
                WHEN 'Adjustment' THEN -amountMinor
                ELSE 0
            END), 0)
        FROM ledger_entries WHERE orgId = :orgId
        """
    )
    fun observeTotalOwed(orgId: String): Flow<Long>

    /** Balance per customer, biggest debt first — the customers list in one query. */
    @Query(
        """
        SELECT customerId, SUM(
            CASE kind
                WHEN 'Charge' THEN amountMinor
                WHEN 'Opening' THEN amountMinor
                WHEN 'Payment' THEN -amountMinor
                WHEN 'Adjustment' THEN -amountMinor
                ELSE 0
            END) AS balanceMinor
        FROM ledger_entries WHERE orgId = :orgId
        GROUP BY customerId HAVING balanceMinor != 0
        ORDER BY balanceMinor DESC
        """
    )
    fun observeBalances(orgId: String): Flow<List<CustomerBalance>>

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM ledger_entries
        WHERE orgId = :orgId AND kind = 'Payment' AND atMillis BETWEEN :from AND :to
        """
    )
    fun observeCollectedBetween(orgId: String, from: Long, to: Long): Flow<Long>
}

/**
 * A machine and the hours it has worked since its meter was last set.
 *
 * Embedded so the row comes back whole with one extra column, the way a job cost
 * comes back with what has been paid on it. [workedSeconds] is the time on every
 * job finished on it after [MachineEntity.hoursAsOf] — see [effective].
 */
data class MachineWithWork(
    @Embedded val machine: MachineEntity,
    val workedSeconds: Long,
) {
    /**
     * The machine with its hours as they stand today: the reading plus the work since.
     *
     * With no service on record the count runs from the reading somebody gave when
     * the machine was added, not from hour zero. Counting from zero made a tractor
     * whose meter said 1,200 "950 hours overdue for service" on the first morning,
     * before anybody had done anything wrong — and an alarm that is wrong on day
     * one is an alarm people stop believing. The honest statement is the narrower
     * one: due after this many hours of work from when the app was told its hours.
     * Logging a service replaces the assumption with the fact.
     */
    fun effective(): MachineEntity = machine.copy(
        hoursUsed = machine.hoursUsed + workedSeconds / 3600.0,
        lastServiceAtHours = machine.lastServiceAtHours ?: machine.hoursUsed,
    )
}

/**
 * What a hired machine did since its hire began, as the figures a rate is applied to.
 * Only finished work counts, and only work that finished after the hire was agreed.
 */
/** A sum of money against one person. */
data class PersonAmount(val personId: String, val amountMinor: Long)

/** What one person did as the operator since their pay terms began. */
data class WageFacts(
    val personId: String,
    val seconds: Long,
    /** Land covered, in whatever land unit each job was measured in. */
    val landQuantity: Double,
    /** Distinct days they did any work. */
    val days: Int,
)

data class HireFacts(
    val machineId: String,
    val seconds: Long,
    /** Land covered, in whatever land unit each job was measured in. */
    val landQuantity: Double,
    /** Distinct days the machine did any work. */
    val days: Int,
    /** What the finished work was billed at, for a share of it. */
    val amountMinor: Long,
)

/** What went into running one machine, all told. */
data class RunningFacts(
    val machineId: String,
    /** Time on every finished job on this machine, since the app first heard of it. */
    val seconds: Long,
    val fuelMinor: Long,
    val serviceMinor: Long,
    /** The last year's share of [seconds], for spreading a loan over a year. */
    val yearSeconds: Long,
)

/** One kind of work on one machine: how much was done, in how long, and what it brought in. */
data class WorkKindFacts(
    val workType: String,
    val unit: MeasureUnit,
    val quantity: Double,
    val seconds: Long,
    val earnedMinor: Long,
    val jobs: Int,
    /**
     * The part of [quantity] that came with a clock reading. Work entered afterwards
     * with no times adds its acres and none of its hours, so dividing all the acres
     * by the hours there are makes the machine look faster than it is.
     */
    val timedQuantity: Double = quantity,
)

/** A finished job's money, as far as it is known. */
data class JobMargin(
    val jobId: String,
    val earnedMinor: Long,
    val fuelMinor: Long,
    val peopleMinor: Long,
    /** Costs somebody recorded and nobody has put a figure on. */
    val unpriced: Int,
) {
    /**
     * How much this job lost, or null when it did not — or when nobody can say.
     *
     * Only when every cost has a figure. A job with a crew nobody has priced has
     * not made money or lost it yet; calling it a loss would be as wrong as
     * calling it a profit.
     */
    val lostMinor: Long?
        get() = if (unpriced == 0 && earnedMinor < fuelMinor + peopleMinor) {
            fuelMinor + peopleMinor - earnedMinor
        } else null
}

/** Fuel burnt on jobs, and the time those jobs took — for litres per hour. */
data class FuelOnJobs(
    val machineId: String,
    val litres: Double,
    val seconds: Long,
    val jobs: Int,
)

data class CustomerBalance(
    val customerId: String,
    val balanceMinor: Long,
)

/** How many changes of one kind are waiting. */
data class OutboxGroup(val tableName: String, val count: Int)

@Dao
interface OutboxDao {
    @Insert suspend fun enqueue(item: OutboxEntity)

    /** The queue proper: everything the user has not set aside. */
    @Query(
        """
        SELECT * FROM outbox WHERE blockedAt IS NULL
        ORDER BY createdAt ASC, seq ASC LIMIT :limit
        """
    )
    suspend fun nextBatch(limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE blockedAt IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE blockedAt IS NOT NULL")
    fun observeSetAsideCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE blockedAt IS NOT NULL")
    suspend fun setAsideCount(): Int

    /**
     * Whether this exact row is already waiting to be sent.
     *
     * Asked before a restore overwrites anything: a row with a change queued on
     * this phone is newer than whatever the server last heard, and letting the
     * download win would quietly undo work somebody did while out of signal.
     */
    @Query(
        """
        SELECT COUNT(*) FROM outbox
        WHERE tableName = :table AND entityId = :entityId AND blockedAt IS NULL
        """
    )
    suspend fun pendingFor(table: String, entityId: String): Int

    /** Lets the queue past one change, without throwing it away. */
    @Query("UPDATE outbox SET blockedAt = :now WHERE seq = :seq")
    suspend fun setAside(seq: Long, now: Long)

    /**
     * Sets aside everything still queued for one business.
     *
     * Used when this person is no longer in it. Those changes can never be
     * accepted now — the server will refuse every one — and left in the queue the
     * first of them would block it for good, including work for a second business
     * the same driver still works for. Kept rather than deleted, because they are
     * a record of work that was really done and may still be owed for.
     */
    @Query("UPDATE outbox SET blockedAt = :now WHERE orgId = :orgId AND blockedAt IS NULL")
    suspend fun setAsideForOrg(orgId: String, now: Long)

    /**
     * Sets aside everything, for an account that has been turned off.
     *
     * Not the same as being removed from one business: there is no business left
     * to send to. Kept rather than deleted for the same reason — somebody whose
     * account is restored, or who is asked later what work was done, still has it.
     */
    @Query("UPDATE outbox SET blockedAt = :now WHERE blockedAt IS NULL")
    suspend fun setAsideEverything(now: Long)

    /** The same count, for the sync engine, which reports after a run rather than watching. */
    @Query("SELECT COUNT(*) FROM outbox WHERE blockedAt IS NULL")
    suspend fun pendingCount(): Int

    /**
     * What is still waiting for ONE business.
     *
     * Per business rather than per phone, because the beacon is written onto a
     * membership: a driver who works for two contractors would otherwise tell
     * each of them how far behind the other one was.
     */
    @Query("SELECT COUNT(*) FROM outbox WHERE orgId = :orgId AND blockedAt IS NULL")
    suspend fun pendingCountFor(orgId: String): Int

    /**
     * What is waiting, by kind.
     *
     * "47 changes waiting" means nothing to the person who made them. "12 jobs, 3
     * payments" is the same number said in things they recognise doing.
     */
    @Query(
        """
        SELECT tableName AS tableName, COUNT(*) AS count FROM outbox
        WHERE blockedAt IS NULL
        GROUP BY tableName ORDER BY COUNT(*) DESC, tableName ASC
        """
    )
    fun observePendingByTable(): Flow<List<OutboxGroup>>

    @Query("SELECT COUNT(*) FROM outbox WHERE tableName = :table AND blockedAt IS NULL")
    fun observePendingCountFor(table: String): Flow<Int>

    @Delete suspend fun remove(item: OutboxEntity)

    /**
     * Several accepted changes in one transaction.
     *
     * One at a time, each removal is its own write and wakes every screen
     * watching a table the sent-state triggers touch — a phone sending a week's
     * backlog would redraw its home once per change.
     */
    @Delete suspend fun removeAll(items: List<OutboxEntity>)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastAttemptAt = :now, lastError = :error WHERE seq = :seq")
    suspend fun recordFailure(seq: Long, now: Long, error: String?)
}

/**
 * A booking and how far the work it asked for has got.
 *
 * [jobStatus] is the status of the job started from it, or null when none was —
 * which is how "started" is known without anybody having written it onto the booking.
 * A discarded job does not count: the work did not happen, so the booking is open again.
 */
data class BookingRow(
    @androidx.room.Embedded val booking: BookingEntity,
    val jobStatus: JobStatus?,
)

private const val BOOKING_ROWS = """
    SELECT bookings.*, (
        SELECT jobs.status FROM jobs
        WHERE jobs.bookingId = bookings.id AND jobs.deletedAt IS NULL AND jobs.status != 'Discarded'
        ORDER BY CASE jobs.status WHEN 'Running' THEN 0 WHEN 'Paused' THEN 1 ELSE 2 END
        LIMIT 1
    ) AS jobStatus FROM bookings
"""

@Dao
interface BookingDao {
    @Upsert suspend fun upsert(booking: BookingEntity)

    @Upsert suspend fun upsertRequest(request: BookingRequestEntity)

    @Query("SELECT * FROM booking_requests WHERE orgId = :orgId ORDER BY (status = 'New') DESC, createdAt DESC LIMIT 200")
    fun observeRequests(orgId: String): Flow<List<BookingRequestEntity>>

    @Query("SELECT COUNT(*) FROM booking_requests WHERE orgId = :orgId AND status = 'New'")
    fun observeNewRequestCount(orgId: String): Flow<Int>

    @Query("SELECT * FROM booking_requests WHERE orgId = :orgId AND id = :id")
    suspend fun findRequest(orgId: String, id: String): BookingRequestEntity?

    @Query("SELECT * FROM bookings WHERE orgId = :orgId AND id = :id")
    suspend fun find(orgId: String, id: String): BookingEntity?

    @Query(BOOKING_ROWS + " WHERE orgId = :orgId AND id = :id")
    fun observe(orgId: String, id: String): Flow<BookingRow?>

    @Query(BOOKING_ROWS + " WHERE orgId = :orgId AND deletedAt IS NULL AND day BETWEEN :from AND :to ORDER BY day, createdAt")
    fun observeDays(orgId: String, from: Long, to: Long): Flow<List<BookingRow>>

    /**
     * Planned for a day before [before] and never started. These are the ones that
     * were promised and did not happen, which nothing may quietly drop: a phone that
     * had not synced would otherwise cancel real work by looking away.
     */
    @Query(
        BOOKING_ROWS + """
        WHERE orgId = :orgId AND deletedAt IS NULL AND day < :before AND status = 'Planned'
          AND NOT EXISTS (
              SELECT 1 FROM jobs WHERE jobs.bookingId = bookings.id
                AND jobs.deletedAt IS NULL AND jobs.status != 'Discarded'
          )
        ORDER BY day, createdAt
        """
    )
    fun observeOverdue(orgId: String, before: Long): Flow<List<BookingRow>>

    /** What this customer has booked and not started, up to and including [throughDay]. */
    @Query(
        BOOKING_ROWS + """
        WHERE orgId = :orgId AND customerId = :customerId AND deletedAt IS NULL
          AND day <= :throughDay AND status = 'Planned'
          AND NOT EXISTS (
              SELECT 1 FROM jobs WHERE jobs.bookingId = bookings.id
                AND jobs.deletedAt IS NULL AND jobs.status != 'Discarded'
          )
        ORDER BY day DESC, createdAt
        """
    )
    fun observeOpenFor(orgId: String, customerId: String, throughDay: Long): Flow<List<BookingRow>>
}

/**
 * Everything a business has, as rows, for taking it out of the app.
 *
 * Whole tables for one business, deleted rows left out, oldest first. The period filter
 * is applied by the caller on the date each row carries, so that a bill the owner
 * edited later is still found under the day the work was done.
 */
@Dao
interface ExportDao {
    /** How much work has been finished here, for deciding whether the app has had a fair chance. */
    @Query("SELECT COUNT(*) FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL AND status = 'Completed'")
    suspend fun completedJobs(orgId: String): Int

    @Query("SELECT * FROM customers WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY name")
    suspend fun customers(orgId: String): List<CustomerEntity>

    @Query("SELECT * FROM fields WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY name")
    suspend fun fields(orgId: String): List<FieldEntity>

    @Query("SELECT * FROM machines WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY name")
    suspend fun machines(orgId: String): List<MachineEntity>

    @Query("SELECT * FROM jobs WHERE orgId = :orgId AND deletedAt IS NULL AND status != 'Discarded' ORDER BY workedAt")
    suspend fun jobs(orgId: String): List<JobEntity>

    @Query("SELECT * FROM ledger_entries WHERE orgId = :orgId ORDER BY atMillis, createdAt")
    suspend fun ledger(orgId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM fuel_entries WHERE orgId = :orgId ORDER BY atMillis")
    suspend fun fuel(orgId: String): List<FuelEntryEntity>

    @Query("SELECT * FROM service_records WHERE orgId = :orgId ORDER BY atMillis")
    suspend fun services(orgId: String): List<ServiceRecordEntity>

    @Query("SELECT * FROM payouts WHERE orgId = :orgId ORDER BY atMillis")
    suspend fun payouts(orgId: String): List<PayoutEntity>

    @Query("SELECT * FROM machine_papers WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY machineId, kind")
    suspend fun papers(orgId: String): List<MachinePaperEntity>

    @Query("SELECT * FROM machine_finance WHERE orgId = :orgId")
    suspend fun finance(orgId: String): List<MachineFinanceEntity>

    @Query("SELECT * FROM job_costs WHERE orgId = :orgId ORDER BY atMillis")
    suspend fun jobCosts(orgId: String): List<JobCostEntity>

    @Query("SELECT * FROM bookings WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY day")
    suspend fun bookings(orgId: String): List<BookingEntity>

    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND active = 1 ORDER BY displayName")
    suspend fun team(orgId: String): List<MembershipEntity>
}

/** Taking one whole business off this phone. Used for the practice business only. */
@Dao
interface SampleDao {
    @Query("DELETE FROM memberships WHERE orgId = :orgId") suspend fun memberships(orgId: String)
    @Query("DELETE FROM invites WHERE orgId = :orgId") suspend fun invites(orgId: String)
    @Query("DELETE FROM customers WHERE orgId = :orgId") suspend fun customers(orgId: String)
    @Query("DELETE FROM fields WHERE orgId = :orgId") suspend fun fields(orgId: String)
    @Query("DELETE FROM machines WHERE orgId = :orgId") suspend fun machines(orgId: String)
    @Query("DELETE FROM fuel_entries WHERE orgId = :orgId") suspend fun fuel(orgId: String)
    @Query("DELETE FROM service_records WHERE orgId = :orgId") suspend fun services(orgId: String)
    @Query("DELETE FROM machine_finance WHERE orgId = :orgId") suspend fun finance(orgId: String)
    @Query("DELETE FROM machine_papers WHERE orgId = :orgId") suspend fun papers(orgId: String)
    @Query("DELETE FROM payouts WHERE orgId = :orgId") suspend fun payouts(orgId: String)
    @Query("DELETE FROM cash_handovers WHERE orgId = :orgId") suspend fun handovers(orgId: String)
    @Query("DELETE FROM member_details WHERE orgId = :orgId") suspend fun memberDetails(orgId: String)
    @Query("DELETE FROM member_pay_terms WHERE orgId = :orgId") suspend fun payTerms(orgId: String)
    @Query("DELETE FROM job_costs WHERE orgId = :orgId") suspend fun jobCosts(orgId: String)
    @Query("DELETE FROM team_events WHERE orgId = :orgId") suspend fun teamEvents(orgId: String)
    @Query("DELETE FROM breakdowns WHERE orgId = :orgId") suspend fun breakdowns(orgId: String)
    @Query("DELETE FROM rates WHERE orgId = :orgId") suspend fun rates(orgId: String)
    @Query("DELETE FROM jobs WHERE orgId = :orgId") suspend fun jobs(orgId: String)
    @Query("DELETE FROM job_events WHERE orgId = :orgId") suspend fun jobEvents(orgId: String)
    @Query("DELETE FROM ledger_entries WHERE orgId = :orgId") suspend fun ledger(orgId: String)
    @Query("DELETE FROM bookings WHERE orgId = :orgId") suspend fun bookings(orgId: String)
    @Query("DELETE FROM booking_requests WHERE orgId = :orgId") suspend fun requests(orgId: String)
    @Query("DELETE FROM entitlements WHERE orgId = :orgId") suspend fun entitlements(orgId: String)
    @Query("DELETE FROM outbox WHERE orgId = :orgId") suspend fun outbox(orgId: String)
    @Query("DELETE FROM organisations WHERE id = :orgId") suspend fun org(orgId: String)

    /** Every row the business owns, and the business. Callers wrap it in a transaction. */
    suspend fun wipe(orgId: String) {
        memberships(orgId); invites(orgId); customers(orgId); fields(orgId); machines(orgId); fuel(orgId)
        handovers(orgId); memberDetails(orgId); payTerms(orgId)
        services(orgId); finance(orgId); papers(orgId); payouts(orgId); jobCosts(orgId); teamEvents(orgId); breakdowns(orgId)
        rates(orgId); jobs(orgId); jobEvents(orgId); ledger(orgId); bookings(orgId); requests(orgId); entitlements(orgId); outbox(orgId); org(orgId)
    }
}
