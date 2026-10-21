package com.example.fieldtrack.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.session.Capability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val amountRupees: Long,
)

data class MoneyOutState(
    val loaded: Boolean = false,
    /** False when this person may not see what anybody is paid. */
    val mayLook: Boolean = false,
    val mayPay: Boolean = false,
    val rows: List<MoneyOutRow> = emptyList(),
    val totalRupees: Long = 0,
    /** Of the total, what no machine or job can carry. */
    val unattributableRupees: Long = 0,
    /**
     * Priced work that has not been paid for.
     *
     * Not added to the total. This screen is the till, and money still in it
     * is not money out — but a wage ledger that cannot say what is owed sends
     * somebody to the wrong figure at the end of the month.
     */
    val owedRupees: Long = 0,
    /** Costs recorded in the field that nobody has put a figure on. */
    val unpricedCount: Int = 0,
)

/**
 * What the business paid out.
 *
 * Gated on its own capability rather than on seeing money generally. Customer
 * balances and a colleague's wages are not the same kind of secret, and the
 * second one cannot be taken back once seen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyOutViewModel(private val app: FieldFlowApplication) : ViewModel() {

    val state: StateFlow<MoneyOutState> = app.sessionStore.session.flatMapLatest { s ->
        val mayLook = Capability.SeePeopleCost in s.can
        combine(
            app.ledgerRepository.observePayouts(s.orgId),
            app.ledgerRepository.observeOwedOnJobs(s.orgId),
            app.ledgerRepository.observeUnpricedJobCostCount(s.orgId),
        ) { payouts, owed, unpriced ->
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
                        amountRupees = it.amountRupees,
                    )
                },
                totalRupees = payouts.sumOf { it.amountRupees },
                // Anything with no job behind it, not only the month-long
                // ones: a day's pay cannot be laid against a machine either.
                // Keyed on settling no job cost, to match what the machine
                // profit screen leaves out. Keying on the job id instead would
                // disagree with it the moment a payment named a job.
                unattributableRupees = payouts
                    .filter { it.jobCostId == null }
                    .sumOf { it.amountRupees },
                owedRupees = owed,
                unpricedCount = unpriced,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyOutState())
}
