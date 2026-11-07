package com.example.fieldtrack.document

import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyStyle
import com.example.fieldtrack.designsystem.format.money
import com.example.fieldtrack.data.auth.formatPhone
import com.example.fieldtrack.data.billing.TaxRate

/**
 * The printed documents, as HTML.
 *
 * Laid out to match the approved designs: a green letterhead band, an A4 body at 52px
 * side margins, and a footer carrying the app's name. HTML rather than Canvas drawing
 * because a bill is a table with a dozen conditional rows, and every one of those is
 * a line of CSS here against twenty lines of measuring text by hand.
 *
 * Nothing here reaches the network. The typeface is the one bundled with the app,
 * loaded from `android_res`, because these are made in a field with no signal.
 */
object DocumentHtml {

    private const val GREEN = "#0E6B3D"
    private const val GREEN_DARK = "#0A5430"
    private const val GREEN_WASH = "#E4F0E8"
    private const val GREEN_LINE = "#C9DED1"
    private const val INK = "#16231C"
    private const val INK_2 = "#4A5A51"
    private const val INK_3 = "#6B7B72"
    private const val LINE = "#E2E0D8"
    private const val PAPER = "#F5F3EE"
    private const val DUE = "#AE2A1E"
    private const val DUE_DARK = "#8E2217"
    private const val DUE_WASH = "#FBE9E7"
    private const val DUE_LINE = "#F0CFC9"

    /**
     * The style of the document being written on this thread. A bill is always in its
     * own business's currency, so it is carried here for the length of one render and
     * never read from whatever business happens to be on screen.
     */
    private val styleInUse = ThreadLocal<MoneyStyle>()
    private val labelsInUse = ThreadLocal<DocLabels>()

    /** The words of the page being written on this thread. */
    private val L: DocLabels get() = labelsInUse.get() ?: DocLabels.English

    fun render(document: Document, labels: DocLabels = DocLabels.English): String {
        styleInUse.set(document.style)
        labelsInUse.set(labels)
        try {
            return page(
                when (document) {
                    is Document.Bill -> bill(document)
                    is Document.Statement -> statement(document)
                    is Document.Dues -> dues(document)
                    is Document.Receipt -> receipt(document)
                    is Document.IncomeStatement -> income(document)
                }
            )
        } finally {
            styleInUse.remove()
            labelsInUse.remove()
        }
    }

    // ----------------------------------------------------------------- documents

    private fun bill(d: Document.Bill): String = buildString {
        // A business that charges a tax and gives its registration number prints a tax invoice.
        val kind = if (d.letterhead.taxName != null && d.letterhead.gstin != null) L.kindTaxInvoice else L.kindBill
        append(letterhead(d.letterhead, kind, d.billNumber ?: "", d.dateLabel))
        append("<div class='body'>")

        append("<div class='row'>")
        append(
            "<div class='col'>${label(L.billTo)}<div class='big'>${esc(d.customer.name)}</div>" +
                "<div class='sub'>${listOfNotNull(d.customer.addressLine, d.customer.phone).joinToString("<br>") { esc(it) }}</div></div>"
        )
        if (d.workedOnLabel != null) {
            append("<div class='vrule'></div>")
            append(
                "<div class='col'>${label(L.workDoneOn)}<div class='big'>${esc(d.workedOnLabel)}</div>" +
                    "<div class='sub'>${d.machineLabel?.let { esc(it) }.orEmpty()}</div></div>"
            )
        }
        append("</div>")

        append("<div class='gap'></div>")
        append("<table><thead><tr>")
        append("<th>${esc(L.colWork)}</th><th class='r w120'>${esc(L.colMeasure)}</th><th class='r w150'>${esc(L.colRate)}</th><th class='r w130'>${esc(L.colAmount)}</th>")
        append("</tr></thead><tbody>")
        d.lines.forEach { line ->
            append("<tr>")
            append("<td><div class='strong'>${esc(line.title)}</div>")
            line.detail?.let { append("<div class='cell-sub'>${esc(it)}</div>") }
            append("</td>")
            append("<td class='r num'>${esc(line.measure.orEmpty())}</td>")
            append("<td class='r num'>${esc(line.rate.orEmpty())}</td>")
            append("<td class='r num strong'>${money(line.amountMinor)}</td>")
            append("</tr>")
        }
        append("</tbody></table>")

        append("<div class='gap'></div><div class='totals'>")
        append(totalRow(L.thisBill, d.thisBillMinor))
        if (d.previousBalanceMinor != 0L) {
            append(totalRow(L.balanceBefore, d.previousBalanceMinor))
            append("<div class='hr'></div>")
            append(totalRow(L.total, d.totalMinor, strong = true))
        }
        // Prices include the tax; the bill says how much of it that is.
        val bps = d.letterhead.taxPercentBps
        val taxName = d.letterhead.taxName
        if (bps != null && taxName != null) {
            val tax = TaxRate.includedIn(d.totalMinor, bps, d.style.stepMinor)
            append(
                "<div class='cell-sub' style='text-align:right;padding-top:6px'>" +
                    "${esc(L.includesTax.format(taxName, TaxRate.text(bps)))}: ${money(tax)}</div>"
            )
        }
        append("</div>")

        if (d.payments.isNotEmpty()) {
            append("<div class='gap'></div>")
            append(label(L.received))
            append("<table class='plain'><tbody>")
            d.payments.forEach {
                append(
                    "<tr><td class='num w150' style='color:$INK_2'>${esc(it.date)}</td>" +
                        "<td>${esc(it.method)}</td>" +
                        "<td class='r num strong' style='color:$GREEN_DARK'>&minus; ${money(it.amountMinor)}</td></tr>"
                )
            }
            append("</tbody></table>")
        }

        append("<div class='gap'></div>")
        append(dueBand(L.balanceDue, d.balanceInWords, d.balanceMinor))
        append(payBlock(d.letterhead))
        append("<div class='spacer'></div>")
        append(signature(d.letterhead.businessName))
        append("</div>")
        append(footer(d.billNumber?.let { L.billFooter.format(it) }))
    }

    private fun statement(d: Document.Statement): String = buildString {
        append(letterhead(d.letterhead, L.kindStatement, d.periodLabel, d.printedLabel))
        append("<div class='body'>")

        append(label(L.accountOf))
        append("<h1>${esc(d.customer.name)}</h1>")
        append(
            "<div class='sub'>${listOfNotNull(d.customer.addressLine, d.customer.phone).joinToString(" &middot; ") { esc(it) }}</div>"
        )

        // A page listing only payments cannot honestly summarise the account: the
        // charges it filtered out are missing from every figure, so "work billed ₹0"
        // and a "still due" that is really the amount received would both be lies on
        // a document going to a customer. The green total below says all it can say.
        if (!d.paymentsOnly) {
            append("<div class='gap'></div>")
            append("<div class='summary'>")
            append(summaryCell(L.opening, d.openingMinor))
            append(summaryCell(L.workBilled, d.billedMinor))
            append(summaryCell(L.received, d.receivedMinor, color = GREEN_DARK))
            append(summaryCell(L.stillDue, d.closingMinor, color = DUE, wash = true, last = true))
            append("</div>")
        }

        append("<div class='gap'></div>")
        append("<table class='tight'><thead><tr>")
        append("<th class='w125 nowrap'>${esc(L.colDate)}</th><th>${esc(L.colDetails)}</th>")
        if (!d.paymentsOnly) append("<th class='r w104'>${esc(L.colCharged)}</th>")
        append("<th class='r w104'>${esc(L.colPaid)}</th>")
        append("<th class='r w110'>${esc(if (d.paymentsOnly) L.colPaidSoFar else L.colBalance)}</th>")
        append("</tr></thead><tbody class='num'>")
        d.lines.forEach { line ->
            append("<tr${if (line.isBroughtForward) " class='bf'" else ""}>")
            append("<td class='nowrap' style='color:$INK_2'>${esc(line.date)}</td>")
            append("<td${if (line.isBroughtForward) " class='italic'" else ""}>${esc(line.detail)}</td>")
            if (!d.paymentsOnly) {
                append("<td class='r'>${line.chargedMinor?.let { money(it) }.orEmpty()}</td>")
            }
            append("<td class='r strong' style='color:$GREEN_DARK'>${line.paidMinor?.let { money(it) }.orEmpty()}</td>")
            append("<td class='r strong'>${money(line.balanceAfterMinor)}</td>")
            append("</tr>")
        }
        append("</tbody></table>")

        append("<div class='gap'></div>")
        append(
            if (d.paymentsOnly) {
                paidBand(L.totalReceived, d.closingInWords, d.closingMinor)
            } else {
                dueBand(L.balanceOn.format(d.printedLabel.uppercase()), d.closingInWords, d.closingMinor)
            }
        )
        append("<div class='spacer'></div>")
        append(
            "<div class='foot-row'><div class='note'>${esc(L.statementNote)}</div>${signatureBox(d.letterhead.businessName)}</div>"
        )
        append("</div>")
        append(footer(L.entries.format(d.lines.size)))
    }

    private fun dues(d: Document.Dues): String = buildString {
        append(letterhead(d.letterhead, L.kindAmountDue, "", d.asOnLabel))
        append("<div class='body'>")

        append("<div class='row'>")
        append(
            "<div class='col'>${label("ACCOUNT OF")}<h1>${esc(d.customer.name)}</h1>" +
                "<div class='sub'>${listOfNotNull(d.customer.addressLine, d.customer.phone).joinToString(" &middot; ") { esc(it) }}</div></div>"
        )
        append(
            "<div class='col r'>${label("STILL TO PAY", DUE_DARK)}" +
                "<div class='hero-due'>${money(d.totalMinor)}</div></div>"
        )
        append("</div>")

        append("<div class='gap'></div>")
        append(label(L.workNotPaid))
        append("<table><thead><tr>")
        append("<th class='w125 nowrap'>${esc(L.colDate)}</th><th>${esc(L.colWork)}</th><th class='r w110'>${esc(L.colWaiting)}</th><th class='r w125'>${esc(L.colAmount)}</th>")
        append("</tr></thead><tbody class='num'>")
        d.lines.forEach { line ->
            append("<tr>")
            append("<td class='nowrap' style='color:$INK_2'>${esc(line.date)}</td>")
            append("<td><div class='strong'>${esc(line.title)}</div>")
            line.detail?.let { append("<div class='cell-sub'>${esc(it)}</div>") }
            append("</td>")
            append("<td class='r'>${esc(line.age)}</td>")
            append("<td class='r strong'>${money(line.amountMinor)}</td>")
            append("</tr>")
        }
        append("</tbody></table>")

        d.receivedNote?.let {
            append("<div class='gap'></div>")
            append(
                "<div class='credit'><svg width='24' height='24' viewBox='0 0 24 24' fill='none' " +
                    "stroke='$GREEN_DARK' stroke-width='2.2' stroke-linecap='round' " +
                    "stroke-linejoin='round'><path d='M4 12l6 6L20 6'/></svg><div>${esc(it)}</div></div>"
            )
        }

        append("<div class='gap'></div>")
        append(dueBand(L.totalStillToPay, d.totalInWords, d.totalMinor))
        append(payBlock(d.letterhead))
        append("<div class='spacer'></div>")
        append(
            "<div class='foot-row'><div class='note'>${esc(L.duesNote)}</div>" +
                "${signatureBox(d.letterhead.businessName)}</div>"
        )
        append("</div>")
        append(footer(L.jobs.format(d.lines.size)))
    }

    private fun income(d: Document.IncomeStatement): String = buildString {
        append(letterhead(d.letterhead, L.kindIncome, d.periodLabel, d.printedLabel))
        append("<div class='body'>")
        fun rows(list: List<StatementRow>) {
            append("<table class='plain'><tbody>")
            list.forEach { row ->
                append("<tr><td><div class='strong'>${esc(row.label)}</div>")
                row.note?.let { append("<div class='cell-sub'>${esc(it)}</div>") }
                append("</td><td class='r num strong'>${money(row.amountMinor)}</td></tr>")
            }
            append("</tbody></table>")
        }
        append(label(L.incomeIn))
        rows(d.income)
        append("<div class='totals'>${totalRow(L.totalIncome, d.incomeMinor, strong = true)}</div>")
        append("<div class='gap'></div>")
        append(label(L.incomeCosts))
        rows(d.costs)
        append("<div class='totals'>${totalRow(L.totalCosts, d.costsMinor, strong = true)}</div>")
        append("<div class='gap'></div>")
        append("<div class='totals'><div class='hr'></div>${totalRow(L.leftAfterCosts, d.netMinor, strong = true)}</div>")
        if (d.facts.isNotEmpty()) {
            append("<div class='gap'></div>")
            append(label(L.inFigures))
            d.facts.forEach { (k, v) -> append(textRow(k, v)) }
        }
        d.notes.forEach { append("<div class='cell-sub' style='padding-top:8px'>${esc(it)}</div>") }
        append("<div class='spacer'></div>")
        append(
            "<div class='foot-row'><div class='note'>${esc(L.notAudited)}</div>${signatureBox(d.letterhead.businessName)}</div>"
        )
        append("</div>")
        append(footer(d.periodLabel))
    }

    private fun receipt(d: Document.Receipt): String = buildString {
        append(letterhead(d.letterhead, L.kindReceipt, d.dateLabel, ""))
        append("<div class='body'>")
        append(label(L.receivedFrom))
        append("<h1>${esc(d.customer.name)}</h1>")
        append(
            "<div class='sub'>${listOfNotNull(d.customer.addressLine, d.customer.phone).joinToString(" &middot; ") { esc(it) }}</div>"
        )
        append("<div class='gap'></div>")
        append(paidBand(L.paymentReceived, d.amountInWords, d.amountMinor))
        append("<div class='gap'></div><div class='totals'>")
        append(textRow(L.paidBy, d.methodLabel))
        d.note?.takeIf { it.isNotBlank() }?.let {
            append(textRow(L.forWhat, it))
        }
        append("<div class='hr'></div>")
        if (d.balanceAfterMinor > 0) {
            append(totalRow(L.stillDueAfter, d.balanceAfterMinor, strong = true))
        } else if (d.balanceAfterMinor < 0) {
            append(totalRow(L.paidAhead, -d.balanceAfterMinor, strong = true))
        } else {
            append("<div class='total-row'><span class='strong'>${esc(L.accountSettled)}</span><span class='num strong'>${money(0)}</span></div>")
        }
        append("</div>")
        append("<div class='spacer'></div>")
        append(signature(d.letterhead.businessName))
        append("</div>")
        append(footer(L.receiptFooter))
    }

    // -------------------------------------------------------------- shared parts

    private fun letterhead(l: Letterhead, kind: String, line1: String, line2: String): String {
        val contact = listOfNotNull(
            l.phone?.let { formatPhone(it) },
            l.gstin?.let { "${l.taxName?.takeIf { n -> n.isNotBlank() } ?: L.taxIdDefault} $it" },
        ).joinToString(" &middot; ") { esc(it) }
        return buildString {
            append("<div class='head'><div class='mark'>$TRACTOR</div><div class='who'>")
            append("<div class='name'>${esc(l.businessName)}</div>")
            val lines = listOfNotNull(l.addressLine?.let { esc(it) }, contact.ifBlank { null })
            if (lines.isNotEmpty()) append("<div class='contact'>${lines.joinToString("<br>")}</div>")
            append("</div><div class='kind'>")
            append("<div class='kind-label'>$kind</div>")
            if (line1.isNotBlank()) append("<div class='kind-no'>${esc(line1)}</div>")
            append("<div class='kind-date'>${esc(line2)}</div>")
            append("</div></div>")
        }
    }

    private fun label(text: String, color: String = INK_2) =
        "<div class='label' style='color:$color'>${esc(text)}</div>"

    /** A label and a word or two, laid out like a total row. */
    private fun textRow(label: String, value: String) =
        "<div class='total-row'><span style='color:$INK_2'>${esc(label)}</span><span class='semi'>${esc(value)}</span></div>"

    private fun totalRow(label: String, amount: Long, strong: Boolean = false) =
        "<div class='total-row'><span${if (strong) " class='strong'" else " style='color:$INK_2'"}>" +
            "${esc(label)}</span><span class='num ${if (strong) "strong" else "semi"}'>${money(amount)}</span></div>"

    private fun summaryCell(
        label: String,
        amount: Long,
        color: String = INK,
        wash: Boolean = false,
        last: Boolean = false,
    ) = "<div class='sum-cell${if (wash) " wash" else ""}${if (last) " last" else ""}'>" +
        "<div class='sum-label' style='color:${if (wash) DUE_DARK else INK_2}'>${esc(label)}</div>" +
        "<div class='sum-value' style='color:$color'>${money(amount)}</div></div>"

    /** The same band in green, for a page that is good news. */
    private fun paidBand(title: String, words: String?, amount: Long) =
        "<div class='paid-band'><div><div class='paid-title'>${esc(title)}</div>" +
            wordsLine(words) +
            "<div class='paid-amount'>${money(amount)}</div></div>"

    private fun dueBand(title: String, words: String?, amount: Long) =
        "<div class='due-band'><div><div class='due-title'>${esc(title)}</div>" +
            wordsLine(words) +
            "<div class='due-amount'>${money(amount)}</div></div>"

    /** The amount in words, left off where there is no wording for the currency. */
    private fun wordsLine(words: String?) =
        if (words == null || !L.showAmountInWords) "</div>" else "<div class='due-words'>${esc(words)}</div></div>"

    private fun payBlock(l: Letterhead): String {
        val details = l.payDetails?.takeIf { it.isNotBlank() }
        if (l.upiId == null && details != null) {
            return "<div class='gap'></div><div class='pay'><div>" +
                "<div class='label'>${esc(L.payTo)}</div>" +
                "<div class='upi'>${details.lines().joinToString("<br>") { esc(it) }}</div></div></div>"
        }
        val upi = l.upiId ?: return ""
        return "<div class='gap'></div><div class='pay'><div class='qr'>UPI<br>QR</div>" +
            "<div><div class='label'>${esc(L.payByUpi)}</div><div class='upi'>${esc(upi)}</div>" +
            "<div class='cell-sub'>${esc(L.scanUpi)}</div>" +
            "</div></div>"
    }

    private fun signature(business: String) =
        "<div class='sign-row'>${signatureBox(business)}</div>"

    private fun signatureBox(business: String) =
        "<div class='sign'><div class='sign-line'></div><div class='sign-for'>${esc(L.signFor.format(business))}</div></div>"

    private fun footer(right: String?) =
        "<div class='foot'><div class='foot-mark'>$TRACTOR</div>" +
            "<div class='foot-text'><strong>${esc(L.madeWith)}</strong> &middot; " +
            "${esc(L.tagline)}</div>" +
            "<div class='foot-right'>${right?.let { esc(it) + " &middot; " }.orEmpty()}${esc(L.pageOne)}</div></div>"

    private const val TRACTOR =
        "<svg viewBox='0 0 32 32' fill='none' stroke='#FFFFFF' stroke-width='2.4' " +
            "stroke-linecap='round' stroke-linejoin='round'><circle cx='22' cy='21' r='8'/>" +
            "<circle cx='7' cy='25' r='4'/><path d='M3 21v-6h6V8h7v7'/><path d='M16 15h7'/></svg>"

    private fun money(amount: Long) = esc(money(amount, styleInUse.get() ?: Money.style))

    /** The customer's name goes on a page unescaped at your peril. */
    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun page(content: String): String = """
<!doctype html>
<html lang="${esc(L.languageTag)}"${if (L.rightToLeft) " dir='rtl'" else ""}><head><meta charset="utf-8">
<meta name="viewport" content="width=794">
<style>
@font-face {
  font-family: Figtree;
  src: url('file:///android_res/font/figtree.ttf');
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: #FFFFFF; }
body {
  width: 794px; font-family: Figtree, sans-serif; color: $INK;
  -webkit-font-smoothing: antialiased; font-size: 15px;
  /*
     A4 at this width is 1123px tall. Filling it puts the footer on the bottom edge
     the way it sits on paper; without this a short bill's footer floats under the
     last line and the page reads as though it were cut off.
  */
  min-height: 1123px; display: flex; flex-direction: column;
}
.spacer { flex: 1; min-height: 26px; }
.head {
  background: $GREEN; color: #FFFFFF; padding: 30px 52px;
  display: flex; align-items: flex-start; gap: 20px;
}
.mark {
  width: 54px; height: 54px; border-radius: 15px; background: rgba(255,255,255,0.18);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.mark svg { width: 32px; height: 32px; }
.who { flex: 1; min-width: 0; }
.name { font-size: 27px; font-weight: 800; letter-spacing: -0.01em; line-height: 1.15; }
.contact { font-size: 14px; color: rgba(255,255,255,0.88); margin-top: 7px; line-height: 1.5; }
.kind { text-align: right; flex-shrink: 0; }
.kind-label { font-size: 13px; font-weight: 700; letter-spacing: 0.18em; color: rgba(255,255,255,0.78); }
.kind-no { font-size: 21px; font-weight: 800; margin-top: 4px; white-space: nowrap; }
.kind-date { font-size: 13.5px; color: rgba(255,255,255,0.88); margin-top: 6px; }
.body { padding: 34px 52px 0; flex: 1; display: flex; flex-direction: column; }
.row { display: flex; gap: 28px; align-items: flex-start; }
.col { flex: 1; min-width: 0; }
.col.r { text-align: right; flex: 0 0 auto; }
.vrule { width: 1px; background: $LINE; align-self: stretch; }
.label { font-size: 12px; font-weight: 700; letter-spacing: 0.14em; }
h1 { margin: 6px 0 0; font-size: 26px; font-weight: 800; letter-spacing: -0.01em; }
.big { font-size: 20px; font-weight: 800; margin-top: 7px; }
.sub { font-size: 14.5px; color: $INK_2; margin-top: 4px; line-height: 1.5; }
.hero-due { font-size: 44px; font-weight: 800; color: $DUE; letter-spacing: -0.025em; line-height: 1.1; margin-top: 2px; }
.gap { height: 22px; flex-shrink: 0; }
table { width: 100%; border-collapse: collapse; font-size: 15px; margin-top: 11px; }
table.tight { font-size: 14px; }
th {
  text-align: left; font-size: 11.5px; font-weight: 700; letter-spacing: 0.1em;
  color: $GREEN_DARK; background: $GREEN_WASH; padding: 11px 13px; border: 1px solid $GREEN_LINE;
}
td { padding: 13px; border: 1px solid $LINE; vertical-align: top; }
table.tight td { padding: 9px 12px; }
table.plain td { border: 0; border-bottom: 1px solid $LINE; padding: 11px 0; }
.r { text-align: right; }
.num { font-variant-numeric: tabular-nums; }
.strong { font-weight: 700; }
.semi { font-weight: 600; }
.italic { font-style: italic; color: $INK_2; }
.bf td { background: #FAF9F6; }
.cell-sub { font-size: 13.5px; color: $INK_2; margin-top: 3px; }
.nowrap { white-space: nowrap; }
.w104 { width: 104px; } .w110 { width: 110px; } .w120 { width: 120px; }
.w125 { width: 125px; } .w130 { width: 130px; } .w150 { width: 150px; }
.totals { display: flex; flex-direction: column; align-items: flex-end; }
.total-row { width: 340px; display: flex; justify-content: space-between; padding: 7px 0; }
.hr { width: 340px; height: 1px; background: $LINE; margin: 7px 0; }
.summary { display: flex; border: 1px solid $LINE; border-radius: 12px; overflow: hidden; }
.sum-cell { flex: 1; padding: 15px 18px; border-right: 1px solid $LINE; }
.sum-cell.last { border-right: 0; }
.sum-cell.wash { background: $DUE_WASH; }
.sum-label { font-size: 11.5px; font-weight: 700; letter-spacing: 0.1em; }
.sum-value { font-size: 21px; font-weight: 700; font-variant-numeric: tabular-nums; margin-top: 5px; }
.due-band {
  background: $DUE_WASH; border: 1px solid $DUE_LINE; border-radius: 12px;
  padding: 18px 22px; display: flex; align-items: center; justify-content: space-between; gap: 20px;
}
.paid-band {
  background: $GREEN_WASH; border: 1px solid $GREEN_LINE; border-radius: 12px;
  padding: 18px 22px; display: flex; align-items: center; justify-content: space-between; gap: 20px;
}
.paid-title { font-size: 13px; font-weight: 700; letter-spacing: 0.12em; color: $GREEN_DARK; }
.paid-amount { font-size: 38px; font-weight: 800; font-variant-numeric: tabular-nums; color: $GREEN_DARK; letter-spacing: -0.02em; }
.due-title { font-size: 13px; font-weight: 700; letter-spacing: 0.12em; color: $DUE_DARK; }
.due-words { font-size: 14px; color: #6B4A45; margin-top: 5px; }
.due-amount { font-size: 38px; font-weight: 800; font-variant-numeric: tabular-nums; color: $DUE; letter-spacing: -0.02em; }
.credit {
  background: $GREEN_WASH; border: 1px solid $GREEN_LINE; border-radius: 12px;
  padding: 16px 20px; display: flex; align-items: center; gap: 18px;
  font-size: 14.5px; color: #26432F; line-height: 1.5;
}
.credit svg { flex-shrink: 0; }
.pay { display: flex; gap: 20px; align-items: center; border: 1px solid $LINE; border-radius: 12px; padding: 16px 20px; }
.qr {
  width: 88px; height: 88px; border: 2px dashed #C3C0B6; border-radius: 10px;
  display: flex; align-items: center; justify-content: center; text-align: center;
  font-size: 11.5px; font-weight: 700; color: $INK_3; line-height: 1.3; flex-shrink: 0;
}
.upi { font-size: 18px; font-weight: 700; margin-top: 5px; }
.sign-row { display: flex; justify-content: flex-end; padding-bottom: 20px; }
.foot-row { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; padding-bottom: 18px; }
.note { font-size: 12.5px; color: $INK_2; max-width: 340px; line-height: 1.5; }
.sign { width: 230px; text-align: center; }
.sign-line { height: 1px; background: $INK; }
.sign-for { font-size: 13px; color: $INK_2; margin-top: 7px; }
.foot {
  background: $PAPER; border-top: 1px solid $LINE; padding: 16px 52px;
  display: flex; align-items: center; gap: 12px; flex-shrink: 0;
}
.foot-mark { width: 30px; height: 30px; border-radius: 9px; background: $GREEN; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.foot-mark svg { width: 19px; height: 19px; stroke-width: 2.6; }
.foot-text { flex: 1; font-size: 12.5px; color: $INK_2; }
.foot-text strong { color: $INK; }
.foot-right { font-size: 12px; color: $INK_3; font-variant-numeric: tabular-nums; flex-shrink: 0; }
</style></head><body>$content</body></html>
""".trimIndent()
}
