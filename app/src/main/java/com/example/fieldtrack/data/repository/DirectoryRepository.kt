package com.example.fieldtrack.data.repository

import androidx.room.withTransaction
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

/**
 * Customers, machines and the rate card — the things a job is assembled from.
 *
 * Grouped into one repository because they are only ever read together, at the
 * moment someone is setting up a job, and splitting them would mean three objects
 * threaded through the same screen for no gain.
 */
class DirectoryRepository(
    private val db: FieldTrackDatabase,
    private val syncQueue: SyncQueue,
    private val ledger: LedgerRepository,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val customers = db.customerDao()
    private val machines = db.machineDao()
    private val jobs = db.jobDao()

    // ---------------------------------------------------------------- customers

    fun observeCustomers(orgId: String): Flow<List<CustomerEntity>> = customers.observeAll(orgId)

    fun searchCustomers(orgId: String, term: String): Flow<List<CustomerEntity>> =
        if (term.isBlank()) customers.observeAll(orgId) else customers.search(orgId, term)

    fun observeCustomer(orgId: String, id: String): Flow<CustomerEntity?> =
        customers.observe(orgId, id)

    /**
     * Adds a customer, and records any balance carried in from a paper notebook as a
     * dated opening entry rather than a number on the customer row — so the khata
     * explains itself from its first line.
     */
    suspend fun addCustomer(
        orgId: String,
        name: String,
        phone: String?,
        village: String?,
        openingBalanceRupees: Long = 0,
        byPersonId: String? = null,
    ): String {
        val now = clock.now()
        val customer = CustomerEntity(
            id = ids.newId(),
            orgId = orgId,
            name = name.trim(),
            phone = phone?.trim()?.ifBlank { null },
            village = village?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            customers.upsert(customer)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_CUSTOMERS, customer.id, OutboxJson.encodeToString(customer)
            )
        }
        if (openingBalanceRupees > 0) {
            ledger.recordOpeningBalance(orgId, customer.id, openingBalanceRupees, byPersonId)
        }
        return customer.id
    }

    /**
     * Correct a customer's details.
     *
     * Their khata is untouched: this changes who the entries are about, never what
     * they say. A typo from voice input is the common case and must be a two-tap fix.
     */
    suspend fun updateCustomer(
        orgId: String,
        can: Capabilities,
        customerId: String,
        name: String,
        phone: String?,
        village: String?,
    ) {
        can.require(Capability.EditCustomers)
        db.withTransaction {
            val existing = customers.find(orgId, customerId) ?: return@withTransaction
            val updated = existing.copy(
                name = name.trim(),
                phone = phone?.trim()?.ifBlank { null },
                village = village?.trim()?.ifBlank { null },
                updatedAt = clock.now(),
            )
            customers.upsert(updated)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_CUSTOMERS, updated.id, OutboxJson.encodeToString(updated)
            )
        }
    }

    // ----------------------------------------------------------------- machines

    fun observeMachines(orgId: String): Flow<List<MachineEntity>> = machines.observeAll(orgId)

    /** One machine, watched. */
    fun observeMachine(orgId: String, machineId: String) = machines.observe(orgId, machineId)

    suspend fun addMachine(
        orgId: String,
        name: String,
        kind: MachineKind,
        plate: String? = null,
        serviceIntervalHours: Double? = 250.0,
    ): String {
        val now = clock.now()
        val machine = MachineEntity(
            id = ids.newId(),
            orgId = orgId,
            name = name.trim(),
            kind = kind,
            plate = plate?.trim()?.ifBlank { null },
            serviceIntervalHours = serviceIntervalHours,
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            machines.upsert(machine)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_MACHINES, machine.id, OutboxJson.encodeToString(machine)
            )
        }
        return machine.id
    }

    /**
     * Correct a machine.
     *
     * [status] is deliberately not a parameter: a machine is working because a job is
     * running on it, and letting someone set that by hand would put the fleet screen
     * and the job list into disagreement.
     */
    suspend fun updateMachine(
        orgId: String,
        can: Capabilities,
        machineId: String,
        name: String,
        kind: MachineKind,
        plate: String?,
        hoursUsed: Double?,
        serviceIntervalHours: Double?,
    ) {
        can.require(Capability.EditMachines)
        db.withTransaction {
            val existing = machines.find(orgId, machineId) ?: return@withTransaction
            val updated = existing.copy(
                name = name.trim(),
                kind = kind,
                plate = plate?.trim()?.ifBlank { null },
                hoursUsed = hoursUsed ?: existing.hoursUsed,
                serviceIntervalHours = serviceIntervalHours,
                updatedAt = clock.now(),
            )
            machines.upsert(updated)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_MACHINES, updated.id, OutboxJson.encodeToString(updated)
            )
        }
    }

    suspend fun findMachine(orgId: String, machineId: String) = machines.find(orgId, machineId)

    suspend fun findCustomer(orgId: String, customerId: String) = customers.find(orgId, customerId)

    // -------------------------------------------------------------------- rates

    fun observeRateCard(orgId: String): Flow<List<RateEntity>> = jobs.observeRateCard(orgId)

    /**
     * The rate to offer for this work, preferring whatever was agreed with this
     * customer last time over the general rate. Returns null when there is no
     * history — the screen then asks rather than guessing.
     */
    suspend fun suggestedRate(orgId: String, workType: String, customerId: String?): RateEntity? =
        jobs.rateFor(orgId, workType, customerId)

    /**
     * Remembers a rate so it is offered next time.
     *
     * Saved against the customer when it differs from the general rate, because "what
     * I charged Ramesh last time" is the number a contractor actually reaches for.
     */
    suspend fun rememberRate(
        orgId: String,
        workType: String,
        billingMode: BillingMode,
        unit: MeasureUnit,
        amountRupees: Long,
        customerId: String? = null,
    ) {
        val existing = jobs.rateFor(orgId, workType, customerId)
        save(
            RateEntity(
                id = existing?.takeIf { it.customerId == customerId }?.id ?: ids.newId(),
                orgId = orgId,
                workType = workType,
                billingMode = billingMode,
                unit = unit,
                amountRupees = amountRupees,
                customerId = customerId,
                // Implicit learning never invents a label. It is overwriting whatever
                // row it matched, and renaming a contractor's "paddy" rate because a
                // job happened to use it is not something a background write may do.
                label = existing?.takeIf { it.customerId == customerId }?.label,
                updatedAt = clock.now(),
            )
        )

        // Seed the general card the first time this kind of work is done.
        //
        // Without this the card stays empty forever: every rate learned from a job is
        // attached to the customer it was charged to, and the card deliberately shows
        // only the general ones. A contractor would finish a season and still open
        // "My rates" to be told he has none.
        //
        // Only when there is nothing there yet. After that the card is his — a cheap
        // rate given to one farmer must never quietly become the price everybody sees.
        if (customerId != null && jobs.rateFor(orgId, workType, null) == null) {
            save(
                RateEntity(
                    id = ids.newId(),
                    orgId = orgId,
                    workType = workType,
                    billingMode = billingMode,
                    unit = unit,
                    amountRupees = amountRupees,
                    customerId = null,
                    updatedAt = clock.now(),
                )
            )
        }
    }

    /** Every rate that could apply, best first, for a screen that offers a choice. */
    suspend fun ratesFor(orgId: String, workType: String, customerId: String?): List<RateEntity> =
        jobs.ratesFor(orgId, workType, customerId)

    suspend fun rateById(rateId: String): RateEntity? = jobs.rateById(rateId)

    /** The deals struck with named customers, which the general card leaves out. */
    fun observeCustomerRates(orgId: String): Flow<List<RateEntity>> =
        jobs.observeCustomerRates(orgId)

    /**
     * A rate the user typed on purpose, as opposed to one the app picked up from a job.
     *
     * Passing [rateId] edits that exact row; passing null adds one. Adding is never
     * folded into an existing row even when the work type matches, because a second
     * rate for the same work is exactly what labels are for.
     */
    suspend fun setRate(
        orgId: String,
        workType: String,
        billingMode: BillingMode,
        unit: MeasureUnit,
        amountRupees: Long,
        label: String? = null,
        customerId: String? = null,
        rateId: String? = null,
    ): String {
        val rate = RateEntity(
            id = rateId ?: ids.newId(),
            orgId = orgId,
            workType = workType,
            billingMode = billingMode,
            unit = unit,
            amountRupees = amountRupees,
            customerId = customerId,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = clock.now(),
        )
        save(rate)
        return rate.id
    }

    /**
     * Takes a rate off the card.
     *
     * Safe by construction: a job copies the rate onto itself when it is created, so
     * nothing already billed reads from here. Removing a rate changes what is offered
     * next time and nothing that has already happened.
     */
    suspend fun deleteRate(orgId: String, rateId: String) {
        db.withTransaction {
            jobs.deleteRate(orgId, rateId)
            syncQueue.enqueueDelete(orgId, SyncQueue.TABLE_RATES, rateId)
        }
    }

    /**
     * Writes a rate and queues it for the server in one transaction.
     *
     * Rates used to be written straight to the table with nothing queued, so a
     * contractor's whole rate card lived on one phone and no reinstall or second
     * device would ever see it. Every path through this file now goes through here.
     */
    private suspend fun save(rate: RateEntity) {
        db.withTransaction {
            jobs.upsertRate(rate)
            syncQueue.enqueueUpsert(
                rate.orgId, SyncQueue.TABLE_RATES, rate.id, OutboxJson.encodeToString(rate)
            )
        }
    }
}
