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
}
