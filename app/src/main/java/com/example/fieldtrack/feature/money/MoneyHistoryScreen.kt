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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
enum class HistoryKind {
    Received, Billed, Corrected, PaidOut, Fuel, Repairs,

    /** Cash a team member handed to whoever keeps the money: moves inside the business, so in no total. */
    HandedOver,
}

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
    /** For a payment out: what it was for (a job, a day, an advance, machine hire). */
    val covers: PayoutCovers? = null,
    /** Who wrote it down. Every line says so: the record of the money is a record of people. */
    val byName: String? = null,
    /** Who took it back, when it was. */
    val voidedByName: String? = null,
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
    /** Farmers' bills and payments. */
    val seesMoney: Boolean = false,
    /** Wages, advances and machine hire. */
    val seesPeople: Boolean = false,
    val canTakePayment: Boolean = false,
    val canPayOut: Boolean = false,
    /** What farmers still owe, all told. */
    val toCollectMinor: Long = 0,
    /** The business's cash team members took from farmers and have not handed over. */
    val cashWithTeamMinor: Long = 0,
    /** For choosing whose payment came in; empty for somebody who may not take one. */
    val customers: List<com.example.fieldtrack.data.db.entity.CustomerEntity> = emptyList(),
) {
    /** Only the kinds this person may see are offered as a filter. */
    val filters: List<HistoryFilter>
        get() = listOfNotNull(
            HistoryFilter.All,
            HistoryFilter.Received.takeIf { seesMoney },
            HistoryFilter.Billed.takeIf { seesMoney },
            HistoryFilter.PaidOut.takeIf { seesPeople },
            HistoryFilter.Costs,
        )
}

/**
 * The one place for money: what this period brought in and cost, what farmers still owe, the
 * two things people come here to do (take a payment, pay somebody), and every movement of
 * money in one list, newest first.
 *
 * Each kind is shown only to somebody who may see it: farmers' bills and payments with "see
 * money", wages, advances and hire with "see what people are paid", diesel and repairs with
 * either. The page says plainly which kinds are kept from this person. A line leads to the
 * page it came from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyHistoryViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val form = MutableStateFlow(Triple(HistoryFilter.All, HistoryPeriod.Month, ""))

    val state: StateFlow<MoneyHistoryState> = app.sessionStore.session.flatMapLatest { s ->
        val seesMoney = s.canSeeMoney
        val seesPeople = Capability.SeePeopleCost in s.can
        val now = System.currentTimeMillis()
        val cash = if (seesMoney) app.peopleRepository.observeCashHeld(s.orgId) else kotlinx.coroutines.flow.flowOf(emptyMap())
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
                    app.ledgerRepository.observeBalances(s.orgId),
                    app.peopleRepository.observeHandovers(s.orgId),
                ) { c, m, t, b, h -> Lists(c, m, t, b, h) },
            ) { ledger, payouts, fuel, service, (customers, machines, team, balances, handovers) ->
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
                                    byName = it.byPersonId?.let { id -> personName[id] },
                                )
                            )
                        }
                        // Cash moving from the person who took it to the one who keeps it.
                        handovers.forEach {
                            add(
                                HistoryRow(
                                    id = "h-" + it.id, atMillis = it.atMillis, kind = HistoryKind.HandedOver,
                                    who = personName[it.fromPersonId].orEmpty(),
                                    detail = it.note,
                                    amountMinor = it.amountMinor, voided = it.voidedAt != null,
                                    open = HistoryOpen.Person(membershipId(s.orgId, it.fromPersonId)),
                                    byName = it.byPersonId?.let { id -> personName[id] },
                                    voidedByName = it.voidedByPersonId?.let { id -> personName[id] },
                                )
                            )
                        }
                    }
                    // Diesel and repairs are the business's own running costs, not a farmer's
                    // account: shown to anybody who sees either kind of money.
                    run {
                        fuel.forEach {
                            add(
                                HistoryRow(
                                    id = "f-" + it.id, atMillis = it.atMillis, kind = HistoryKind.Fuel,
                                    who = machineName[it.machineId].orEmpty(), detail = null, amountMinor = it.costMinor,
                                    open = HistoryOpen.Machine(it.machineId),
                                    byName = it.byPersonId?.let { id -> personName[id] },
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
                                    amountMinor = it.amountMinor, voided = it.voidedAt != null, covers = it.covers,
                                    byName = it.byPersonId?.let { id -> personName[id] },
                                    voidedByName = it.voidedByPersonId?.let { id -> personName[id] },
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
                    // A name finds what was done to them and what they did: the second is the audit.
                    .filter {
                        q.isEmpty() || it.who.lowercase().contains(q) || it.detail.orEmpty().lowercase().contains(q) ||
                            it.byName.orEmpty().lowercase().contains(q) || it.voidedByName.orEmpty().lowercase().contains(q)
                    }
                    .sortedByDescending { it.atMillis }
                val counted = shown.filter { !it.voided }
                MoneyHistoryState(
                    loaded = true, mayLook = true, filter = filter, period = period, query = query,
                    rows = shown,
                    seesMoney = seesMoney,
                    seesPeople = seesPeople,
                    canTakePayment = Capability.TakePayment in s.can,
                    canPayOut = Capability.PayOut in s.can,
                    toCollectMinor = if (seesMoney) balances.sumOf { maxOf(it.balanceMinor, 0) } else 0,
                    customers = if (Capability.TakePayment in s.can) customers else emptyList(),
                    inMinor = counted.filter { it.kind == HistoryKind.Received }.sumOf { it.amountMinor },
                    outMinor = counted.filter { it.kind == HistoryKind.PaidOut || it.kind == HistoryKind.Fuel || it.kind == HistoryKind.Repairs }
                        .sumOf { it.amountMinor },
                )
            }.combine(cash) { st, held -> st.copy(cashWithTeamMinor = held.values.sum()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyHistoryState())

    fun setFilter(filter: HistoryFilter) = form.update { it.copy(first = filter) }
    fun setPeriod(period: HistoryPeriod) = form.update { it.copy(second = period) }
    fun setQuery(text: String) = form.update { it.copy(third = text.take(40)) }
}

/** The lists the money page reads besides the money itself. */
private data class Lists(
    val customers: List<com.example.fieldtrack.data.db.entity.CustomerEntity>,
    val machines: List<com.example.fieldtrack.data.db.entity.MachineEntity>,
    val team: List<com.example.fieldtrack.data.db.entity.MembershipEntity>,
    val balances: List<com.example.fieldtrack.data.db.dao.CustomerBalance>,
    val handovers: List<com.example.fieldtrack.data.db.entity.CashHandoverEntity>,
)

/** Where the money page leads, grouped so it travels as one argument. */
class MoneyActions(
    val onFilter: (HistoryFilter) -> Unit,
    val onPeriod: (HistoryPeriod) -> Unit,
    val onQuery: (String) -> Unit,
    val onOpen: (HistoryOpen) -> Unit,
    /** Opens the payment form for the farmer picked. */
    val onReceived: (customerId: String) -> Unit,
    val onPaySomeone: () -> Unit,
    /** What each person is owed and has been paid. */
    val onPeople: () -> Unit,
    val onReports: () -> Unit,
)

@Composable
fun MoneyHistoryScreen(
    state: MoneyHistoryState,
    actions: MoneyActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var pickingFarmer by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var farmerQuery by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.menu_money),
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
            item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ---- The figures: this period, and what is still to come in.
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
                        if (state.seesMoney) {
                            Text(
                                text = stringResource(R.string.money_to_collect, money(state.toCollectMinor)),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.onDarkMuted,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (state.cashWithTeamMinor > 0) {
                            Text(
                                text = stringResource(R.string.money_cash_with_team, money(state.cashWithTeamMinor)),
                                style = FieldTrackTheme.type.bodySmall,
                                color = colors.onDarkMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    // ---- The two things people come here to do.
                    val receive = state.canTakePayment && state.customers.isNotEmpty()
                    if (receive || state.canPayOut) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (receive) com.example.fieldtrack.designsystem.component.FtButton(
                                text = stringResource(R.string.money_got_paid),
                                onClick = { farmerQuery = ""; pickingFarmer = true },
                                icon = FieldTrackIcons.Add,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.canPayOut) com.example.fieldtrack.designsystem.component.FtButton(
                                text = stringResource(R.string.money_pay_someone),
                                onClick = actions.onPaySomeone,
                                tone = com.example.fieldtrack.designsystem.component.FtButtonTone.Secondary,
                                icon = FieldTrackIcons.Minus,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ---- Pages behind this one.
                    FtCard(padding = 0.dp) {
                        if (state.seesPeople) {
                            FtListRow(
                                title = stringResource(R.string.money_people_row),
                                subtitle = stringResource(R.string.money_people_note),
                                subtitleMaxLines = 2,
                                leading = { FtIconTile(FieldTrackIcons.Team, size = 40.dp) },
                                showChevron = true,
                                onClick = actions.onPeople,
                            )
                            FtRowDivider()
                        }
                        FtListRow(
                            title = stringResource(R.string.money_reports_row),
                            subtitle = stringResource(
                                if (state.seesPeople) R.string.money_reports_note else R.string.money_reports_note_short
                            ),
                            subtitleMaxLines = 2,
                            leading = { FtIconTile(FieldTrackIcons.Report, size = 40.dp) },
                            showChevron = true,
                            onClick = actions.onReports,
                        )
                    }

                    // ---- Every movement of money.
                    FtSectionLabel(stringResource(R.string.mh_title), Modifier.padding(top = 8.dp))
                    if (state.filters.size > 2) FtPick(
                        options = state.filters,
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
                        onPick = actions.onFilter,
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
                        onPick = actions.onPeriod,
                    )
                    FtTextField(
                        value = state.query,
                        onValueChange = actions.onQuery,
                        leadingIcon = FieldTrackIcons.Search,
                        placeholder = stringResource(R.string.mh_search),
                    )
                    // Said, not left to be guessed: an empty kind is either nothing or not yours to see.
                    val hidden = listOfNotNull(
                        stringResource(R.string.mh_hidden_farmers).takeIf { !state.seesMoney },
                        stringResource(R.string.mh_hidden_people).takeIf { !state.seesPeople },
                    )
                    if (hidden.isNotEmpty()) Text(
                        text = hidden.joinToString(" "),
                        style = FieldTrackTheme.type.caption,
                        color = colors.inkTertiary,
                    )
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
                            HistoryLine(row, actions.onOpen)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(dimens.screenPadding)) }
        }
    }

    if (pickingFarmer) {
        com.example.fieldtrack.feature.work.PickCustomerSheet(
            customers = state.customers,
            query = farmerQuery,
            onQuery = { farmerQuery = it },
            onPick = { pickingFarmer = false; actions.onReceived(it.id) },
            onDismiss = { pickingFarmer = false },
            title = stringResource(R.string.money_received_who),
        )
    }
}

@Composable
private fun HistoryLine(row: HistoryRow, onOpen: (HistoryOpen) -> Unit) {
    val colors = FieldTrackTheme.colors
    // A payment out says what it was for, which says more than "paid out" ever could.
    val label = stringResource(
        when (row.kind) {
            HistoryKind.Received -> R.string.mh_k_received
            HistoryKind.Billed -> R.string.mh_k_billed
            HistoryKind.Corrected -> R.string.mh_k_corrected
            HistoryKind.PaidOut -> when (row.covers) {
                PayoutCovers.Job -> R.string.money_out_for_job
                PayoutCovers.Day -> R.string.money_out_for_day
                PayoutCovers.Period -> R.string.money_out_for_period
                PayoutCovers.Advance -> R.string.money_out_for_advance
                PayoutCovers.Hire -> R.string.money_out_for_hire
                null -> R.string.mh_k_paid
            }
            HistoryKind.Fuel -> R.string.mh_k_fuel
            HistoryKind.Repairs -> R.string.mh_k_repairs
            HistoryKind.HandedOver -> R.string.mh_k_handover
        }
    )
    // Paid to nobody on the team and not named: the day's labourers.
    val who = row.who.ifBlank {
        if (row.kind == HistoryKind.PaidOut) stringResource(R.string.payout_labourers) else ""
    }
    val incoming = row.kind == HistoryKind.Received
    val outgoing = row.kind == HistoryKind.PaidOut || row.kind == HistoryKind.Fuel || row.kind == HistoryKind.Repairs
    FtListRow(
        title = who.ifBlank { label },
        subtitle = listOfNotNull(
            label.takeIf { who.isNotBlank() }, row.detail,
            row.byName?.let { stringResource(R.string.mh_by, it) },
            when {
                !row.voided -> null
                row.voidedByName != null -> stringResource(R.string.mh_taken_back_by, row.voidedByName)
                else -> stringResource(R.string.person_pay_taken_back)
            },
        ).joinToString(" · ").ifBlank { null },
        subtitleMaxLines = 2,
        leading = {
            FtIconTile(
                icon = when (row.kind) {
                    HistoryKind.Received -> FieldTrackIcons.Banknote
                    HistoryKind.Billed, HistoryKind.Corrected -> FieldTrackIcons.Bill
                    HistoryKind.PaidOut -> FieldTrackIcons.Team
                    HistoryKind.Fuel -> FieldTrackIcons.Fuel
                    HistoryKind.Repairs -> FieldTrackIcons.Service
                    HistoryKind.HandedOver -> FieldTrackIcons.Swap
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
