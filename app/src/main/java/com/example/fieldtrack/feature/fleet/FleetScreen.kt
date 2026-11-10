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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fieldtrack.R
import com.example.fieldtrack.designsystem.component.FtFitText
import com.example.fieldtrack.designsystem.layout.AdaptiveBody
import com.example.fieldtrack.designsystem.layout.AdaptiveScreen
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
import com.example.fieldtrack.feature.WeekBars
import com.example.fieldtrack.feature.dayMonth
import com.example.fieldtrack.feature.iconFor
import com.example.fieldtrack.feature.machine.matching

@Composable
fun FleetScreen(
    state: FleetState,
    onUpkeep: () -> Unit,
    modifier: Modifier = Modifier,
    /** The machines themselves, listed right here: the tab is called Fleet, so it shows the fleet. */
    machines: com.example.fieldtrack.feature.machine.MachinesState = com.example.fieldtrack.feature.machine.MachinesState(),
    onOpenMachine: (String) -> Unit = {},
    /** Null for somebody who may not add a machine. */
    onAdd: (() -> Unit)? = null,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }

    AdaptiveScreen(modifier) {
        FtTopBar(
            title = stringResource(R.string.fleet_title),
            action = {
                if (onAdd != null) {
                    com.example.fieldtrack.designsystem.component.FtIconButton(
                        icon = FieldTrackIcons.Add,
                        contentDescription = stringResource(R.string.machines_add_cd),
                        onClick = onAdd,
                        tint = colors.onPrimary,
                        background = colors.primary,
                    )
                }
            },
        )

        if (state.loaded && !state.hasMachines) {
            FtCard(Modifier.padding(horizontal = dimens.screenPadding)) {
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
            return@AdaptiveScreen
        }

        // How the machines stand and how hard they worked on one side; what needs
        // doing about them on the other.
        AdaptiveBody(primary = {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatusTile(state.working, R.string.fleet_working, colors.primary, Modifier.weight(1f))
                StatusTile(state.idle, R.string.fleet_idle, colors.inkSecondary, Modifier.weight(1f))
                StatusTile(state.down, R.string.fleet_down, colors.due, Modifier.weight(1f))
            }

            // Only when something does: an "all is well" card is a line to read for nothing.
            if (state.attention.isNotEmpty()) {
                FtSectionLabel(stringResource(R.string.fleet_attention))
                state.attention.forEach { item ->
                    AttentionCard(item, onClick = onUpkeep)
                }
            }

            // ---- The machines themselves.
            FtSectionLabel(stringResource(R.string.fleet_your_machines))
            if (machines.rows.size > com.example.fieldtrack.feature.machine.MACHINE_SEARCH_FROM) {
                com.example.fieldtrack.feature.machine.MachineSearch(query, onQuery = { query = it })
            }
            machines.rows.matching(query).forEach { row ->
                com.example.fieldtrack.feature.machine.MachineListRow(row, onClick = { onOpenMachine(row.id) })
            }
        }, secondary = {
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = stringResource(R.string.fleet_upkeep),
                    subtitle = stringResource(R.string.fleet_upkeep_note),
                    leading = { FtIconTile(FieldTrackIcons.Service, size = 40.dp) },
                    showChevron = true,
                    onClick = onUpkeep,
                )
            }
            HoursChart(state.weekHours)
        })
    }
}

@Composable
private fun StatusTile(count: Int, labelRes: Int, tint: Color, modifier: Modifier = Modifier) {
    FtCard(modifier, padding = 13.dp) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FtFitText(
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
        // The bars themselves are shared with the driver's own work screen.
        WeekBars(hours, Modifier.padding(top = 14.dp))
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
