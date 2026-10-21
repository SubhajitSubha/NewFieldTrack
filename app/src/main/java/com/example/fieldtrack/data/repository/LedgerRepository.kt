package com.example.fieldtrack.data.repository

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.dao.CustomerBalance
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.entity.PayoutEntity
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.db.dao.JobCostWithPaid
import com.example.fieldtrack.data.db.entity.JobCostDoc
import com.example.fieldtrack.data.db.entity.JobCostAmountDoc
import com.example.fieldtrack.data.db.entity.JobCostEntity
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.session.Capabilities

/**
 * The khata.
 *
 * Every method here appends. Nothing updates, nothing deletes, and no balance is ever
 * stored — a balance is the sum of the entries, computed in SQL.
 *
 * That design choice is what makes money survive being offline. Two people can take a
 * payment from the same customer in the same hour with no signal between them, and
 * when both phones sync the result is two entries that add up correctly. Had the
 * balance been a column, one of those payments would have quietly overwritten the
 * other, and a contractor would be short several thousand rupees with no trace of why.
 *
 * Corrections are reversing entries that point back at what they cancel, so a customer
 * disputing a figure can be walked through every line rather than told to trust a total.
 */
class LedgerRepository(
    private val db: FieldTrackDatabase,
    private val syncQueue: SyncQueue,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    private val deviceId: String,
) {
    private val ledger = db.ledgerDao()

    /** What one customer owes. Positive means money is owed to the business. */
    fun observeBalance(orgId: String, customerId: String): Flow<Long> =
        ledger.observeBalance(orgId, customerId)

    fun observeTotalOwed(orgId: String): Flow<Long> = ledger.observeTotalOwed(orgId)

    fun observeBalances(orgId: String): Flow<List<CustomerBalance>> = ledger.observeBalances(orgId)

    fun observeLedger(orgId: String, customerId: String): Flow<List<LedgerEntryEntity>> =
        ledger.observeFor(orgId, customerId)

    fun observeCollectedBetween(orgId: String, from: Long, to: Long): Flow<Long> =
        ledger.observeCollectedBetween(orgId, from, to)

    /** Money received. Reduces what the customer owes. */
    suspend fun recordPayment(
        orgId: String,
        customerId: String,
        amountRupees: Long,
        method: PaymentMethod,
        byPersonId: String? = null,
        note: String? = null,
        /**
         * The job this money was handed over for, when it was handed over at the
         * bill. Provenance only: settlement still clears the oldest debt first, the
         * way a khata always has, because the cash itself carries no instruction.
         */
        jobId: String? = null,
        atMillis: Long = clock.now(),
    ): String = append(
        orgId = orgId,
        customerId = customerId,
        kind = LedgerKind.Payment,
        amountRupees = amountRupees,
        method = method,
        jobId = jobId,
        note = note,
        byPersonId = byPersonId,
        atMillis = atMillis,
    )

    /**
     * A balance carried in from a paper notebook when a customer is first added.
     *
     * Recorded as a dated entry rather than a starting number on the customer row, so
     * the khata explains itself from the first line.
     */
    suspend fun recordOpeningBalance(
        orgId: String,
        customerId: String,
        amountRupees: Long,
        byPersonId: String? = null,
        atMillis: Long = clock.now(),
    ): String = append(
        orgId = orgId,
        customerId = customerId,
        kind = LedgerKind.Opening,
        amountRupees = amountRupees,
        note = null,
        byPersonId = byPersonId,
        atMillis = atMillis,
    )

    /** A discount or write-off. Reduces what is owed without money changing hands. */
    suspend fun recordAdjustment(
        orgId: String,
        customerId: String,
        amountRupees: Long,
        note: String,
        byPersonId: String? = null,
    ): String = append(
        orgId = orgId,
        customerId = customerId,
        kind = LedgerKind.Adjustment,
        amountRupees = amountRupees,
        note = note,
        byPersonId = byPersonId,
        atMillis = clock.now(),
    )

    /**
     * Cancel an earlier entry by writing its opposite.
     *
     * A reversed payment becomes a charge and a reversed charge becomes an adjustment,
     * both pointing at the original. The original stays visible: a khata that can be
     * silently rewritten is one a customer is right not to trust.
     */
    suspend fun reverse(
        orgId: String,
        entry: LedgerEntryEntity,
        reason: String,
        byPersonId: String? = null,
    ): String {
        val opposite = when (entry.kind) {
            LedgerKind.Payment -> LedgerKind.Charge
            LedgerKind.Charge, LedgerKind.Opening -> LedgerKind.Adjustment
            LedgerKind.Adjustment -> LedgerKind.Charge
        }
        return append(
            orgId = orgId,
            customerId = entry.customerId,
            kind = opposite,
            amountRupees = entry.amountRupees,
            note = reason,
            byPersonId = byPersonId,
            atMillis = clock.now(),
            reversesEntryId = entry.id,
        )
    }

    private suspend fun append(
        orgId: String,
        customerId: String,
        kind: LedgerKind,
        amountRupees: Long,
        note: String?,
        byPersonId: String?,
        atMillis: Long,
        method: PaymentMethod? = null,
        jobId: String? = null,
        reversesEntryId: String? = null,
    ): String {
        require(amountRupees > 0) { "A ledger entry must be a positive amount; direction comes from its kind" }
        val entry = LedgerEntryEntity(
            id = ids.newId(),
            orgId = orgId,
            customerId = customerId,
            kind = kind,
            amountRupees = amountRupees,
            method = method,
            jobId = jobId,
            note = note,
            atMillis = atMillis,
            byPersonId = byPersonId,
            reversesEntryId = reversesEntryId,
            createdAt = clock.now(),
            deviceId = deviceId,
        )
        db.withTransaction {
            ledger.insert(entry)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_LEDGER, entry.id, OutboxJson.encodeToString(entry)
            )
        }
        return entry.id
    }

    /**
     * Records money leaving the business.
     *
     * [covers] is stored rather than inferred, because it decides what this can
     * ever be counted into. A month's wages laid against a machine would make
     * every machine look worse than it is, and spread across all of them would
     * be an estimate wearing the clothes of a fact.
     */
    suspend fun recordPayout(
        orgId: String,
        can: Capabilities,
        amountRupees: Long,
        covers: PayoutCovers,
        atMillis: Long,
        byPersonId: String?,
        toPersonId: String? = null,
        toName: String? = null,
        headcount: Int = 1,
        jobId: String? = null,
        jobCostId: String? = null,
        method: PaymentMethod? = null,
        note: String? = null,
    ): PayoutEntity {
        can.require(Capability.PayOut)
        require(amountRupees > 0) { "A payment out is money leaving, so it is positive" }
        require(headcount >= 1) { "A payment covers at least one person" }
        // The invariant the machine-profit figures rest on. A job's costs are
        // totalled from job_costs alone, so a payment that names a job without
        // settling a recorded cost would be money this business spent on that
        // job and never counted against it.
        require((jobId == null) == (jobCostId == null)) {
            "A payment against a job settles a recorded job cost, or names no job"
        }
        val now = clock.now()
        val payout = PayoutEntity(
            id = ids.newId(),
            orgId = orgId,
            amountRupees = amountRupees,
            atMillis = atMillis,
            covers = covers,
            toPersonId = toPersonId,
            toName = toName,
            headcount = headcount,
            jobId = jobId,
            jobCostId = jobCostId,
            method = method,
            note = note,
            byPersonId = byPersonId,
            createdAt = now,
            deviceId = deviceId,
        )
        db.withTransaction {
            db.orgDao().upsertPayout(payout)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_PAYOUTS, payout.id, OutboxJson.encodeToString(payout)
            )
        }
        return payout
    }

    fun observePayouts(orgId: String): Flow<List<PayoutEntity>> =
        db.orgDao().observePayouts(orgId)

    /**
     * Sends a job cost up as two documents, not one.
     *
     * The work facts go where every member can read them, because the driver
     * who counted the four men has to see his own entry. The rupees go to a
     * sibling document guarded like a payment.
     *
     * One row would have been simpler and wrong: Firestore grants or refuses a
     * whole document and cannot hide a field, so making the headcount readable
     * would have put the crew's wages in the pocket of somebody the app says
     * may not see them. The screen hid them; the database did not.
     *
     * Both go in the caller's transaction, and the amount is queued second so
     * that a pass which stops between them leaves a cost waiting to be priced
     * rather than a price belonging to no cost.
     */
    private suspend fun enqueueJobCost(cost: JobCostEntity) {
        syncQueue.enqueueUpsert(
            cost.orgId,
            SyncQueue.TABLE_JOB_COSTS,
            cost.id,
            OutboxJson.encodeToString(
                JobCostDoc(
                    id = cost.id,
                    orgId = cost.orgId,
                    jobId = cost.jobId,
                    kind = cost.kind,
                    headcount = cost.headcount,
                    note = cost.note,
                    byPersonId = cost.byPersonId,
                    atMillis = cost.atMillis,
                    createdAt = cost.createdAt,
                    deviceId = cost.deviceId,
                )
            ),
        )
        cost.amountRupees?.let { amount ->
            syncQueue.enqueueUpsert(
                cost.orgId,
                SyncQueue.TABLE_JOB_COST_AMOUNTS,
                cost.id,
                OutboxJson.encodeToString(
                    JobCostAmountDoc(
                        id = cost.id,
                        orgId = cost.orgId,
                        amountRupees = amount,
                        updatedAt = clock.now(),
                    )
                ),
            )
        }
    }

    fun observeJobCosts(orgId: String, jobId: String): Flow<List<JobCostWithPaid>> =
        db.orgDao().observeJobCosts(orgId, jobId)

    fun observeOwedOnJobs(orgId: String): Flow<Long> = db.orgDao().observeOwedOnJobs(orgId)

    fun observeUnpricedJobCostCount(orgId: String): Flow<Int> =
        db.orgDao().observeUnpricedJobCostCount(orgId)

    /**
     * Records what a job took beyond the machine.
     *
     * Only [Capability.RecordWork] to say how many worked, because the person
     * who knows that is the one standing in the field, and a headcount is not a
     * wage. Putting a figure on it needs [Capability.PayOut], so a driver can
     * record four men without ever being shown what four men cost.
     */
    suspend fun recordJobCost(
        orgId: String,
        can: Capabilities,
        jobId: String,
        kind: JobCostKind,
        headcount: Int? = null,
        amountRupees: Long? = null,
        note: String? = null,
        byPersonId: String?,
        atMillis: Long,
    ): JobCostEntity {
        can.require(Capability.RecordWork)
        if (amountRupees != null) can.require(Capability.PayOut)
        require(headcount == null || headcount >= 1) {
            "A headcount is how many worked, or null when nobody counted"
        }
        require(amountRupees == null || amountRupees > 0) {
            "A cost with a figure on it is money; nothing is null, not zero"
        }
        val now = clock.now()
        val cost = JobCostEntity(
            id = ids.newId(),
            orgId = orgId,
            jobId = jobId,
            kind = kind,
            headcount = headcount,
            amountRupees = amountRupees,
            note = note,
            byPersonId = byPersonId,
            atMillis = atMillis,
            createdAt = now,
            deviceId = deviceId,
        )
        db.withTransaction {
            db.orgDao().upsertJobCost(cost)
            enqueueJobCost(cost)
        }
        return cost
    }

    /** Puts a figure on a cost somebody else recorded, or corrects one. */
    suspend fun priceJobCost(
        orgId: String,
        can: Capabilities,
        costId: String,
        amountRupees: Long,
        note: String? = null,
    ): JobCostEntity {
        can.require(Capability.PayOut)
        require(amountRupees > 0) { "A cost is money; to unprice it, say so explicitly" }
        val existing = db.orgDao().findJobCost(orgId, costId)
            ?: error("No such cost in this business")
        val updated = existing.copy(
            amountRupees = amountRupees,
            note = note ?: existing.note,
            syncState = SyncState.Pending,
        )
        db.withTransaction {
            db.orgDao().upsertJobCost(updated)
            enqueueJobCost(updated)
        }
        return updated
    }

    /**
     * Pays a job cost, in whole or in part.
     *
     * Writes an ordinary payment carrying [PayoutEntity.jobCostId]. The cost
     * itself does not change: it was already counted against the job the moment
     * it was priced, and counting the payment as a second cost is precisely the
     * double-count the link exists to prevent. What is still owed is the
     * difference, worked out where it is read.
     */
    suspend fun settleJobCost(
        orgId: String,
        can: Capabilities,
        costId: String,
        amountRupees: Long,
        atMillis: Long,
        byPersonId: String?,
        method: PaymentMethod? = null,
    ): PayoutEntity {
        can.require(Capability.PayOut)
        val cost = db.orgDao().findJobCost(orgId, costId)
            ?: error("No such cost in this business")
        return recordPayout(
            orgId = orgId,
            can = can,
            amountRupees = amountRupees,
            covers = PayoutCovers.Job,
            atMillis = atMillis,
            byPersonId = byPersonId,
            headcount = cost.headcount ?: 1,
            jobId = cost.jobId,
            jobCostId = cost.id,
            method = method,
            note = cost.note,
        )
    }

    /**
     * Records a job's cost and pays it in one go.
     *
     * The ordinary case from the Pay out screen, where the owner is holding the
     * cash as they type. Both rows are written in one transaction so a job can
     * never end up with a payment against a cost that was not recorded.
     */
    suspend fun payForJob(
        orgId: String,
        can: Capabilities,
        jobId: String,
        amountRupees: Long,
        atMillis: Long,
        byPersonId: String?,
        kind: JobCostKind = JobCostKind.Labour,
        headcount: Int? = null,
        note: String? = null,
        method: PaymentMethod? = null,
    ): PayoutEntity {
        can.require(Capability.PayOut)
        val cost = recordJobCost(
            orgId = orgId,
            can = can,
            jobId = jobId,
            kind = kind,
            headcount = headcount,
            amountRupees = amountRupees,
            note = note,
            byPersonId = byPersonId,
            atMillis = atMillis,
        )
        return settleJobCost(
            orgId = orgId,
            can = can,
            costId = cost.id,
            amountRupees = amountRupees,
            atMillis = atMillis,
            byPersonId = byPersonId,
            method = method,
        )
    }

}
