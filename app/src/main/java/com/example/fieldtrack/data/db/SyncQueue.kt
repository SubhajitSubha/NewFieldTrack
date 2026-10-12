package com.example.fieldtrack.data.db

import com.example.fieldtrack.data.db.dao.OutboxDao
import com.example.fieldtrack.data.db.entity.OutboxEntity
import kotlinx.serialization.json.Json
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
    }

    companion object {
        const val TABLE_ORGS = "organisations"
        const val TABLE_MEMBERSHIPS = "memberships"
        const val TABLE_JOBS = "jobs"
        const val TABLE_JOB_EVENTS = "job_events"
        const val TABLE_LEDGER = "ledger_entries"
        const val TABLE_CUSTOMERS = "customers"
        const val TABLE_MACHINES = "machines"
        const val TABLE_FUEL = "fuel_entries"
        const val TABLE_BREAKDOWNS = "breakdowns"
        const val TABLE_RATES = "rates"
        const val TABLE_SERVICE = "service_records"
        const val TABLE_INVITES = "invites"

        /**
         * Invite codes, published at the top level rather than under an
         * organisation. A driver holding a code does not know which organisation it
         * belongs to — finding that out is the entire point of the lookup.
         */
        const val TABLE_INVITE_CODES = "inviteCodes"

        /** Collections that live at the root because they are read before joining. */
        val ROOT_COLLECTIONS = setOf(TABLE_INVITE_CODES)
    }
}
