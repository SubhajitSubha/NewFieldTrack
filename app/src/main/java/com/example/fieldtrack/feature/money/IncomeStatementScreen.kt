package com.example.fieldtrack.feature.money

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.costing.Assets
import com.example.fieldtrack.data.costing.Costing
import com.example.fieldtrack.data.db.LedgerKind
import com.example.fieldtrack.data.db.entity.moneyStyle
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtEmphasisCard
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.document.Document
import com.example.fieldtrack.document.DocumentShare
import com.example.fieldtrack.document.Documents
import com.example.fieldtrack.document.Party
import com.example.fieldtrack.document.StatementRow
import com.example.fieldtrack.feature.customer.financialYearStart
import com.example.fieldtrack.feature.dayMonthYear
import com.example.fieldtrack.feature.home.landWorked
import com.example.fieldtrack.feature.labelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which stretch of time the statement covers. */
enum class IncomePeriod { ThisYear, LastYear, ThisSeason, LastSeason }

data class IncomeState(
    val loaded: Boolean = false,
    val period: IncomePeriod = IncomePeriod.ThisYear,
    val allowed: Boolean = false,
    val busy: Boolean = false,
    val document: Document.IncomeStatement? = null,
)

/**
 * A season's or a year's income and costs on one page, for a bank loan, an accountant or a
 * licence. Offered only to somebody who sees both the money and what the business pays,
 * because a statement missing the wages would read as profit and be wrong by the biggest cost.
 */
class IncomeStatementViewModel(private val app: FieldFlowApplication) : ViewModel() {
    private val _state = MutableStateFlow(IncomeState())
    val state: StateFlow<IncomeState> = _state.asStateFlow()

    init { build(IncomePeriod.ThisYear) }

    fun choose(period: IncomePeriod) = build(period)

    private fun build(period: IncomePeriod) {
        _state.update { it.copy(period = period, busy = true) }
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val allowed = Capability.SeeMoney in s.can && Capability.SeePeopleCost in s.can
            if (!allowed) {
                _state.update { it.copy(loaded = true, allowed = false, busy = false, document = null) }
                return@launch
            }
            val doc = statement(s.orgId, period, System.currentTimeMillis())
            _state.update { it.copy(loaded = true, allowed = true, busy = false, document = doc) }
        }
    }

    private suspend fun statement(orgId: String, period: IncomePeriod, now: Long): Document.IncomeStatement {
        val zone = BusinessClock.zone
        val seasons = app.directoryRepository.observeSeasons(orgId).first()
        val (from, to) = when (period) {
            IncomePeriod.ThisYear -> financialYearStart(now).atStartOfDay(zone).toInstant().toEpochMilli() to now
            IncomePeriod.LastYear -> {
                val start = financialYearStart(now)
                start.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli() to
                    start.atStartOfDay(zone).toInstant().toEpochMilli() - 1
            }
            IncomePeriod.ThisSeason -> seasons.current(now, zone).startMillis(zone) to now
            IncomePeriod.LastSeason -> seasons.previous(now, zone).startMillis(zone) to seasons.current(now, zone).startMillis(zone) - 1
        }
        val asked = if (period == IncomePeriod.ThisYear || period == IncomePeriod.ThisSeason) Long.MAX_VALUE else to
        val dao = app.db.orgDao()
        val org = dao.findOrg(orgId)
        val style = org.moneyStyle()
        val earned = dao.observeEarnedBetween(orgId, from, asked).first()
        val fuel = dao.observeFuelSpendBetween(orgId, from, asked).first()
        val service = dao.observeServiceSpendBetween(orgId, from, asked).first()
        val onJobs = dao.observeJobCostBetween(orgId, from, asked).first()
        val loose = dao.observeLooseWagesBetween(orgId, from, asked).first()
        val unpriced = dao.observeUnpricedBetween(orgId, from, asked).first()
        val loan = Costing.instalmentsBetween(app.db.machineDao().observeMonthlyLoans(orgId).first(), from, to, style.stepMinor)
        val ledger = app.db.ledgerDao().observeAll(orgId).first()
        val received = ledger.filter { it.kind == LedgerKind.Payment && it.atMillis in from..to }.sumOf { it.amountMinor }
        val owed = app.ledgerRepository.observeBalances(orgId).first().filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor }
        val jobs = app.db.jobDao().observeCompletedBetween(orgId, from, asked).first()
        val (area, unit) = landWorked(jobs, org?.defaultLandUnit ?: com.example.fieldtrack.data.db.MeasureUnit.Acre)
        val machines = app.db.machineDao().observeAllOnce(orgId)
        val today = LocalDate.now(zone).toEpochDay()
        val finance = app.db.exportDao().finance(orgId).associateBy { it.id }
        val worth = machines.sumOf { Assets.bookValue(finance[it.id], today) ?: 0L }
        val context = app.applicationContext

        val people = onJobs + loose
        val costs = buildList {
            add(StatementRow(context.getString(R.string.income_fuel), fuel))
            add(StatementRow(context.getString(R.string.income_repairs), service))
            add(StatementRow(context.getString(R.string.income_people), people))
            if (loan > 0) add(StatementRow(context.getString(R.string.income_loans), loan, context.getString(R.string.income_loans_note)))
        }
        val notes = buildList {
            if (unpriced > 0) add(context.resources.getQuantityString(R.plurals.income_unpriced_note, unpriced, unpriced))
            add(context.getString(R.string.income_billed_note))
        }
        val facts = buildList {
            add(context.getString(R.string.income_f_jobs) to jobs.size.toString())
            if (area > 0) add(context.getString(R.string.income_f_land) to "${com.example.fieldtrack.designsystem.format.quantity(area)} ${context.getString(unit.labelRes)}")
            add(context.getString(R.string.income_f_customers) to jobs.map { it.customerId }.distinct().size.toString())
            add(context.getString(R.string.income_f_received) to money(received))
            add(context.getString(R.string.income_f_owed) to money(owed))
            add(context.getString(R.string.income_f_machines) to machines.size.toString())
            if (worth > 0) add(context.getString(R.string.income_f_worth) to money(worth))
        }
        return Document.IncomeStatement(
            letterhead = Documents.letterheadOf(org),
            style = style,
            customer = Party(org?.name.orEmpty()),
            periodLabel = "${dayMonthYear(from)} – ${dayMonthYear(to.coerceAtMost(now))}",
            printedLabel = dayMonthYear(now),
            income = listOf(StatementRow(context.getString(R.string.income_billed), earned)),
            costs = costs,
            netMinor = earned - costs.sumOf { it.amountMinor },
            facts = facts,
            notes = notes,
        )
    }

    fun share(context: Context) {
        val doc = _state.value.document ?: return
        viewModelScope.launch { runCatching { DocumentShare.share(context, doc, context.getString(R.string.income_share)) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeStatementScreen(
    state: IncomeState,
    onPeriod: (IncomePeriod) -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.income_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back_cd),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (state.loaded && !state.allowed) {
                Text(stringResource(R.string.income_not_allowed), style = FieldTrackTheme.type.body, color = colors.inkSecondary)
                return@Column
            }
            Text(stringResource(R.string.income_intro), style = FieldTrackTheme.type.body, color = colors.inkSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    IncomePeriod.ThisYear to R.string.income_this_year,
                    IncomePeriod.LastYear to R.string.income_last_year,
                    IncomePeriod.ThisSeason to R.string.income_this_season,
                    IncomePeriod.LastSeason to R.string.income_last_season,
                ).forEach { (p, label) -> FtChip(label = stringResource(label), selected = state.period == p, onClick = { onPeriod(p) }) }
            }
            state.document?.let { d ->
                Text(d.periodLabel, style = FieldTrackTheme.type.caption, color = colors.inkTertiary)
                FtEmphasisCard {
                    Text(stringResource(R.string.income_left).uppercase(), style = FieldTrackTheme.type.overline, color = colors.onDarkMuted)
                    Text(money(d.netMinor), style = FieldTrackTheme.type.moneyHero, color = colors.onDark, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        stringResource(R.string.income_in_out, money(d.incomeMinor), money(d.costsMinor)),
                        style = FieldTrackTheme.type.bodySmall, color = colors.onDarkMuted, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FtSectionLabel(stringResource(R.string.income_costs))
                FtCard {
                    d.costs.forEach { row ->
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(row.label, style = FieldTrackTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
                            Text(money(row.amountMinor), style = FieldTrackTheme.type.rowTitle, color = colors.ink)
                        }
                    }
                }
                d.notes.forEach { Text(it, style = FieldTrackTheme.type.caption, color = colors.inkTertiary) }
            }
        }
        FtButton(
            text = stringResource(R.string.income_share_button),
            onClick = onShare,
            enabled = state.document != null && !state.busy,
            modifier = Modifier.fillMaxWidth().padding(dimens.screenPadding),
        )
    }
}
