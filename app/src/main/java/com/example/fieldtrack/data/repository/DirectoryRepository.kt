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
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.db.dao.FieldWorkTotals
import com.example.fieldtrack.data.db.entity.FieldEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MachineFinanceEntity
import com.example.fieldtrack.data.billing.JobTerms
import com.example.fieldtrack.data.costing.Costing
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.fieldtrack.data.plan.PlanSettings
import com.example.fieldtrack.data.season.SeasonCalendar
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

    // ----------------------------------------------------------------- seasons

    /**
     * When this business's seasons begin, live.
     *
     * Read off the business's own row rather than the session. The session is a
     * snapshot taken when somebody signs in, and an owner who changes the dates
     * expects a driver's phone to follow when the change reaches it — which it
     * does through this, because the row is what the sync updates.
     */
    fun observeSeasons(orgId: String): Flow<SeasonCalendar> =
        db.orgDao().observeOrg(orgId)
            .map { SeasonCalendar.parse(it?.seasons) }
            .distinctUntilChanged()

    /**
     * How this business reads its plan, live, from its own row for the reason
     * [observeSeasons] gives: a driver's phone follows when the owner changes it.
     */
    fun observePlanSettings(orgId: String): Flow<PlanSettings> =
        db.orgDao().observeOrg(orgId)
            .map { PlanSettings(PlanSettings.cleanHour(it?.planDayStartHour), it?.driversSeeAll ?: false) }
            .distinctUntilChanged()

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
        openingBalanceMinor: Long = 0,
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
        if (openingBalanceMinor > 0) {
            ledger.recordOpeningBalance(orgId, customer.id, openingBalanceMinor, byPersonId)
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

    /**
     * The machines, with their hours as they stand today.
     *
     * [MachineEntity.hoursUsed] on a row is the meter reading somebody last gave;
     * what the screens mean by "hours" is that plus the work finished since, and
     * this is the one place that adds them. Everything that reads hours — service
     * reminders, the fleet, the fuel log — reads it from here, so none of them can
     * disagree. Never write one of these back: the copy carries hours that include
     * work already counted, and saving it would count that work twice.
     */
    fun observeMachines(orgId: String): Flow<List<MachineEntity>> =
        machines.observeAllWithWork(orgId).map { rows -> rows.map { it.effective() } }

    /** One machine, watched, with today's hours. Read-only, for the reason above. */
    fun observeMachine(orgId: String, machineId: String): Flow<MachineEntity?> =
        machines.observeWithWork(orgId, machineId).map { it?.effective() }

    suspend fun addMachine(
        orgId: String,
        name: String,
        kind: MachineKind,
        plate: String? = null,
        serviceIntervalHours: Double? = 250.0,
        /**
         * What the meter reads now. The form always had a box for it and the add
         * flow never passed it on, so a reading typed here was thrown away.
         */
        hoursUsed: Double? = null,
    ): String {
        val now = clock.now()
        val machine = MachineEntity(
            id = ids.newId(),
            orgId = orgId,
            name = name.trim(),
            kind = kind,
            plate = plate?.trim()?.ifBlank { null },
            hoursUsed = hoursUsed?.coerceAtLeast(0.0) ?: 0.0,
            hoursAsOf = now,
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
        /**
         * A new meter reading, or null to leave the hours alone.
         *
         * Null matters: the form shows today's hours, and saving a corrected
         * plate must not turn them into the new reading — that would round away
         * the minutes worked since the last one, every time anybody fixed a name.
         * A reading is true as of now, so work finished from here is added on top.
         */
        hoursUsed: Double?,
        serviceIntervalHours: Double?,
    ) {
        can.require(Capability.EditMachines)
        db.withTransaction {
            val existing = machines.find(orgId, machineId) ?: return@withTransaction
            val now = clock.now()
            val updated = existing.copy(
                name = name.trim(),
                kind = kind,
                plate = plate?.trim()?.ifBlank { null },
                hoursUsed = hoursUsed?.coerceAtLeast(0.0) ?: existing.hoursUsed,
                hoursAsOf = if (hoursUsed != null) now else existing.hoursAsOf,
                serviceIntervalHours = serviceIntervalHours,
                updatedAt = now,
            )
            machines.upsert(updated)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_MACHINES, updated.id, OutboxJson.encodeToString(updated)
            )
        }
    }

    /** One machine with today's hours. Read-only — see [observeMachines]. */
    suspend fun findMachine(orgId: String, machineId: String): MachineEntity? =
        machines.findWithWork(orgId, machineId)?.effective()

    // ------------------------------------------------------- what a machine costs

    /** The owner's own figures for one machine, or null where there are none. */
    suspend fun findMachineFinance(orgId: String, machineId: String): MachineFinanceEntity? =
        machines.findFinance(orgId, machineId)

    fun observeMachineFinance(orgId: String): Flow<List<MachineFinanceEntity>> =
        machines.observeFinance(orgId)

    /**
     * Set what a machine costs to own.
     *
     * Held to the same bar as reading it back: an owner or a manager who sees
     * what the business pays. A driver cannot read this record, so being able to
     * write it would be a way to overwrite figures he is not allowed to look at.
     *
     * Blank or zero means "none" and is stored as null, so a loan that has been
     * paid off stops counting rather than counting as ₹0 for ever.
     */
    suspend fun setMachineFinance(
        orgId: String,
        can: Capabilities,
        machineId: String,
        monthlyMinor: Long?,
        hoursPerYear: Int?,
    ) {
        can.require(Capability.EditMachines)
        can.require(Capability.SeePeopleCost)
        require(monthlyMinor == null || monthlyMinor >= 0) { "A loan instalment is not negative" }
        require(hoursPerYear == null || hoursPerYear in 0..MAX_HOURS_PER_YEAR) {
            "A machine cannot work more hours than there are in a year"
        }
        db.withTransaction {
            if (machines.find(orgId, machineId) == null) return@withTransaction
            val finance = MachineFinanceEntity(
                id = machineId,
                orgId = orgId,
                monthlyMinor = monthlyMinor?.takeIf { it > 0 },
                hoursPerYear = hoursPerYear?.takeIf { it > 0 },
                updatedAt = clock.now(),
            )
            machines.upsertFinance(finance)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_MACHINE_FINANCE, finance.id,
                OutboxJson.encodeToString(finance),
            )
        }
    }

    /**
     * What an hour on each machine costs, by machine.
     *
     * Always computed, whoever is asking; whether it is shown is the screen's
     * decision against [Capability.SeePeopleCost]. A reader who may not see the
     * loan simply gets none from the database — the record never reaches the
     * phone — so there is nothing here to leak.
     */
    fun observeRunningCosts(orgId: String): Flow<Map<String, Costing.RunningCost>> {
        val yearAgo = clock.now() - 365L * 24 * 60 * 60 * 1000
        return combine(
            machines.observeRunningFacts(orgId, yearAgo),
            machines.observeFinance(orgId),
            db.orgDao().observeOrg(orgId),
        ) { facts, finance, org ->
            val step = org?.moneyStepMinor ?: 1L
            val financeById = finance.associateBy { it.id }
            facts.associate { it.machineId to Costing.running(it, financeById[it.machineId], step) }
        }
    }

    fun observeWorkKinds(orgId: String, machineId: String) = machines.observeWorkKinds(orgId, machineId)

    private companion object {
        const val MAX_HOURS_PER_YEAR = 8_760
    }

    suspend fun findCustomer(orgId: String, customerId: String) = customers.find(orgId, customerId)

    // -------------------------------------------------------------------- rates

    fun observeRateCard(orgId: String): Flow<List<RateEntity>> = jobs.observeRateCard(orgId)

    /**
     * The rate to offer for this work, preferring whatever was agreed with this
     * customer last time over the general rate. Returns null when there is no
     * history — the screen then asks rather than guessing.
     */
    suspend fun suggestedRate(
        orgId: String,
        workType: String,
        customerId: String?,
        /** Only a rate for this way of charging, when given: a per-hour rate is no answer to "per acre". */
        billingMode: BillingMode? = null,
        unit: MeasureUnit? = null,
    ): RateEntity? = jobs.rateFor(orgId, workType, customerId, billingMode, unit)

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
        amountMinor: Long,
        customerId: String? = null,
        /** The terms the price came with, so the next job at this rate starts from them. */
        terms: JobTerms = JobTerms(),
    ) {
        // A lump sum agreed for one job is not a price per anything, and offering it
        // next time as a rate per bigha would put ₹15,000 in the box.
        if (billingMode == BillingMode.Fixed) return
        // Matched on how the work is charged as well as what it is. Without that, an
        // hourly job overwrote the same farmer's per-acre rate, and the next acre job
        // was offered the hourly figure.
        val existing = jobs.rateFor(orgId, workType, customerId, billingMode, unit)
        save(
            RateEntity(
                id = existing?.takeIf { it.customerId == customerId }?.id ?: ids.newId(),
                orgId = orgId,
                workType = workType,
                billingMode = billingMode,
                unit = unit,
                amountMinor = amountMinor,
                customerId = customerId,
                // Implicit learning never invents a label. It is overwriting whatever
                // row it matched, and renaming a contractor's "paddy" rate because a
                // job happened to use it is not something a background write may do.
                label = existing?.takeIf { it.customerId == customerId }?.label,
                minimumMinor = terms.minimumMinor,
                arrivalMinor = terms.arrivalMinor,
                fuelTerms = terms.fuelTerms,
                waitingRateMinor = terms.waitingRateMinor,
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
        if (customerId != null && jobs.rateFor(orgId, workType, null, billingMode, unit) == null) {
            save(
                RateEntity(
                    id = ids.newId(),
                    orgId = orgId,
                    workType = workType,
                    billingMode = billingMode,
                    unit = unit,
                    amountMinor = amountMinor,
                    customerId = null,
                    minimumMinor = terms.minimumMinor,
                    arrivalMinor = terms.arrivalMinor,
                    fuelTerms = terms.fuelTerms,
                    waitingRateMinor = terms.waitingRateMinor,
                    updatedAt = clock.now(),
                )
            )
        }
    }

    /** Every rate that could apply, best first, for a screen that offers a choice. */
    suspend fun ratesFor(
        orgId: String,
        workType: String,
        customerId: String?,
        billingMode: BillingMode? = null,
        unit: MeasureUnit? = null,
    ): List<RateEntity> = jobs.ratesFor(orgId, workType, customerId, billingMode, unit)

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
        amountMinor: Long,
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
            amountMinor = amountMinor,
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
    /**
     * A farmer's plot, under the name the farmer uses for it.
     *
     * Gated on [Capability.EditCustomers] rather than a capability of its own.
     * A field is part of the customer's record — somebody trusted to write down
     * who the farmer is can write down which plot of his was worked, and a
     * twelfth switch for it would be a switch nobody could explain.
     */
    suspend fun addField(
        orgId: String,
        can: Capabilities,
        customerId: String,
        name: String,
        area: Double,
        unit: MeasureUnit,
        crop: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): FieldEntity {
        can.require(Capability.EditCustomers)
        require(name.isNotBlank()) { "A field is found by its name, so it has to have one" }
        require(area > 0) { "A field has a size" }
        val now = clock.now()
        val field = FieldEntity(
            id = ids.newId(),
            orgId = orgId,
            customerId = customerId,
            name = name.trim(),
            area = area,
            unit = unit,
            crop = crop?.trim()?.ifBlank { null },
            latitude = latitude,
            longitude = longitude,
            createdAt = now,
            updatedAt = now,
        )
        saveField(field)
        return field
    }

    suspend fun updateField(
        orgId: String,
        can: Capabilities,
        fieldId: String,
        name: String,
        area: Double,
        unit: MeasureUnit,
        crop: String?,
        latitude: Double?,
        longitude: Double?,
    ): FieldEntity {
        can.require(Capability.EditCustomers)
        require(name.isNotBlank()) { "A field is found by its name, so it has to have one" }
        require(area > 0) { "A field has a size" }
        val existing = customers.findField(orgId, fieldId)
            ?: error("No such field in this business")
        val updated = existing.copy(
            name = name.trim(),
            area = area,
            unit = unit,
            crop = crop?.trim()?.ifBlank { null },
            latitude = latitude,
            longitude = longitude,
            updatedAt = clock.now(),
            syncState = SyncState.Pending,
        )
        saveField(updated)
        return updated
    }

    /**
     * Takes a field off the list without destroying it.
     *
     * Soft, because jobs point at it. A field deleted outright would leave
     * every job ever done there unable to say where it was, and a contractor
     * looking back at last season would find the work and not the place.
     */
    suspend fun removeField(orgId: String, can: Capabilities, fieldId: String) {
        can.require(Capability.EditCustomers)
        val existing = customers.findField(orgId, fieldId) ?: return
        saveField(existing.copy(deletedAt = clock.now(), syncState = SyncState.Pending))
    }

    private suspend fun saveField(field: FieldEntity) {
        db.withTransaction {
            customers.upsertField(field)
            syncQueue.enqueueUpsert(
                field.orgId, SyncQueue.TABLE_FIELDS, field.id, OutboxJson.encodeToString(field)
            )
        }
    }

    fun observeAllFields(orgId: String): Flow<List<FieldEntity>> = customers.observeAllFields(orgId)

    fun observeFields(orgId: String, customerId: String): Flow<List<FieldEntity>> =
        customers.observeFields(orgId, customerId)

    fun observeField(orgId: String, fieldId: String): Flow<FieldEntity?> =
        customers.observeField(orgId, fieldId)

    fun observeFieldWork(orgId: String, customerId: String): Flow<List<FieldWorkTotals>> =
        customers.observeFieldWork(orgId, customerId)
}
