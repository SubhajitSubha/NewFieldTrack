package com.example.fieldtrack.data.db

import com.example.fieldtrack.data.db.dao.OutboxDao
import com.example.fieldtrack.data.db.entity.OutboxEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Wall clock, injectable so tests are not at the mercy of the real one. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

/**
 * Identifiers are generated here, on the device.
 *
 * A job started with no signal must have a real, final id the instant it exists —
 * waiting for a server to name a row is exactly the dependency this app cannot have.
 * UUID collisions across devices are not a practical concern.
 */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}

internal val OutboxJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * The queue of local changes waiting for the server.
 *
 * Callers must enqueue inside the same transaction as the row they are writing. The
 * repositories below all do; if a future one forgets, the symptom is a change that
 * never reaches the server, which is why [SyncQueue] is never exposed to the UI layer.
 */
class SyncQueue(
    private val outboxDao: OutboxDao,
    private val clock: Clock = Clock.System,
    /**
     * Told whenever something is queued, so a send can be arranged.
     *
     * A lambda rather than a direct call, because this class has no business
     * knowing about WorkManager or Android at all — and because a test queueing a
     * hundred changes should not be scheduling background work.
     *
     * The default does nothing, which is the correct behaviour for a queue with
     * nobody listening: the change is still safely written down.
     */
    private val onEnqueued: () -> Unit = {},
) {
    suspend fun enqueueUpsert(orgId: String, table: String, entityId: String, payloadJson: String) {
        outboxDao.enqueue(
            OutboxEntity(
                orgId = orgId,
                tableName = table,
                entityId = entityId,
                operation = OutboxOp.Upsert,
                payloadJson = payloadJson,
                createdAt = clock.now(),
            )
        )
        onEnqueued()
    }

    /**
     * Queues a membership, and the note under the person that points at it.
     *
     * Both or neither: a membership the person cannot find is a business they
     * silently lose access to when they change handset.
     */
    suspend fun enqueueMembership(
        orgId: String,
        personId: String,
        membershipId: String,
        payloadJson: String,
        indexJson: String,
    ) {
        enqueueUpsert(orgId, TABLE_MEMBERSHIPS, membershipId, payloadJson)
        enqueueUpsert(orgId, TABLE_MEMBER_ORGS, "${personId}_$orgId", indexJson)
    }

    /**
     * A few fields of a membership, for the person editing their own row.
     *
     * Sent as a patch because a person may change their name or step out, and
     * nothing else — and a whole row would repeat every switch as this phone last
     * saw it. The transport merges memberships, so the rest of the server's copy,
     * grants included, is left exactly as the owner set it.
     */
    suspend fun enqueueMembershipPatch(orgId: String, membershipId: String, patch: JsonObject) =
        enqueuePatch(orgId, TABLE_MEMBERSHIPS, membershipId, patch)

    /**
     * A few fields of a row, merged into the server's copy instead of replacing it.
     *
     * For a row two people can change at once. A whole row carries every field as this
     * phone last saw it, so one that has been offline puts the old status, note and
     * count back over what somebody else did in the meantime. Only the table must be
     * one the transport merges: see [MERGED_TABLES].
     */
    suspend fun enqueuePatch(orgId: String, table: String, entityId: String, patch: JsonObject) {
        check(table in MERGED_TABLES) { "$table is replaced whole, not merged" }
        enqueueUpsert(orgId, table, entityId, patch.toString())
    }

    suspend fun enqueueDelete(orgId: String, table: String, entityId: String) {
        outboxDao.enqueue(
            OutboxEntity(
                orgId = orgId,
                tableName = table,
                entityId = entityId,
                operation = OutboxOp.Delete,
                payloadJson = "",
                createdAt = clock.now(),
            )
        )
        onEnqueued()
    }

    companion object {
        /** A document in each business that every commit touches, so a reader can tell cheaply whether anything changed. */
        const val META = "meta"
        const val CHANGED = "changed"
        const val TABLE_ORGS = "organisations"
        const val TABLE_MEMBERSHIPS = "memberships"
        const val TABLE_JOBS = "jobs"
        const val TABLE_JOB_EVENTS = "job_events"
        const val TABLE_LEDGER = "ledger_entries"
        const val TABLE_CUSTOMERS = "customers"
        const val TABLE_FIELDS = "fields"
        const val TABLE_MACHINES = "machines"
        const val TABLE_FUEL = "fuel_entries"
        const val TABLE_BREAKDOWNS = "breakdowns"
        const val TABLE_RATES = "rates"
        const val TABLE_SERVICE = "service_records"
        const val TABLE_TEAM_EVENTS = "team_events"
        const val TABLE_PAYOUTS = "payouts"
        const val TABLE_JOB_COSTS = "job_costs"
        const val TABLE_JOB_COST_AMOUNTS = "job_cost_amounts"
        const val TABLE_MACHINE_FINANCE = "machine_finance"
        const val TABLE_MACHINE_PAPERS = "machine_papers"
        const val TABLE_BOOKING_REQUESTS = "booking_requests"
        /** What the business has paid for: written by the server, read here. Never queued. */
        const val TABLE_ENTITLEMENT = "entitlement"
        /** A purchase the owner's phone hands to the server to check. */
        const val TABLE_PURCHASE_CLAIMS = "purchase_claims"
        const val TABLE_BOOKINGS = "bookings"
        const val TABLE_INVITES = "invites"

        /**
         * Invite codes, published at the top level rather than under an
         * organisation. A driver holding a code does not know which organisation it
         * belongs to — finding that out is the entire point of the lookup.
         */
        const val TABLE_INVITE_CODES = "inviteCodes"

        /**
         * A note that a person belongs to an organisation, filed under the person.
         *
         * Everything else is filed under the organisation, which is right for
         * people already inside one. A phone that has just been signed into knows
         * only who its owner is, and rules cannot search — so without a list kept
         * where the person can be looked up by their own id, a new handset has no
         * way to discover which business's work to ask for. Its entity id is
         * `{personId}_{orgId}`; the transport turns that into a path.
         */
        const val TABLE_MEMBER_ORGS = "memberOrgs"

        /** Collections that live at the root because they are read before joining. */
        val ROOT_COLLECTIONS = setOf(TABLE_INVITE_CODES)

        /**
         * Tables the transport merges rather than replaces, so a patch is safe.
         *
         * Memberships, because a person edits only their own name or leaves; bookings,
         * because two planners can change one at once.
         */
        val MERGED_TABLES = setOf(TABLE_MEMBERSHIPS, TABLE_BOOKINGS, TABLE_BOOKING_REQUESTS)
    }
}
