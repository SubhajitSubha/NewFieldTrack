package com.example.fieldtrack.data.billing

import com.example.fieldtrack.data.db.entity.LedgerEntryEntity
import com.example.fieldtrack.feature.home.OVERDUE_DAYS
import com.example.fieldtrack.feature.home.debtsByAge

/**
 * What to say before more work is booked or started for somebody who already owes.
 *
 * Never a refusal: a contractor knows the farmer, the harvest is in a week, and work that
 * has to be turned away is not the app's call to make. It is said once, plainly, at the
 * moment it is still possible to decide.
 */
sealed interface CreditWarning {
    /** They owe more than the limit the owner set for them. */
    data class OverLimit(val owedMinor: Long, val limitMinor: Long) : CreditWarning

    /** They have owed for longer than money is normally left. */
    data class Late(val owedMinor: Long, val days: Int) : CreditWarning
}

object CreditCheck {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * The warning for a customer with these ledger lines and this limit, or null when there
     * is nothing to say. Over the limit wins over late: it is the harder fact.
     */
    fun warning(entries: List<LedgerEntryEntity>, limitMinor: Long?, now: Long): CreditWarning? {
        val debt = debtsByAge(entries).firstOrNull() ?: return null
        if (limitMinor != null && limitMinor > 0 && debt.amountMinor > limitMinor) {
            return CreditWarning.OverLimit(debt.amountMinor, limitMinor)
        }
        val days = ((now - debt.oldestMillis) / DAY_MILLIS).toInt()
        return if (days > OVERDUE_DAYS) CreditWarning.Late(debt.amountMinor, days) else null
    }
}
