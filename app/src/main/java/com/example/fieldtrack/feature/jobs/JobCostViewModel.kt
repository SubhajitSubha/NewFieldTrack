package com.example.fieldtrack.feature.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JobCostState(
    val loaded: Boolean = false,
    /** Set when paying or pricing something already recorded. */
    val existingId: String? = null,
    val kind: JobCostKind = JobCostKind.Labour,
    /** Null means nobody counted — a real answer, not a missing one. */
    val headcount: Int? = DEFAULT_HEADS,
    val amountText: String = "",
    val note: String = "",
    val paid: Boolean = true,
    /** False for somebody who may record who worked but not what it cost. */
    val canPrice: Boolean = false,
    val saving: Boolean = false,
) {
    val amountRupees: Long
        get() = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0

    /**
     * What one pair of hands came to.
     *
     * Only above one head, because "₹500 each" for a single labourer is the
     * amount again in different words. Integer division, and the screen says
     * "about" when it does not divide evenly rather than printing a figure
     * that cannot be multiplied back.
     */
    val eachRupees: Long?
        get() = headcount
            // Only wages divide by heads. A tea bill split four ways is a
            // number nobody asked for, and the headcount it uses is the one
            // left over from the Labourers option this entry is not.
            ?.takeIf { kind == JobCostKind.Labour && it > 1 && amountRupees > 0 }
            ?.let { amountRupees / it }

    val eachExact: Boolean
        get() = headcount?.let { amountRupees % it == 0L } ?: true

    val canSave: Boolean
        get() = !saving && when {
            // Nothing to say but a figure, once the cost itself is recorded.
            existingId != null -> amountRupees > 0
            // Other spending with no word on what it was for is a mystery in
            // the accounts a month later, so the note is not optional.
            canPrice && kind == JobCostKind.Other -> amountRupees > 0 && note.isNotBlank()
            canPrice -> amountRupees > 0
            // A driver is only ever saying how many worked.
            else -> true
        }

    companion object {
        /** Two, because one labourer is rare and nought is not an entry. */
        const val DEFAULT_HEADS = 2
    }
}

/**
 * Recording what a job took beyond the machine.
 *
 * The same screen for the driver standing in the field and the owner holding
 * the cash, because it is the same fact arriving in two halves. Without
 * [Capability.PayOut] it collapses to the one question that person can answer
 * — how many worked — and never shows a rupee.
 */
class JobCostViewModel(
    private val app: FieldFlowApplication,
    private val jobId: String,
    private val costId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(JobCostState())
    val state: StateFlow<JobCostState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = app.sessionStore.load() ?: return@launch
            val canPrice = Capability.PayOut in s.can
            val existing = costId?.let { app.db.orgDao().findJobCost(s.orgId, it) }
            _state.update {
                it.copy(
                    loaded = true,
                    canPrice = canPrice,
                    existingId = existing?.id,
                    kind = existing?.kind ?: JobCostKind.Labour,
                    headcount = existing?.headcount ?: JobCostState.DEFAULT_HEADS,
                    amountText = existing?.amountRupees?.toString().orEmpty(),
                    note = existing?.note.orEmpty(),
                )
            }
        }
    }

    fun setKind(kind: JobCostKind) = _state.update { it.copy(kind = kind) }

    fun setHeadcount(value: Int) = _state.update {
        it.copy(headcount = value.coerceIn(1, 200))
    }

    /** Says nobody counted, rather than inventing a number that looks counted. */
    fun setUncounted() = _state.update { it.copy(headcount = null) }

    fun setCounted() = _state.update { it.copy(headcount = JobCostState.DEFAULT_HEADS) }

    fun setAmount(value: String) = _state.update {
        it.copy(amountText = value.filter { c -> c.isDigit() })
    }

    fun setNote(value: String) = _state.update { it.copy(note = value) }

    fun setPaid(value: Boolean) = _state.update { it.copy(paid = value) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _state.value
        if (!current.canSave) return@launch
        _state.update { it.copy(saving = true) }
        val s = app.sessionStore.load() ?: run {
            _state.update { it.copy(saving = false) }
            return@launch
        }
        val at = System.currentTimeMillis()
        val note = current.note.trim().ifBlank { null }

        runCatching {
            val existingId = current.existingId
            when {
                // Pricing or paying something already on the job. The cost is
                // only rewritten when the figure actually changed, so paying a
                // second instalment does not re-state what the job cost.
                existingId != null -> {
                    val before = app.db.orgDao().findJobCost(s.orgId, existingId)
                    if (before?.amountRupees != current.amountRupees) {
                        app.ledgerRepository.priceJobCost(
                            orgId = s.orgId,
                            can = s.can,
                            costId = existingId,
                            amountRupees = current.amountRupees,
                            note = note,
                        )
                    }
                    if (current.paid) {
                        app.ledgerRepository.settleJobCost(
                            orgId = s.orgId,
                            can = s.can,
                            costId = existingId,
                            amountRupees = current.amountRupees,
                            atMillis = at,
                            byPersonId = s.personId,
                        )
                    }
                }

                current.canPrice && current.paid -> app.ledgerRepository.payForJob(
                    orgId = s.orgId,
                    can = s.can,
                    jobId = jobId,
                    amountRupees = current.amountRupees,
                    atMillis = at,
                    byPersonId = s.personId,
                    kind = current.kind,
                    headcount = current.headcount.takeIf { current.kind == JobCostKind.Labour },
                    note = note,
                )

                else -> app.ledgerRepository.recordJobCost(
                    orgId = s.orgId,
                    can = s.can,
                    jobId = jobId,
                    kind = current.kind,
                    headcount = current.headcount.takeIf { current.kind == JobCostKind.Labour },
                    // Null, not nought, when this person may not price it.
                    amountRupees = current.amountRupees.takeIf { current.canPrice && it > 0 },
                    note = note,
                    byPersonId = s.personId,
                    atMillis = at,
                )
            }
        }.onFailure {
            _state.update { st -> st.copy(saving = false) }
            return@launch
        }
        _state.update { it.copy(saving = false) }
        onSaved()
    }
}
