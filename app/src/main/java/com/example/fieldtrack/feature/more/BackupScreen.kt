package com.example.fieldtrack.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.SyncQueue
import com.example.fieldtrack.data.sync.SyncStage
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.dayMonthYear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.fieldtrack.feature.timeAgo

/** One kind of thing waiting to go up. */
data class WaitingKind(val table: String, val count: Int)

data class BackupState(
    val stage: SyncStage = SyncStage.Idle,
    val pending: Int = 0,
    val lastSuccessAt: Long? = null,
    val error: String? = null,
    val retryAtMillis: Long? = null,
    val destination: String? = null,
    val waiting: List<WaitingKind> = emptyList(),
    val busy: Boolean = false,
    /** The change holding the queue up, when one is. */
    val blockedSeq: Long? = null,
    val setAside: Int = 0,
) {
    /** Nothing waiting, and a server has actually confirmed having it. */
    val safelyBackedUp: Boolean
        get() = pending == 0 && lastSuccessAt != null && stage == SyncStage.Idle
}

class BackupViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val busy = MutableStateFlow(false)

    val state: StateFlow<BackupState> = combine(
        app.syncEngine.status,
        // Watched rather than taken from the last run, so a job finished in another
        // tab bumps the count here without anybody pressing anything.
        app.db.outboxDao().observePendingByTable(),
        busy,
    ) { status, groups, working ->
        BackupState(
            stage = status.stage,
            pending = groups.sumOf { it.count },
            lastSuccessAt = status.lastSuccessAt,
            error = status.error,
            retryAtMillis = status.retryAtMillis,
            blockedSeq = status.blockedSeq,
            setAside = status.setAside,
            destination = app.syncEngine.destination,
            waiting = groups.map { WaitingKind(it.tableName, it.count) },
            busy = working,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupState())

    /** Lets the queue past the change the server refuses. The user's call. */
    fun setAsideBlocked() = viewModelScope.launch {
        val seq = state.value.blockedSeq ?: return@launch
        busy.value = true
        try {
            app.syncEngine.setAside(seq)
            app.syncEngine.sync(force = true)
        } finally {
            busy.value = false
        }
    }

    /** Forced: the user is watching, so a backoff timer is not an answer. */
    fun backUpNow() = viewModelScope.launch {
        busy.value = true
        try {
            app.syncEngine.sync(force = true)
        } finally {
            busy.value = false
        }
    }
}

/**
 * Where the user's work stands relative to the server.
 *
 * Written to be read by somebody who is worried. The question behind opening this
 * screen is never "what is the queue depth" — it is "have I lost anything", and the
 * answer is on every variant of this screen, including the broken ones.
 */
@Composable
fun BackupScreen(
    state: BackupState,
    onBack: () -> Unit,
    onBackUpNow: () -> Unit,
    onSetAside: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.sync_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            StatusCard(state)

            if (state.waiting.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.sync_whats_waiting))
                FtCard(padding = 0.dp) {
                    state.waiting.forEachIndexed { index, kind ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = labelFor(kind.table),
                            trailing = {
                                Text(
                                    text = kind.count.toString(),
                                    style = FieldTrackTheme.type.label,
                                    color = colors.inkSecondary,
                                )
                            },
                        )
                    }
                }
            }

            // The way past a refusal, offered only when there is one. Deliberately
            // below the server's own words: the decision is only fair if they have
            // read what it said first.
            if (state.stage == SyncStage.Blocked && state.blockedSeq != null) {
                FtButton(
                    text = stringResource(R.string.sync_set_aside),
                    onClick = onSetAside,
                    tone = FtButtonTone.Secondary,
                    enabled = !state.busy,
                    height = 50.dp,
                )
                Text(
                    text = stringResource(R.string.sync_set_aside_note),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }

            if (state.setAside > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.sync_set_aside_count, state.setAside, state.setAside,
                    ),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                )
            }

            // Said on every variant, not just the happy one. Somebody reading a
            // screen that says a change was refused needs to be told, in the same
            // breath, that refusing it did not destroy anything.
            FtCard(background = colors.surfaceMuted, borderColor = colors.line) {
                Text(
                    text = stringResource(R.string.sync_safe_note),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                )
                // The whole point of this screen is that nobody should need it.
                // Saying so is what stops a contractor treating backup as a chore
                // he has to remember, which is how work goes missing when he
                // forgets.
                Text(
                    text = stringResource(R.string.sync_automatic),
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            Box(Modifier.padding(bottom = dimens.space4))
        }

        if (state.stage != SyncStage.NoBackend) {
            Column(Modifier.padding(dimens.screenPadding)) {
                // Always secondary. Sending happens by itself; this is here for
                // somebody who is anxious and wants to watch it go, not because
                // the app is waiting to be told.
                FtButton(
                    text = stringResource(R.string.sync_now),
                    onClick = onBackUpNow,
                    enabled = !state.busy && state.stage != SyncStage.Sending,
                    tone = FtButtonTone.Secondary,
                    height = 54.dp,
                )
            }
        }
    }
}

/** The headline: what is happening, and when anything last reached the server. */
@Composable
private fun StatusCard(state: BackupState) {
    val colors = FieldTrackTheme.colors

    val tint: Color
    val wash: Color
    val title: String
    val body: String

    when (state.stage) {
        SyncStage.Idle -> when {
            state.pending > 0 -> {
                tint = colors.money; wash = colors.moneyWash
                title = stringResource(R.string.sync_waiting)
                body = stringResource(R.string.sync_offline_body)
            }
            // An empty queue that has never been sent anywhere is not a backup,
            // and must not be dressed up as one. This is the fresh-install state.
            state.lastSuccessAt == null -> {
                tint = colors.inkSecondary; wash = colors.surfaceMuted
                title = stringResource(R.string.sync_nothing_waiting)
                body = stringResource(R.string.sync_nothing_waiting_body)
            }
            else -> {
                tint = colors.money; wash = colors.moneyWash
                title = stringResource(R.string.sync_all_safe)
                body = stringResource(R.string.sync_all_safe_body)
            }
        }

        SyncStage.Sending -> {
            tint = colors.primary; wash = colors.primaryWash
            title = stringResource(R.string.sync_sending)
            // Not the all-clear line: this is the middle of an upload, and saying
            // everything is already on the server over the top of a waiting count
            // is the screen contradicting itself.
            body = stringResource(R.string.sync_sending_body)
        }

        SyncStage.Offline -> {
            tint = colors.inkSecondary; wash = colors.surfaceMuted
            title = stringResource(R.string.sync_offline_title)
            body = stringResource(R.string.sync_offline_body)
        }

        SyncStage.Waiting -> {
            tint = colors.inkSecondary; wash = colors.surfaceMuted
            title = stringResource(R.string.sync_waiting_title)
            body = stringResource(R.string.sync_offline_body)
        }

        SyncStage.Blocked -> {
            tint = colors.due; wash = colors.dueWash
            title = stringResource(R.string.sync_blocked_title)
            body = stringResource(R.string.sync_blocked_body)
        }

        SyncStage.NoBackend -> {
            tint = colors.inkSecondary; wash = colors.surfaceMuted
            title = stringResource(R.string.sync_no_backend_title)
            body = stringResource(R.string.sync_no_backend_body)
        }
    }

    FtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FtIconTile(
                icon = if (state.safelyBackedUp) FieldTrackIcons.Check else FieldTrackIcons.Sync,
                size = 44.dp,
                background = wash,
                tint = tint,
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(text = title, style = FieldTrackTheme.type.sectionTitle, color = colors.ink)
                if (state.pending > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.sync_changes_waiting, state.pending, state.pending,
                        ),
                        style = FieldTrackTheme.type.caption,
                        color = tint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        Text(
            text = body,
            style = FieldTrackTheme.type.bodySmall,
            color = colors.inkSecondary,
            modifier = Modifier.padding(top = 11.dp),
        )

        // The server's own words, kept verbatim. A paraphrase would be this app
        // guessing at a failure it does not understand.
        if (state.stage == SyncStage.Blocked && !state.error.isNullOrBlank()) {
            Column(Modifier.padding(top = 11.dp)) {
                Text(
                    text = stringResource(R.string.sync_reason),
                    style = FieldTrackTheme.type.overline,
                    color = colors.inkTertiary,
                )
                Text(
                    text = state.error,
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Column(Modifier.padding(top = 11.dp)) {
            Text(
                text = state.lastSuccessAt?.let {
                    stringResource(R.string.sync_last_backup, timeAgo(it))
                } ?: stringResource(R.string.sync_never),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
            state.retryAtMillis?.takeIf { state.stage == SyncStage.Waiting }?.let {
                Text(
                    text = stringResource(R.string.sync_retry_at, clockTime(it)),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            state.destination?.let {
                Text(
                    text = stringResource(R.string.sync_destination, it),
                    style = FieldTrackTheme.type.caption,
                    color = colors.inkTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * The outbox's table name, said as the thing the user actually did.
 *
 * Every table is named explicitly and the fallback says nothing in particular. An
 * `else` that pointed at a real label meant a table nobody had mapped yet was
 * reported as something else entirely — rates were counted as business details,
 * which is a quietly wrong answer to "what is waiting".
 */
@Composable
private fun labelFor(table: String): String = stringResource(
    when (table) {
        SyncQueue.TABLE_JOBS -> R.string.sync_item_jobs
        SyncQueue.TABLE_JOB_EVENTS -> R.string.sync_item_job_events
        SyncQueue.TABLE_LEDGER -> R.string.sync_item_ledger
        SyncQueue.TABLE_CUSTOMERS -> R.string.sync_item_customers
        SyncQueue.TABLE_MACHINES -> R.string.sync_item_machines
        SyncQueue.TABLE_FUEL -> R.string.sync_item_fuel
        SyncQueue.TABLE_BREAKDOWNS -> R.string.sync_item_breakdowns
        SyncQueue.TABLE_MEMBERSHIPS -> R.string.sync_item_memberships
        SyncQueue.TABLE_RATES -> R.string.sync_item_rates
        SyncQueue.TABLE_ORGS -> R.string.sync_item_orgs
        // Both halves of an invite read as the same thing to the person waiting for
        // it to go up, and two rows labelled "Other changes" is not an answer.
        SyncQueue.TABLE_INVITES, SyncQueue.TABLE_INVITE_CODES -> R.string.sync_item_invites
        else -> R.string.sync_item_other
    }
)
