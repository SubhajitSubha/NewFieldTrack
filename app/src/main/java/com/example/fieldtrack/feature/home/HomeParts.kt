package com.example.fieldtrack.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtFitText
import com.example.fieldtrack.data.repository.ClockCheck
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.format.duration
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.clockTime
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.daysAgo
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.measure
import com.example.fieldtrack.feature.timeAgo
import com.example.fieldtrack.feature.workLabel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * The parts every home is built from.
 *
 * One look across all three: paper ground, white cards edged with a hairline,
 * and exactly one deep card per screen for the thing that screen is about. A
 * bank statement, not a poster — figures in tabular digits, labels small and
 * spaced, colour kept for meaning (green running, amber waiting, red stopped)
 * so that when colour appears it is saying something.
 */

/** Width at which a home stops being one column and becomes two. */
private val TWO_PANE = 600.dp

/** Which column a block sits in once there is room for two. */
enum class Pane { Main, Side }

class HomeBlock(val pane: Pane, val content: @Composable ColumnScope.() -> Unit)

/**
 * A scrolling home that is one column on a phone and two on a tablet.
 *
 * On a phone, blocks stack in the order given — that order is the screen's
 * priority. With room for two, the main pane keeps what is happening now and
 * the side pane takes what is worth knowing; nothing moves to a different
 * screen, and nothing is dropped.
 */
@Composable
fun HomeFrame(
    header: @Composable () -> Unit,
    blocks: List<HomeBlock>,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        val wide = maxWidth >= TWO_PANE
        val gutter = if (wide) 28.dp else dimens.screenPadding
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 1120.dp)
                    .fillMaxWidth()
                    .padding(horizontal = gutter)
            ) {
                header()
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(
                            Modifier.weight(1.4f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            blocks.filter { it.pane == Pane.Main }.forEach { it.content(this) }
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            blocks.filter { it.pane == Pane.Side }.forEach { it.content(this) }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        blocks.forEach { it.content(this) }
                    }
                }
                Spacer(Modifier.height(dimens.space8))
            }
        }
    }
}

/**
 * Date, name, and whether everything has reached the server.
 *
 * The date leads because a contractor's first question on opening the app is
 * which day's figures these are. The sync mark is on every home because an
 * offline-first app owes people an honest answer to "is my work safe".
 */
@Composable
fun HomeHeader(
    title: String,
    subtitle: String?,
    pendingUploads: Int,
    onOpenSync: () -> Unit,
    /** The server refused a change; the mark turns red and says so. */
    stuck: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = longDate(now).uppercase(),
                style = FieldTrackTheme.type.overline,
                color = colors.inkTertiary,
            )
            Text(
                text = title,
                style = FieldTrackTheme.type.screenTitle,
                color = colors.ink,
                // Two lines, so a long business name at bigger text wraps rather
                // than losing its last word to an ellipsis.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { heading() },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = FieldTrackTheme.type.bodySmall,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            HeardLine(Modifier.padding(top = 2.dp))
        }
        SyncMark(pendingUploads, stuck, onOpenSync, Modifier.padding(start = 12.dp, top = 2.dp))
    }
}

/**
 * Three states, never confused with each other: everything sent, some waiting for
 * a signal (amber — it will go on its own), and stuck (red — it will not, until
 * somebody decides). Amber for stuck would tell a driver to wait for signal that
 * is never going to help.
 */
@Composable
private fun SyncMark(pending: Int, stuck: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    val waiting = pending > 0
    val (wash, ink) = when {
        stuck -> colors.dueWash to colors.dueInk
        waiting -> colors.moneyWash to colors.moneyInk
        else -> colors.surface to colors.inkSecondary
    }
    Row(
        modifier
            .clip(FieldTrackTheme.shapes.chip)
            .background(wash)
            .border(
                FieldTrackTheme.dimens.hairline,
                if (stuck || waiting) wash else colors.line,
                FieldTrackTheme.shapes.chip,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = when {
                stuck -> FieldTrackIcons.Offline
                waiting -> FieldTrackIcons.Sync
                else -> FieldTrackIcons.Check
            },
            contentDescription = null,
            tint = if (stuck || waiting) ink else colors.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = when {
                stuck -> stringResource(R.string.home_sync_stuck)
                waiting -> pluralStringResource(R.plurals.home_sync_waiting, pending, pending)
                else -> stringResource(R.string.home_sync_done)
            },
            style = FieldTrackTheme.type.caption,
            color = ink,
        )
    }
}

/** "Monday, 26 October". */
fun longDate(millis: Long): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(millis))

/** A section heading with an optional way to see the rest. */
@Composable
fun HomeSection(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = FieldTrackTheme.type.sectionTitle,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = FieldTrackTheme.type.label,
                color = colors.primary,
                modifier = Modifier
                    .clip(FieldTrackTheme.shapes.small)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Ticks once a second while [active], so a running clock on a home moves.
 *
 * Only redraws: the seconds themselves are worked out from the job's own
 * `runningSince`, so a clock read after the app was killed is still right.
 */
@Composable
fun rememberNow(active: Boolean, periodMillis: Long = 1_000): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, periodMillis) {
        now.longValue = System.currentTimeMillis()
        while (active) {
            delay(periodMillis)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

/** Often enough to catch a clock crossing midnight; rare enough to cost nothing. */
const val SLOW_TICK_MILLIS = 60_000L

/**
 * A running clock as text, and the only thing that redraws as it ticks.
 *
 * The tick is read in here rather than by the card around it. Read higher up,
 * every second redrew the whole card — names, buttons, warning — to change two
 * digits, which on a cheap phone left on a home screen all day is battery spent
 * on nothing. Without seconds it ticks every fifteen: the minute it shows is at
 * most that late, and nobody reads a crew list to the second.
 */
@Composable
fun TickingDuration(
    elapsedSeconds: Long,
    runningSince: Long?,
    paused: Boolean,
    withSeconds: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow(
        active = !paused && runningSince != null,
        periodMillis = if (withSeconds) 1_000 else 15_000,
    )
    Text(
        text = duration(liveSeconds(elapsedSeconds, runningSince, now), withSeconds = withSeconds),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** The job somebody has open, as a home shows it. */
data class OpenJob(
    val jobId: String,
    val customerName: String,
    val workType: String,
    val machineName: String?,
    val machineKind: MachineKind,
    val amountDone: Double,
    val unit: MeasureUnit,
    val elapsedSeconds: Long,
    val runningSince: Long?,
    val paused: Boolean,
    /** This person already said the long stretch was real work; do not flag it. */
    val clockConfirmed: Boolean = false,
)

/**
 * The open job, on the screen's one deep card.
 *
 * Big clock, whose field, and the two things a person reaches for without
 * opening it: stop the clock for lunch, or go into the job. Finishing lives
 * inside, where the acres get counted — finishing from here would bill a job
 * whose measurement nobody had looked at.
 */
@Composable
fun OpenJobCard(
    job: OpenJob,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    dark: Boolean,
) {
    val colors = FieldTrackTheme.colors
    // Only for the "left on" warning, which changes at most once a night.
    val now by rememberNow(active = !job.paused, periodMillis = SLOW_TICK_MILLIS)
    val ink = if (dark) colors.onDark else colors.ink
    val muted = if (dark) colors.onDarkMuted else colors.inkSecondary

    val body: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(paused = job.paused, dark = dark)
            Text(
                text = stringResource(
                    if (job.paused) R.string.home_job_paused else R.string.home_job_running
                ).uppercase(),
                style = FieldTrackTheme.type.overline,
                color = if (dark) colors.onDarkMuted else colors.inkTertiary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            )
            job.machineName?.let {
                Text(
                    text = it,
                    style = FieldTrackTheme.type.caption,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TickingDuration(
            elapsedSeconds = job.elapsedSeconds,
            runningSince = job.runningSince,
            paused = job.paused,
            withSeconds = true,
            style = FieldTrackTheme.type.moneyHero,
            color = if (job.paused) muted else ink,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = job.customerName,
            style = FieldTrackTheme.type.rowTitle,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = workAndAmount(job.workType, job.amountDone, job.unit),
            style = FieldTrackTheme.type.bodySmall,
            color = muted,
            modifier = Modifier.padding(top = 2.dp),
        )
        // Said on the card itself, not only inside the job: this is the screen
        // somebody opens the next morning, and the moment to catch a night on the
        // clock is before anyone presses Finish on it.
        if (!job.paused && !job.clockConfirmed &&
            ClockCheck.looksForgotten(job.runningSince, now)
        ) {
            ClockWarning(since = job.runningSince ?: now, now = now, dark = dark)
        }
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroButton(
                text = stringResource(if (job.paused) R.string.home_job_resume else R.string.home_job_pause),
                icon = if (job.paused) FieldTrackIcons.Play else FieldTrackIcons.Pause,
                filled = false,
                dark = dark,
                onClick = if (job.paused) onResume else onPause,
                modifier = Modifier.weight(1f),
            )
            HeroButton(
                text = stringResource(R.string.home_job_open),
                icon = FieldTrackIcons.Forward,
                filled = true,
                dark = dark,
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (dark) {
        FtEmphasisCard(onClick = onOpen, content = body)
    } else {
        FtCard(onClick = onOpen, borderColor = colors.primary, content = body)
    }
}

/** A clock that has run on: amber on a light card, a lighter amber on the deep one. */
@Composable
private fun ClockWarning(since: Long, now: Long, dark: Boolean) {
    val colors = FieldTrackTheme.colors
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(FieldTrackTheme.shapes.small)
            .background(if (dark) colors.onDarkFaint else colors.moneyWash)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            FieldTrackIcons.Clock,
            contentDescription = null,
            tint = if (dark) colors.moneyOnDark else colors.moneyInk,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.home_clock_left_on, sinceMoment(since, now)),
            style = FieldTrackTheme.type.bodySmall,
            color = if (dark) colors.onDark else colors.moneyInk,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** "8:02 AM", "8:02 AM yesterday", or "8:02 AM, 31 Oct". */
@Composable
fun sinceMoment(millis: Long, now: Long): String {
    val today = ClockCheck.midnightOf(now)
    return when (ClockCheck.midnightOf(millis)) {
        today -> clockTime(millis)
        ClockCheck.midnightOf(today - 1) -> stringResource(R.string.clock_moment_yesterday, clockTime(millis))
        else -> stringResource(R.string.event_clock_day_time, dayMonth(millis), clockTime(millis))
    }
}

/** "Rotavator · 6 acre", or only the work while nothing is counted yet. */
@Composable
fun workAndAmount(workType: String, amount: Double, unit: MeasureUnit): String {
    val work = workLabel(workType)
    if (amount <= 0.0) return work
    return stringResource(R.string.home_work_and_amount, work, measure(amount, unit))
}

@Composable
private fun LiveDot(paused: Boolean, dark: Boolean) {
    val colors = FieldTrackTheme.colors
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                when {
                    paused -> colors.money
                    dark -> colors.liveDot
                    else -> colors.primary
                }
            )
    )
}

/**
 * A button that sits on either ground. Filled is the way forward; outlined is
 * the thing you might also do.
 */
@Composable
fun HeroButton(
    text: String,
    icon: ImageVector?,
    filled: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
) {
    val colors = FieldTrackTheme.colors
    // White with the deep card's own colour on it, in both themes: surface and
    // ink would turn the button dark-on-dark in the dark theme.
    val background = when {
        filled && dark -> colors.onDark
        filled -> colors.primary
        else -> Color.Transparent
    }
    val content = when {
        filled && dark -> colors.deep
        filled -> colors.onPrimary
        dark -> colors.onDark
        else -> colors.ink
    }
    val edge = when {
        filled -> Color.Transparent
        dark -> colors.onDarkFaint
        else -> colors.line
    }
    Row(
        modifier
            .height(height)
            .clip(FieldTrackTheme.shapes.medium)
            .background(background)
            .border(1.5.dp, edge, FieldTrackTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = FieldTrackTheme.type.button, color = content, maxLines = 1)
    }
}

/** One figure in a [StatStrip]. */
data class Stat(val label: String, val value: String, val note: String? = null)

/**
 * Two to four figures in one card, divided by hairlines.
 *
 * One card rather than a tile each: the figures are read together ("3 jobs, 5
 * hours, 12 acres") and tiles would make them look like separate claims.
 */
@Composable
fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    FtCard(modifier, padding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(FieldTrackTheme.dimens.hairline)
                            .fillMaxHeight()
                            .padding(vertical = 14.dp)
                            .background(colors.line)
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stat.label.uppercase(),
                        style = FieldTrackTheme.type.overline,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FtFitText(
                        text = stat.value,
                        style = FieldTrackTheme.type.moneyMedium,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    stat.note?.let {
                        Text(
                            text = it,
                            style = FieldTrackTheme.type.caption,
                            color = colors.inkTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The last seven days as bars, today on the right.
 *
 * Real figures only: a day with nothing recorded is a short stub, not a guess.
 * Day letters under each bar because "the tall one was Thursday" is how
 * anybody reads this.
 */
@Composable
fun SevenDays(
    values: List<Double>,
    dark: Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 46.dp,
    now: Long = System.currentTimeMillis(),
) {
    val colors = FieldTrackTheme.colors
    val peak = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val letters = remember(now) { lastSevenDayLetters(now) }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            val today = index == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val fraction = (value / peak).coerceIn(0.0, 1.0).toFloat()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 40.dp)
                        .height(4.dp + barHeight * fraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            // Colour only where there was work. An empty today drawn
                            // in the bright colour reads as "something happened".
                            when {
                                value <= 0 -> if (dark) colors.onDarkFaint else colors.line
                                dark && today -> colors.liveDot
                                dark -> colors.liveDot.copy(alpha = 0.45f)
                                today -> colors.primary
                                else -> colors.primary.copy(alpha = 0.45f)
                            }
                        )
                )
                Text(
                    text = stringResource(letters[index]),
                    style = FieldTrackTheme.type.caption,
                    color = when {
                        dark && today -> colors.onDark
                        dark -> colors.onDarkMuted
                        today -> colors.ink
                        else -> colors.inkTertiary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun lastSevenDayLetters(now: Long): List<Int> {
    val byWeekday = mapOf(
        Calendar.MONDAY to R.string.day_mon,
        Calendar.TUESDAY to R.string.day_tue,
        Calendar.WEDNESDAY to R.string.day_wed,
        Calendar.THURSDAY to R.string.day_thu,
        Calendar.FRIDAY to R.string.day_fri,
        Calendar.SATURDAY to R.string.day_sat,
        Calendar.SUNDAY to R.string.day_sun,
    )
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -6)
    }
    return List(7) {
        val letter = byWeekday.getValue(calendar.get(Calendar.DAY_OF_WEEK))
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        letter
    }
}

/** Where an alert row leads. */
interface AlertActions {
    /** Straight into the running job, where its clock can be put right. */
    fun liveJob(jobId: String)
    fun machine(machineId: String)
    /** The machine's diesel log, for a machine drinking more than it did. */
    fun fuel(machineId: String)
    fun customer(customerId: String)
    fun customers()
    fun moneyOut()
    fun sync()
}

/**
 * What needs somebody, most urgent first, three at a time.
 *
 * Three because a home that lists eleven problems has stopped being a home;
 * the rest are one tap away and the count says how many. Before the data has
 * loaded this draws nothing, rather than an "all clear" that might be a lie.
 */
@Composable
fun AttentionCard(
    alerts: List<HomeAlert>,
    loaded: Boolean,
    actions: AlertActions,
) {
    if (!loaded) return
    val colors = FieldTrackTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sorted = alerts.sortedBy { it.rank }
    val shown = if (expanded) sorted else sorted.take(3)

    HomeSection(stringResource(R.string.home_attention_title))
    if (sorted.isEmpty()) {
        FtCard(background = colors.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FtIconTile(
                    icon = FieldTrackIcons.Check,
                    size = 40.dp,
                    background = colors.primaryWash,
                    tint = colors.primaryPressed,
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        text = stringResource(R.string.home_attention_clear),
                        style = FieldTrackTheme.type.rowTitle,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.home_attention_clear_note),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        return
    }

    FtCard(padding = 0.dp) {
        shown.forEachIndexed { index, alert ->
            if (index > 0) FtRowDivider()
            AlertRow(alert, actions)
        }
        if (sorted.size > 3) {
            FtRowDivider()
            Text(
                text = if (expanded) {
                    stringResource(R.string.home_attention_less)
                } else {
                    pluralStringResource(R.plurals.home_attention_more, sorted.size - 3, sorted.size - 3)
                },
                style = FieldTrackTheme.type.label,
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

private enum class Tone { Stop, Wait, Calm }

@Composable
private fun AlertRow(alert: HomeAlert, actions: AlertActions) {
    val colors = FieldTrackTheme.colors
    val tone: Tone
    val icon: ImageVector
    val title: String
    val note: String
    val onClick: () -> Unit

    when (alert) {
        is HomeAlert.Stuck -> {
            tone = Tone.Stop
            icon = FieldTrackIcons.Offline
            title = stringResource(R.string.home_alert_stuck)
            note = pluralStringResource(R.plurals.home_alert_stuck_note, alert.waiting, alert.waiting)
            onClick = actions::sync
        }
        is HomeAlert.ClockLeftOn -> {
            tone = Tone.Wait
            icon = FieldTrackIcons.Clock
            title = stringResource(
                R.string.home_alert_clock, alert.personName.ifBlank { stringResource(R.string.home_someone) },
            )
            note = stringResource(
                R.string.home_alert_clock_note,
                alert.customerName,
                sinceMoment(alert.sinceMillis, System.currentTimeMillis()),
            )
            onClick = { actions.liveJob(alert.jobId) }
        }
        is HomeAlert.MachineDown -> {
            tone = Tone.Stop
            icon = FieldTrackIcons.Breakdown
            title = stringResource(R.string.home_alert_down, alert.machineName)
            // Hours while it is fresh, days once it has stood a while: "down for
            // 15 days" is the fact; a date makes the reader do the subtraction.
            note = if (System.currentTimeMillis() - alert.sinceMillis < 86_400_000L) {
                stringResource(R.string.home_alert_down_note, timeAgo(alert.sinceMillis))
            } else {
                stringResource(R.string.home_alert_down_days, daysAgo(alert.sinceMillis))
            }
            onClick = { actions.machine(alert.machineId) }
        }
        is HomeAlert.ServiceOverdue -> {
            tone = Tone.Stop
            icon = FieldTrackIcons.Service
            title = stringResource(R.string.home_alert_service_over, alert.machineName)
            note = stringResource(R.string.home_alert_service_over_note, quantity(alert.hoursOver))
            onClick = { actions.machine(alert.machineId) }
        }
        is HomeAlert.FuelHigh -> {
            tone = Tone.Wait
            icon = FieldTrackIcons.Fuel
            title = stringResource(R.string.home_alert_fuel, alert.machineName)
            note = stringResource(
                R.string.home_alert_fuel_note,
                quantity(alert.recentLitresPerHour),
                quantity(alert.beforeLitresPerHour),
                alert.percent,
            )
            onClick = { actions.fuel(alert.machineId) }
        }
        is HomeAlert.Overdue -> {
            tone = Tone.Wait
            icon = FieldTrackIcons.money()
            if (alert.customerId != null) {
                title = stringResource(
                    R.string.home_alert_owes, alert.customerName.orEmpty(), money(alert.amountMinor),
                )
                note = stringResource(R.string.home_alert_owes_note, daysAgo(alert.sinceMillis))
                onClick = { actions.customer(alert.customerId) }
            } else {
                title = pluralStringResource(
                    R.plurals.home_alert_late, alert.customers, alert.customers,
                )
                note = stringResource(
                    R.string.home_alert_late_note, money(alert.amountMinor), OVERDUE_DAYS,
                )
                onClick = actions::customers
            }
        }
        is HomeAlert.Unpriced -> {
            tone = Tone.Wait
            icon = FieldTrackIcons.Team
            title = pluralStringResource(R.plurals.home_alert_unpriced, alert.count, alert.count)
            note = stringResource(R.string.home_alert_unpriced_note)
            onClick = actions::moneyOut
        }
        is HomeAlert.ServiceSoon -> {
            tone = Tone.Calm
            icon = FieldTrackIcons.Service
            title = stringResource(R.string.home_alert_service_soon, alert.machineName)
            note = stringResource(R.string.machines_service_due, quantity(alert.hoursLeft))
            onClick = { actions.machine(alert.machineId) }
        }
        is HomeAlert.Waiting -> {
            tone = Tone.Calm
            icon = FieldTrackIcons.Sync
            title = stringResource(R.string.driver_waiting_title)
            note = pluralStringResource(R.plurals.driver_waiting_count, alert.count, alert.count)
            onClick = actions::sync
        }
    }

    val (wash, ink) = when (tone) {
        Tone.Stop -> colors.dueWash to colors.dueInk
        Tone.Wait -> colors.moneyWash to colors.moneyInk
        Tone.Calm -> colors.surfaceMuted to colors.inkSecondary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FtIconTile(icon = icon, size = 40.dp, background = wash, tint = ink)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = FieldTrackTheme.type.rowTitle,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = note,
                style = FieldTrackTheme.type.caption,
                color = if (tone == Tone.Stop) colors.dueInk else colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            FieldTrackIcons.Forward,
            contentDescription = null,
            tint = colors.inkTertiary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(18.dp),
        )
    }
}

/** A tappable figure on a white card: label, amount, one line of context. */
@Composable
fun FigureCard(
    label: String,
    value: String,
    note: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    valueColor: Color = FieldTrackTheme.colors.ink,
) {
    val colors = FieldTrackTheme.colors
    FtCard(modifier, onClick = onClick) {
        Text(
            text = label.uppercase(),
            style = FieldTrackTheme.type.overline,
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FtFitText(
            text = value,
            style = FieldTrackTheme.type.moneyMedium,
            color = valueColor,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (note != null) {
            Text(
                text = note,
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** An icon tile for a machine, in the home's neutral wash. */
@Composable
fun MachineTile(kind: MachineKind, size: Dp = 40.dp) = FtIconTile(
    icon = iconFor(kind),
    size = size,
    background = FieldTrackTheme.colors.primaryWash,
    tint = FieldTrackTheme.colors.primaryPressed,
)

/**
 * "Not sent yet", small and amber, under a row's figure.
 *
 * Only ever the absence of a tick: rows that reached the server carry no mark at
 * all, so a list of finished work reads clean and the one still on the phone is
 * the one that stands out.
 */
@Composable
fun NotSentMark() {
    Text(
        text = stringResource(R.string.job_not_sent_short),
        style = FieldTrackTheme.type.caption,
        color = FieldTrackTheme.colors.moneyInk,
        maxLines = 1,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/**
 * "Lost ₹320", small and red, under a finished job's amount.
 *
 * Only on the jobs that did, so a list of ordinary work reads clean and the one
 * that cost more than it brought in is the one that stands out.
 */
@Composable
fun LostMark(amountMinor: Long) {
    Text(
        text = stringResource(R.string.job_lost_short, com.example.fieldtrack.designsystem.format.money(amountMinor)),
        style = FieldTrackTheme.type.caption,
        color = FieldTrackTheme.colors.dueInk,
        maxLines = 1,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** A quiet placeholder for a figure that has not loaded, so nothing reads as ₹0 by mistake. */
const val NOT_YET = "—"
