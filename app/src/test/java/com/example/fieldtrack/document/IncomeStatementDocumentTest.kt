package com.example.fieldtrack.document

import com.example.fieldtrack.designsystem.format.DigitGrouping
import com.example.fieldtrack.designsystem.format.Money
import com.example.fieldtrack.designsystem.format.MoneyStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page handed to a bank: its sums, its wording, and that it never names a customer. */
class IncomeStatementDocumentTest {

    private fun doc() = Document.IncomeStatement(
        letterhead = Letterhead(businessName = "Harpal Agro Works", addressLine = "Dosimana"),
        style = MoneyStyle.of("INR", 100, DigitGrouping.Indian),
        customer = Party("Harpal Agro Works"),
        periodLabel = "1 Apr 2026 – 31 Mar 2027",
        printedLabel = "7 Nov 2026",
        income = listOf(StatementRow("Work billed", 12_000_000)),
        costs = listOf(StatementRow("Diesel", 2_500_000), StatementRow("Wages and labour", 3_000_000), StatementRow("Loan instalments", 1_000_000, "An estimate")),
        netMinor = 5_500_000,
        facts = listOf("Jobs finished" to "212", "Machines" to "3"),
        notes = listOf("2 crew costs have no amount yet and are not counted."),
    )

    @Test fun `income and costs are the sums of their lines`() {
        val d = doc()
        assertEquals(12_000_000L, d.incomeMinor)
        assertEquals(6_500_000L, d.costsMinor)
        assertEquals(d.incomeMinor - d.costsMinor, d.netMinor)
    }

    @Test fun `the page says what it is, shows every line, and says it is not audited`() {
        Money.grouping = DigitGrouping.Indian
        val html = DocumentHtml.render(doc())
        for (text in listOf("INCOME STATEMENT", "Work billed", "Diesel", "Wages and labour", "Loan instalments", "An estimate", "Total income", "Total costs", "Left after costs",
            "Jobs finished", "212", "2 crew costs have no amount yet", "has not been audited", "Harpal Agro Works")) {
            assertTrue("missing: $text", html.contains(text))
        }
        assertTrue(html.contains("1,20,000"))
        assertTrue(html.contains("55,000"))
    }

    @Test fun `the file is named for the period and the business`() {
        assertEquals("Income-statement-1-Apr-2026-31-Mar-2027-Harpal-Agro-Works", doc().fileName.replace("--", "-"))
    }
}
