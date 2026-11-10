package com.example.fieldtrack.feature.rates

import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.workTypeOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One saved rate, ready to draw. */
data class RateRow(
    val id: String,
    val workType: WorkTypeOption,
    val billingMode: BillingMode,
    val unit: MeasureUnit,
    val amountMinor: Long,
    val label: String?,
    /** The rate as stored, for the terms that come with it. */
    val rate: RateEntity? = null,
)

data class RateCardState(
    val loaded: Boolean = false,
    val rates: List<RateRow> = emptyList(),
    /** How many customers have been given a price of their own. */
    val customerDeals: Int = 0,
    val perCustomer: Boolean = true,
    val canEdit: Boolean = true,
    /** False for a driver the owner did not trust with money. */
    val canSeeRates: Boolean = true,
    /** Every work there is to price: ours, the business's own names, then "Other". */
    val works: List<WorkTypeOption> = com.example.fieldtrack.feature.WorkCatalog.all(emptyList()),
)

/** What is being typed into the add/edit sheet, if anything. */
data class RateDraft(
    /** Null when adding; the row's id when editing one that already exists. */
    val id: String? = null,
    val workType: WorkTypeOption = workTypeOptions.first(),
    val billingMode: BillingMode = workTypeOptions.first().defaultMode,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val amountText: String = "",
    val label: String = "",
    /** What "Other" work is called. Empty keeps "Other". */
    val otherName: String = "",
    /** The terms this price usually comes with: minimum, arrival, diesel, waiting. */
    val terms: com.example.fieldtrack.feature.work.TermsDraft = com.example.fieldtrack.feature.work.TermsDraft(),
) {
    val amountMinor: Long get() = MoneyInput.parse(amountText) ?: 0L
    val canSave: Boolean get() = amountMinor > 0
    val editing: Boolean get() = id != null
}

/**
 * The price list a contractor works from.
 *
 * Rates have always been learned quietly from finished jobs. This is the first place
 * they can be seen, corrected, or set before the first job of a season — which is
 * what a contractor actually does when the rate for paddy goes up and he has not cut
 * any yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RateCardViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val _draft = MutableStateFlow<RateDraft?>(null)
    val draft: StateFlow<RateDraft?> = _draft.asStateFlow()

    /** The rate the user has asked to remove, held until they confirm. */
    private val _confirmDelete = MutableStateFlow<RateRow?>(null)
    val confirmDelete: StateFlow<RateRow?> = _confirmDelete.asStateFlow()

    val state: StateFlow<RateCardState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeRateCard(s.orgId),
            app.directoryRepository.observeCustomerRates(s.orgId),
            app.sessionStore.rememberPerCustomer,
            app.directoryRepository.observeWorkHistory(s.orgId),
        ) { card, customerRates, perCustomer, history ->
            RateCardState(
                works = com.example.fieldtrack.feature.WorkCatalog.all(history.names),
                loaded = true,
                rates = card.mapNotNull { it.asRow() },
                // Counted by customer, not by row: one farmer with three agreed
                // rates is one deal to the person reading this, not three.
                customerDeals = customerRates.mapNotNull { it.customerId }.distinct().size,
                perCustomer = perCustomer,
                canEdit = Capability.EditBusiness in s.can,
                canSeeRates = s.canSeeMoney,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RateCardState())

    /** What the business measures land in, so a new rate is not offered per acre to a bigha business. */
    private var landUnit: MeasureUnit = MeasureUnit.Acre

    init {
        viewModelScope.launch { app.sessionStore.load()?.let { landUnit = it.landUnit } }
    }

    private fun unitFor(mode: BillingMode) = com.example.fieldtrack.feature.work.resolveUnit(mode, landUnit)

    fun add() {
        val work = workTypeOptions.first()
        _draft.value = RateDraft(
            workType = work,
            billingMode = work.defaultMode,
            unit = unitFor(work.defaultMode),
        )
    }

    fun edit(row: RateRow) {
        _draft.value = RateDraft(
            id = row.id,
            workType = row.workType,
            billingMode = row.billingMode,
            unit = row.unit,
            amountText = MoneyInput.text(row.amountMinor),
            label = row.label.orEmpty(),
            terms = com.example.fieldtrack.feature.work.TermsDraft.of(row.rate),
        )
    }

    fun dismissDraft() {
        _draft.value = null
    }

    fun setWorkType(option: WorkTypeOption) = _draft.update {
        it?.copy(
            workType = option,
            // The old billing mode may make no sense for this work — you cannot
            // charge rotavating by the trip — so fall back to what does.
            billingMode = if (it.billingMode in option.billingModes) it.billingMode
            else option.defaultMode,
            unit = if (it.billingMode in option.billingModes) it.unit
            else unitFor(option.defaultMode),
        )
    }

    fun setBillingMode(mode: BillingMode) = _draft.update {
        if (it == null || mode !in it.workType.billingModes) it
        else it.copy(billingMode = mode, unit = unitFor(mode))
    }

    fun setUnit(unit: MeasureUnit) = _draft.update { it?.copy(unit = unit) }

    fun setAmount(value: String) = _draft.update {
        it?.copy(amountText = MoneyInput.clean(value))
    }

    fun setLabel(value: String) = _draft.update { it?.copy(label = value) }

    fun nameOther(value: String) = _draft.update { it?.copy(otherName = value.take(com.example.fieldtrack.feature.WorkCatalog.MAX_NAME)) }

    fun setTerms(terms: com.example.fieldtrack.feature.work.TermsDraft) = _draft.update { it?.copy(terms = terms) }

    fun saveDraft() = viewModelScope.launch {
        val draft = _draft.value ?: return@launch
        if (!draft.canSave) return@launch
        val s = app.sessionStore.load() ?: return@launch
        val workKey = if (draft.workType.key == com.example.fieldtrack.feature.WorkCatalog.OTHER) {
            com.example.fieldtrack.feature.WorkCatalog.keyForName(
                draft.otherName, state.value.works.map { it.key },
            ) { app.getString(it.labelRes) }
        } else draft.workType.key
        app.directoryRepository.setRate(
            orgId = s.orgId,
            workType = workKey,
            billingMode = draft.billingMode,
            unit = draft.unit,
            amountMinor = draft.amountMinor,
            label = draft.label,
            rateId = draft.id,
            terms = draft.terms.toTerms(),
        )
        _draft.value = null
    }

    fun askDelete(row: RateRow) {
        _confirmDelete.value = row
    }

    fun dismissDelete() {
        _confirmDelete.value = null
    }

    fun confirmDeleteNow() = viewModelScope.launch {
        val row = _confirmDelete.value ?: return@launch
        val s = app.sessionStore.load() ?: return@launch
        app.directoryRepository.deleteRate(s.orgId, row.id)
        _confirmDelete.value = null
        _draft.value = null
    }

    fun setPerCustomer(enabled: Boolean) = app.sessionStore.setRememberPerCustomer(enabled)

    /**
     * A saved rate as a row. Work this build has no word for is the business's own name for
     * it, and is shown by that name: hiding it hid rates the business had set itself.
     */
    private fun RateEntity.asRow(): RateRow? {
        val option = com.example.fieldtrack.feature.WorkCatalog.option(workType)
        return RateRow(
            id = id,
            workType = option,
            billingMode = billingMode,
            unit = unit,
            amountMinor = amountMinor,
            label = label,
            rate = this,
        )
    }
}
