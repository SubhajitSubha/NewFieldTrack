package com.example.fieldtrack.notify

import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobStatus
import com.example.fieldtrack.data.db.entity.JobEntity
import com.example.fieldtrack.data.session.Startup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Keeps the notification shade in step with this person's open job.
 *
 * Watches who is signed in as well as the job, so signing out, being removed
 * from the business, switching to another one, or handing the job to somebody
 * else all take the clock out of the shade — it is this person's clock on this
 * business, and nobody else's.
 */
class WorkClock(
    private val app: FieldFlowApplication,
    private val notifier: WorkClockNotifier,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun watch(scope: CoroutineScope) = scope.launch {
        app.sessionStore.startup.flatMapLatest { startup ->
            val session = (startup as? Startup.Ready)?.session
                ?: return@flatMapLatest flowOf<Pair<JobEntity?, String>?>(null)
            combine(
                app.workRepository.observeLiveFor(session.orgId, session.personId),
                app.directoryRepository.observeCustomers(session.orgId),
            ) { job, customers ->
                job to customers.firstOrNull { it.id == job?.customerId }?.name.orEmpty()
            }
        }.collect { open ->
            runCatching {
                if (open == null) notifier.clearAll() else notifier.showClock(open.first, open.second)
            }
        }
    }

    /** Reads the job once and redraws. For a notification press, or permission just granted. */
    suspend fun refresh() {
        val (job, name) = current() ?: return notifier.clearAll()
        notifier.showClock(job, name)
    }

    /**
     * The evening question, if a clock is still going.
     *
     * Not for a stretch only just begun — somebody who started threshing at
     * seven is not to be asked at eight whether they forgot to stop.
     */
    suspend fun remindIfStillRunning(now: Long = System.currentTimeMillis()) {
        val (job, name) = current() ?: return
        val since = job?.runningSince
        if (job?.status == JobStatus.Running && since != null && now - since >= MIN_STRETCH_MILLIS) {
            notifier.remindIfRunning(job, name, now)
        }
        // Whatever the reminder did, the clock itself is redrawn from the facts,
        // which also clears a stale one left by a process that died.
        notifier.showClock(job, name)
    }

    private suspend fun current(): Pair<JobEntity?, String>? {
        val session = runCatching { app.sessionStore.load() }.getOrNull() ?: return null
        val job = app.workRepository.observeLiveFor(session.orgId, session.personId).first()
        val name = job?.let { app.directoryRepository.observeCustomers(session.orgId).first() }
            ?.firstOrNull { it.id == job.customerId }?.name.orEmpty()
        return job to name
    }

    private companion object {
        const val MIN_STRETCH_MILLIS = 2L * 60 * 60 * 1000
    }
}
