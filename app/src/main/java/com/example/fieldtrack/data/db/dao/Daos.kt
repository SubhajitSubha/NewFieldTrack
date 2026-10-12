package com.example.fieldtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.FuelEntryEntity
import com.example.fieldtrack.data.db.entity.InviteEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.JobEventEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.OutboxEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.db.entity.ServiceRecordEntity
import kotlinx.coroutines.flow.Flow

/*
 * Every read is scoped by orgId. That is not politeness — it is the mechanism that
 * stops one business's customers appearing in another's app on a shared driver's
 * phone. There is deliberately no "get all customers" query to misuse.
 */

@Dao
interface OrgDao {
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

    /** The team list: everyone in one business. */
    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND active = 1 ORDER BY role, displayName")
    fun observeTeam(orgId: String): Flow<List<MembershipEntity>>

    /** Everyone ever on the books here, including people who have left. */
    @Query("SELECT * FROM memberships WHERE orgId = :orgId ORDER BY active DESC, role, displayName")
    fun observeEveryone(orgId: String): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND id = :membershipId LIMIT 1")
    suspend fun membershipById(orgId: String, membershipId: String): MembershipEntity?

    @Query("SELECT * FROM memberships WHERE orgId = :orgId AND id = :membershipId LIMIT 1")
    fun observeMembershipById(orgId: String, membershipId: String): Flow<MembershipEntity?>

    /** Guards the last-owner rule: a business must always have someone in charge. */
    @Query("SELECT COUNT(*) FROM memberships WHERE orgId = :orgId AND active = 1 AND role = 'Owner'")
    suspend fun activeOwnerCount(orgId: String): Int

    @Query("SELECT COUNT(*) FROM memberships WHERE personId = :personId")
    suspend fun membershipCountFor(personId: String): Int

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

    @Upsert suspend fun upsertField(field: FieldEntity)

    @Query("SELECT * FROM fields WHERE orgId = :orgId AND customerId = :customerId AND deletedAt IS NULL ORDER BY name")
    fun observeFields(orgId: String, customerId: String): Flow<List<FieldEntity>>
}

@Dao
interface MachineDao {
    @Upsert suspend fun upsert(machine: MachineEntity)

    @Query("SELECT * FROM machines WHERE orgId = :orgId AND deletedAt IS NULL ORDER BY name")
    fun observeAll(orgId: String): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :id AND orgId = :orgId")
    fun observe(orgId: String, id: String): Flow<MachineEntity?>

    @Query("SELECT * FROM machines WHERE id = :id AND orgId = :orgId")
    suspend fun find(orgId: String, id: String): MachineEntity?

    /** Machines whose next service is within [withinHours] of use, soonest first. */
    @Query(
        """
        SELECT * FROM machines
        WHERE orgId = :orgId AND deletedAt IS NULL
          AND serviceIntervalHours IS NOT NULL
          AND (COALESCE(lastServiceAtHours, 0) + serviceIntervalHours) - hoursUsed <= :withinHours
        ORDER BY (COALESCE(lastServiceAtHours, 0) + serviceIntervalHours) - hoursUsed ASC
        """
    )
    fun observeServiceDue(orgId: String, withinHours: Double): Flow<List<MachineEntity>>

    @Upsert suspend fun upsertFuel(entry: FuelEntryEntity)

    @Query("SELECT * FROM fuel_entries WHERE orgId = :orgId AND machineId = :machineId ORDER BY atMillis DESC")
    fun observeFuel(orgId: String, machineId: String): Flow<List<FuelEntryEntity>>

    @Query("SELECT COALESCE(SUM(costRupees), 0) FROM fuel_entries WHERE orgId = :orgId AND atMillis >= :since")
    fun observeFuelSpendSince(orgId: String, since: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(costRupees), 0) FROM fuel_entries
        WHERE orgId = :orgId AND machineId = :machineId
        """
    )
    fun observeFuelSpendOnMachine(orgId: String, machineId: String): Flow<Long>

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

    @Query("UPDATE breakdowns SET reportedByPersonId = :to WHERE reportedByPersonId = :from")
    suspend fun adoptBreakdowns(from: String, to: String)

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

    /**
     * Jobs still open on this machine. A paused job counts: the machine is sitting in
     * somebody's field, not back in the yard waiting to be sent out again.
     */
    @Query(
        """
        SELECT COUNT(*) FROM jobs
        WHERE orgId = :orgId AND machineId = :machineId AND deletedAt IS NULL
          AND status IN ('Running', 'Paused')
        """
    )
    suspend fun liveJobCountOnMachine(orgId: String, machineId: String): Int

    @Query("UPDATE jobs SET operatorPersonId = :to WHERE operatorPersonId = :from")
    suspend fun adoptJobs(from: String, to: String)

    @Query("UPDATE job_events SET byPersonId = :to WHERE byPersonId = :from")
    suspend fun adoptJobEvents(from: String, to: String)

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
        WHERE orgId = :orgId AND machineId = :machineId AND deletedAt IS NULL
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
        SELECT COALESCE(SUM(
            CASE billingMode
                WHEN 'Fixed' THEN rateRupees
                WHEN 'PerTrip' THEN tripTotalRupees
                ELSE CAST(ROUND(quantity * rateRupees) AS INTEGER)
            END
        ), 0)
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
    suspend fun lastTripRupees(jobId: String): Double?

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

    /** Everything one driver has ever done here, for their own record. */
    @Query(
        """
        SELECT * FROM jobs
        WHERE orgId = :orgId AND operatorPersonId = :personId AND deletedAt IS NULL
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
        WHERE orgId = :orgId AND operatorPersonId = :personId AND deletedAt IS NULL
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

    @Insert suspend fun insertEvent(event: JobEventEntity)

    @Query("SELECT * FROM job_events WHERE jobId = :jobId ORDER BY atMillis ASC")
    fun observeEvents(jobId: String): Flow<List<JobEventEntity>>

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
        ORDER BY customerId IS NULL, updatedAt DESC, id ASC
        LIMIT 1
        """
    )
    suspend fun rateFor(orgId: String, workType: String, customerId: String?): RateEntity?

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
        ORDER BY customerId IS NULL, updatedAt DESC, id ASC
        """
    )
    suspend fun ratesFor(orgId: String, workType: String, customerId: String?): List<RateEntity>

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
}

/**
 * The khata.
 *
 * Reads only ever aggregate; there is no update or delete, because a ledger that can
 * be edited is a ledger nobody can argue from.
 */
@Dao
interface LedgerDao {
    @Insert suspend fun insert(entry: LedgerEntryEntity)

    @Query("UPDATE ledger_entries SET byPersonId = :to WHERE byPersonId = :from")
    suspend fun adoptEntries(from: String, to: String)

    @Query("SELECT * FROM ledger_entries WHERE orgId = :orgId AND customerId = :customerId ORDER BY atMillis DESC, createdAt DESC")
    fun observeFor(orgId: String, customerId: String): Flow<List<LedgerEntryEntity>>

    /** What one customer owes. Positive means they owe the business. */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE kind
                WHEN 'Charge' THEN amountRupees
                WHEN 'Opening' THEN amountRupees
                WHEN 'Payment' THEN -amountRupees
                WHEN 'Adjustment' THEN -amountRupees
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
                WHEN 'Charge' THEN amountRupees
                WHEN 'Opening' THEN amountRupees
                WHEN 'Payment' THEN -amountRupees
                WHEN 'Adjustment' THEN -amountRupees
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
                WHEN 'Charge' THEN amountRupees
                WHEN 'Opening' THEN amountRupees
                WHEN 'Payment' THEN -amountRupees
                WHEN 'Adjustment' THEN -amountRupees
                ELSE 0
            END) AS balanceRupees
        FROM ledger_entries WHERE orgId = :orgId
        GROUP BY customerId HAVING balanceRupees != 0
        ORDER BY balanceRupees DESC
        """
    )
    fun observeBalances(orgId: String): Flow<List<CustomerBalance>>

    @Query(
        """
        SELECT COALESCE(SUM(amountRupees), 0) FROM ledger_entries
        WHERE orgId = :orgId AND kind = 'Payment' AND atMillis BETWEEN :from AND :to
        """
    )
    fun observeCollectedBetween(orgId: String, from: Long, to: Long): Flow<Long>
}

data class CustomerBalance(
    val customerId: String,
    val balanceRupees: Long,
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

    /** Lets the queue past one change, without throwing it away. */
    @Query("UPDATE outbox SET blockedAt = :now WHERE seq = :seq")
    suspend fun setAside(seq: Long, now: Long)

    /** The same count, for the sync engine, which reports after a run rather than watching. */
    @Query("SELECT COUNT(*) FROM outbox WHERE blockedAt IS NULL")
    suspend fun pendingCount(): Int

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

    @Query("UPDATE outbox SET attempts = attempts + 1, lastAttemptAt = :now, lastError = :error WHERE seq = :seq")
    suspend fun recordFailure(seq: Long, now: Long, error: String?)
}
