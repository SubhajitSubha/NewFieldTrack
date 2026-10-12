package com.example.fieldtrack.feature.fleet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.iconFor

@Composable
fun FleetScreen(
    state: FleetState,
    onSeeAllMachines: () -> Unit,
    onUpkeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(title = stringResource(R.string.fleet_title))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (state.loaded && !state.hasMachines) {
                FtCard {
                    Text(
                        text = stringResource(R.string.fleet_empty_title),
                        style = FieldTrackTheme.type.rowTitle,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(R.string.fleet_empty_body),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkTertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatusTile(state.working, R.string.fleet_working, colors.primary, Modifier.weight(1f))
                    StatusTile(state.idle, R.string.fleet_idle, colors.inkSecondary, Modifier.weight(1f))
                    StatusTile(state.down, R.string.fleet_down, colors.due, Modifier.weight(1f))
                }

                HoursChart(state.weekHours)

                FtSectionLabel(stringResource(R.string.fleet_attention))
                if (state.attention.isEmpty()) {
                    FtCard {
                        Text(
                            text = stringResource(R.string.fleet_all_well),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                } else {
                    state.attention.forEach { item ->
                        // Tappable now. These rows named the broken machine and then
                        // offered nothing to do about it, which is where the dead end
                        // was most visible.
                        AttentionCard(item, onClick = onUpkeep)
                    }
                }

                FtButton(
                    text = stringResource(R.string.fleet_upkeep),
                    onClick = onUpkeep,
                    icon = FieldTrackIcons.Service,
                    tone = FtButtonTone.Secondary,
                    height = 54.dp,
                )

                FtButton(
                    text = stringResource(R.string.fleet_see_all),
                    onClick = onSeeAllMachines,
                    tone = FtButtonTone.Secondary,
                    height = 54.dp,
                )
            }
            Box(Modifier.height(dimens.space4))
        }
    }
}

@Composable
private fun StatusTile(count: Int, labelRes: Int, tint: Color, modifier: Modifier = Modifier) {
    FtCard(modifier, padding = 13.dp) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = FieldTrackTheme.type.moneyMedium,
                color = tint,
            )
            Text(
                text = stringResource(labelRes).uppercase(),
                style = FieldTrackTheme.type.overline,
                color = FieldTrackTheme.colors.inkTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Hours worked per day this week, from jobs actually recorded.
 *
 * One hue with the current day picked out in ink: rank and "today" are the only two
 * things this chart has to say, and both survive being read in sunlight.
 */
@Composable
private fun HoursChart(hours: List<Double>) {
    val colors = FieldTrackTheme.colors
    val peak = hours.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val today = weekdayIndex(System.currentTimeMillis())
    val labels = listOf(
        R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
        R.string.day_fri, R.string.day_sat, R.string.day_sun,
    )

    FtCard(padding = 17.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.fleet_hours_week),
                style = FieldTrackTheme.type.sectionTitle,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.fleet_hours_total, quantity(hours.sum())),
                style = FieldTrackTheme.type.caption,
                color = colors.inkTertiary,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEachIndexed { index, value ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
}

@Composable
private fun AttentionCard(item: Attention, onClick: () -> Unit) {
    val colors = FieldTrackTheme.colors
    val urgent = item.reason == AttentionReason.Breakdown ||
        item.reason == AttentionReason.ServiceOverdue

    FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
        FtListRow(
            title = item.machineName,
            subtitle = when (item.reason) {
                AttentionReason.Breakdown ->
                    stringResource(R.string.fleet_breakdown_since, dayMonth(item.value.toLong()))
                AttentionReason.ServiceOverdue -> stringResource(R.string.machines_service_overdue)
                AttentionReason.ServiceSoon ->
                    stringResource(R.string.machines_service_due, quantity(item.value))
            },
            statusStripe = if (urgent) colors.due else colors.money,
            showChevron = true,
            onClick = onClick,
            leading = {
                FtIconTile(
                    icon = iconFor(item.kind),
                    background = if (urgent) colors.dueWash else colors.moneyWash,
                    tint = if (urgent) colors.due else colors.money,
                )
            },
        )
    }
}
