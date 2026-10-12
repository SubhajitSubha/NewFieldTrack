package com.example.fieldtrack.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.EntryMethod
import com.example.fieldtrack.data.db.JobEventType
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.SyncState
import kotlinx.serialization.Serializable

/*
 * The FieldTrack schema.
 *
 * Three rules run through all of it.
 *
 * 1. Money is whole rupees in a Long, never a floating point number, and never paise.
 *    Nobody in this business splits a rupee. Measurements (3.5 acre) are Doubles; the
 *    amount they produce is rounded to a whole rupee at the point of calculation.
 *
 * 2. Every row carries `orgId`. A driver can work for several businesses, so scoping
 *    is not a UI filter — every query is constrained by the organisation whose session
 *    is active, and there is no query that can return another business's rows.
 *
 * 3. Identifiers are UUIDs generated on the device. A job started in a dead zone has a
 *    real identity immediately and never waits for a server to name it.
 *
 * There are deliberately no foreign keys. Rows arrive from sync in an order we do not
 * control, and a constraint violation mid-sync would drop real work recorded in a
 * field. Integrity is enforced in the repositories, where it can fail softly.
 */

// ------------------------------------------------------------------ organisation

@Entity(tableName = "organisations")
@Serializable
data class OrgEntity(
    @PrimaryKey val id: String,
    val name: String,
    val area: String? = null,
    val logoPath: String? = null,
    /**
     * What goes in the letterhead of a bill.
     *
     * All optional. A farmer trading under his own name has a village and a phone
     * and nothing else, and a bill that prints empty labels for the rest looks like
     * a form someone failed to fill in — so each line appears only when it is set.
     */
    val phone: String? = null,
    val addressLine: String? = null,
    val gstin: String? = null,
    val upiId: String? = null,
    /** What this business measures land in by default. Overridable per job. */
    val defaultLandUnit: MeasureUnit = MeasureUnit.Acre,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.Pending,
)

/**
 * One person's place in one organisation.
 *
 * A professional driver has several of these rows — two businesses in the same week,
 * or a different one each season. Leaving does not delete the row: [active] goes
 * false, read access stops, and the work they recorded stays with the business that
 * owns it.
 */
@Entity(
    tableName = "memberships",
    indices = [
        Index("orgId"),
        Index("personId"),
        Index(value = ["orgId", "personId"], unique = true),
    ],
)
@Serializable
data class MembershipEntity(
    /**
     * Deterministic: `{orgId}_{personId}`, built by [membershipId].
     *
     * Not a random id, because a security rule has to be able to find this row
     * without being told where it is. Rules can fetch a document at a path they can
     * construct, and cannot search a collection for one — so "is this caller a member
     * here" is only answerable if the caller's own id is in the path. A random id
     * with the person named in a field is unfindable to a rule, and the whole
     * organisation has to be left open instead.
     */
    @PrimaryKey val id: String,
    val orgId: String,
    /** Stable identity of the person across organisations — their auth subject. */
    val personId: String,
    val displayName: String,
    val phone: String? = null,
    val photoPath: String? = null,
    val role: MemberRole,

    /** Money, rates and balances. False for an operator unless the owner grants it. */
    val canSeeMoney: Boolean = false,
    val canAddCustomers: Boolean = false,
    /** Whether this person may close a bill and take payment, not just record work. */
    val canCloseBills: Boolean = false,

    val active: Boolean = true,
    val joinedAt: Long,
    val leftAt: Long? = null,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.Pending,
)

/** Where a membership lives, derivable by anyone who knows the two ids. */
fun membershipId(orgId: String, personId: String): String = "${orgId}_$personId"

/** An unredeemed invite code. One code, one organisation, one expiry. */
@Entity(tableName = "invites", indices = [Index("orgId"), Index(value = ["code"], unique = true)])
@Serializable
data class InviteEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val code: String,
    val role: MemberRole = MemberRole.Operator,
    val canSeeMoney: Boolean = false,
    val canAddCustomers: Boolean = false,
    val canCloseBills: Boolean = false,
    val createdAt: Long,
    val expiresAt: Long,
    val redeemedAt: Long? = null,
    val redeemedByPersonId: String? = null,
    val syncState: SyncState = SyncState.Pending,
)

// ---------------------------------------------------------------------- parties

@Entity(tableName = "customers", indices = [Index("orgId"), Index(value = ["orgId", "name"])])
@Serializable
data class CustomerEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val name: String,
    val phone: String? = null,
    val village: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Soft delete: a customer with history is hidden, never removed. */
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.Pending,
)

@Entity(tableName = "fields", indices = [Index("orgId"), Index("customerId")])
@Serializable
data class FieldEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val customerId: String,
    /** Whatever the farmer calls it — "canal-side plot", not a survey number. */
    val name: String,
    val area: Double,
    val unit: MeasureUnit,
    val crop: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.Pending,
)

// --------------------------------------------------------------------- machines

@Entity(tableName = "machines", indices = [Index("orgId")])
@Serializable
data class MachineEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val name: String,
    val kind: MachineKind,
    val plate: String? = null,
    val photoPath: String? = null,
    val status: MachineStatus = MachineStatus.Idle,
    val hoursUsed: Double = 0.0,
    /** Service every N hours. Null means this machine is not on a schedule. */
    val serviceIntervalHours: Double? = null,
    val lastServiceAtHours: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.Pending,
)

@Entity(tableName = "fuel_entries", indices = [Index("orgId"), Index("machineId"), Index("jobId")])
@Serializable
data class FuelEntryEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val machineId: String,
    val jobId: String? = null,
    val litres: Double,
    val costRupees: Long,
    val atMillis: Long,
    val byPersonId: String? = null,
    val syncState: SyncState = SyncState.Pending,
)

@Entity(tableName = "service_records", indices = [Index("orgId"), Index("machineId")])
@Serializable
data class ServiceRecordEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val machineId: String,
    val description: String,
    val costRupees: Long? = null,
    val atMachineHours: Double? = null,
    val atMillis: Long,
    val vendor: String? = null,
    val syncState: SyncState = SyncState.Pending,
)

@Entity(tableName = "breakdowns", indices = [Index("orgId"), Index("machineId"), Index("jobId")])
@Serializable
data class BreakdownEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val machineId: String,
    val jobId: String? = null,
    val category: BreakdownCategory,
    val note: String? = null,
    val photoPath: String? = null,
    val reportedAt: Long,
    val reportedByPersonId: String? = null,
    val resolvedAt: Long? = null,
    val syncState: SyncState = SyncState.Pending,
)

// ------------------------------------------------------------------------- work

/**
 * The rate card. A job picks up its rate from here so nobody retypes it in a field.
 *
 * A row with a [customerId] overrides the general rate for that customer — "what I
 * charged Ramesh last time" is the number a contractor actually wants.
 *
 * [label] lets one kind of work carry more than one rate. Harvesting wheat and
 * harvesting paddy are the same machine doing the same job for very different money,
 * and a contractor who has to pick "Harvesting" and then retype the rate every time
 * has a rate card that does not know his trade. Free text on purpose: the word is
 * his, not a list this app decided on.
 */
@Entity(tableName = "rates", indices = [Index("orgId"), Index(value = ["orgId", "workType"])])
@Serializable
data class RateEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val workType: String,
    val billingMode: BillingMode,
    val unit: MeasureUnit,
    val amountRupees: Long,
    val customerId: String? = null,
    val label: String? = null,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.Pending,
)

/**
 * One piece of paid work.
 *
 * [quantity] is always the billable figure in [unit] — acres, trips, quintals,
 * kilometres, or hours. [elapsedSeconds] is tracked for every job regardless, because
 * an owner wants machine utilisation even on work charged by the acre.
 *
 * [workedAt] and [recordedAt] differ whenever a job is typed in after the fact. Both
 * are kept, and [entryMethod] says which kind of record this is.
 */
@Entity(
    tableName = "jobs",
    indices = [
        Index("orgId"),
        Index("customerId"),
        Index("machineId"),
        Index("operatorPersonId"),
        Index(value = ["orgId", "status"]),
        Index(value = ["orgId", "workedAt"]),
    ],
)
@Serializable
data class JobEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val customerId: String,
    val fieldId: String? = null,
    val machineId: String? = null,
    /** Who did the work. An owner driving their own machine is recorded here too. */
    val operatorPersonId: String? = null,

    val workType: String,
    val billingMode: BillingMode,
    val unit: MeasureUnit,
    val rateRupees: Long,
    val quantity: Double = 0.0,
    /**
     * What the trips on this job come to, added up as each one was made.
     *
     * Trip work is not quantity times rate. A contractor hauling for one farmer all
     * day raises the price when the load gets heavier or the field gets further, and
     * adds the next trip at the new rate rather than starting a second job. Each trip
     * is therefore priced when it happens and banked here; the rate on the job is
     * only what the NEXT trip will cost.
     */
    val tripTotalRupees: Long = 0,
    val elapsedSeconds: Long = 0,
    val distanceMetres: Long? = null,

    val status: JobStatus = JobStatus.Running,
    /** When the work happened. */
    val workedAt: Long,
    /** When it was entered into the app. Equal to workedAt for a timed job. */
    val recordedAt: Long,
    val entryMethod: EntryMethod = EntryMethod.Timed,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    /** Moment the timer last resumed; null while paused. Lets elapsed survive a kill. */
    val runningSince: Long? = null,

    val note: String? = null,
    /**
     * The bill this job was billed under, once it is finished.
     *
     * Split into the financial year and a number within it so the next number is a
     * MAX over the year rather than a string comparison. Formatted for the eye as
     * "2026-27/0241" — the shape an Indian accountant expects to see.
     */
    val billFy: String? = null,
    val billSeq: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,

    val syncState: SyncState = SyncState.Pending,
    /** Which device last wrote this row — the tiebreak in a field-level merge. */
    val lastWriterDeviceId: String? = null,
    /** Bumped on every local write, so a stale server copy is recognisable. */
    val revision: Long = 1,
) {
    /** What this job is worth, rounded to the rupee the way a contractor would. */
    val amountRupees: Long
        get() = when (billingMode) {
            BillingMode.Fixed -> rateRupees
            // Trips carry their own prices; multiplying loses every rate change.
            BillingMode.PerTrip -> tripTotalRupees
            else -> Math.round(quantity * rateRupees)
        }
}

/**
 * Append-only timeline for a job: started, paused, a trip added, fuel put in.
 *
 * Kept separate from the job row so two devices recording different events during one
 * handover both survive — events merge by union, where a single mutable job row would
 * have to pick a winner.
 */
@Entity(tableName = "job_events", indices = [Index("jobId"), Index("orgId")])
@Serializable
data class JobEventEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val orgId: String,
    val type: JobEventType,
    val atMillis: Long,
    val byPersonId: String? = null,
    val numericValue: Double? = null,
    val textValue: String? = null,
    val syncState: SyncState = SyncState.Pending,
)

// ----------------------------------------------------------------------- ledger

/**
 * One line of a customer's khata. Append-only, always.
 *
 * A balance is never stored and never edited — it is the sum of these rows. That is
 * what lets an owner and a driver both take a payment while offline and have the two
 * entries simply add up when they sync, instead of one silently overwriting the other.
 *
 * A mistake is fixed by writing an [LedgerKind.Adjustment] that reverses it and
 * pointing [reversesEntryId] at the original. Nothing is ever deleted, so a customer
 * disputing a figure can be walked through every line.
 */
@Entity(
    tableName = "ledger_entries",
    indices = [Index("orgId"), Index("customerId"), Index("jobId"), Index(value = ["orgId", "atMillis"])],
)
@Serializable
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val orgId: String,
    val customerId: String,
    val kind: LedgerKind,
    /** Always positive. [kind] decides which way it moves the balance. */
    val amountRupees: Long,
    val jobId: String? = null,
    val method: PaymentMethod? = null,
    val note: String? = null,
    val atMillis: Long,
    val byPersonId: String? = null,
    /** Set on a correcting entry to name the entry it cancels. */
    val reversesEntryId: String? = null,
    val createdAt: Long,
    val syncState: SyncState = SyncState.Pending,
    val deviceId: String? = null,
)

// ----------------------------------------------------------------------- outbox

/**
 * The queue of local changes waiting to reach the server.
 *
 * Nothing in this app writes to the network directly. A mutation writes its row and
 * appends here in one transaction, and a background worker drains the queue whenever
 * there is signal. If the process dies between the two, the transaction rolls back and
 * neither happened — which is the only acceptable outcome when the row is a payment.
 */
@Entity(tableName = "outbox", indices = [Index("createdAt"), Index("orgId")])
@Serializable
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val orgId: String,
    val tableName: String,
    val entityId: String,
    val operation: com.example.fieldtrack.data.db.OutboxOp,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    /**
     * When the user set this change aside, if they have.
     *
     * A change the server refuses holds the whole queue, which is right: letting
     * later changes jump it would put events on the server before the jobs they
     * belong to. But a refusal that can never succeed would then stop the app ever
     * syncing again, so there has to be a way past it — and the only honest one is
     * the user deciding, having been told what the server said.
     *
     * Set aside, never deleted. The row stays here with its payload intact, so a
     * change somebody made is still a change somebody made, and a later version of
     * this app can offer to send it again.
     */
    val blockedAt: Long? = null,
)
