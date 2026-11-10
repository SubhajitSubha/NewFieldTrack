package com.example.fieldtrack.document

import android.content.Context
import com.example.fieldtrack.R

/**
 * Every fixed word on a printed page, so a bill can be in the language of the person who gets it.
 *
 * The defaults are English and are what a test sees; [of] reads the same words from the string
 * resources of whichever language is asked for (a test keeps the two in step). Phrases that carry
 * a figure or a name are templates with `%1$s` slots, filled by the page, never glued in English
 * word order.
 */
data class DocLabels(
    val languageTag: String = "en",
    val rightToLeft: Boolean = false,
    /**
     * Whether the amount is also written out in words. Only English has the words so far: a Hindi bill
     * with a line of English number-words under the total would be wrong, so there it is left off.
     */
    val showAmountInWords: Boolean = true,

    val kindBill: String = "BILL",
    val kindTaxInvoice: String = "TAX INVOICE",
    val kindStatement: String = "STATEMENT",
    val kindAmountDue: String = "AMOUNT DUE",
    val kindReceipt: String = "RECEIPT",
    val kindIncome: String = "INCOME STATEMENT",

    val billTo: String = "BILL TO",
    val workDoneOn: String = "WORK DONE ON",
    val colWork: String = "WORK",
    val colMeasure: String = "MEASURE",
    val colRate: String = "RATE",
    val colAmount: String = "AMOUNT",
    val thisBill: String = "This bill",
    val balanceBefore: String = "Balance before this bill",
    val total: String = "Total",
    /** Slots: tax name, rate. */
    val includesTax: String = "Includes %1\$s %2\$s%%",
    val received: String = "RECEIVED",
    val balanceDue: String = "BALANCE DUE",
    /** Slot: the bill number. */
    val billFooter: String = "Bill %1\$s",

    val accountOf: String = "ACCOUNT OF",
    val opening: String = "OPENING",
    val workBilled: String = "WORK BILLED",
    val stillDue: String = "STILL DUE",
    val colDate: String = "DATE",
    val colDetails: String = "DETAILS",
    val colCharged: String = "CHARGED",
    val colPaid: String = "PAID",
    val colBalance: String = "BALANCE",
    val colPaidSoFar: String = "PAID SO FAR",
    val totalReceived: String = "TOTAL RECEIVED",
    /** Slot: the date. */
    val balanceOn: String = "BALANCE ON %1\$s",
    val statementNote: String = "Please tell us within 7 days if any line here does not match your record.",
    /** Slot: how many. */
    val entries: String = "%1\$d entries",

    val workNotPaid: String = "WORK NOT YET PAID FOR",
    val colWaiting: String = "WAITING",
    val totalStillToPay: String = "TOTAL STILL TO PAY",
    val duesNote: String = "If you have already paid any of this, tell us and we will correct the record the same day.",
    val jobs: String = "%1\$d jobs",

    val incomeIn: String = "WORK BILLED AND MONEY IN",
    val incomeCosts: String = "WHAT IT COST",
    val totalIncome: String = "Total income",
    val totalCosts: String = "Total costs",
    val leftAfterCosts: String = "Left after costs",
    val inFigures: String = "THE BUSINESS IN FIGURES",
    val notAudited: String = "Prepared from the business's own records kept in FieldTrack. It has not been audited.",

    val receivedFrom: String = "RECEIVED FROM",
    val paymentReceived: String = "PAYMENT RECEIVED",
    val paidBy: String = "Paid by",
    val forWhat: String = "For",
    val stillDueAfter: String = "Still due after this",
    val paidAhead: String = "Paid ahead",
    val accountSettled: String = "Account settled",
    val receiptFooter: String = "Receipt",

    val taxIdDefault: String = "GSTIN",
    val payTo: String = "PAY TO",
    val payByUpi: String = "PAY BY UPI",
    val scanUpi: String = "Scan with any UPI app, or pay in cash at the next job.",
    /** Slot: the business. */
    val signFor: String = "For %1\$s",
    val madeWith: String = "Made with FieldTrack",
    val tagline: String = "record the work, send the bill, keep the khata",
    val pageOne: String = "page 1 of 1",
) {
    companion object {
        val English = DocLabels()

        /** The words of [context]'s language. Pass a context already set to the language wanted. */
        fun of(context: Context, tag: String = "en", rtl: Boolean = false, words: Boolean = true): DocLabels {
            fun s(id: Int) = context.getString(id)
            return DocLabels(
                languageTag = tag, rightToLeft = rtl, showAmountInWords = words,
                kindBill = s(R.string.doc_kind_bill), kindTaxInvoice = s(R.string.doc_kind_tax_invoice),
                kindStatement = s(R.string.doc_kind_statement), kindAmountDue = s(R.string.doc_kind_amount_due),
                kindReceipt = s(R.string.doc_kind_receipt), kindIncome = s(R.string.doc_kind_income),
                billTo = s(R.string.doc_bill_to), workDoneOn = s(R.string.doc_work_done_on),
                colWork = s(R.string.doc_col_work), colMeasure = s(R.string.doc_col_measure),
                colRate = s(R.string.doc_col_rate), colAmount = s(R.string.doc_col_amount),
                thisBill = s(R.string.doc_this_bill), balanceBefore = s(R.string.doc_balance_before), total = s(R.string.doc_total),
                includesTax = s(R.string.doc_includes_tax), received = s(R.string.doc_received),
                balanceDue = s(R.string.doc_balance_due), billFooter = s(R.string.doc_bill_footer),
                accountOf = s(R.string.doc_account_of), opening = s(R.string.doc_opening), workBilled = s(R.string.doc_work_billed),
                stillDue = s(R.string.doc_still_due), colDate = s(R.string.doc_col_date), colDetails = s(R.string.doc_col_details),
                colCharged = s(R.string.doc_col_charged), colPaid = s(R.string.doc_col_paid), colBalance = s(R.string.doc_col_balance),
                colPaidSoFar = s(R.string.doc_col_paid_so_far), totalReceived = s(R.string.doc_total_received),
                balanceOn = s(R.string.doc_balance_on), statementNote = s(R.string.doc_statement_note), entries = s(R.string.doc_entries),
                workNotPaid = s(R.string.doc_work_not_paid), colWaiting = s(R.string.doc_col_waiting),
                totalStillToPay = s(R.string.doc_total_still_to_pay), duesNote = s(R.string.doc_dues_note), jobs = s(R.string.doc_jobs),
                incomeIn = s(R.string.doc_income_in), incomeCosts = s(R.string.doc_income_costs),
                totalIncome = s(R.string.doc_total_income), totalCosts = s(R.string.doc_total_costs),
                leftAfterCosts = s(R.string.doc_left_after_costs), inFigures = s(R.string.doc_in_figures), notAudited = s(R.string.doc_not_audited),
                receivedFrom = s(R.string.doc_received_from), paymentReceived = s(R.string.doc_payment_received),
                paidBy = s(R.string.doc_paid_by), forWhat = s(R.string.doc_for_what), stillDueAfter = s(R.string.doc_still_due_after),
                paidAhead = s(R.string.doc_paid_ahead), accountSettled = s(R.string.doc_account_settled), receiptFooter = s(R.string.doc_receipt_footer),
                taxIdDefault = s(R.string.doc_tax_id_default), payTo = s(R.string.doc_pay_to), payByUpi = s(R.string.doc_pay_by_upi),
                scanUpi = s(R.string.doc_scan_upi), signFor = s(R.string.doc_sign_for), madeWith = s(R.string.doc_made_with),
                tagline = s(R.string.doc_tagline), pageOne = s(R.string.doc_page_one),
            )
        }
    }
}
