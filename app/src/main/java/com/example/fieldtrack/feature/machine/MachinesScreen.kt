package com.example.fieldtrack.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MachineStatus
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconButton
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtStatus
import com.example.fieldtrack.designsystem.component.FtStatusPill
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.iconFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MachineRow(
    val id: String,
    val name: String,
    val kind: MachineKind,
    val plate: String?,
    val status: MachineStatus,
    val hoursUsed: Double,
    /** Hours of use left before the next service; null when none is scheduled. */
    val hoursToService: Double?,
)

data class MachinesState(
    val loaded: Boolean = false,
    val rows: List<MachineRow> = emptyList(),
    /** False for a driver, who sees the fleet but may not change it. */
    val canEdit: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MachinesViewModel(app: FieldFlowApplication) : ViewModel() {

    // Watched, not read once. This list used to take the session at the moment the
    // screen was built and keep it: switch to another business and the Machines tab
    // went on listing the one you had left, with that business's permissions on the
    // add button. Every other screen follows the session; this one now does too.
    val state: StateFlow<MachinesState> = app.sessionStore.session.flatMapLatest { s ->
        app.directoryRepository.observeMachines(s.orgId).map { machines ->
            MachinesState(
                loaded = true,
                canEdit = Capability.EditMachines in s.can,
                rows = machines.map { m ->
                    MachineRow(
                        id = m.id,
                        name = m.name,
                        kind = m.kind,
                        plate = m.plate,
                        status = m.status,
                        hoursUsed = m.hoursUsed,
                        hoursToService = m.serviceIntervalHours?.let { interval ->
                            (m.lastServiceAtHours ?: 0.0) + interval - m.hoursUsed
                        },
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MachinesState())

    init {
        viewModelScope.launch { app.sessionStore.load() }
    }
}

@Composable
fun MachinesScreen(
    state: MachinesState,
    onAdd: () -> Unit,
    /** Opens the machine for correction. Null for someone who may not change the fleet. */
    onEdit: ((String) -> Unit)?,
    /**
     * Null when this is a bottom-bar tab and there is nothing behind it.
     *
     * It is a tab for a solo owner and an ordinary screen for a business one, which
     * is exactly how it ended up with no way back: it looked like a root because for
     * half the users it is.
     */
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
    ) {
        FtTopBar(
            title = stringResource(R.string.machines_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
            action = {
                if (onEdit != null) {
                    FtIconButton(
                        icon = FieldTrackIcons.Add,
                        contentDescription = stringResource(R.string.machines_add_cd),
                        onClick = onAdd,
                        tint = colors.onPrimary,
                        background = colors.primary,
                    )
                }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.space6,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loaded && state.rows.isEmpty()) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(R.string.machines_empty_title),
                            style = FieldTrackTheme.type.rowTitle,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.machines_empty_body),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkTertiary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (state.rows.size > MACHINE_SEARCH_FROM) {
                item { MachineSearch(query, onQuery = { query = it }) }
            }
            items(state.rows.matching(query), key = { it.id }) { row ->
                MachineListRow(row, onClick = onEdit?.let { edit -> { edit(row.id) } })
            }

            item { Box(Modifier.height(dimens.space4)) }
        }
    }
}

/** One machine in a list: what it is, how it stands, and its page on a tap. Shared with the fleet. */
@Composable
internal fun MachineListRow(row: MachineRow, onClick: (() -> Unit)?) {
    val colors = FieldTrackTheme.colors
    FtCard(padding = 0.dp, shape = FieldTrackTheme.shapes.medium) {
        FtListRow(
            title = row.name,
            subtitle = row.subtitle(),
            onClick = onClick,
            showChevron = onClick != null,
            leading = {
                FtIconTile(
                    icon = iconFor(row.kind),
                    size = 52.dp,
                    background = if (row.status == MachineStatus.Working) colors.primaryWash
                    else colors.surfaceMuted,
                    tint = if (row.status == MachineStatus.Working) colors.primary
                    else colors.inkSecondary,
                )
            },
            trailing = {
                FtStatusPill(
                    status = row.status.asPillStatus(),
                    label = stringResource(row.status.labelRes()),
                )
            },
        )
    }
}

/** A box to narrow a long list of machines by name or plate. */
@Composable
internal fun MachineSearch(query: String, onQuery: (String) -> Unit) {
    com.example.fieldtrack.designsystem.component.FtTextField(
        value = query,
        onValueChange = onQuery,
        leadingIcon = FieldTrackIcons.Search,
        placeholder = stringResource(R.string.machines_search),
    )
}

/** The machines whose name or plate holds what was typed. */
internal fun List<MachineRow>.matching(query: String): List<MachineRow> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.name.contains(q, ignoreCase = true) || it.plate.orEmpty().contains(q, ignoreCase = true) }
}

/** A short list is read at a glance; past this many, a search box earns its place. */
internal const val MACHINE_SEARCH_FROM = 8

/** "HR 22 F 4411 · 1,240 hr", with a service warning when one is close. */
@Composable
private fun MachineRow.subtitle(): String {
    val parts = buildList {
        plate?.let { add(it) }
        add(stringResource(R.string.machines_hours, quantity(hoursUsed)))
        hoursToService?.let { left ->
            if (left <= 0) add(stringResource(R.string.machines_service_overdue))
            else if (left <= SERVICE_WARNING_HOURS) {
                add(stringResource(R.string.machines_service_due, quantity(left)))
            }
        }
    }
    return parts.joinToString(" · ")
}

/** How close a service has to be before it is worth mentioning on a list row. */
private const val SERVICE_WARNING_HOURS = 50.0

private fun MachineStatus.asPillStatus() = when (this) {
    MachineStatus.Working -> FtStatus.Running
    MachineStatus.Down -> FtStatus.Breakdown
    MachineStatus.Idle -> FtStatus.Idle
}

/** What a machine's status is called. Shared with the detail screen. */
internal fun MachineStatus.labelRes() = when (this) {
    MachineStatus.Working -> R.string.status_running
    MachineStatus.Down -> R.string.status_breakdown
    MachineStatus.Idle -> R.string.status_idle
}
