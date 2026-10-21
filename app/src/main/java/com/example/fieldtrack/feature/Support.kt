package com.example.fieldtrack.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fieldtrack.FieldFlowApplication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.R
import com.example.fieldtrack.feature.fleet.weekdayIndex
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.res.pluralStringResource

/**
 * Minimal ViewModel wiring.
 *
 * No dependency injection framework: this app has one Application object holding a
 * handful of repositories, and a container would be more machinery than the problem
 * deserves. If the graph grows past what fits on one screen, revisit it.
 */
private class SimpleFactory(private val create: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (FieldFlowApplication) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as FieldFlowApplication
    return viewModel(key = key, factory = rememberSimpleFactory { create(app) })
}

@Composable
fun rememberSimpleFactory(create: () -> ViewModel): ViewModelProvider.Factory =
    SimpleFactory(create)

/** Day boundaries, used for "today" on the home screen. */
fun startOfToday(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun greetingFor(now: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val res = when {
        hour < 12 -> R.string.home_greeting_morning
        hour < 17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    return androidx.compose.ui.res.stringResource(res)
}

fun clockTime(millis: Long): String = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
    .format(java.util.Date(millis))

/** "9 Oct" — dates on a khata line, where the year is almost never in question. */
fun dayMonth(millis: Long): String = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
    .format(java.util.Date(millis))

/** "October 2026" — the heading over a month's worth of rows. */
fun monthYear(millis: Long): String =
    java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        .format(java.util.Date(millis))

/** "10 Oct 2026" — on a bill, where the year is part of the record. */
fun dayMonthYear(millis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        .format(java.util.Date(millis))

/**
 * How long a debt has been outstanding.
 *
 * Same-day debts read as "today" rather than "0 days", which is both wrong-sounding
 * and faintly accusing about money that is hours old.
 */
@Composable
fun daysAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - millis).coerceAtLeast(0L) / 86_400_000L).toInt()
    return if (days == 0) androidx.compose.ui.res.stringResource(R.string.time_today)
    else androidx.compose.ui.res.pluralStringResource(R.plurals.count_days, days, days)
}

/**
 * A work type's name, in the reader's language.
 *
 * `workType` is stored as a key — "Rotavator", "Hauling" — and screens have been
 * printing the key. That reads correctly today only because the keys happen to be
 * English words; the first translated build would show them untranslated beside
 * everything that is. Falls back to the key for work this build does not know,
 * which is better than a blank.
 */
@Composable
fun workLabel(workType: String): String =
    workTypeOptions.firstOrNull { it.key == workType }
        ?.let { stringResource(it.labelRes) }
        ?: workType

/** "3.5 acre" — a quantity and its unit, ordered by the language, not by Kotlin. */
@Composable
fun measure(amount: Double, unit: MeasureUnit): String =
    stringResource(R.string.measure_value, quantity(amount), stringResource(unit.labelRes))

/** "10" — the day, for a list read down its dates. */
@Composable
fun dayOfMonth(millis: Long): String =
    java.text.SimpleDateFormat("d", Locale.getDefault()).format(java.util.Date(millis))

/** "Oct" — the month, under the day. */
@Composable
fun monthShort(millis: Long): String =
    java.text.SimpleDateFormat("MMM", Locale.getDefault()).format(java.util.Date(millis))

/**
 * Seven bars, Monday first, with today picked out.
 *
 * Lives here because two screens draw it — the owner's fleet and the driver's own
 * work — and a chart copied into both is a chart that stops agreeing with itself
 * the first time one of them is adjusted.
 */
@Composable
fun WeekBars(hours: List<Double>, modifier: Modifier = Modifier) {
    val colors = FieldTrackTheme.colors
    val peak = hours.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val today = weekdayIndex(System.currentTimeMillis())
    val labels = listOf(
        R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
        R.string.day_fri, R.string.day_sat, R.string.day_sun,
    )

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        hours.forEachIndexed { index, value ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // A day with no work still shows a sliver, so the week reads as
                // seven days rather than as a gap in the data.
                val fraction = (value / peak).coerceIn(0.0, 1.0)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((12 + fraction * 74).dp)
                        .clip(FieldTrackTheme.shapes.small)
                        .background(
                            when {
                                index == today -> colors.ink
                                value > 0 -> colors.primary
                                else -> colors.line
                            }
                        )
                )
                Text(
                    text = stringResource(labels[index]),
                    style = FieldTrackTheme.type.caption,
                    color = if (index == today) colors.ink else colors.inkTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

/** Two letters for an avatar, from however many words the name has. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2)
        else -> "${parts[0].first()}${parts[1].first()}"
    }.uppercase()
}

/** The glyph for a machine category. */
fun iconFor(kind: MachineKind): ImageVector = when (kind) {
    MachineKind.Tractor -> FieldTrackIcons.Tractor
    MachineKind.Harvester -> FieldTrackIcons.Harvester
    MachineKind.Rotavator -> FieldTrackIcons.Rotavator
    MachineKind.Cultivator -> FieldTrackIcons.Cultivator
    MachineKind.Trolley -> FieldTrackIcons.Trolley
    MachineKind.Thresher -> FieldTrackIcons.Thresher
    MachineKind.Sprayer -> FieldTrackIcons.Sprayer
    MachineKind.Transplanter -> FieldTrackIcons.Transplanter
    MachineKind.PowerTiller -> FieldTrackIcons.PowerTiller
    MachineKind.Baler -> FieldTrackIcons.Baler
    MachineKind.Leveller -> FieldTrackIcons.Leveller
    MachineKind.Other -> FieldTrackIcons.More
}

/** The work types the picker offers, each with the glyph and default billing. */
data class WorkTypeOption(
    val labelRes: Int,
    val key: String,
    val icon: ImageVector,
    /**
     * The ways this work is actually charged for, most common first.
     *
     * The rate picker offers only these. Nobody bills rotavator work by the quintal,
     * and showing the option invites a tap that produces a job measured in bags at a
     * per-acre rate — a number that is wrong in a way the khata cannot later explain.
     */
    val billingModes: List<BillingMode>,
) {
    /** What the picker lands on. Always one of [billingModes], by construction. */
    val defaultMode: BillingMode get() = billingModes.first()
}

private val AREA = BillingMode.PerAcre
private val HOUR = BillingMode.PerHour
private val TRIP = BillingMode.PerTrip
private val LOAD = BillingMode.PerUnit
private val DISTANCE = BillingMode.PerKm
private val FIXED = BillingMode.Fixed

/**
 * The work a contractor sells, and how each kind is really charged for.
 *
 * [WorkTypeOption.billingModes] is the whole point of this table. Tillage is sold by
 * the area or by the hour; a combine is sometimes paid out of the crop, so harvesting
 * adds by-the-quintal; a trolley is paid by the trip, the kilometre or the load, and
 * never by the acre. Every list ends in a fixed price, because an agreed lump sum is
 * always possible, and "Other" carries all six for the work this table has not met.
 */
val workTypeOptions = listOf(
    WorkTypeOption(R.string.work_type_rotavator, "Rotavator", FieldTrackIcons.Rotavator, listOf(AREA, HOUR, FIXED)),
    WorkTypeOption(R.string.work_type_ploughing, "Ploughing", FieldTrackIcons.Cultivator, listOf(HOUR, AREA, FIXED)),
    WorkTypeOption(R.string.work_type_harvesting, "Harvesting", FieldTrackIcons.Harvester, listOf(AREA, HOUR, LOAD, FIXED)),
    WorkTypeOption(R.string.work_type_levelling, "Levelling", FieldTrackIcons.Leveller, listOf(AREA, HOUR, FIXED)),
    WorkTypeOption(R.string.work_type_spraying, "Spraying", FieldTrackIcons.Sprayer, listOf(AREA, LOAD, FIXED)),
    WorkTypeOption(R.string.work_type_hauling, "Hauling", FieldTrackIcons.Trolley, listOf(TRIP, DISTANCE, LOAD, FIXED)),
    WorkTypeOption(R.string.work_type_sowing, "Sowing", FieldTrackIcons.Transplanter, listOf(AREA, HOUR, FIXED)),
    WorkTypeOption(R.string.work_type_other, "Other", FieldTrackIcons.More, listOf(FIXED, AREA, HOUR, TRIP, LOAD, DISTANCE)),
)

/** Label for a unit. Regional land units are the reason these are resources. */
val MeasureUnit.labelRes: Int
    get() = when (this) {
        MeasureUnit.Acre -> R.string.unit_acre
        MeasureUnit.Bigha -> R.string.unit_bigha
        MeasureUnit.Kanal -> R.string.unit_kanal
        MeasureUnit.Guntha -> R.string.unit_guntha
        MeasureUnit.Katha -> R.string.unit_katha
        MeasureUnit.Hour -> R.string.unit_hour
        MeasureUnit.Trip -> R.string.unit_trip
        MeasureUnit.Quintal -> R.string.unit_quintal
        MeasureUnit.Bag -> R.string.unit_bag
        MeasureUnit.Piece -> R.string.unit_piece
        MeasureUnit.Kilometre -> R.string.unit_km
    }

val BillingMode.labelRes: Int
    get() = when (this) {
        // Never shown for PerAcre, which is named after the unit — see billingLabel.
        BillingMode.PerAcre -> R.string.billing_per_land
        BillingMode.PerHour -> R.string.billing_per_hour
        BillingMode.PerTrip -> R.string.billing_per_trip
        BillingMode.PerUnit -> R.string.billing_per_unit
        BillingMode.PerKm -> R.string.billing_per_km
        BillingMode.Fixed -> R.string.billing_fixed
    }

/**
 * What to call a billing mode on screen.
 *
 * Area work is named after the unit the business measures in, because a contractor
 * who told us he works in bigha should never be offered "per acre" again — least of
 * all on the same screen as a rate that says "per Bigha".
 */
@Composable
fun billingLabel(mode: BillingMode, landUnit: MeasureUnit): String =
    if (mode == BillingMode.PerAcre) {
        stringResource(R.string.billing_per_land, unitInPhrase(landUnit))
    } else {
        stringResource(mode.labelRes)
    }

/**
 * A unit's name as it reads inside a phrase: "per acre", not "per Acre".
 *
 * The label keeps its capital where it stands alone as a chip, and loses it here so
 * "Per acre" sits beside "Per hour" instead of shouting next to it. Lowercasing is a
 * no-op in the languages this app is headed for, which do not mark case at all.
 */
@Composable
fun unitInPhrase(unit: MeasureUnit): String =
    stringResource(unit.labelRes).lowercase(Locale.getDefault())

val BillingMode.captionRes: Int
    get() = when (this) {
        BillingMode.PerAcre -> R.string.billing_per_acre_caption
        BillingMode.PerHour -> R.string.billing_per_hour_caption
        BillingMode.PerTrip -> R.string.billing_per_trip_caption
        BillingMode.PerUnit -> R.string.billing_per_unit_caption
        BillingMode.PerKm -> R.string.billing_per_km_caption
        BillingMode.Fixed -> R.string.billing_fixed_caption
    }

/** What a driver would call the thing that has gone wrong. */
val BreakdownCategory.labelRes: Int
    get() = when (this) {
        BreakdownCategory.Hydraulics -> R.string.breakdown_hydraulics
        BreakdownCategory.Engine -> R.string.breakdown_engine
        BreakdownCategory.Tyre -> R.string.breakdown_tyre
        BreakdownCategory.Electrical -> R.string.breakdown_electrical
        BreakdownCategory.Implement -> R.string.breakdown_implement
        BreakdownCategory.Other -> R.string.breakdown_other
    }

/**
 * The icon for a job: the work, not the machine.
 *
 * A job is a piece of work — rotavating, harvesting, hauling — and that is what
 * somebody scanning a list is looking for. Showing the machine instead gives every
 * row on a one-tractor outfit the same picture, which is a column of identical
 * tractors telling the reader nothing. The machine is the fallback, for work this
 * build does not have an icon for.
 */
fun iconForWork(workType: String, machineKind: MachineKind): ImageVector =
    workTypeOptions.firstOrNull { it.key == workType }?.icon ?: iconFor(machineKind)

/** The units that make sense for a billing mode. */
fun unitsFor(mode: BillingMode): List<MeasureUnit> = when (mode) {
    BillingMode.PerAcre -> listOf(
        MeasureUnit.Acre, MeasureUnit.Bigha, MeasureUnit.Kanal, MeasureUnit.Guntha, MeasureUnit.Katha
    )
    BillingMode.PerHour -> listOf(MeasureUnit.Hour)
    BillingMode.PerTrip -> listOf(MeasureUnit.Trip)
    BillingMode.PerUnit -> listOf(MeasureUnit.Quintal, MeasureUnit.Bag, MeasureUnit.Piece)
    BillingMode.PerKm -> listOf(MeasureUnit.Kilometre)
    BillingMode.Fixed -> listOf(MeasureUnit.Piece)
}

/**
 * How long ago, at the resolution that matters.
 *
 * Minutes for the last hour, hours for the last day, then the date. "Last backup 3
 * days ago" is a prompt to find signal; "last backup 4,320 minutes ago" is not.
 */
@Composable
fun timeAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    val elapsed = (now - millis).coerceAtLeast(0L)
    val minutes = (elapsed / 60_000L).toInt()
    val hours = minutes / 60
    return when {
        minutes < 1 -> stringResource(R.string.sync_just_now)
        hours < 1 -> pluralStringResource(R.plurals.time_minutes_ago, minutes, minutes)
        hours < 24 -> pluralStringResource(R.plurals.time_hours_ago, hours, hours)
        else -> dayMonthYear(millis)
    }
}
