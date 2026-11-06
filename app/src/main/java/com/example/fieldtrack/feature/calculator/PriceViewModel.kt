package com.example.fieldtrack.feature.calculator

import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.billing.Billing
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.entity.RateEntity
import com.example.fieldtrack.feature.WorkTypeOption
import com.example.fieldtrack.feature.unitsFor
import com.example.fieldtrack.feature.work.RateSuggestion
import com.example.fieldtrack.feature.work.resolveUnit
import com.example.fieldtrack.feature.workTypeOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What is typed into the calculator. Nothing here is ever saved.
 *
 * A sum, not a record: no customer, no job, no bill, nothing queued to send. It
 * exists for the call that says "what would it come to for three bigha?", which
 * a contractor otherwise answers with a pencil and a guess.
 */
data class PriceInput(
    val workType: WorkTypeOption = workTypeOptions.first(),
    val billingMode: BillingMode = workTypeOptions.first().defaultMode,
    val unit: MeasureUnit = MeasureUnit.Acre,
    val rateText: String = "",
    /**
     * True while the rate shown is one the rate card supplied, not one typed.
     *
     * Decides what happens to it when the question changes. A rate somebody typed
     * is theirs and stays, because "what if ₹1,200" is the whole point. A rate the
     * card supplied belonged to the question it answered: carried over to "per
     * hour" from "per bigha" it would be a number from another question, so it
     * goes when the card has nothing for the new one.
     */
    val rateFromCard: Boolean = false,
    /** How much work, for everything except hourly work. */
    val quantityText: String = "",
    /** Hourly work is said as hours and minutes, which is how anyone says it. */
    val hoursText: String = "",
    val minutesText: String = "",
)

data class PriceState(
    val loaded: Boolean = false,
    /** False for somebody who may not see rates: a price is money, and so is a sum of one. */
    val allowed: Boolean = true,
    /** What this business measures land in, which names the area billing mode. */
    val landUnit: MeasureUnit = MeasureUnit.Acre,
    val input: PriceInput = PriceInput(),
    /** Rates from the rate card that fit exactly what is chosen, offered as one tap each. */
    val usualRates: List<RateSuggestion> = emptyList(),
) {
    val rateMinor: Long get() = MoneyInput.parse(input.rateText) ?: 0L

    /** True when the figure is a count of whole things — trips, bags, pieces. */
    val countsWhole: Boolean get() = input.unit.countsWhole

    val byTheHour: Boolean get() = input.billingMode == BillingMode.PerHour

    val fixedPrice: Boolean get() = input.billingMode == BillingMode.Fixed

    /** Seconds of work, for the hourly case; zero otherwise. */
    val seconds: Long
        get() {
            val h = input.hoursText.toLongOrNull() ?: 0L
            val m = input.minutesText.toLongOrNull() ?: 0L
            return h * 3600 + m * 60
        }

    /** The amount of work, in the unit of the chosen mode. Zero while nothing is entered. */
    val quantity: Double
        get() = if (byTheHour) seconds / 3600.0 else input.quantityText.toDoubleOrNull() ?: 0.0

    /**
     * What the bill would come to, or null while there is not yet enough to say.
     *
     * Null rather than zero: "₹0" reads as an answer, and a screen that has been
     * given half a question has not got one.
     */
    val amountMinor: Long?
        get() {
            if (rateMinor <= 0) return null
            if (!fixedPrice && quantity <= 0.0) return null
            return Billing.amount(input.billingMode, quantity, rateMinor, Money.style.stepMinor)
        }
}

/** Units that only come in whole numbers: nobody carries 2.5 trips or sells 1.5 bags. */
val MeasureUnit.countsWhole: Boolean
    get() = this == MeasureUnit.Trip || this == MeasureUnit.Bag || this == MeasureUnit.Piece

/**
 * The price calculator: work, how it is charged, how much, at what rate.
 *
 * Starts from the rate card, since the rate is the one number a contractor
 * already knows he has written down somewhere, and lets every figure be
 * overwritten, because the point is "what if". The sum itself is
 * [Billing.amount], the same one a real bill uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PriceViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val input = MutableStateFlow(PriceInput())

    /**
     * False until the first look at the business is done. Without it the screen
     * would draw once in acres and once in the business's own unit, which for a
     * contractor who works in bigha is a flash of the wrong word.
     */
    private val ready = MutableStateFlow(false)

    /** The general rate card, as last seen, so a change of work can prefill from it. */
    private var card: List<RateEntity> = emptyList()
    private var landUnit: MeasureUnit = MeasureUnit.Acre

    val state: StateFlow<PriceState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.directoryRepository.observeRateCard(s.orgId),
            input,
            ready,
        ) { rates, typed, isReady ->
            card = rates
            landUnit = s.landUnit
            PriceState(
                loaded = isReady,
                allowed = s.canSeeMoney,
                landUnit = s.landUnit,
                input = typed,
                usualRates = typed.fittingRates(rates),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PriceState())

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            // The business's own land unit and its own rate, not the built-in acre:
            // a contractor who works in bigha is never shown acres.
            val rates = app.directoryRepository.observeRateCard(s.orgId).first()
            card = rates
            landUnit = s.landUnit
            input.update { it.copy(unit = resolveUnit(it.billingMode, s.landUnit)).withRateFrom(rates) }
            ready.value = true
        }
    }

    fun selectWorkType(option: WorkTypeOption) = input.update {
        it.copy(
            workType = option,
            billingMode = option.defaultMode,
            unit = resolveUnit(option.defaultMode, landUnit),
        ).withRateFrom(card).recleaned()
    }

    fun setBillingMode(mode: BillingMode) = input.update {
        // Refuse a mode this work is not sold by, as the job flow does: the picker
        // already hides them, and this is what holds if something else calls.
        if (mode !in it.workType.billingModes) it
        else it.copy(billingMode = mode, unit = resolveUnit(mode, landUnit)).withRateFrom(card).recleaned()
    }

    fun setUnit(unit: MeasureUnit) = input.update {
        if (unit !in unitsFor(it.billingMode)) it else it.copy(unit = unit).withRateFrom(card).recleaned()
    }

    fun setRate(text: String) = input.update {
        it.copy(rateText = MoneyInput.clean(text), rateFromCard = false)
    }

    /** One of the card's rates, tapped. */
    fun pickRate(amountMinor: Long) = input.update { it.copy(rateText = MoneyInput.text(amountMinor), rateFromCard = true) }

    fun setQuantity(text: String) = input.update {
        it.copy(quantityText = cleanQuantity(text, whole = it.unit.countsWhole))
    }

    fun setHours(text: String) = input.update { it.copy(hoursText = text.filter(Char::isDigit).take(MAX_HOURS_DIGITS)) }

    fun setMinutes(text: String) = input.update { it.copy(minutesText = text.filter(Char::isDigit).take(MAX_MINUTES_DIGITS)) }

    private companion object {
        const val MAX_RATE_DIGITS = 8
        const val MAX_HOURS_DIGITS = 4
        const val MAX_MINUTES_DIGITS = 4
    }
}

/**
 * Cleans the amount typed against the unit now chosen. "2.5" typed under acres and
 * then switched to trips would otherwise stay, and be priced as two and a half loads.
 */
internal fun PriceInput.recleaned(): PriceInput {
    if (!unit.countsWhole) return this
    // Cut at the point rather than letting the cleaner drop it: "2.5" is two loads, not 25.
    val whole = quantityText.substringBefore('.').substringBefore(',')
    return copy(quantityText = cleanQuantity(whole, whole = true))
}

/**
 * The card's rates for exactly what is chosen — same work, same way of charging,
 * same unit. A per-hour rate offered under "per bigha" would be a number from
 * another question.
 */
internal fun PriceInput.fittingRates(card: List<RateEntity>): List<RateSuggestion> =
    card.filter {
        it.workType == workType.key && it.billingMode == billingMode && it.unit == unit
    }.map { RateSuggestion(it.amountMinor, it.label) }

/**
 * Fills the rate from the card when it has one for what is chosen. Where it has
 * none, a rate typed by hand stays and a rate the card gave earlier goes — see
 * [PriceInput.rateFromCard].
 */
internal fun PriceInput.withRateFrom(card: List<RateEntity>): PriceInput {
    val best = fittingRates(card).firstOrNull()
        ?: return if (rateFromCard) copy(rateText = "", rateFromCard = false) else this
    return copy(rateText = MoneyInput.text(best.amountMinor), rateFromCard = true)
}

/**
 * Keeps what can be part of a quantity: digits, one decimal point, two decimals.
 * A comma is taken as the point, because that is what a Hindi or Punjabi keyboard
 * offers.
 */
internal fun cleanQuantity(text: String, whole: Boolean): String {
    val normal = text.replace(',', '.').filter { it.isDigit() || (!whole && it == '.') }
    val point = normal.indexOf('.')
    if (point < 0) return normal.take(MAX_INTEGER_DIGITS)
    val integer = normal.substring(0, point).take(MAX_INTEGER_DIGITS)
    val decimals = normal.substring(point + 1).filter(Char::isDigit).take(2)
    return "$integer.$decimals"
}

private const val MAX_INTEGER_DIGITS = 6
