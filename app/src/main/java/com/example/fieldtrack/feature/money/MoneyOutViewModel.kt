package com.example.fieldtrack.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One payment that left the business. */
data class MoneyOutRow(
    val id: String,
    val atMillis: Long,
    val name: String,
    val headcount: Int,
    val covers: PayoutCovers,
    val amountMinor: Long,
    /** Taken back: still listed, struck through, and counted in nothing. */
    val voided: Boolean = false,
)

data class MoneyOutState(
    val loaded: Boolean = false,
    /** False when this person may not see what anybody is paid. */
    val mayLook: Boolean = false,
    val mayPay: Boolean = false,
    val rows: List<MoneyOutRow> = emptyList(),
    val totalMinor: Long = 0,
    /** Of the total, what no machine or job can carry. */
    val unattributableMinor: Long = 0,
    /**
     * Priced work that has not been paid for.
     *
     * Not added to the total. This screen is the till, and money still in it
     * is not money out — but a wage ledger that cannot say what is owed sends
     * somebody to the wrong figure at the end of the month.
     */
    val owedMinor: Long = 0,
    /** Costs recorded in the field that nobody has put a figure on. */
    val unpricedCount: Int = 0,
    /** What is owed to the people whose machines the business uses, and what has been paid. */
    val hire: List<com.example.fieldtrack.data.repository.HireBalance> = emptyList(),
    /** A payment for machine hire being written, if one is. */
    val paying: HirePay? = null,
)

/** A payment for the use of somebody's machine, as it is being typed. */
data class HirePay(
    val balance: com.example.fieldtrack.data.repository.HireBalance,
    val amountText: String,
    val method: com.example.fieldtrack.data.db.PaymentMethod,
) {
    val amountMinor: Long get() = com.example.fieldtrack.designsystem.format.MoneyInput.parse(amountText) ?: 0L
    val canSave: Boolean get() = amountMinor > 0
}

/**
 * What the business paid out.
 *
 * Gated on its own capability rather than on seeing money generally. Customer
 * balances and a colleague's wages are not the same kind of secret, and the
 * second one cannot be taken back once seen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyOutViewModel(private val app: FieldFlowApplication) : ViewModel() {

    private val payForm = kotlinx.coroutines.flow.MutableStateFlow<HirePay?>(null)

    /** Starts a payment to a machine's owner, for what is still owed. */
    fun startHirePay(balance: com.example.fieldtrack.data.repository.HireBalance) {
        payForm.value = HirePay(
            balance = balance,
            amountText = com.example.fieldtrack.designsystem.format.MoneyInput.text(balance.balanceMinor.coerceAtLeast(0)),
            method = com.example.fieldtrack.designsystem.format.Money.methods.firstOrNull()
                ?: com.example.fieldtrack.data.db.PaymentMethod.Cash,
        )
    }

    fun setHireAmount(text: String) = payForm.update { it?.copy(amountText = com.example.fieldtrack.designsystem.format.MoneyInput.clean(text)) }
    fun setHireMethod(method: com.example.fieldtrack.data.db.PaymentMethod) = payForm.update { it?.copy(method = method) }
    fun dismissHirePay() { payForm.value = null }

    fun saveHirePay() = viewModelScope.launch {
        val pay = payForm.value?.takeIf { it.canSave } ?: return@launch
        val s = app.sessionStore.load() ?: return@launch
        runCatching {
            app.ledgerRepository.recordPayout(
                orgId = s.orgId,
                can = s.can,
                amountMinor = pay.amountMinor,
                covers = PayoutCovers.Hire,
                atMillis = System.currentTimeMillis(),
                byPersonId = s.personId,
                toPersonId = pay.balance.personId,
                toName = pay.balance.name,
                method = pay.method,
            )
        }
        payForm.value = null
    }

    val state: StateFlow<MoneyOutState> = app.sessionStore.session.flatMapLatest { s ->
        val mayLook = Capability.SeePeopleCost in s.can
        combine(
            app.ledgerRepository.observePayouts(s.orgId),
            app.ledgerRepository.observeOwedOnJobs(s.orgId),
            app.ledgerRepository.observeUnpricedJobCostCount(s.orgId),
            app.hireRepository.observe(s.orgId),
            payForm,
        ) { payouts, owed, unpriced, hire, paying ->
            // The screen is told nothing it may not show. Filtering here rather
            // than in the view keeps the figures out of the state altogether.
            if (!mayLook) {
                return@combine MoneyOutState(
                    loaded = true,
                    mayLook = false,
                    mayPay = Capability.PayOut in s.can,
                )
            }
            MoneyOutState(
                loaded = true,
                mayLook = true,
                mayPay = Capability.PayOut in s.can,
                rows = payouts.map {
                    MoneyOutRow(
                        id = it.id,
                        atMillis = it.atMillis,
                        name = it.toName.orEmpty(),
                        headcount = it.headcount,
                        covers = it.covers,
                        amountMinor = it.amountMinor,
                        voided = it.voidedAt != null,
                    )
                },
                totalMinor = payouts.filter { it.voidedAt == null }.sumOf { it.amountMinor },
                // Anything with no job behind it, not only the month-long
                // ones: a day's pay cannot be laid against a machine either.
                // Keyed on settling no job cost, to match what the machine
                // profit screen leaves out. Keying on the job id instead would
                // disagree with it the moment a payment named a job.
                // Money paid for the use of a machine is the exception: it settles what the
                // machine's own work earned its owner, so it is laid against that machine.
                unattributableMinor = payouts
                    .filter { it.voidedAt == null && it.jobCostId == null && it.covers != PayoutCovers.Hire }
                    .sumOf { it.amountMinor },
                owedMinor = owed,
                unpricedCount = unpriced,
                hire = hire,
                paying = paying,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyOutState())
}
