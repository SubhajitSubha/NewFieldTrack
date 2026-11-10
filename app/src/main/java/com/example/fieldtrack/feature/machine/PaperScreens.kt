package com.example.fieldtrack.feature.machine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.R
import com.example.fieldtrack.data.costing.Assets
import com.example.fieldtrack.data.costing.PaperStatus
import com.example.fieldtrack.data.db.PaperKind
import com.example.fieldtrack.data.region.BusinessClock
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.designsystem.component.FtButton
import com.example.fieldtrack.designsystem.component.FtButtonTone
import com.example.fieldtrack.designsystem.component.FtCard
import com.example.fieldtrack.designsystem.component.FtChip
import com.example.fieldtrack.designsystem.component.FtListRow
import com.example.fieldtrack.designsystem.component.FtRowDivider
import com.example.fieldtrack.designsystem.component.FtSectionLabel
import com.example.fieldtrack.designsystem.component.FtTextField
import com.example.fieldtrack.designsystem.component.FtTopBar
import com.example.fieldtrack.designsystem.format.MoneyInput
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.dayMonthYear
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** An epoch day as the moment that day starts in the business's own zone, for the date formatters. */
fun dayStartMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay).atStartOfDay(BusinessClock.zone).toInstant().toEpochMilli()

fun todayEpochDay(): Long = LocalDate.now(BusinessClock.zone).toEpochDay()

val PaperKind.labelRes: Int
    get() = when (this) {
        PaperKind.Registration -> R.string.paper_registration
        PaperKind.Insurance -> R.string.paper_insurance
        PaperKind.Fitness -> R.string.paper_fitness
        PaperKind.Permit -> R.string.paper_permit
        PaperKind.Pollution -> R.string.paper_pollution
        PaperKind.Other -> R.string.paper_other
    }

/** A calendar over any day: the one the date pickers elsewhere forbid the future on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnyDaySheet(selectedEpochDay: Long?, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    // The platform calendar speaks UTC midnights, so an epoch day goes in and out unchanged.
    val state = rememberDatePickerState(initialSelectedDateMillis = selectedEpochDay?.times(DAY_MILLIS))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPicked(Math.floorDiv(it, DAY_MILLIS)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.finish_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** The words for where a paper stands. */
@Composable
fun paperStatusText(status: PaperStatus): String = when (status) {
    PaperStatus.NoDate -> stringResource(R.string.paper_no_date)
    is PaperStatus.Valid -> stringResource(R.string.paper_valid, status.daysLeft.toInt())
    is PaperStatus.Soon -> if (status.daysLeft == 0L) stringResource(R.string.paper_last_day)
        else pluralStringResource(R.plurals.paper_runs_out, status.daysLeft.toInt(), status.daysLeft.toInt())
    is PaperStatus.Expired -> pluralStringResource(R.plurals.paper_expired, status.daysAgo.toInt(), status.daysAgo.toInt())
}

/** The papers on a machine's page. */
@Composable
fun PapersCard(papers: List<PaperRow>, canEdit: Boolean, onOpen: (String?) -> Unit) {
    val colors = FieldTrackTheme.colors
    if (papers.isEmpty() && !canEdit) return
    FtSectionLabel(stringResource(R.string.papers_title))
    FtCard(padding = 0.dp) {
        Column {
            papers.forEachIndexed { index, paper ->
                if (index > 0) FtRowDivider()
                val warn = paper.status is PaperStatus.Soon || paper.status is PaperStatus.Expired
                FtListRow(
                    title = paperName(paper.kind, paper.label) + (paper.number?.let { " · $it" } ?: ""),
                    subtitle = buildString {
                        paper.expiresDay?.let { append(dayMonthYear(dayStartMillis(it))).append(" · ") }
                        append(paperStatusText(paper.status))
                    },
                    statusStripe = if (warn) colors.due else null,
                    showChevron = canEdit,
                    onClick = if (canEdit) ({ onOpen(paper.id) }) else null,
                )
            }
            if (canEdit) {
                if (papers.isNotEmpty()) FtRowDivider()
                FtListRow(
                    title = stringResource(R.string.papers_add),
                    showChevron = true,
                    onClick = { onOpen(null) },
                )
            }
        }
    }
}

// --------------------------------------------------------------------- a paper

data class PaperEditState(
    val loaded: Boolean = false,
    val machineName: String = "",
    val exists: Boolean = false,
    val kind: PaperKind = PaperKind.Insurance,
    /** What the owner calls an "Other" paper. Empty keeps "Other". */
    val label: String = "",
    val number: String = "",
    val expiresDay: Long? = null,
    val note: String = "",
    val canEdit: Boolean = true,
    val saving: Boolean = false,
)

class PaperEditViewModel(
    private val app: FieldFlowApplication,
    private val machineId: String,
    private val paperId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(PaperEditState())
    val state: StateFlow<PaperEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val machine = app.directoryRepository.findMachine(s.orgId, machineId)
            val paper = paperId?.let { app.directoryRepository.findPaper(s.orgId, it) }
            _state.update {
                it.copy(
                    loaded = true,
                    machineName = machine?.name.orEmpty(),
                    exists = paper != null,
                    kind = paper?.kind ?: it.kind,
                    label = paper?.label.orEmpty(),
                    number = paper?.number.orEmpty(),
                    expiresDay = paper?.expiresDay,
                    note = paper?.note.orEmpty(),
                    canEdit = Capability.EditMachines in s.can,
                )
            }
        }
    }

    fun setKind(v: PaperKind) = _state.update { it.copy(kind = v) }
    fun setLabel(v: String) = _state.update { it.copy(label = v.take(40)) }
    fun setNumber(v: String) = _state.update { it.copy(number = v.take(40)) }
    fun setExpires(v: Long?) = _state.update { it.copy(expiresDay = v) }
    fun setNote(v: String) = _state.update { it.copy(note = v.take(200)) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val c = _state.value
        _state.update { it.copy(saving = true) }
        runCatching {
            app.directoryRepository.savePaper(
                s.orgId, s.can, machineId, c.kind, c.number, c.expiresDay, c.note, paperId = paperId, label = c.label,
            )
        }
        _state.update { it.copy(saving = false) }
        onDone()
    }

    fun remove(onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        paperId?.let { runCatching { app.directoryRepository.removePaper(s.orgId, s.can, it) } }
        onDone()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperEditScreen(
    state: PaperEditState,
    onKind: (PaperKind) -> Unit,
    onNumber: (String) -> Unit,
    onLabel: (String) -> Unit = {},
    onExpires: (Long?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(if (state.exists) R.string.paper_edit_title else R.string.paper_new_title),
            subtitle = state.machineName.takeIf { it.isNotBlank() },
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
            FtSectionLabel(stringResource(R.string.paper_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperKind.entries.forEach { kind ->
                    FtChip(label = stringResource(kind.labelRes), selected = state.kind == kind, onClick = { onKind(kind) })
                }
            }
            // A paper this list has no word for gets the owner's own.
            if (state.kind == PaperKind.Other) {
                FtTextField(
                    value = state.label,
                    onValueChange = onLabel,
                    label = stringResource(R.string.paper_other_name),
                    placeholder = stringResource(R.string.paper_other_name_hint),
                )
            }
            FtTextField(
                value = state.number,
                onValueChange = onNumber,
                label = stringResource(R.string.paper_number),
                placeholder = stringResource(R.string.paper_number_hint),
            )
            FtSectionLabel(stringResource(R.string.paper_runs_out_on), Modifier.padding(top = 4.dp))
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = state.expiresDay?.let { dayMonthYear(dayStartMillis(it)) } ?: stringResource(R.string.paper_pick_day),
                    showChevron = true,
                    onClick = { picking = true },
                )
            }
            if (state.expiresDay != null) {
                FtChip(label = stringResource(R.string.paper_no_expiry), selected = false, onClick = { onExpires(null) })
            }
            FtTextField(
                value = state.note,
                onValueChange = onNote,
                label = stringResource(R.string.paper_note),
            )
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.exists && state.canEdit) {
                FtButton(text = stringResource(R.string.paper_remove), onClick = onRemove, tone = FtButtonTone.Danger, modifier = Modifier.fillMaxWidth())
            }
            FtButton(
                text = stringResource(R.string.paper_save),
                onClick = onSave,
                enabled = state.canEdit && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (picking) {
        AnyDaySheet(state.expiresDay, onDismiss = { picking = false }, onPicked = { onExpires(it); picking = false })
    }
}

// ----------------------------------------------------------------- what it is worth

data class WorthState(
    val loaded: Boolean = false,
    val machineName: String = "",
    val purchasedDay: Long? = null,
    val priceText: String = "",
    val resaleText: String = "",
    val lifeText: String = "",
    val canEdit: Boolean = true,
    val saving: Boolean = false,
    /** What it comes to today, worked out as the figures are typed. */
    val worthMinor: Long? = null,
)

class WorthViewModel(private val app: FieldFlowApplication, private val machineId: String) : ViewModel() {
    private val _state = MutableStateFlow(WorthState())
    val state: StateFlow<WorthState> = _state.asStateFlow()

    private var monthly: Long? = null
    private var perYear: Int? = null

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val machine = app.directoryRepository.findMachine(s.orgId, machineId)
            val allowed = Capability.EditMachines in s.can && Capability.SeePeopleCost in s.can
            val f = if (Capability.SeePeopleCost in s.can) app.directoryRepository.findMachineFinance(s.orgId, machineId) else null
            monthly = f?.monthlyMinor
            perYear = f?.hoursPerYear
            _state.update {
                it.copy(
                    loaded = true,
                    machineName = machine?.name.orEmpty(),
                    purchasedDay = f?.purchasedDay,
                    priceText = f?.purchaseMinor?.let { v -> MoneyInput.text(v) }.orEmpty(),
                    resaleText = f?.resaleMinor?.let { v -> MoneyInput.text(v) }.orEmpty(),
                    lifeText = f?.lifeYears?.toString().orEmpty(),
                    canEdit = allowed,
                ).recomputed()
            }
        }
    }

    private fun WorthState.recomputed(): WorthState = copy(
        worthMinor = Assets.bookValue(
            com.example.fieldtrack.data.db.entity.MachineFinanceEntity(
                id = machineId, orgId = "", purchasedDay = purchasedDay, purchaseMinor = MoneyInput.parse(priceText),
                resaleMinor = MoneyInput.parse(resaleText), lifeYears = lifeText.toIntOrNull(), updatedAt = 0,
            ),
            todayEpochDay(),
        )
    )

    fun setDay(v: Long?) = _state.update { it.copy(purchasedDay = v).recomputed() }
    fun setPrice(v: String) = _state.update { it.copy(priceText = MoneyInput.clean(v)).recomputed() }
    fun setResale(v: String) = _state.update { it.copy(resaleText = MoneyInput.clean(v)).recomputed() }
    fun setLife(v: String) = _state.update { it.copy(lifeText = v.filter { c -> c.isDigit() }.take(2)).recomputed() }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val s = app.sessionStore.load() ?: return@launch
        val c = _state.value
        _state.update { it.copy(saving = true) }
        runCatching {
            app.directoryRepository.setMachineFinance(
                s.orgId, s.can, machineId, monthly, perYear,
                purchasedDay = c.purchasedDay,
                purchaseMinor = MoneyInput.parse(c.priceText),
                resaleMinor = MoneyInput.parse(c.resaleText),
                lifeYears = c.lifeText.toIntOrNull()?.takeIf { it in 1..60 },
            )
        }
        _state.update { it.copy(saving = false) }
        onDone()
    }
}

@Composable
fun WorthScreen(
    state: WorthState,
    onDay: (Long?) -> Unit,
    onPrice: (String) -> Unit,
    onResale: (String) -> Unit,
    onLife: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.worth_title),
            subtitle = state.machineName.takeIf { it.isNotBlank() },
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
            Text(stringResource(R.string.worth_intro), style = FieldTrackTheme.type.body, color = colors.inkSecondary)
            FtSectionLabel(stringResource(R.string.worth_bought_on))
            FtCard(padding = 0.dp) {
                FtListRow(
                    title = state.purchasedDay?.let { dayMonthYear(dayStartMillis(it)) } ?: stringResource(R.string.paper_pick_day),
                    showChevron = true,
                    onClick = { picking = true },
                )
            }
            FtTextField(
                value = state.priceText, onValueChange = onPrice, label = stringResource(R.string.worth_price),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                prefix = com.example.fieldtrack.designsystem.format.Money.style.symbol,
            )
            FtTextField(
                value = state.resaleText, onValueChange = onResale, label = stringResource(R.string.worth_resale),
                helper = stringResource(R.string.worth_resale_note),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                prefix = com.example.fieldtrack.designsystem.format.Money.style.symbol,
            )
            FtTextField(
                value = state.lifeText, onValueChange = onLife, label = stringResource(R.string.worth_life),
                helper = stringResource(R.string.worth_life_note, Assets.DEFAULT_LIFE_YEARS),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                suffix = stringResource(R.string.worth_years),
            )
            state.worthMinor?.let { worth ->
                FtCard {
                    Text(stringResource(R.string.worth_today), style = FieldTrackTheme.type.overline, color = colors.inkTertiary)
                    Text(money(worth), style = FieldTrackTheme.type.moneyLarge, color = colors.ink, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        FtButton(
            text = stringResource(R.string.paper_save),
            onClick = onSave,
            enabled = state.canEdit && !state.saving,
            modifier = Modifier.fillMaxWidth().padding(dimens.screenPadding),
        )
    }
    if (picking) {
        AnyDaySheet(state.purchasedDay, onDismiss = { picking = false }, onPicked = { onDay(it); picking = false })
    }
}

// -------------------------------------------------------------- the asset register

data class AssetRow(
    val machineId: String,
    val name: String,
    val boughtDay: Long?,
    val priceMinor: Long?,
    val worthMinor: Long?,
    val yearlyMinor: Long?,
)

data class AssetRegisterState(
    val loaded: Boolean = false,
    val rows: List<AssetRow> = emptyList(),
    val totalPriceMinor: Long = 0,
    val totalWorthMinor: Long = 0,
    val canEdit: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AssetRegisterViewModel(app: FieldFlowApplication) : ViewModel() {
    val state: StateFlow<AssetRegisterState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeMachines(s.orgId),
            app.directoryRepository.observeMachineFinance(s.orgId),
        ) { machines, finance ->
            val today = todayEpochDay()
            val byId = finance.associateBy { it.id }
            val rows = machines.map { m ->
                val f = byId[m.id]
                AssetRow(m.id, m.name, f?.purchasedDay, f?.purchaseMinor, Assets.bookValue(f, today), Assets.yearlyDepreciation(f))
            }.sortedWith(compareByDescending<AssetRow> { it.worthMinor ?: -1L }.thenBy { it.name })
            AssetRegisterState(
                loaded = true,
                rows = rows,
                totalPriceMinor = rows.sumOf { it.priceMinor ?: 0L },
                totalWorthMinor = rows.sumOf { it.worthMinor ?: 0L },
                canEdit = Capability.EditMachines in s.can && Capability.SeePeopleCost in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetRegisterState())

    init { viewModelScope.launch { app.sessionStore.load() } }
}

@Composable
fun AssetRegisterScreen(
    state: AssetRegisterState,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FieldTrackTheme.colors
    val dimens = FieldTrackTheme.dimens
    Column(modifier.fillMaxSize().background(colors.paper)) {
        FtTopBar(
            title = stringResource(R.string.assets_title),
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
            com.example.fieldtrack.designsystem.component.FtEmphasisCard {
                Text(stringResource(R.string.assets_worth_today).uppercase(), style = FieldTrackTheme.type.overline, color = colors.onDarkMuted)
                Text(money(state.totalWorthMinor), style = FieldTrackTheme.type.moneyHero, color = colors.onDark, modifier = Modifier.padding(top = 6.dp))
                Text(
                    stringResource(R.string.assets_paid, money(state.totalPriceMinor)),
                    style = FieldTrackTheme.type.bodySmall, color = colors.onDarkMuted, modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (state.rows.isEmpty() && state.loaded) {
                Text(stringResource(R.string.assets_none), style = FieldTrackTheme.type.body, color = colors.inkSecondary)
            }
            FtCard(padding = 0.dp) {
                Column {
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) FtRowDivider()
                        FtListRow(
                            title = row.name,
                            subtitle = if (row.worthMinor != null) {
                                listOfNotNull(
                                    row.boughtDay?.let { stringResource(R.string.assets_bought, dayMonthYear(dayStartMillis(it))) },
                                    row.yearlyMinor?.let { stringResource(R.string.assets_per_year, money(it)) },
                                ).joinToString(" · ")
                            } else stringResource(R.string.assets_add_cost),
                            subtitleMaxLines = 2,
                            trailing = row.worthMinor?.let { w ->
                                { Text(money(w), style = FieldTrackTheme.type.rowTitle, color = colors.ink) }
                            },
                            showChevron = state.canEdit,
                            onClick = if (state.canEdit) ({ onOpen(row.machineId) }) else null,
                        )
                    }
                }
            }
            Spacer(Modifier.height(dimens.screenPadding))
        }
    }
}

/** A paper's name: the owner's own for an "Other" one, ours in the reader's language otherwise. */
@Composable
fun paperName(kind: PaperKind, label: String?): String =
    label?.takeIf { kind == PaperKind.Other && it.isNotBlank() } ?: stringResource(kind.labelRes)
