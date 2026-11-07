package com.example.fieldtrack.data.billing.store

import android.content.Context
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.sync.Firestore
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Hands a purchase to our server to check.
 *
 * The owner's phone writes a small claim under the business; our server asks the store whether
 * it is real, whose it is, and when it runs out, and writes the answer for every phone to read.
 * Nothing on any phone decides whether a business has paid, so nothing on a phone can say it has.
 *
 * Claimed again about once a day while a purchase is active, which is how a renewal reaches the
 * server without anything else listening for it. Best effort: no signal today means tomorrow.
 */
class PurchaseClaims(private val app: FieldFlowApplication) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Claims every active purchase this account has, when it is the owner's phone and the claim is due. */
    suspend fun claimActive() {
        val session = app.sessionStore.current() ?: return
        // Only whoever may change the business can pay for it.
        if (Capability.EditBusiness !in session.can) return
        for (purchase in app.storeBilling.active()) {
            if (!due(purchase.token)) continue
            if (send(session.orgId, purchase)) {
                prefs.edit().putLong(KEY_PREFIX + id(purchase.token), System.currentTimeMillis()).apply()
                app.storeBilling.acknowledge(purchase)
            }
        }
    }

    /** One purchase, now: right after it was made, or when somebody asks to restore it. */
    suspend fun claim(purchase: StorePurchase): Boolean {
        val session = app.sessionStore.current() ?: return false
        val sent = send(session.orgId, purchase)
        if (sent) {
            prefs.edit().putLong(KEY_PREFIX + id(purchase.token), System.currentTimeMillis()).apply()
            app.storeBilling.acknowledge(purchase)
        }
        return sent
    }

    private fun due(token: String) =
        System.currentTimeMillis() - prefs.getLong(KEY_PREFIX + id(token), 0L) >= DAY

    private suspend fun send(orgId: String, purchase: StorePurchase): Boolean {
        if (FirebaseAuth.getInstance().currentUser == null) return false
        return runCatching {
            withTimeout(20_000) {
                Firestore.instance.collection("orgs").document(orgId)
                    .collection(SyncQueue.TABLE_PURCHASE_CLAIMS).document(id(purchase.token))
                    .set(mapOf("productId" to purchase.productId, "token" to purchase.token, "at" to System.currentTimeMillis()))
                    .await()
            }
        }.isSuccess
    }

    companion object {
        const val PREFS = "fieldtrack.claims"
        private const val KEY_PREFIX = "claimed-"
        private const val DAY = 24L * 60 * 60 * 1000

        /** A short name for a claim, from its token, so claiming the same purchase twice writes the same document. */
        fun id(token: String): String =
            MessageDigest.getInstance("SHA-1").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    }
}
