package com.example.fieldtrack.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.fieldtrack.data.db.Clock

/** Somebody on the team who could be paid. */
data class PayablePerson(val personId: String, val name: String)

/** A job a payment could be laid against. */
data class PayableJob(val jobId: String, val label: String)

data class PayOutState(
    val loaded: Boolean = false,
    val people: List<PayablePerson> = emptyList(),
    val jobs: List<PayableJob> = emptyList(),
    /** Null means labourers — nobody with an account here. */
    val toPersonId: String? = null,
    val headcount: Int = 2,
    val amountText: String = "",
    val covers: PayoutCovers = PayoutCovers.Day,
    val jobId: String? = null,
    val method: PaymentMethod = PaymentMethod.Cash,
    val mayPay: Boolean = false,
    val busy: Boolean = false,
) {
    val amountRupees: Long get() = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val payingLabourers: Boolean get() = toPersonId == null
    val canSave: Boolean
        get() = mayPay && !busy && amountRupees > 0 &&
            (covers != PayoutCovers.Job || jobId != null)
}

/**
 * Recording money that left the business.
 *
 * One form for everybody paid. A permanent driver, a man taken on for two days
 * and three labourers nobody wrote a name for differ only in whether the person
 * has an account here — so the only question the form asks about that is who,
 * and it accepts "nobody in particular, there were three of them".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PayOutViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val form = MutableStateFlow(PayOutState())

    val state: StateFlow<PayOutState> = app.sessionStore.session.flatMapLatest { s ->
        combine(
            app.teamRepository.observeTeam(s.orgId),
            app.workRepository.observeRecent(s.orgId),
            form,
        ) { members, recentJobs, typed ->
            typed.copy(
                loaded = true,
                people = members.map { PayablePerson(it.personId, it.displayName) },
                jobs = recentJobs.map {
                    PayableJob(it.id, it.workType)
                },
                mayPay = Capability.PayOut in s.can,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayOutState())

    fun setPerson(personId: String?) = form.update { it.copy(toPersonId = personId) }
    fun setHeadcount(n: Int) = form.update { it.copy(headcount = n.coerceIn(1, 99)) }
    fun setAmount(text: String) = form.update { it.copy(amountText = text.filter { c -> c.isDigit() }) }
    fun setCovers(covers: PayoutCovers) = form.update { it.copy(covers = covers) }
    fun setJob(jobId: String?) = form.update { it.copy(jobId = jobId) }
    fun setMethod(method: PaymentMethod) = form.update { it.copy(method = method) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val now = state.value
        if (!now.canSave) return@launch
        val s = app.sessionStore.load() ?: return@launch
        form.update { it.copy(busy = true) }
        val at = Clock.System.now()
        val toName = now.toPersonId
            ?.let { id -> now.people.firstOrNull { it.personId == id }?.name }
        val jobId = now.jobId.takeIf { now.covers == PayoutCovers.Job }
        runCatching {
            if (jobId != null) {
                // Money laid against a job goes in by the same door as money
                // entered on the job itself: it is written to that job's cost
                // sheet, and the payment settles it. Writing a bare payment
                // here instead would leave a cost no screen totals, or — once
                // the job sheet counted it too — the same rupees twice.
                app.ledgerRepository.payForJob(
                    orgId = s.orgId,
                    can = s.can,
                    jobId = jobId,
                    amountRupees = now.amountRupees,
                    atMillis = at,
                    byPersonId = s.personId,
                    kind = JobCostKind.Labour,
                    headcount = if (now.payingLabourers) now.headcount else 1,
                    note = toName,
                    method = now.method,
                )
            } else {
                app.ledgerRepository.recordPayout(
                    orgId = s.orgId,
                    can = s.can,
                    amountRupees = now.amountRupees,
                    covers = now.covers,
                    atMillis = at,
                    byPersonId = s.personId,
                    toPersonId = now.toPersonId,
                    toName = toName,
                    headcount = if (now.payingLabourers) now.headcount else 1,
                    method = now.method,
                )
            }
        }.onSuccess { onSaved() }
        form.update { it.copy(busy = false) }
    }
}
