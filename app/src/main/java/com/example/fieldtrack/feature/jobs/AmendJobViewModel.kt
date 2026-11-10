package com.example.fieldtrack.feature.jobs

import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.MoneyInput
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.billing.Billing
import com.example.fieldtrack.feature.calculator.cleanQuantity
import com.example.fieldtrack.feature.calculator.countsWhole
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AmendJobState(
    val loaded: Boolean = false,
    val customerName: String = "",
    val workType: String = "",
    val unit: MeasureUnit = MeasureUnit.Acre,
    val billingMode: BillingMode = BillingMode.PerAcre,
    /** What the job was billed at before this correction. */
    val originalQuantity: Double = 0.0,
    val originalRateMinor: Long = 0,
    /** The rounding this job was billed with, kept so a correction rounds the same way. */
    val stepMinor: Long = 100,
    val originalAmountMinor: Long = 0,
    /** The job being corrected, so the preview keeps its minimum, arrival charge and the rest. Null in tests of the arithmetic alone. */
    val job: com.example.fieldtrack.data.db.entity.JobEntity? = null,
    val quantityText: String = "",
    val rateText: String = "",
    val reason: String = "",
    val allowed: Boolean = true,
    val saving: Boolean = false,
) {
    val quantity: Double get() = quantityText.toDoubleOrNull() ?: 0.0
    val rateMinor: Long get() = MoneyInput.parse(rateText) ?: 0L

    /**
     * A fixed-price job is not re-multiplied; its rate is the whole bill.
     *
     * Trip work multiplies here on purpose, and matches: correcting a billed trip
     * job reprices every trip at the corrected rate, so the preview and the result
     * are the same figure.
     */
    val newAmountMinor: Long
        get() {
            val j = job ?: return Billing.amount(billingMode, quantity, rateMinor, stepMinor)
            val corrected = j.copy(
                quantity = quantity,
                rateMinor = rateMinor,
                tripTotalMinor = if (billingMode == BillingMode.PerTrip) {
                    Billing.amount(BillingMode.PerTrip, quantity, rateMinor, j.stepMinor)
                } else j.tripTotalMinor,
            )
            return corrected.amountMinor
        }

    /** Positive when the customer ends up owing more. */
    val deltaMinor: Long get() = newAmountMinor - originalAmountMinor

    /**
     * A reason is required only when money moves. Fixing a typo in a note should not
     * demand an explanation, but moving someone's khata always should — that line is
     * what gets read out when the two of them disagree later.
     */
    val canSave: Boolean
        get() = loaded && allowed && !saving && figuresMakeSense &&
            (deltaMinor == 0L || reason.isNotBlank())

    /**
     * Whether what is typed is a number at all. "4.5.2" read as nought and posted an
     * adjustment taking the whole bill off, so an entry that is not a quantity is
     * refused rather than priced as none.
     */
    val figuresMakeSense: Boolean
        get() = (billingMode == BillingMode.Fixed || quantityText.toDoubleOrNull() != null) &&
            rateText.isNotBlank()
}

/**
 * Correcting a job that has already been billed.
 *
 * The screen never rewrites the original bill; it works out the difference and the
 * repository adds a new khata line for it. So the question this view model answers,
 * over and over as the farmer types, is "what will this do to what they owe me".
 */
class AmendJobViewModel(
    private val app: FieldFlowApplication,
    private val jobId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AmendJobState())
    val state: StateFlow<AmendJobState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val job = app.workRepository.observeJob(s.orgId, jobId).first() ?: return@launch
            val customer = app.directoryRepository.findCustomer(s.orgId, job.customerId)
            _state.update {
                it.copy(
                    loaded = true,
                    customerName = customer?.name.orEmpty(),
                    workType = job.workType,
                    unit = job.unit,
                    billingMode = job.billingMode,
                    originalQuantity = job.quantity,
                    originalRateMinor = job.rateMinor,
                    stepMinor = job.stepMinor,
                    originalAmountMinor = job.amountMinor,
                    job = job,
                    quantityText = trimZero(job.quantity),
                    rateText = MoneyInput.text(job.rateMinor),
                    allowed = Capability.AmendBilledWork in s.can,
                )
            }
        }
    }

    fun setQuantity(v: String) =
        _state.update { it.copy(quantityText = cleanQuantity(v, whole = it.unit.countsWhole)) }

    fun setRate(v: String) =
        _state.update { it.copy(rateText = MoneyInput.clean(v)) }

    fun setReason(v: String) = _state.update { it.copy(reason = v) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        runCatching {
            app.workRepository.amendCompletedJob(
                orgId = s.orgId,
                jobId = jobId,
                can = s.can,
                quantity = current.quantity,
                rateMinor = current.rateMinor,
                byPersonId = s.personId,
                reason = current.reason.ifBlank { com.example.fieldtrack.feature.workLabel(app, current.workType) },
            )
        }.onFailure { _state.update { st -> st.copy(allowed = false) } }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/** Eight digits is a rate of ten crore a unit; more is a slip of the thumb. */
private const val MAX_RATE_DIGITS = 8

/** "3.5" stays, "4.0" becomes "4" — nobody writes the trailing zero by hand. */
private fun trimZero(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else value.toString()
