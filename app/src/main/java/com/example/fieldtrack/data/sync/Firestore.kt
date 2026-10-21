package com.example.fieldtrack.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.memoryCacheSettings

/**
 * The one configured Firestore instance this app uses.
 *
 * Firestore refuses to have its settings changed once anything has touched it, and
 * the instance is a process-wide singleton. So the settings cannot live in the
 * constructor of whichever class happens to want them — with three users (the
 * outgoing queue, the invite lookup and the restore) whoever is built first wins
 * and the next one crashes, which is precisely what happened the moment a second
 * reader existed.
 *
 * Configured exactly once here, before the instance is handed to anybody.
 *
 * **The local cache stays off**, and that is the setting worth protecting. Left on,
 * a write succeeds the moment it reaches the cache, whether or not a server ever
 * hears — and this app would take that as permission to delete the row from its
 * outbox. The queue would empty itself on a phone with no signal and the work would
 * exist nowhere but a cache a reinstall clears. Reads are likewise answered by the
 * server rather than from a stale copy, which is what a restore and an invite
 * lookup both need.
 */
object Firestore {
    val instance: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(memoryCacheSettings {})
                .build()
        }
    }
}
