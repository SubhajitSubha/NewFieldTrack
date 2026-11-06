package com.example.fieldtrack.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.sync.SyncWorker
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * How long ago this phone last heard from the server, and whether that is worth saying.
 *
 * The phone only learns what others did by asking. Nothing tells it when a driver
 * finishes a job, so a screen left open does not change by itself — and a screen that
 * does not change looks exactly like a screen with nothing new on it. Saying how old
 * it is, and offering to ask again, is the honest answer.
 */
data class Heard(
    /** Only for a business with somebody else in it: a one-person business has no one to hear from. */
    val show: Boolean = false,
    /** When the last successful read finished; null if there never has been one. */
    val updatedAt: Long? = null,
    val now: Long = 0,
)

/** How old the last read is, in the words the screen uses. */
sealed interface HeardAge {
    data object Never : HeardAge
    data object JustNow : HeardAge
    data class Minutes(val n: Int) : HeardAge
    data class Hours(val n: Int) : HeardAge

    /** Long enough that what is on screen may well be behind. */
    val stale: Boolean get() = this is Never || (this is Hours && n >= STALE_HOURS)

    companion object {
        const val STALE_HOURS = 6
    }
}

fun heardAge(updatedAt: Long?, now: Long): HeardAge {
    if (updatedAt == null || updatedAt <= 0) return HeardAge.Never
    // A clock corrected since then must not read as the future.
    val minutes = ((now - updatedAt).coerceAtLeast(0) / 60_000).toInt()
    return when {
        minutes < 1 -> HeardAge.JustNow
        minutes < 60 -> HeardAge.Minutes(minutes)
        else -> HeardAge.Hours(minutes / 60)
    }
}

/** Read again every half minute, so "3 min ago" keeps moving on a phone that hears nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
fun FieldFlowApplication.observeHeard(): Flow<Heard> = sessionStore.session.flatMapLatest { s ->
    combine(
        db.orgDao().observeTeam(s.orgId),
        flow {
            while (true) {
                emit(pullWatermarks.lastSuccessAt(s.orgId).takeIf { it > 0 })
                delay(HEARD_EVERY_MILLIS)
            }
        },
    ) { team, at -> Heard(show = team.size > 1, updatedAt = at, now = System.currentTimeMillis()) }
}

private const val HEARD_EVERY_MILLIS = 30_000L

/** How often a screen that is open asks again by itself. */
private const val ASK_EVERY_MILLIS = 2 * 60_000L

/** How long "Updating…" is allowed to stand if nothing comes back. */
private const val UPDATING_MILLIS = 20_000L

/**
 * "Updated 3 min ago", tapped to ask again — and, while this is on screen, asked again
 * every two minutes without anybody doing anything.
 */
@Composable
fun HeardLine(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as FieldFlowApplication
    val heard by remember(app) { app.observeHeard() }.collectAsStateWithLifecycle(Heard())
    if (!heard.show) return

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(ASK_EVERY_MILLIS)
                SyncWorker.runNow(app, force = true)
            }
        }
    }

    var askedAt by remember { mutableLongStateOf(0L) }
    val updating = askedAt > 0 && heard.now - askedAt < UPDATING_MILLIS &&
        (heard.updatedAt ?: 0L) < askedAt
    val age = heardAge(heard.updatedAt, heard.now)
    val colors = FieldTrackTheme.colors

    Row(
        modifier
            .clickable(role = Role.Button) {
                askedAt = System.currentTimeMillis()
                SyncWorker.runNow(app, force = true)
            }
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = FieldTrackIcons.Sync,
            contentDescription = null,
            tint = if (age.stale && !updating) colors.moneyInk else colors.inkTertiary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = when {
                updating -> stringResource(R.string.home_heard_updating)
                age is HeardAge.Never -> stringResource(R.string.home_heard_never)
                age is HeardAge.JustNow -> stringResource(R.string.home_heard_now)
                age is HeardAge.Minutes -> pluralStringResource(R.plurals.home_heard_minutes, age.n, age.n)
                age is HeardAge.Hours -> pluralStringResource(R.plurals.home_heard_hours, age.n, age.n)
                else -> ""
            },
            style = FieldTrackTheme.type.caption,
            color = if (age.stale && !updating) colors.moneyInk else colors.inkTertiary,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}
