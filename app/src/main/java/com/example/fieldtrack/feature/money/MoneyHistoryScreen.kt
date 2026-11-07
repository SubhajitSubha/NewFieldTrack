package com.example.fieldtrack.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtIconTile
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtPick
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.labelRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What kind of movement a line is. */
enum class HistoryKind { Received, Billed, Corrected, PaidOut, Fuel, Repairs }

/** Which lines are shown. */
enum class HistoryFilter { All, Received, Billed, PaidOut, Costs }

enum class HistoryPeriod(val days: Long?) { Month(30), Quarter(90), Year(365), Ever(null) }

/** Where a line leads when it is tapped. */
sealed interface HistoryOpen {
    data class Customer(val customerId: String) : HistoryOpen
    data class Person(val membershipId: String) : HistoryOpen
    data class Machine(val machineId: String) : HistoryOpen
}

data class HistoryRow(
    val id: String,
    val atMillis: Long,
    val kind: HistoryKind,
    /** Who or what: the farmer, the person, the machine. */
    val who: String,
    val detail: String?,
    val amountMinor: Long,
    val voided: Boolean = false,
    val open: HistoryOpen? = null,
)

data class MoneyHistoryState(
    val loaded: Boolean = false,
    val mayLook: Boolean = false,
    val filter: HistoryFilter = HistoryFilter.All,
    val period: HistoryPeriod = HistoryPeriod.Month,
    val query: String = "",
    val rows: List<HistoryRow> = emptyList(),
    val inMinor: Long = 0,
    val outMinor: Long = 0,
)

/**
 * Every movement of money in one list, newest first: what came in from farmers, what was billed,
 * what went out to people, and what was spent on diesel and repairs.
 *
 * Each kind is shown only to somebody who may see it: the farmers' side with money, wages and
 * hire with what the business pays. A line leads to the page it came from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyHistoryViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val form = MutableStateFlow(Triple(HistoryFilter.All, HistoryPeriod.Month, ""))

    val state: StateFlow<MoneyHistoryState> = app.sessionStore.session.flatMapLatest { s ->
        val seesMoney = s.canSeeMoney
        val seesPeople = Capability.SeePeopleCost in s.can
        val now = System.currentTimeMillis()
        form.flatMapLatest { (filter, period, query) ->
            val from = period.days?.let { now - it * 86_400_000L } ?: 0L
            combine(
                app.db.ledgerDao().observeSince(s.orgId, from),
                app.db.orgDao().observePayoutsSince(s.orgId, from),
                app.db.machineDao().observeFuelSince(s.orgId, from),
                app.db.machineDao().observeServiceSince(s.orgId, from),
                combine(
                    app.directoryRepository.observeCustomers(s.orgId),
                    app.directoryRepository.observeMachines(s.orgId),
                    app.teamRepository.observeEveryone(s.orgId),
                ) { c, m, t -> Triple(c, m, t) },
            ) { ledger, payouts, fuel, service, (customers, machines, team) ->
                if (!seesMoney && !seesPeople) return@combine MoneyHistoryState(loaded = true, filter = filter, period = period, query = query)
                val customerName = customers.associate { it.id to it.name }
                val machineName = machines.associate { it.id to it.name }
                val personName = team.associate { it.personId to it.displayName }
                val rows = buildList {
                    if (seesMoney) {
                        ledger.forEach {
                            add(
                                HistoryRow(
                                    id = "l-" + it.id, atMillis = it.atMillis,
                                    kind = when (it.kind) {
                                        LedgerKind.Payment -> HistoryKind.Received
                                        LedgerKind.Charge, LedgerKind.Opening -> HistoryKind.Billed
                                        LedgerKind.Adjustment -> HistoryKind.Corrected
                                    },
                                    who = customerName[it.customerId].orEmpty(),
                                    detail = it.note?.takeIf { n -> n.isNotBlank() },
                                    amountMinor = kotlin.math.abs(it.amountMinor),
                                    open = HistoryOpen.Customer(it.customerId),
                                )
                            )
                        }
                        fuel.forEach {
                            add(
                                HistoryRow(
                                    id = "f-" + it.id, atMillis = it.atMillis, kind = HistoryKind.Fuel,
                                    who = machineName[it.machineId].orEmpty(), detail = null, amountMinor = it.costMinor,
                                    open = HistoryOpen.Machine(it.machineId),
                                )
                            )
                        }
                        service.forEach {
                            add(
                                HistoryRow(
                                    id = "s-" + it.id, atMillis = it.atMillis, kind = HistoryKind.Repairs,
                                    who = machineName[it.machineId].orEmpty(), detail = it.description,
                                    amountMinor = it.costMinor ?: 0, open = HistoryOpen.Machine(it.machineId),
                                )
                            )
                        }
                    }
                    if (seesPeople) {
                        payouts.forEach {
                            add(
                                HistoryRow(
                                    id = "p-" + it.id, atMillis = it.atMillis, kind = HistoryKind.PaidOut,
                                    who = it.toPersonId?.let { id -> personName[id] } ?: it.toName.orEmpty(),
                                    detail = it.note?.takeIf { n -> n.isNotBlank() },
                                    amountMinor = it.amountMinor, voided = it.voidedAt != null,
                                    open = it.toPersonId?.let { id -> HistoryOpen.Person(membershipId(s.orgId, id)) },
                                )
                            )
                        }
                    }
                }
                val q = query.trim().lowercase()
                val shown = rows
                    .filter {
                        when (filter) {
                            HistoryFilter.All -> true
                            HistoryFilter.Received -> it.kind == HistoryKind.Received
                            HistoryFilter.Billed -> it.kind == HistoryKind.Billed || it.kind == HistoryKind.Corrected
                            HistoryFilter.PaidOut -> it.kind == HistoryKind.PaidOut
                            HistoryFilter.Costs -> it.kind == HistoryKind.Fuel || it.kind == HistoryKind.Repairs
                        }
                    }
                    .filter { q.isEmpty() || it.who.lowercase().contains(q) || it.detail.orEmpty().lowercase().contains(q) }
                    .sortedByDescending { it.atMillis }
                val counted = shown.filter { !it.voided }
                MoneyHistoryState(
                    loaded = true, mayLook = true, filter = filter, period = period, query = query,
                    rows = shown,
                    inMinor = counted.filter { it.kind == HistoryKind.Received }.sumOf { it.amountMinor },
                    outMinor = counted.filter { it.kind == HistoryKind.PaidOut || it.kind == HistoryKind.Fuel || it.kind == HistoryKind.Repairs }
                        .sumOf { it.amountMinor },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyHistoryState())

    fun setFilter(filter: HistoryFilter) = form.update { it.copy(first = filter) }
    fun setPeriod(period: HistoryPeriod) = form.update { it.copy(second = period) }
    fun setQuery(text: String) = form.update { it.copy(third = text.take(40)) }
}

@Composable
fun MoneyHistoryScreen(
    state: MoneyHistoryState,
    onBack: () -> Unit,
    onFilter: (HistoryFilter) -> Unit,
    onPeriod: (HistoryPeriod) -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (HistoryOpen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.mh_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        if (state.loaded && !state.mayLook) {
            Column(Modifier.weight(1f).padding(dimens.screenPadding)) {
                FtCard {
                    Text(
                        text = stringResource(R.string.money_out_locked),
                        style = FieldTrackTheme.type.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
            }
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FtPick(
                        options = HistoryFilter.entries,
                        selected = state.filter,
                        text = {
                            stringResource(
                                when (it) {
                                    HistoryFilter.All -> R.string.mh_all
                                    HistoryFilter.Received -> R.string.mh_received
                                    HistoryFilter.Billed -> R.string.mh_billed
                                    HistoryFilter.PaidOut -> R.string.mh_paid_out
                                    HistoryFilter.Costs -> R.string.mh_costs
                                }
                            )
                        },
                        onPick = onFilter,
                    )
                    FtPick(
                        options = HistoryPeriod.entries,
                        selected = state.period,
                        text = {
                            stringResource(
                                when (it) {
                                    HistoryPeriod.Month -> R.string.mh_30
                                    HistoryPeriod.Quarter -> R.string.mh_90
                                    HistoryPeriod.Year -> R.string.mh_year
                                    HistoryPeriod.Ever -> R.string.mh_ever
                                }
                            )
                        },
                        onPick = onPeriod,
                    )
                    FtTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        leadingIcon = FieldTrackIcons.Search,
                        placeholder = stringResource(R.string.mh_search),
                    )
                    FtCard(background = colors.deep, borderColor = colors.deep) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Text(stringResource(R.string.mh_in).uppercase(), style = FieldTrackTheme.type.caption, color = colors.onDarkMuted)
                                Text(money(state.inMinor), style = FieldTrackTheme.type.sectionTitle, color = colors.onDark)
                            }
                            Column {
                                Text(stringResource(R.string.mh_out).uppercase(), style = FieldTrackTheme.type.caption, color = colors.onDarkMuted)
                                Text(money(state.outMinor), style = FieldTrackTheme.type.sectionTitle, color = colors.onDark)
                            }
                        }
                    }
                }
            }
            if (state.loaded && state.rows.isEmpty()) {
                item {
                    FtCard {
                        Text(
                            text = stringResource(R.string.mh_none),
                            style = FieldTrackTheme.type.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }
            state.rows.groupBy { dayMonthYear(it.atMillis) }.forEach { (day, rows) ->
                item(key = "d-$day") { FtSectionLabel(day) }
                item(key = "c-$day") {
                    FtCard(padding = 0.dp) {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) FtRowDivider()
                            HistoryLine(row, onOpen)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(dimens.screenPadding)) }
        }
    }
}

@Composable
private fun HistoryLine(row: HistoryRow, onOpen: (HistoryOpen) -> Unit) {
    val colors = FieldTrackTheme.colors
    val label = stringResource(
        when (row.kind) {
            HistoryKind.Received -> R.string.mh_k_received
            HistoryKind.Billed -> R.string.mh_k_billed
            HistoryKind.Corrected -> R.string.mh_k_corrected
            HistoryKind.PaidOut -> R.string.mh_k_paid
            HistoryKind.Fuel -> R.string.mh_k_fuel
            HistoryKind.Repairs -> R.string.mh_k_repairs
        }
    )
    val incoming = row.kind == HistoryKind.Received
    val outgoing = row.kind == HistoryKind.PaidOut || row.kind == HistoryKind.Fuel || row.kind == HistoryKind.Repairs
    FtListRow(
        title = row.who.ifBlank { label },
        subtitle = listOfNotNull(
            label.takeIf { row.who.isNotBlank() }, row.detail,
            if (row.voided) stringResource(R.string.person_pay_taken_back) else null,
        ).joinToString(" · ").ifBlank { null },
        leading = {
            FtIconTile(
                icon = when (row.kind) {
                    HistoryKind.Received -> FieldTrackIcons.Banknote
                    HistoryKind.Billed, HistoryKind.Corrected -> FieldTrackIcons.Bill
                    HistoryKind.PaidOut -> FieldTrackIcons.Team
                    HistoryKind.Fuel -> FieldTrackIcons.Fuel
                    HistoryKind.Repairs -> FieldTrackIcons.Service
                },
                size = 40.dp,
                background = if (incoming) colors.moneyWash else colors.surfaceMuted,
                tint = if (incoming) colors.money else colors.inkSecondary,
            )
        },
        trailing = {
            Text(
                text = (if (incoming) "+" else if (outgoing) "−" else "") + money(row.amountMinor),
                style = FieldTrackTheme.type.rowTitle.copy(
                    textDecoration = if (row.voided) TextDecoration.LineThrough else null,
                ),
                color = if (row.voided) colors.inkTertiary else if (incoming) colors.moneyInk else colors.ink,
            )
        },
        showChevron = row.open != null,
        onClick = row.open?.let { target -> { onOpen(target) } },
    )
}
