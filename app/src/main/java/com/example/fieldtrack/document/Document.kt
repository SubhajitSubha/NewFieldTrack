package com.example.fieldtrack.document

/**
 * What a printed document says, with nothing about how it is drawn.
 *
 * Deliberately plain data with no Android types in it: the arithmetic and the wording
 * decisions are the part that can be wrong in a way a customer argues about, so they
 * are testable without rendering anything.
 */

/** The business, as it appears in a letterhead. Blank lines are simply not drawn. */
data class Letterhead(
    val businessName: String,
    val addressLine: String? = null,
    val phone: String? = null,
    val gstin: String? = null,
    val upiId: String? = null,
)

/** Who the document is for. */
data class Party(
    val name: String,
    val addressLine: String? = null,
    val phone: String? = null,
)

/** One priced line of work. */
data class WorkLine(
    val title: String,
    val detail: String? = null,
    val measure: String? = null,
    val rate: String? = null,
    val amountRupees: Long,
)

/** One line of the running khata: what happened and where the balance stood after. */
data class StatementLine(
    val date: String,
    val detail: String,
    val chargedRupees: Long?,
    val paidRupees: Long?,
    val balanceAfterRupees: Long,
    val isBroughtForward: Boolean = false,
)

/** One unpaid charge on a dues list. */
data class DueLine(
    val date: String,
    val title: String,
    val detail: String?,
    val age: String,
    val amountRupees: Long,
)

/** Money received against a bill. */
data class PaymentLine(
    val date: String,
    val method: String,
    val amountRupees: Long,
)

/**
 * The three documents this app produces.
 *
 * They share a letterhead and a footer and differ in the middle, which is why they
 * are one sealed type rather than three unrelated builders that would drift apart the
 * first time the business address changed shape.
 */
sealed interface Document {
    val letterhead: Letterhead
    val customer: Party
    /** The file name offered when this is saved or shared. */
    val fileName: String

    /** One job, priced, with what was paid and what is left. */
    data class Bill(
        override val letterhead: Letterhead,
        override val customer: Party,
        val billNumber: String?,
        val dateLabel: String,
        val workedOnLabel: String?,
        val machineLabel: String?,
        val lines: List<WorkLine>,
        val previousBalanceRupees: Long,
        val payments: List<PaymentLine>,
        val balanceRupees: Long,
        val balanceInWords: String,
    ) : Document {
        val thisBillRupees: Long get() = lines.sumOf { it.amountRupees }
        val totalRupees: Long get() = thisBillRupees + previousBalanceRupees
        override val fileName: String
            get() = "Bill-${billNumber?.replace('/', '-') ?: dateLabel}-${customer.name}"
                .sanitisedFileName()
    }

    /** Everything that happened on an account over a period, with a running balance. */
    data class Statement(
        override val letterhead: Letterhead,
        override val customer: Party,
        val periodLabel: String,
        val printedLabel: String,
        val openingRupees: Long,
        val billedRupees: Long,
        val receivedRupees: Long,
        val closingRupees: Long,
        val closingInWords: String,
        val lines: List<StatementLine>,
        /**
         * True when this lists only money received.
         *
         * The running column then counts up what they have paid rather than what
         * they owe, because a column of balances on a page with no charges on it
         * reads as though the charges were hidden.
         */
        val paymentsOnly: Boolean = false,
    ) : Document {
        override val fileName: String
            get() = (if (paymentsOnly) "Payments" else "Statement")
                .let { "$it-$periodLabel-${customer.name}" }
                .sanitisedFileName()
    }

    /** Only what has not been paid for, which is what gets sent when chasing money. */
    data class Dues(
        override val letterhead: Letterhead,
        override val customer: Party,
        val asOnLabel: String,
        val lines: List<DueLine>,
        val totalRupees: Long,
        val totalInWords: String,
        /** Credit where it is due, so the demand does not look like it ignores them. */
        val receivedNote: String?,
    ) : Document {
        override val fileName: String
            get() = "Amount-due-$asOnLabel-${customer.name}".sanitisedFileName()
    }
}

/** Keeps a customer's name from turning into a path or an unopenable file. */
private fun String.sanitisedFileName(): String =
    replace(Regex("[^A-Za-z0-9\\-_ ]"), "").trim().replace(Regex("\\s+"), "-").take(80)
