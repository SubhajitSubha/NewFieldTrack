package com.example.fieldtrack.data.sync

import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.membershipId
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads a person's work back out of Firestore.
 *
 * Only the collections this app writes, and only under organisations the person is
 * actually a member of — the rules would refuse anything else, and asking for it
 * would be this client deciding what it is entitled to rather than the server.
 *
 * Every read here asks the server and refuses to be answered from the cache, and
 * that is not a preference — it is what makes the answers safe to act on.
 *
 * Firestore, left to itself, serves a `get` out of the local cache when it cannot
 * reach the network, and a document missing from an empty cache comes back as a
 * document that does not exist. Every question this class asks would then get the
 * most damaging possible answer the moment a phone lost signal: "you have no
 * businesses" to a restore, "your account is fine" to a lock, and — worst —
 * "this membership is gone" to the check that decides whether somebody has been
 * thrown out of the business they are standing in a field working for.
 *
 * With [Source.SERVER] an unreachable server raises instead, which every caller
 * already treats as "nothing has changed". Silence is then silence, and only a
 * real answer can take anything away from anybody.
 */
class FirestoreRestore(
    private val firestore: FirebaseFirestore = Firestore.instance,
) : RemoteRestore {

    override suspend fun orgsFor(personId: String): List<RemoteOrg> =
        withTimeout(TIMEOUT_MILLIS) {
            firestore.collection(PEOPLE).document(personId).collection(ORGS)
                .get(Source.SERVER)
                .await()
                .documents
                .map {
                    RemoteOrg(
                        orgId = it.getString("orgId") ?: it.id,
                        orgName = it.getString("orgName").orEmpty(),
                    )
                }
        }

    override suspend fun fetchOrg(orgId: String): List<RemoteRow> =
        withTimeout(ORG_TIMEOUT_MILLIS) {
            val rows = mutableListOf<RemoteRow>()
            val org = firestore.collection(ORGS).document(orgId)

            org.get(Source.SERVER).await().takeIf { it.exists() }?.let { snapshot ->
                rows += RemoteRow(SyncQueue.TABLE_ORGS, orgId, snapshot.data.orEmpty())
            }

            for (table in RESTORED_COLLECTIONS) {
                org.collection(table).get(Source.SERVER).await().documents.forEach { doc ->
                    rows += RemoteRow(table, doc.id, doc.data.orEmpty())
                }
            }
            rows
        }

    override suspend fun fetchMyMembership(orgId: String, personId: String): RemoteRow? =
        withTimeout(TIMEOUT_MILLIS) {
            val id = membershipId(orgId, personId)
            val snapshot = firestore.collection(ORGS).document(orgId)
                .collection(SyncQueue.TABLE_MEMBERSHIPS).document(id)
                .get(Source.SERVER)
                .await()
            if (!snapshot.exists()) null
            else RemoteRow(SyncQueue.TABLE_MEMBERSHIPS, id, snapshot.data.orEmpty())
        }

    override suspend fun fetchChangesSince(orgId: String, since: Long): PullPage =
        withTimeout(ORG_TIMEOUT_MILLIS) {
            val rows = mutableListOf<RemoteRow>()
            val org = firestore.collection(ORGS).document(orgId)
            var high = since

            // Where each collection that filled its page stopped. See [resumeFrom].
            val truncatedAt = mutableListOf<Long>()

            // The organisation row is a document, not a collection, so there is no
            // page to ask for: fetch it and let its own stamp decide. It is one
            // read, and it carries the name, address and UPI id that print on
            // every bill the drivers hand out.
            org.get(Source.SERVER).await().takeIf { it.exists() }?.let { snapshot ->
                val at = snapshot.serverAt()
                if (at > since) {
                    rows += RemoteRow(SyncQueue.TABLE_ORGS, orgId, snapshot.data.orEmpty())
                    high = maxOf(high, at)
                }
            }

            for (table in RESTORED_COLLECTIONS) {
                val page = org.collection(table)
                    .whereGreaterThan(FIELD_SERVER_AT, Timestamp(Date(since)))
                    .orderBy(FIELD_SERVER_AT)
                    .limit(PAGE_LIMIT)
                    .get(Source.SERVER)
                    .await()

                page.documents.forEach { doc ->
                    rows += RemoteRow(table, doc.id, doc.data.orEmpty())
                    high = maxOf(high, doc.serverAt())
                }
                if (page.size().toLong() == PAGE_LIMIT) {
                    truncatedAt += page.documents.last().serverAt()
                }
            }
            PullPage(rows, resumeFrom(since = since, newest = high, truncatedAt = truncatedAt))
        }

    override suspend fun isAccountDisabled(personId: String): Boolean =
        withTimeout(TIMEOUT_MILLIS) {
            val snapshot = firestore.collection(PEOPLE).document(personId).get(Source.SERVER).await()
            snapshot.exists() && snapshot.getBoolean(FIELD_DISABLED) == true
        }

    /**
     * When the server wrote this row.
     *
     * Zero for a document written before the stamp existed. Such a row is invisible
     * to a ranged query anyway, so it can only arrive through a full restore, where
     * the mark is not read from it.
     */
    private fun DocumentSnapshot.serverAt(): Long =
        getTimestamp(FIELD_SERVER_AT)?.toDate()?.time ?: 0L

    private companion object {
        const val ORGS = "orgs"
        const val FIELD_DISABLED = "disabled"

        /**
         * How many rows of one collection a single pass will take.
         *
         * Deliberately not unbounded: the first pass after an upgrade asks from
         * zero, and a busy season's job events would otherwise arrive as one
         * enormous read on a phone with a patchy connection.
         */
        const val PAGE_LIMIT = 300L
        const val PEOPLE = "people"
        const val TIMEOUT_MILLIS = 20_000L

        /** Longer, because this is a whole business's history in one go. */
        const val ORG_TIMEOUT_MILLIS = 120_000L

        /**
         * What comes back, and in this order.
         *
         * Customers and machines before jobs, jobs before their events and money:
         * the local database has foreign keys and an event whose job has not
         * arrived yet is a row with nothing to point at. Invite codes are left out
         * deliberately — they are a way in, not a record of work, and a restored
         * handset has no business re-publishing them.
         */
        val RESTORED_COLLECTIONS = listOf(
            SyncQueue.TABLE_MEMBERSHIPS,
            SyncQueue.TABLE_CUSTOMERS,
            SyncQueue.TABLE_MACHINES,
            SyncQueue.TABLE_RATES,
            SyncQueue.TABLE_JOBS,
            SyncQueue.TABLE_JOB_EVENTS,
            SyncQueue.TABLE_LEDGER,
            SyncQueue.TABLE_FUEL,
            SyncQueue.TABLE_BREAKDOWNS,
            SyncQueue.TABLE_SERVICE,
            SyncQueue.TABLE_TEAM_EVENTS,
            SyncQueue.TABLE_PAYOUTS,
            SyncQueue.TABLE_JOB_COSTS,
        )
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
