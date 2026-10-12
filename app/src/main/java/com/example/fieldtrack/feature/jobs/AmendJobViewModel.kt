package com.example.fieldtrack.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
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
    val originalRateRupees: Long = 0,
    val originalAmountRupees: Long = 0,
    val quantityText: String = "",
    val rateText: String = "",
    val reason: String = "",
    val allowed: Boolean = true,
    val saving: Boolean = false,
) {
    val quantity: Double get() = quantityText.toDoubleOrNull() ?: 0.0
    val rateRupees: Long get() = rateText.filter { it.isDigit() }.toLongOrNull() ?: 0L

    /**
     * A fixed-price job is not re-multiplied; its rate is the whole bill.
     *
     * Trip work multiplies here on purpose, and matches: correcting a billed trip
     * job reprices every trip at the corrected rate, so the preview and the result
     * are the same figure.
     */
    val newAmountRupees: Long
        get() = if (billingMode == BillingMode.Fixed) rateRupees
        else Math.round(quantity * rateRupees)

    /** Positive when the customer ends up owing more. */
    val deltaRupees: Long get() = newAmountRupees - originalAmountRupees

    /**
     * A reason is required only when money moves. Fixing a typo in a note should not
     * demand an explanation, but moving someone's khata always should — that line is
     * what gets read out when the two of them disagree later.
     */
    val canSave: Boolean
        get() = loaded && allowed && !saving &&
            (deltaRupees == 0L || reason.isNotBlank())
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
                    originalRateRupees = job.rateRupees,
                    originalAmountRupees = job.amountRupees,
                    quantityText = trimZero(job.quantity),
                    rateText = job.rateRupees.toString(),
                    allowed = Capability.AmendBilledWork in s.can,
                )
            }
        }
    }

    fun setQuantity(v: String) =
        _state.update { it.copy(quantityText = v.filter { c -> c.isDigit() || c == '.' }) }

    fun setRate(v: String) =
        _state.update { it.copy(rateText = v.filter { c -> c.isDigit() }) }

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
                rateRupees = current.rateRupees,
                byPersonId = s.personId,
                reason = current.reason.ifBlank { current.workType },
            )
        }.onFailure { _state.update { st -> st.copy(allowed = false) } }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}

/** "3.5" stays, "4.0" becomes "4" — nobody writes the trailing zero by hand. */
private fun trimZero(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else value.toString()
