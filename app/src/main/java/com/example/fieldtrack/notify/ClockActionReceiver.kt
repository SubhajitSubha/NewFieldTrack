package com.example.fieldtrack.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.fieldtrack.FieldFlowApplication

/**
 * Pause and Resume, pressed in the notification shade.
 *
 * The same calls the job screen makes, so the job row, its timeline and the
 * queue to the server are written exactly as if the button had been pressed in
 * the app. The notification is redrawn by whoever watches the job — not here —
 * so the shade and the app cannot disagree about which state it is in.
 */
class ClockActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? FieldFlowApplication ?: return
        val orgId = intent.getStringExtra(EXTRA_ORG) ?: return
        val jobId = intent.getStringExtra(EXTRA_JOB) ?: return
        val pending = goAsync()
        app.runInBackground {
            try {
                // A press racing a change from another phone — the job already
                // finished, or handed over — is dropped: the job's real state is
                // what the notification redraws to.
                runCatching {
                    when (intent.action) {
                        PAUSE -> app.workRepository.pause(orgId, jobId)
                        RESUME -> app.workRepository.resume(orgId, jobId)
                    }
                }
                app.workClock.refresh()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val PAUSE = "com.example.fieldtrack.clock.PAUSE"
        const val RESUME = "com.example.fieldtrack.clock.RESUME"
        const val EXTRA_ORG = "orgId"
        const val EXTRA_JOB = "jobId"
    }
}
