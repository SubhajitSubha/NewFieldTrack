package com.example.fieldtrack.feature.money

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.ViewModel
import com.example.fieldtrack.designsystem.format.MoneyInput
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
    /** The payer's own name for an "Other" way of paying. */
    val methodLabel: String = "",
    /** What the chosen person has been advanced and not yet settled. */
    val advanceOutstandingMinor: Long = 0,
    /** How much of it this pay takes off, as typed. */
    val recoverText: String = "",
    /** A line to remember it by: what the day was, why a different amount. Optional. */
    val note: String = "",
    val mayPay: Boolean = false,
    val busy: Boolean = false,
    /** The chosen person's UPI id, when they have given one: they can be paid from here. */
    val payeeUpi: String? = null,
    val businessName: String = "",
) {
    val amountMinor: Long get() = MoneyInput.parse(amountText) ?: 0L
    val payingLabourers: Boolean get() = toPersonId == null

    /** The part of the outstanding advance this pay settles: never more than is owed back. */
    val recoverMinor: Long
        get() = if (covers == PayoutCovers.Advance) 0L
        else (MoneyInput.parse(recoverText) ?: 0L).coerceIn(0L, advanceOutstandingMinor)

    val canSave: Boolean
        get() = mayPay && !busy && amountMinor > 0 &&
            (covers != PayoutCovers.Job || jobId != null) &&
            (covers != PayoutCovers.Advance || toPersonId != null)

    /** The UPI link that pays them this amount, when they can be paid by UPI from here. */
    val upiLink: String?
        get() {
            val id = payeeUpi ?: return null
            if (!canSave || !com.example.fieldtrack.data.payments.Upi.looksValid(id)) return null
            if (com.example.fieldtrack.designsystem.format.Money.style.currencyCode != "INR") return null
            val name = people.firstOrNull { it.personId == toPersonId }?.name.orEmpty()
            return com.example.fieldtrack.data.payments.Upi.payLink(id, name, amountMinor, note = businessName)
        }
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
            form.map { it.toPersonId }.distinctUntilChanged().flatMapLatest { personId ->
                if (personId == null) flowOf(0L to null)
                else combine(
                    app.ledgerRepository.observeAdvanceOutstanding(s.orgId, personId),
                    app.peopleRepository.observeDetails(s.orgId, personId),
                ) { advance, details -> advance to details?.payoutUpi }
            },
            app.db.orgDao().observeOrg(s.orgId),
        ) { members, recentJobs, typed, (advanceOutstanding, upi), org ->
            typed.copy(
                loaded = true,
                payeeUpi = upi,
                businessName = org?.name.orEmpty(),
                advanceOutstandingMinor = advanceOutstanding,
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
    fun setAmount(text: String) = form.update { it.copy(amountText = MoneyInput.clean(text)) }
    fun setCovers(covers: PayoutCovers) = form.update { it.copy(covers = covers) }
    fun setJob(jobId: String?) = form.update { it.copy(jobId = jobId) }
    fun setMethod(method: PaymentMethod) = form.update { it.copy(method = method) }

    fun setMethodLabel(text: String) = form.update { it.copy(methodLabel = text) }
    fun setRecover(text: String) = form.update { it.copy(recoverText = MoneyInput.clean(text)) }
    fun setNote(text: String) = form.update { it.copy(note = text.take(120)) }

    /**
     * Saves the payment as made by UPI, once the UPI app said it went (or the person said so).
     * The UPI reference goes in the note, so the payment can be matched to the bank's own record.
     */
    fun saveAsUpi(transactionId: String?, onSaved: () -> Unit) {
        form.update {
            it.copy(
                method = PaymentMethod.Upi,
                note = listOfNotNull(it.note.trim().ifBlank { null }, transactionId?.let { t -> "UPI $t" }).joinToString(" · "),
            )
        }
        save(onSaved)
    }

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
                    amountMinor = now.amountMinor,
                    atMillis = at,
                    byPersonId = s.personId,
                    kind = JobCostKind.Labour,
                    headcount = if (now.payingLabourers) now.headcount else 1,
                    note = toName,
                    method = now.method,
                    methodLabel = now.methodLabel,
                )
            } else {
                app.ledgerRepository.recordPayout(
                    orgId = s.orgId,
                    can = s.can,
                    amountMinor = now.amountMinor,
                    covers = now.covers,
                    atMillis = at,
                    byPersonId = s.personId,
                    toPersonId = now.toPersonId,
                    toName = toName,
                    headcount = if (now.payingLabourers) now.headcount else 1,
                    method = now.method,
                    methodLabel = now.methodLabel,
                    note = now.note.trim().ifBlank { null },
                    recoversAdvanceMinor = now.recoverMinor,
                )
            }
        }.onSuccess { onSaved() }
        form.update { it.copy(busy = false) }
    }
}
