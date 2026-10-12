package com.example.fieldtrack.data.sync

import com.example.fieldtrack.data.db.Clock
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.db.entity.InviteCodeDoc
import com.example.fieldtrack.data.db.MemberRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads a single invite code from Firestore.
 *
 * One document, fetched by name. Not a query: a query over codes would be a way to
 * enumerate every open invite in the system, and the security rules refuse to list
 * this collection for exactly that reason. Holding the code is the authorisation,
 * so the code has to be the address.
 */
class FirestoreInviteLookup(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val clock: Clock = Clock.System,
) : InviteLookup {

    override suspend fun find(code: String): InviteLookupResult = try {
        withTimeout(TIMEOUT_MILLIS) {
            // Rules require a signed-in caller even to read a code, so that a lost
            // code is not readable by the whole internet.
            if (auth.currentUser == null) auth.signInAnonymously().await()

            val snapshot = firestore.collection(SyncQueue.TABLE_INVITE_CODES)
                .document(code)
                .get()
                .await()

            if (!snapshot.exists()) {
                InviteLookupResult.NotFound
            } else {
                val invite = snapshot.asInviteCode(code)
                val stale = invite.redeemedAt != null || invite.expiresAt <= clock.now()
                if (stale) InviteLookupResult.NoLongerValid(invite)
                else InviteLookupResult.Found(invite)
            }
        }
    } catch (timeout: TimeoutCancellationException) {
        InviteLookupResult.Unreachable(NO_ANSWER)
    } catch (failure: Exception) {
        InviteLookupResult.Unreachable(
            failure.message?.takeIf { it.isNotBlank() } ?: failure::class.java.simpleName
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.asInviteCode(code: String) =
        InviteCodeDoc(
            code = code,
            orgId = getString("orgId").orEmpty(),
            orgName = getString("orgName").orEmpty(),
            inviteId = getString("inviteId").orEmpty(),
            role = runCatching { MemberRole.valueOf(getString("role").orEmpty()) }
                .getOrDefault(MemberRole.Operator),
            canSeeMoney = getBoolean("canSeeMoney") ?: false,
            canAddCustomers = getBoolean("canAddCustomers") ?: false,
            canCloseBills = getBoolean("canCloseBills") ?: false,
            expiresAt = getLong("expiresAt") ?: 0L,
            redeemedAt = getLong("redeemedAt"),
        )

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val NO_ANSWER = "No answer from the server"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
