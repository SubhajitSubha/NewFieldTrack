package com.example.fieldtrack.data.repository

import com.example.fieldtrack.data.region.BusinessClock
import androidx.room.withTransaction
import com.example.fieldtrack.data.billing.Billing
import com.example.fieldtrack.data.billing.JobTerms
import com.example.fieldtrack.data.billing.Quote
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.FuelTerms
import com.example.fieldtrack.data.db.BookingStatus
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.FuelEntryEntity
import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.EntryMethod
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.IdGenerator
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.OutboxJson
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.ServiceRecordEntity
import com.example.fieldtrack.data.db.financialYear
import com.example.fieldtrack.data.db.entity.JobEventEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.fieldtrack.data.db.entity.MachineEntity
import kotlinx.serialization.encodeToString

/** Something the caller asked for that the data will not allow. */
sealed class WorkError(message: String) : Exception(message) {
    class NoSuchJob(id: String) : WorkError("No job $id in this organisation")
    class NotRunning(id: String) : WorkError("Job $id is not running")
    class AlreadyFinished(id: String) : WorkError("Job $id is already finished")
    class NotFinished(id: String) : WorkError("Job $id has not been billed yet")
}

/** A backdated entry that collides with work already recorded on the same machine. */
data class OverlapWarning(
    val jobId: String,
    val startedAt: Long,
    val completedAt: Long,
)

/**
 * The work loop: start, measure, pause, finish, bill.
 *
 * Two things this class guarantees, and the reason the UI must go through it rather
 * than touching DAOs.
 *
 * **Every mutation is one transaction containing both the row and its outbox entry.**
 * If the process is killed between writing a completed job and queueing it for the
 * server, neither happened. A job that exists locally but will never sync is worse
 * than one that was never recorded, because the contractor believes it is safe.
 *
 * **Finishing a job writes the charge to the ledger in that same transaction.** Work
 * that is done but not billed is the single most expensive bug this app could have.
 */
class WorkRepository(
    private val db: FieldTrackDatabase,
    private val syncQueue: SyncQueue,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    private val deviceId: String,
) {
    private val jobs = db.jobDao()
    private val ledger = db.ledgerDao()
    private val machines = db.machineDao()

    fun observeLiveJob(orgId: String, personId: String): Flow<JobEntity?> =
        jobs.observeLiveFor(orgId, personId)

    fun observeLive(orgId: String): Flow<List<JobEntity>> = jobs.observeLive(orgId)

    /** The last few jobs, for choosing what a payment was for. */
    fun observeRecent(orgId: String, limit: Int = 6): Flow<List<JobEntity>> =
        jobs.observeRecent(orgId, limit)

    fun observeJob(orgId: String, jobId: String): Flow<JobEntity?> = jobs.observe(orgId, jobId)

    fun observeEvents(jobId: String): Flow<List<JobEventEntity>> = jobs.observeEvents(jobId)

    /** Everything one driver has done for this business, and nothing from another. */
    fun observeForOperator(orgId: String, personId: String): Flow<List<JobEntity>> =
        jobs.observeForOperator(orgId, personId)

    /** A driver's own work in a window, for their day. */
    fun observeForOperator(orgId: String, personId: String, from: Long, to: Long) =
        jobs.observeForOperator(orgId, personId, from, to)

    /** The job this driver has open right now, running or paused. */
    fun observeLiveFor(orgId: String, personId: String) = jobs.observeLiveFor(orgId, personId)

    fun observeForCustomer(orgId: String, customerId: String): Flow<List<JobEntity>> =
        jobs.observeForCustomer(orgId, customerId)

    /** Finished work from [dayStart] onward — the home screen's "today". */
    fun observeCompletedToday(orgId: String, dayStart: Long): Flow<List<JobEntity>> =
        jobs.observeCompletedBetween(orgId, dayStart, Long.MAX_VALUE)

    /**
     * Elapsed time including the stretch since the timer last resumed.
     *
     * Derived rather than stored, so a job survives the process being killed: on
     * restart the clock picks up from `runningSince` instead of resetting to zero.
     */
    fun elapsedSeconds(job: JobEntity, now: Long = clock.now()): Long {
        val since = job.runningSince ?: return job.elapsedSeconds
        return job.elapsedSeconds + ((now - since).coerceAtLeast(0L) / 1000)
    }

    /**
     * The billable figure as the bill would have it if the job were finished now.
     *
     * Hourly work bills from the clock, which is still running until [complete]
     * writes it down, so its stored quantity is nought until then. Every screen
     * showing an open job's money asks here — three screens each working it out
     * for themselves is how one of them came to show ₹0 for a job the clock had
     * priced at nearly ten thousand.
     */
    /**
     * The rounding a new job's bill uses: this business's setting now, kept on the job
     * so that changing it later cannot move a bill that already exists.
     */
    private suspend fun stepFor(orgId: String): Long =
        db.orgDao().findOrg(orgId)?.moneyStepMinor ?: 100L

    fun billableQuantity(job: JobEntity, now: Long = clock.now()): Double =
        if (job.billsFromClock) elapsedSeconds(job, now) / 3600.0
        else job.quantity

    /**
     * What the job comes to now. Only hourly work is worked out here; everything
     * else asks the job itself, because recomputing quantity × rate would undo
     * per-trip pricing.
     */
    fun billableMinor(job: JobEntity, now: Long = clock.now()): Long =
        if (job.billsFromClock) {
            job.amountFor(
                Billing.amount(BillingMode.PerHour, billableQuantity(job, now), job.rateMinor, job.stepMinor)
            )
        } else {
            job.amountMinor
        }

    suspend fun startJob(
        orgId: String,
        customerId: String,
        workType: String,
        billingMode: BillingMode,
        unit: MeasureUnit,
        rateMinor: Long,
        machineId: String? = null,
        fieldId: String? = null,
        operatorPersonId: String? = null,
        /** The booking this work was planned as, which is what makes that booking started. */
        bookingId: String? = null,
        terms: JobTerms = JobTerms(),
    ): String {
        val now = clock.now()
        // The booking is only the thing this work was planned as if it still is one:
        // still on the plan, for this farmer, with no job already carrying it. A flow
        // left open while the booking was cancelled, or started on another phone,
        // would otherwise tie a second job to it. The work itself still starts.
        val plannedAs = bookingId?.let { id ->
            db.bookingDao().observe(orgId, id).first()?.takeIf {
                it.booking.status == BookingStatus.Planned && it.jobStatus == null &&
                    it.booking.customerId == customerId
            }?.booking?.id
        }
        val job = JobEntity(
            id = ids.newId(),
            orgId = orgId,
            customerId = customerId,
            fieldId = fieldId,
            machineId = machineId,
            operatorPersonId = operatorPersonId,
            bookingId = plannedAs,
            workType = workType,
            billingMode = billingMode,
            unit = unit,
            rateMinor = rateMinor,
            stepMinor = stepFor(orgId),
            status = JobStatus.Running,
            workedAt = now,
            recordedAt = now,
            entryMethod = EntryMethod.Timed,
            startedAt = now,
            runningSince = now,
            createdAt = now,
            updatedAt = now,
            lastWriterDeviceId = deviceId,
        ).let { terms.applyTo(it) }
        db.withTransaction {
            jobs.upsert(job)
            addEvent(job, JobEventType.Started, operatorPersonId, at = now)
            enqueue(job)
            markMachine(orgId, machineId, MachineStatus.Working)
        }
        return job.id
    }

    /** Set the billable figure outright — the hero stepper on the tracking screen. */
    /**
     * Set the billable figure outright.
     *
     * On trip work this says "there were six trips", which can only mean six at the
     * current rate — there is no per-trip history to preserve in a figure typed in
     * one go. Use [addTrip] to price them as they happen.
     */
    suspend fun setQuantity(orgId: String, jobId: String, quantity: Double) =
        mutate(orgId, jobId) { job ->
            val next = quantity.coerceAtLeast(0.0)
            job.copy(
                quantity = next,
                tripTotalMinor = if (job.billingMode == BillingMode.PerTrip) {
                    Billing.amount(BillingMode.PerTrip, next, job.rateMinor, job.stepMinor)
                } else job.tripTotalMinor,
            ) to Event(JobEventType.QuantitySet, numericValue = next)
        }

    /**
     * What the crew noted to prove the work: the meter's reading, a photo of it, and where the
     * phone was, at the start or at the finish. Only what is given is written; anything left
     * null keeps what was there, so a photo added later does not blank the reading.
     */
    suspend fun recordProof(
        orgId: String,
        jobId: String,
        atFinish: Boolean,
        meter: Double? = null,
        photoId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ) = mutate(orgId, jobId) { job ->
        val changed = if (atFinish) {
            job.copy(
                meterEnd = meter ?: job.meterEnd,
                endPhotoId = photoId ?: job.endPhotoId,
                endLat = latitude ?: job.endLat,
                endLng = if (latitude != null) longitude else job.endLng,
            )
        } else {
            job.copy(
                meterStart = meter ?: job.meterStart,
                startPhotoId = photoId ?: job.startPhotoId,
                startLat = latitude ?: job.startLat,
                startLng = if (latitude != null) longitude else job.startLng,
            )
        }
        changed to if (photoId != null) Event(JobEventType.PhotoAdded, textValue = photoId) else null
    }

    /**
     * Nudge the billable figure up or down by one step.
     *
     * The step is half a unit for measured work and a whole one for counted work:
     * nobody ploughs a tenth of an acre and bills for it, and half a trip does not
     * exist. Lives here rather than in a ViewModel so every caller steps the same way.
     */
    suspend fun adjustQuantity(orgId: String, jobId: String, up: Boolean) =
        mutate(orgId, jobId) { job ->
            val step = when (job.billingMode) {
                BillingMode.PerTrip, BillingMode.PerUnit -> 1.0
                BillingMode.PerKm -> 1.0
                else -> 0.5
            }
            val next = (job.quantity + if (up) step else -step).coerceAtLeast(0.0)
            job.copy(
                quantity = next,
                tripTotalMinor = if (job.billingMode == BillingMode.PerTrip) {
                    Billing.amount(BillingMode.PerTrip, next, job.rateMinor, job.stepMinor)
                } else job.tripTotalMinor,
            ) to Event(JobEventType.QuantitySet, numericValue = next)
        }

    /** One more load carried. The primary action on trip work. */
    /**
     * One more trip, at whatever the rate is right now.
     *
     * The price is banked on the job as the trip is made and recorded on the event,
     * so changing the rate and adding another trip does exactly what a contractor
     * means by it: this load cost more, the earlier ones did not change.
     */
    suspend fun addTrip(orgId: String, jobId: String, distanceMetres: Long? = null) =
        mutate(orgId, jobId) { job ->
            job.copy(
                quantity = job.quantity + 1,
                tripTotalMinor = job.tripTotalMinor + job.rateMinor,
                distanceMetres = distanceMetres?.let { (job.distanceMetres ?: 0L) + it }
                    ?: job.distanceMetres,
            ) to Event(JobEventType.TripAdded, numericValue = job.rateMinor.toDouble())
        }

    /**
     * Undo a miscount. Never goes below zero.
     *
     * Takes off what that trip actually cost, read back from its own event, which is
     * not the current rate if the price has moved since.
     */
    suspend fun removeLastTrip(orgId: String, jobId: String) {
        // The newest trip not already taken back. Reading only the newest trip made
        // a second undo take the same price off again, and the job drifted from what
        // its own trips added up to.
        val standing = ArrayDeque<Double?>()
        jobs.tripEvents(jobId).forEach { event ->
            if (event.type == JobEventType.TripAdded) standing.addLast(event.numericValue)
            else standing.removeLastOrNull()
        }
        val lastTripMinor = standing.lastOrNull()?.let { Math.round(it) }
        mutate(orgId, jobId) { job ->
            val removed = lastTripMinor ?: job.rateMinor
            job.copy(
                quantity = (job.quantity - 1).coerceAtLeast(0.0),
                tripTotalMinor = (job.tripTotalMinor - removed).coerceAtLeast(0L),
            ) to Event(JobEventType.TripRemoved, numericValue = removed.toDouble())
        }
    }

    suspend fun pause(orgId: String, jobId: String) = mutate(orgId, jobId) { job ->
        if (job.status != JobStatus.Running) throw WorkError.NotRunning(jobId)
        job.copy(
            status = JobStatus.Paused,
            elapsedSeconds = elapsedSeconds(job),
            runningSince = null,
        ) to Event(JobEventType.Paused)
    }

    suspend fun resume(orgId: String, jobId: String) {
        mutate(orgId, jobId) { job ->
            job.copy(status = JobStatus.Running, runningSince = clock.now()) to
                Event(JobEventType.Resumed)
        }
        // The machine may have been left Down by a breakdown or Idle by its repair.
        // Either way it is out in a field again now.
        val machineId = jobs.find(orgId, jobId)?.machineId
        db.withTransaction { refreshMachineStatus(orgId, machineId) }
    }

    /**
     * Stop a clock that was left running, at the time the work actually ended.
     *
     * The job is left paused rather than finished. Finishing is where the acres
     * get counted and the bill is seen, and a person putting the clock right at
     * seven in the morning is often about to carry on the same field — resuming
     * from here starts a fresh stretch and the night is simply not in it.
     *
     * [stoppedAt] is held between the moment the clock last started and now: a
     * clock cannot stop before it ran, and stopping it in the future is a pause.
     * Returns the seconds taken off.
     */
    suspend fun stopClockAt(
        orgId: String,
        jobId: String,
        stoppedAt: Long,
        byPersonId: String?,
    ): Long {
        var removed = 0L
        db.withTransaction {
            val job = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
            val since = job.runningSince
            if (job.status != JobStatus.Running || since == null) {
                throw WorkError.NotRunning(jobId)
            }
            val now = clock.now()
            val at = stoppedAt.coerceIn(since, now)
            removed = (now - at) / 1000

            val stopped = job.copy(
                status = JobStatus.Paused,
                elapsedSeconds = job.elapsedSeconds + (at - since) / 1000,
                runningSince = null,
                updatedAt = now,
                revision = job.revision + 1,
                lastWriterDeviceId = deviceId,
            )
            jobs.upsert(stopped)
            addEvent(
                job = stopped,
                type = JobEventType.ClockCorrected,
                byPersonId = byPersonId,
                numericValue = removed.toDouble(),
                textValue = at.toString(),
                at = now,
            )
            enqueue(stopped)
        }
        return removed
    }

    /** Pass a running job to another person without stopping the clock. */
    suspend fun handOver(orgId: String, jobId: String, toPersonId: String) =
        mutate(orgId, jobId) { job ->
            job.copy(operatorPersonId = toPersonId) to
                Event(JobEventType.HandedOver, textValue = toPersonId)
        }

    /**
     * Change what an open job is charged at.
     *
     * Safe to do freely because nothing has been billed yet — the charge is worked
     * out at [complete]. Once a job is finished this is refused, and the correction
     * goes through [amendCompletedJob] instead, which moves the khata and records a
     * reason.
     */
    suspend fun setRate(orgId: String, jobId: String, rateMinor: Long) {
        val job = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
        if (job.status == JobStatus.Completed) throw WorkError.AlreadyFinished(jobId)
        mutate(orgId, jobId) { current ->
            current.copy(rateMinor = rateMinor.coerceAtLeast(0L)) to
                Event(JobEventType.RateChanged, numericValue = rateMinor.toDouble())
        }
    }

    /**
     * Fuel put into the machine, logged against the job it was burnt on.
     *
     * Recorded even when the job has no machine attached: a contractor filling a
     * hired tractor still wants the cost against the day's work. Nothing here touches
     * the customer's bill — fuel is the owner's cost, not the customer's.
     */
    suspend fun addFuel(
        orgId: String,
        jobId: String,
        litres: Double,
        costMinor: Long,
        byPersonId: String? = null,
    ) {
        db.withTransaction {
            val job = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
            val machineId = job.machineId ?: return@withTransaction
            val entry = FuelEntryEntity(
                id = ids.newId(),
                orgId = orgId,
                machineId = machineId,
                jobId = jobId,
                litres = litres,
                costMinor = costMinor,
                atMillis = clock.now(),
                byPersonId = byPersonId ?: job.operatorPersonId,
            )
            machines.upsertFuel(entry)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_FUEL, entry.id, OutboxJson.encodeToString(entry)
            )
            addEvent(
                job = job,
                type = JobEventType.FuelAdded,
                byPersonId = entry.byPersonId,
                numericValue = litres,
            )
        }
    }

    /**
     * The machine has stopped working mid-job.
     *
     * Pauses the job rather than ending it, because the usual outcome is a repair and
     * a resume an hour later; the customer is not billed for the wait. The machine
     * goes Down so the fleet screen stops claiming it is working, and it stays down
     * until someone says otherwise.
     */
    suspend fun reportBreakdown(
        orgId: String,
        jobId: String,
        category: BreakdownCategory,
        note: String? = null,
        byPersonId: String? = null,
    ) {
        db.withTransaction {
            val job = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
            val now = clock.now()

            val breakdown = BreakdownEntity(
                id = ids.newId(),
                orgId = orgId,
                machineId = job.machineId.orEmpty(),
                jobId = jobId,
                category = category,
                note = note?.trim()?.ifBlank { null },
                reportedAt = now,
                reportedByPersonId = byPersonId ?: job.operatorPersonId,
            )
            machines.upsertBreakdown(breakdown)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_BREAKDOWNS, breakdown.id,
                OutboxJson.encodeToString(breakdown),
            )

            val paused = job.copy(
                status = JobStatus.Paused,
                elapsedSeconds = elapsedSeconds(job),
                runningSince = null,
                updatedAt = now,
                revision = job.revision + 1,
                lastWriterDeviceId = deviceId,
            )
            jobs.upsert(paused)
            addEvent(
                job = paused,
                type = JobEventType.BreakdownReported,
                byPersonId = breakdown.reportedByPersonId,
                textValue = category.name,
                at = now,
            )
            enqueue(paused)
            markMachine(orgId, job.machineId, MachineStatus.Down)
        }
    }

    suspend fun addNote(orgId: String, jobId: String, note: String) =
        mutate(orgId, jobId) { job ->
            job.copy(note = note) to Event(JobEventType.NoteAdded, textValue = note)
        }

    /**
     * The bill for a job as it stands, with every line that makes it up.
     *
     * For a job still running this is what finishing it now would bill; for a finished
     * one it is what was billed. [discountMinor] is what the customer is being let off
     * on top of anything already recorded, for the bill being looked at before it is saved.
     */
    suspend fun quote(
        orgId: String,
        jobId: String,
        discountMinor: Long? = null,
        now: Long = clock.now(),
    ): Quote? {
        val job = jobs.find(orgId, jobId) ?: return null
        val finished = job.status == JobStatus.Completed
        val work = if (job.billsFromClock && !finished) {
            Billing.amount(BillingMode.PerHour, billableQuantity(job, now), job.rateMinor, job.stepMinor)
        } else job.workAmountMinor
        val waiting = if (finished) job.waitingMinor else waitingBilled(job, now)
        val fuel = when {
            finished -> job.fuelBilledMinor
            job.fuelTerms == FuelTerms.BilledExtra -> jobs.fuelCostForJob(orgId, job.id)
            else -> 0L
        }
        val discount = (discountMinor ?: job.discountMinor).coerceAtLeast(0)
        val priced = job.copy(waitingMinor = waiting, fuelBilledMinor = fuel, discountMinor = discount)
        return Quote(
            workMinor = work,
            minimumTopUpMinor = (job.minimumMinor - work).coerceAtLeast(0),
            arrivalMinor = job.arrivalMinor,
            waitingMinor = waiting,
            fuelMinor = fuel,
            discountMinor = discount,
            totalMinor = priced.amountFor(work),
        )
    }

    /**
     * What the time spent waiting for the customer comes to.
     *
     * Waiting is the time the job sat paused: from each pause to the next resume, and from
     * the last pause to now when it is finished while paused. Billed at the job's waiting
     * rate to the way the business bills, and nothing when there is no waiting rate.
     */
    private suspend fun waitingBilled(job: JobEntity, now: Long): Long {
        if (job.waitingRateMinor <= 0) return 0
        var pausedAt: Long? = null
        var seconds = 0L
        for (event in jobs.eventsOnce(job.id)) {
            when (event.type) {
                JobEventType.Paused -> pausedAt = pausedAt ?: event.atMillis
                JobEventType.Resumed -> pausedAt?.let { seconds += (event.atMillis - it) / 1000; pausedAt = null }
                else -> Unit
            }
        }
        pausedAt?.let { seconds += (now - it).coerceAtLeast(0) / 1000 }
        return Billing.amount(BillingMode.PerHour, seconds / 3600.0, job.waitingRateMinor, job.stepMinor)
    }

    /**
     * Finish the job and bill it.
     *
     * The charge lands on the customer's ledger in the same transaction as the job
     * row, so there is no window in which work is complete but unbilled. Returns the
     * amount charged.
     */
    suspend fun complete(
        orgId: String,
        jobId: String,
        byPersonId: String? = null,
        /** Taken off the bill when the customer settles: a round-off, a discount. */
        discountMinor: Long = 0,
    ): Long {
        val now = clock.now()
        var charged = 0L
        db.withTransaction {
            val job = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
            if (job.status == JobStatus.Completed) throw WorkError.AlreadyFinished(jobId)

            // The bill gets its number as it is raised, from the financial year it
            // falls in. Allocated inside this transaction so two jobs finished at the
            // same moment cannot be given the same one.
            val org = db.orgDao().findOrg(orgId)
            val financialYear = financialYear(
                now,
                zone = BusinessClock.zoneOf(org?.timeZoneId),
                startMonth = org?.fiscalStartMonth ?: 4,
            )
            val billSeq = jobs.lastBillSeq(orgId, financialYear) + 1

            val finished = job.copy(
                status = JobStatus.Completed,
                elapsedSeconds = elapsedSeconds(job, now),
                runningSince = null,
                completedAt = now,
                billFy = financialYear,
                billSeq = billSeq,
                updatedAt = now,
                revision = job.revision + 1,
                lastWriterDeviceId = deviceId,
            )
            // For hourly work the billable figure is the clock, so derive it here
            // rather than asking the driver to copy a number they can already see.
            val timed =
                if (finished.billsFromClock) {
                    finished.copy(quantity = finished.elapsedSeconds / 3600.0)
                } else finished
            // The extras that can only be known now: time spent waiting for the customer,
            // and the diesel put in against this job when the customer is to pay for it.
            val billable = timed.copy(
                waitingMinor = waitingBilled(timed, now),
                fuelBilledMinor = if (timed.fuelTerms == FuelTerms.BilledExtra) {
                    jobs.fuelCostForJob(orgId, timed.id)
                } else 0L,
                discountMinor = discountMinor.coerceAtLeast(0),
            )

            charged = billable.amountMinor

            jobs.upsert(billable)
            addEvent(billable, JobEventType.Completed, byPersonId, at = now)
            enqueue(billable)
            refreshMachineStatus(orgId, billable.machineId)

            if (charged > 0) {
                val entry = LedgerEntryEntity(
                    id = ids.newId(),
                    orgId = orgId,
                    customerId = billable.customerId,
                    kind = LedgerKind.Charge,
                    amountMinor = charged,
                    jobId = billable.id,
                    note = billable.workType,
                    atMillis = now,
                    byPersonId = byPersonId,
                    createdAt = now,
                    deviceId = deviceId,
                )
                ledger.insert(entry)
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_LEDGER, entry.id, OutboxJson.encodeToString(entry)
                )
            }
        }
        return charged
    }

    /**
     * Correct a job that has already been billed.
     *
     * The khata is never rewritten. If the corrected amount is higher we append a
     * further charge; if lower, an adjustment that cancels the difference. The
     * original charge stays exactly where it was, so a customer disputing the figure
     * can be walked through what was billed, what changed and why — rather than being
     * shown a number that quietly differs from the one they were given last week.
     *
     * Returns the signed change to what the customer owes.
     */
    suspend fun amendCompletedJob(
        orgId: String,
        jobId: String,
        can: Capabilities,
        quantity: Double? = null,
        rateMinor: Long? = null,
        note: String? = null,
        byPersonId: String? = null,
        reason: String,
    ): Long {
        can.require(Capability.AmendBilledWork)
        var delta = 0L
        db.withTransaction {
            val job = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
            if (job.status != JobStatus.Completed) throw WorkError.NotFinished(jobId)

            val before = job.amountMinor
            val now = clock.now()
            val correctedQuantity = quantity?.coerceAtLeast(0.0) ?: job.quantity
            val correctedRate = rateMinor?.coerceAtLeast(0L) ?: job.rateMinor
            val amended = job.copy(
                quantity = correctedQuantity,
                rateMinor = correctedRate,
                // Correcting a billed trip job reprices every trip at the corrected
                // rate. The alternative — editing one trip of six after the fact —
                // is not something anybody asks for, and the reason recorded with
                // the correction says what happened.
                tripTotalMinor = if (job.billingMode == BillingMode.PerTrip) {
                    Billing.amount(BillingMode.PerTrip, correctedQuantity, correctedRate, job.stepMinor)
                } else job.tripTotalMinor,
                note = note ?: job.note,
                updatedAt = now,
                revision = job.revision + 1,
                lastWriterDeviceId = deviceId,
            )
            delta = amended.amountMinor - before

            jobs.upsert(amended)
            addEvent(
                job = amended,
                type = JobEventType.QuantitySet,
                byPersonId = byPersonId,
                numericValue = amended.quantity,
                textValue = reason,
                at = now,
            )
            enqueue(amended)

            if (delta != 0L) {
                val entry = LedgerEntryEntity(
                    id = ids.newId(),
                    orgId = orgId,
                    customerId = amended.customerId,
                    // Owing more is a charge; owing less is an adjustment. Both are
                    // new lines, because a ledger that can be edited is one nobody
                    // can argue from.
                    kind = if (delta > 0) LedgerKind.Charge else LedgerKind.Adjustment,
                    amountMinor = kotlin.math.abs(delta),
                    jobId = amended.id,
                    note = reason,
                    atMillis = now,
                    byPersonId = byPersonId,
                    createdAt = now,
                    deviceId = deviceId,
                )
                ledger.insert(entry)
                syncQueue.enqueueUpsert(
                    orgId, SyncQueue.TABLE_LEDGER, entry.id, OutboxJson.encodeToString(entry)
                )
            }
        }
        return delta
    }

    /** Throw the job away. Soft-deleted so a sync cannot resurrect it. */
    suspend fun discard(orgId: String, jobId: String) {
        val machineId = jobs.find(orgId, jobId)?.machineId
        mutate(orgId, jobId) { job ->
            job.copy(status = JobStatus.Discarded, deletedAt = clock.now()) to null
        }
        db.withTransaction { refreshMachineStatus(orgId, machineId) }
    }

    /**
     * Keeps a machine's status in step with the work on it.
     *
     * A machine stays Working for as long as its job is open, pauses included — a
     * tractor stopped for lunch is still out with a driver and is not available to
     * send somewhere else, which is the question the fleet screen is answering.
     *
     * Called inside the caller's transaction so the job and the machine can never
     * disagree about whether work is happening.
     */
    // ------------------------------------------------------ keeping a machine going

    /** Everything that has gone wrong with this machine, newest first. */
    fun observeBreakdowns(orgId: String, machineId: String) =
        machines.observeBreakdowns(orgId, machineId)

    /** Everything still wrong, across the yard. */
    fun observeOpenBreakdowns(orgId: String) = machines.observeOpenBreakdowns(orgId)

    fun observeMachinesWithLiveJobs(orgId: String) = jobs.observeMachinesWithLiveJobs(orgId)

    /**
     * Machines whose next service is within [withinHours] of work, soonest first —
     * on their hours as they stand today, not the last reading somebody typed.
     */
    fun observeServiceDue(orgId: String, withinHours: Double): Flow<List<MachineEntity>> =
        machines.observeAllWithWork(orgId).map { rows ->
            rows.map { it.effective() }
                .filter { it.serviceIntervalHours != null }
                .map { it to ((it.lastServiceAtHours ?: 0.0) + it.serviceIntervalHours!! - it.hoursUsed) }
                .filter { (_, left) -> left <= withinHours }
                .sortedBy { (_, left) -> left }
                .map { (machine, _) -> machine }
        }

    fun observeRecentService(orgId: String, limit: Int = 50) =
        machines.observeRecentService(orgId, limit)

    fun observeFuel(orgId: String, machineId: String) = machines.observeFuel(orgId, machineId)

    fun observeFuelSpendOnMachine(orgId: String, machineId: String) =
        machines.observeFuelSpendOnMachine(orgId, machineId)

    fun observeForMachine(orgId: String, machineId: String, limit: Int = 20) =
        jobs.observeForMachine(orgId, machineId, limit)

    fun observeEarnedOnMachine(orgId: String, machineId: String) =
        jobs.observeEarnedOnMachine(orgId, machineId)

    fun observeService(orgId: String, machineId: String) =
        machines.observeService(orgId, machineId)

    /**
     * The machine is fixed.
     *
     * The one thing this app could not say. A breakdown could be reported and never
     * closed, so the machine stayed Down for good and the only escape was to discard
     * the job — paying for an honest report with a day's real work.
     */
    suspend fun resolveBreakdown(orgId: String, breakdownId: String) {
        db.withTransaction {
            val breakdown = machines.breakdownById(breakdownId) ?: return@withTransaction
            if (breakdown.orgId != orgId || breakdown.resolvedAt != null) return@withTransaction
            val fixed = breakdown.copy(resolvedAt = clock.now())
            machines.upsertBreakdown(fixed)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_BREAKDOWNS, fixed.id, OutboxJson.encodeToString(fixed)
            )
            // Not simply Idle: a second fault may still be open, and the paused job
            // may still be sitting in a field waiting to be carried on.
            refreshMachineStatus(orgId, breakdown.machineId)
        }
    }

    /**
     * Work done on a machine — a service, or the repair that fixed a breakdown.
     *
     * Logging one against an open breakdown closes it, because that is what actually
     * happened and asking the user to then also tick it off is asking them to say the
     * same thing twice.
     */
    suspend fun recordService(
        orgId: String,
        machineId: String,
        description: String,
        costMinor: Long? = null,
        vendor: String? = null,
        closesBreakdownId: String? = null,
        /** What was changed — filters, oil, a belt. Optional. */
        partsNote: String? = null,
    ): String {
        val id = ids.newId()
        db.withTransaction {
            val machine = machines.find(orgId, machineId) ?: return@withTransaction
            // The hours it has worked today, not the last reading: a service done
            // after a season's work is stamped with that season's hours, so the
            // next one is counted from here.
            val hoursNow = machines.findWithWork(orgId, machineId)?.effective()?.hoursUsed
                ?: machine.hoursUsed
            val record = ServiceRecordEntity(
                id = id,
                orgId = orgId,
                machineId = machineId,
                description = description.trim(),
                costMinor = costMinor,
                atMachineHours = hoursNow,
                atMillis = clock.now(),
                vendor = vendor?.trim()?.ifBlank { null },
                partsNote = partsNote?.trim()?.ifBlank { null },
            )
            machines.upsertService(record)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_SERVICE, record.id, OutboxJson.encodeToString(record)
            )

            val serviced = machine.copy(
                lastServiceAtHours = hoursNow,
                updatedAt = clock.now(),
            )
            machines.upsert(serviced)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_MACHINES, serviced.id, OutboxJson.encodeToString(serviced)
            )

            closesBreakdownId?.let { breakdownId ->
                machines.breakdownById(breakdownId)
                    ?.takeIf { it.orgId == orgId && it.resolvedAt == null }
                    ?.let { open ->
                        val fixed = open.copy(resolvedAt = clock.now())
                        machines.upsertBreakdown(fixed)
                        syncQueue.enqueueUpsert(
                            orgId, SyncQueue.TABLE_BREAKDOWNS, fixed.id,
                            OutboxJson.encodeToString(fixed),
                        )
                    }
            }
            refreshMachineStatus(orgId, machineId)
        }
        return id
    }

    /**
     * Diesel put in outside a job.
     *
     * Tanks get filled in the yard on the way out, not only mid-furrow. Recording it
     * needed a running job until now, so the fuel that mattered most to a month's
     * costs was the fuel nobody could enter.
     */
    suspend fun addFuelForMachine(
        orgId: String,
        machineId: String,
        litres: Double,
        costMinor: Long,
        byPersonId: String? = null,
    ) {
        db.withTransaction {
            val entry = FuelEntryEntity(
                id = ids.newId(),
                orgId = orgId,
                machineId = machineId,
                jobId = null,
                litres = litres,
                costMinor = costMinor,
                atMillis = clock.now(),
                byPersonId = byPersonId,
            )
            machines.upsertFuel(entry)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_FUEL, entry.id, OutboxJson.encodeToString(entry)
            )
        }
    }

    /** A machine that broke with no job on it — in the yard, or on the road. */
    suspend fun reportBreakdownForMachine(
        orgId: String,
        machineId: String,
        category: BreakdownCategory,
        note: String? = null,
        byPersonId: String? = null,
    ): String {
        val id = ids.newId()
        db.withTransaction {
            val breakdown = BreakdownEntity(
                id = id,
                orgId = orgId,
                machineId = machineId,
                jobId = null,
                category = category,
                note = note?.trim()?.ifBlank { null },
                reportedAt = clock.now(),
                reportedByPersonId = byPersonId,
            )
            machines.upsertBreakdown(breakdown)
            syncQueue.enqueueUpsert(
                orgId, SyncQueue.TABLE_BREAKDOWNS, breakdown.id,
                OutboxJson.encodeToString(breakdown),
            )
            refreshMachineStatus(orgId, machineId)
        }
        return id
    }

    /**
     * Puts every machine's stored status back in step with the facts.
     *
     * Needed because the status used to be asserted by whichever operation happened
     * last, so databases in the wild hold machines marked Idle with a breakdown still
     * open on them — the fleet screen counting none down while naming one that is.
     * Those rows cannot fix themselves: nothing touches a machine that is sitting
     * broken in a yard. Cheap enough to run at every launch, over a handful of
     * machines, and it writes only where a status is genuinely wrong.
     */
    suspend fun reconcileMachineStatuses(orgId: String) {
        db.withTransaction {
            machines.observeAllOnce(orgId).forEach { machine ->
                refreshMachineStatus(orgId, machine.id)
            }
        }
    }

    /**
     * Works out what a machine's status actually is, rather than being told.
     *
     * Every caller used to assert a status directly, and each was right about its own
     * transition and wrong about everything else. Finishing a job set the machine
     * Idle even while a breakdown on it was still open, so a tractor with a seized
     * pump read as free to send out; the only way out of Down was to finish or
     * discard the job, so an honest breakdown report trapped the machine until the
     * owner threw away work he had really done.
     *
     * The three facts that decide it are all in the database, so none of them has to
     * be remembered by a caller:
     *  - anything still broken on it and it is Down,
     *  - otherwise a job open on it and it is Working,
     *  - otherwise it is in the yard.
     */
    suspend fun refreshMachineStatus(orgId: String, machineId: String?) {
        val id = machineId ?: return
        val status = when {
            machines.openBreakdownCount(orgId, id) > 0 -> MachineStatus.Down
            jobs.liveJobCountOnMachine(orgId, id) > 0 -> MachineStatus.Working
            else -> MachineStatus.Idle
        }
        markMachine(orgId, id, status)
    }

    private suspend fun markMachine(orgId: String, machineId: String?, status: MachineStatus) {
        val id = machineId ?: return
        val machine = machines.find(orgId, id) ?: return
        if (machine.status == status) return
        val updated = machine.copy(status = status, updatedAt = clock.now())
        machines.upsert(updated)
        syncQueue.enqueueUpsert(
            orgId, SyncQueue.TABLE_MACHINES, updated.id, OutboxJson.encodeToString(updated)
        )
    }

    /**
     * Work done while the phone was off, typed in afterwards.
     *
     * [workedAt] is when it happened; the job also records when it was entered and
     * that it was entered by hand, so neither side has to rely on memory when an
     * owner and a driver disagree about a day in October.
     *
     * Does not bill automatically — a manual entry goes through the same review and
     * finish flow as any other job, so the amount is seen before it hits a khata.
     */
    suspend fun recordPastWork(
        orgId: String,
        customerId: String,
        workType: String,
        billingMode: BillingMode,
        unit: MeasureUnit,
        rateMinor: Long,
        quantity: Double,
        workedAt: Long,
        startedAt: Long?,
        completedAt: Long?,
        machineId: String? = null,
        fieldId: String? = null,
        operatorPersonId: String? = null,
        note: String? = null,
        terms: JobTerms = JobTerms(),
    ): String {
        val now = clock.now()
        val elapsed = if (startedAt != null && completedAt != null) {
            ((completedAt - startedAt).coerceAtLeast(0L)) / 1000
        } else 0L

        val step = stepFor(orgId)
        val job = JobEntity(
            id = ids.newId(),
            orgId = orgId,
            customerId = customerId,
            fieldId = fieldId,
            machineId = machineId,
            operatorPersonId = operatorPersonId,
            workType = workType,
            billingMode = billingMode,
            unit = unit,
            rateMinor = rateMinor,
            stepMinor = step,
            quantity = quantity,
            // A trip job's bill is its trip total, not quantity times rate, so a
            // job entered afterwards has to be given one. Left at nought it billed
            // nothing, under a screen that had just said "Save ₹4,000".
            tripTotalMinor = if (billingMode == BillingMode.PerTrip) {
                Billing.amount(billingMode, quantity, rateMinor, step)
            } else 0L,
            elapsedSeconds = elapsed,
            status = JobStatus.Paused,
            workedAt = workedAt,
            recordedAt = now,
            entryMethod = EntryMethod.Manual,
            startedAt = startedAt,
            completedAt = completedAt,
            runningSince = null,
            note = note,
            createdAt = now,
            updatedAt = now,
            lastWriterDeviceId = deviceId,
        ).let { terms.applyTo(it) }
        db.withTransaction {
            jobs.upsert(job)
            addEvent(job, JobEventType.Started, operatorPersonId, at = workedAt)
            enqueue(job)
        }
        return job.id
    }

    /**
     * Work already recorded on the same machine across a proposed window.
     *
     * Checked before saving a backdated entry: a machine cannot be in two fields at
     * once, and silently accepting that produces a khata nobody can defend.
     */
    suspend fun findOverlaps(
        orgId: String,
        machineId: String,
        startsAt: Long,
        endsAt: Long,
        ignoreJobId: String = "",
    ): List<OverlapWarning> =
        jobs.overlapping(orgId, machineId, startsAt, endsAt, ignoreJobId)
            .mapNotNull { job ->
                val from = job.startedAt ?: return@mapNotNull null
                val to = job.completedAt ?: return@mapNotNull null
                OverlapWarning(job.id, from, to)
            }

    // ------------------------------------------------------------------ internals

    private class Event(
        val type: JobEventType,
        val numericValue: Double? = null,
        val textValue: String? = null,
    )

    /** Load, transform, persist, log and queue — all inside one transaction. */
    private suspend fun mutate(
        orgId: String,
        jobId: String,
        transform: (JobEntity) -> Pair<JobEntity, Event?>,
    ) {
        db.withTransaction {
            val current = jobs.find(orgId, jobId) ?: throw WorkError.NoSuchJob(jobId)
            val (changed, event) = transform(current)
            val stamped = changed.copy(
                updatedAt = clock.now(),
                revision = current.revision + 1,
                lastWriterDeviceId = deviceId,
            )
            jobs.upsert(stamped)
            if (event != null) {
                addEvent(stamped, event.type, stamped.operatorPersonId, event.numericValue, event.textValue)
            }
            enqueue(stamped)
        }
    }

    private suspend fun addEvent(
        job: JobEntity,
        type: JobEventType,
        byPersonId: String?,
        numericValue: Double? = null,
        textValue: String? = null,
        at: Long = clock.now(),
    ) {
        val event = JobEventEntity(
            id = ids.newId(),
            jobId = job.id,
            orgId = job.orgId,
            type = type,
            atMillis = at,
            byPersonId = byPersonId,
            numericValue = numericValue,
            textValue = textValue,
        )
        jobs.insertEvent(event)
        syncQueue.enqueueUpsert(
            job.orgId, SyncQueue.TABLE_JOB_EVENTS, event.id, OutboxJson.encodeToString(event)
        )
    }

    private suspend fun enqueue(job: JobEntity) {
        syncQueue.enqueueUpsert(
            job.orgId, SyncQueue.TABLE_JOBS, job.id, OutboxJson.encodeToString(job)
        )
    }
}
