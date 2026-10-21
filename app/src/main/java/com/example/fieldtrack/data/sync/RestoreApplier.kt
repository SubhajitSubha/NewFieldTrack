package com.example.fieldtrack.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.BreakdownEntity
import com.example.fieldtrack.data.db.entity.CustomerEntity
import com.example.fieldtrack.data.db.entity.FuelEntryEntity
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.db.entity.JobEventEntity
import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.data.db.entity.MachineEntity
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.db.entity.ServiceRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.example.fieldtrack.data.db.entity.TeamEventEntity
import com.example.fieldtrack.data.db.entity.PayoutEntity
import com.example.fieldtrack.data.db.entity.JobCostDoc
import com.example.fieldtrack.data.db.entity.JobCostAmountDoc
import com.example.fieldtrack.data.db.SyncState
import com.example.fieldtrack.data.db.entity.JobCostEntity
import com.example.fieldtrack.data.db.entity.FieldEntity

/**
 * Writes downloaded rows into the local database.
 *
 * Two rules, and the second is the one that matters:
 *
 * **Order.** Customers and machines land before the jobs that name them, jobs
 * before their events and the money raised against them. The caller supplies the
 * rows already in that order; this only has to not shuffle them.
 *
 * **A local change always wins.** Before any row is written, the outbox is asked
 * whether this phone is already holding an unsent change for it. If it is, the
 * download is dropped. A restore arrives from a server that has not yet heard what
 * somebody did in a field this morning, and letting it overwrite that would be the
 * app destroying work in the course of recovering it — which is a far worse failure
 * than simply not having restored a row.
 */
class RestoreApplier(
    private val db: FieldTrackDatabase,
    /**
     * Says whether a person id is one this install has proved its way out of.
     *
     * Somebody who set the app up before signing in carries an id it invented
     * for itself, and that id reaches the server. Signing in moves every local
     * row onto the proven one, but the membership already filed under the old
     * id stays where it is and cannot be taken down — so without this, every
     * pull brings it back as a second live member wearing the same name.
     */
    private val isAdoptedAway: (personId: String) -> Boolean = { false },
) {

    /**
     * Returns how many rows were actually written.
     *
     * One transaction for the lot, and if that fails, one transaction per row.
     *
     * The batch is the fast path and was the only path, which hid something
     * costly: a row that cannot be written throws, and a throw inside a
     * transaction rolls the whole thing back — so the per-row `runCatching`
     * below was catching the exception while the damage was already done, and
     * a single duplicate job event silently discarded every other row in the
     * pass. The handset pulled once, stopped taking anything in, and said
     * nothing about it.
     *
     * The retry is what makes the per-row catch mean what it says. A bad row
     * is skipped and the rest land, at the cost of a slower second pass that
     * only happens when something has actually gone wrong.
     */
    suspend fun apply(rows: List<RemoteRow>): Int = runCatching {
        var written = 0
        db.withTransaction {
            for (row in rows) {
                if (skip(row)) continue
                if (write(row)) written++
            }
        }
        written
    }.getOrElse { failure ->
        Log.d(TAG, "Batch apply failed, falling back to one row at a time", failure)
        var written = 0
        for (row in rows) {
            runCatching {
                db.withTransaction {
                    if (!skip(row) && write(row)) written++
                }
            }.onFailure { Log.d(TAG, "Could not apply ${row.table}/${row.entityId}", it) }
        }
        return written
    }

    private suspend fun skip(row: RemoteRow): Boolean =
        db.outboxDao().pendingFor(row.table, row.entityId) > 0 || row.isOwnGhost()


    /** A membership filed under an identity this install has left behind. */
    private fun RemoteRow.isOwnGhost(): Boolean {
        if (table != SyncQueue.TABLE_MEMBERSHIPS) return false
        val personId = fields["personId"] as? String ?: return false
        return isAdoptedAway(personId)
    }

    private suspend fun write(row: RemoteRow): Boolean {
        val json = JsonObject(row.fields.mapValues { (_, v) -> v.asJson() })
            .withoutTransportFields()
        return runCatching {
            when (row.table) {
                SyncQueue.TABLE_ORGS ->
                    db.orgDao().upsertOrg(decode<OrgEntity>(json))
                SyncQueue.TABLE_MEMBERSHIPS ->
                    db.orgDao().upsertMembership(decode<MembershipEntity>(json))
                SyncQueue.TABLE_CUSTOMERS ->
                    db.customerDao().upsert(decode<CustomerEntity>(json))
                SyncQueue.TABLE_MACHINES ->
                    db.machineDao().upsert(decode<MachineEntity>(json))
                SyncQueue.TABLE_RATES ->
                    db.jobDao().upsertRate(decode<RateEntity>(json))
                SyncQueue.TABLE_JOBS ->
                    db.jobDao().upsert(decode<JobEntity>(json))
                SyncQueue.TABLE_JOB_EVENTS ->
                    db.jobDao().insertEvent(decode<JobEventEntity>(json))
                SyncQueue.TABLE_LEDGER ->
                    db.ledgerDao().insert(decode<LedgerEntryEntity>(json))
                SyncQueue.TABLE_FUEL ->
                    db.machineDao().upsertFuel(decode<FuelEntryEntity>(json))
                SyncQueue.TABLE_BREAKDOWNS ->
                    db.machineDao().upsertBreakdown(decode<BreakdownEntity>(json))
                SyncQueue.TABLE_PAYOUTS ->
                    db.orgDao().upsertPayout(decode<PayoutEntity>(json))
                // Two documents make one row. The facts arrive without a
                // figure, so an existing row is updated in place rather than
                // replaced — overwriting it would blank the amount on the
                // phone of the person who typed it.
                SyncQueue.TABLE_JOB_COSTS -> {
                    val doc = decode<JobCostDoc>(json)
                    val touched = db.orgDao().updateJobCostFacts(
                        orgId = doc.orgId,
                        id = doc.id,
                        kind = doc.kind,
                        headcount = doc.headcount,
                        note = doc.note,
                        byPersonId = doc.byPersonId,
                        atMillis = doc.atMillis,
                    )
                    if (touched == 0) {
                        db.orgDao().upsertJobCost(
                            JobCostEntity(
                                id = doc.id,
                                orgId = doc.orgId,
                                jobId = doc.jobId,
                                kind = doc.kind,
                                headcount = doc.headcount,
                                // Unpriced until its own document arrives, or
                                // for ever if this person may not read it.
                                amountRupees = null,
                                note = doc.note,
                                byPersonId = doc.byPersonId,
                                atMillis = doc.atMillis,
                                createdAt = doc.createdAt,
                                syncState = SyncState.Synced,
                                deviceId = doc.deviceId,
                            )
                        )
                    }
                }
                // Lands on a row that is already there. If it is not — the
                // page split between the two — the write is a no-op and the
                // next pass resumes from the earlier of the two truncations
                // and brings both down together.
                SyncQueue.TABLE_JOB_COST_AMOUNTS -> {
                    val doc = decode<JobCostAmountDoc>(json)
                    db.orgDao().setJobCostAmount(doc.orgId, doc.id, doc.amountRupees)
                }
                SyncQueue.TABLE_FIELDS ->
                    db.customerDao().upsertField(decode<FieldEntity>(json))
                SyncQueue.TABLE_TEAM_EVENTS ->
                    db.orgDao().addTeamEvent(decode<TeamEventEntity>(json))
                SyncQueue.TABLE_SERVICE ->
                    db.machineDao().upsertService(decode<ServiceRecordEntity>(json))
                // A table this build does not know. Skipped rather than guessed at,
                // and the rest of the restore carries on around it.
                else -> return false
            }
            true
        }.getOrElse {
            // A row that cannot be read is skipped so the rest of the restore
            // carries on, but it is said out loud. Silently dropping it meant
            // a whole collection could go missing from a handset with no
            // evidence anywhere of what happened or why.
            Log.d(TAG, "Could not apply ${row.table}/${row.entityId}", it)
            false
        }
    }

    private inline fun <reified T> decode(json: JsonObject): T = RestoreJson.decodeFromString(
        RestoreJson.encodeToString(JsonObject.serializer(), json)
    )

    /** Drops the bookkeeping the transport adds, which no entity has a field for. */
    private fun JsonObject.withoutTransportFields() =
        JsonObject(filterKeys { !it.startsWith("_") })

    private companion object {
        const val TAG = "RestoreApplier"

        /**
         * Forgiving on purpose. A row written by a newer build may carry fields
         * this one has never heard of, and refusing the whole record over an
         * unexpected key would mean a phone that cannot restore its own history
         * because the owner's other handset updated first.
         */
        val RestoreJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}

/** Firestore's loosely typed values, back into JSON the entities can be read from. */
private fun Any?.asJson(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.asJson() })
    is List<*> -> JsonArray(map { it.asJson() })
    else -> JsonPrimitive(toString())
}
