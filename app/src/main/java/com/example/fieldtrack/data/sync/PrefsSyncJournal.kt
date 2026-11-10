package com.example.fieldtrack.data.sync

import android.content.Context

/** [SyncJournal] kept in preferences, so "last backup" survives the app closing. */
class PrefsSyncJournal(context: Context) : SyncJournal {

    private val prefs = context.getSharedPreferences("fieldtrack.sync", Context.MODE_PRIVATE)

    override var lastSuccessAt: Long?
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_LAST_SUCCESS) else putLong(KEY_LAST_SUCCESS, value)
            }.apply()
        }

    private companion object {
        const val KEY_LAST_SUCCESS = "lastSuccessAt"
    }
}
