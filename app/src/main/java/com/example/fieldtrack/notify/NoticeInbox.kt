package com.example.fieldtrack.notify

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** One thing the phone was told, kept so it is still there after the notification is swiped away. */
data class Notice(
    val id: String,
    val orgId: String,
    /** The event's own key, as the server named it. */
    val event: String,
    /** What it was about (a machine, a person), when the server said. */
    val what: String?,
    val at: Long,
    val read: Boolean,
)

/**
 * A short list of what this phone was told, newest first.
 *
 * A notification on a lock screen is gone the moment it is dismissed, and a phone that was
 * off or out of signal gets its news late and all at once. This keeps the last few so
 * nothing needs hunting for. It lives on the phone only: it is a memory of what arrived,
 * not business data, so it is never sent anywhere and is emptied when the person signs out.
 */
class NoticeInbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Notice>> = _all.asStateFlow()

    fun forOrg(orgId: String): Flow<List<Notice>> = all.map { list -> list.filter { it.orgId == orgId } }

    fun unread(orgId: String): Flow<Int> = all.map { list -> list.count { it.orgId == orgId && !it.read } }

    fun add(orgId: String, event: String, what: String?, at: Long) {
        val notice = Notice("$at-$event", orgId, event, what, at, read = false)
        save((listOf(notice) + _all.value.filter { it.id != notice.id }).take(CAP))
    }

    fun markAllRead(orgId: String) {
        if (_all.value.none { it.orgId == orgId && !it.read }) return
        save(_all.value.map { if (it.orgId == orgId) it.copy(read = true) else it })
    }

    fun clear() = save(emptyList())

    private fun save(list: List<Notice>) {
        _all.value = list
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id).put("org", it.orgId).put("event", it.event)
                    .put("what", it.what ?: JSONObject.NULL).put("at", it.at).put("read", it.read)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<Notice> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Notice(
                id = o.getString("id"), orgId = o.getString("org"), event = o.getString("event"),
                what = o.optString("what").takeIf { o.has("what") && !o.isNull("what") && it.isNotBlank() },
                at = o.getLong("at"), read = o.optBoolean("read"),
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        const val PREFS = "fieldtrack.notices"
        private const val KEY = "list"
        private const val CAP = 60
    }
}
