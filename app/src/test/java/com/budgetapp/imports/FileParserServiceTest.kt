package com.budgetapp.imports

import android.content.Context
import com.budgetapp.data.remote.gemini.ColumnMapping
import com.budgetapp.data.remote.gemini.CreditCardMapping
import com.budgetapp.data.remote.gemini.FileParserService
import com.budgetapp.data.remote.gemini.ParsedTransaction
import com.budgetapp.data.remote.gemini.TransactionType
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

// isRedColor / isGreenColor are tested via reflection
private val isRedColor = FileParserService::class.java
    .getDeclaredMethod("isRedColor", Int::class.java, Int::class.java, Int::class.java)
    .also { it.isAccessible = true }
private val isGreenColor = FileParserService::class.java
    .getDeclaredMethod("isGreenColor", Int::class.java, Int::class.java, Int::class.java)
    .also { it.isAccessible = true }

class FileParserServiceTest {

    private val service = FileParserService(mockk<Context>(relaxed = true))

    private val parseRowData = FileParserService::class.java
        .getDeclaredMethod("parseRowData", List::class.java, ColumnMapping::class.java)
        .also { it.isAccessible = true }

    private val detectColumnMapping = FileParserService::class.java
        .getDeclaredMethod("detectColumnMapping", List::class.java)
        .also { it.isAccessible = true }

    private fun parse(cells: List<String>, mapping: ColumnMapping): ParsedTransaction? =
        parseRowData.invoke(service, cells, mapping) as? ParsedTransaction

    private fun detect(headers: List<String>): ColumnMapping =
        detectColumnMapping.invoke(service, headers) as ColumnMapping

    // ── Separate debit / credit columns ───────────────────────────────────────

    @Test
    fun `expense row with debit value is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, dateColumn = 1, debitColumn = 2, creditColumn = 3)
        val tx = parse(listOf("Coffee shop", "2024-05-01", "12.50", ""), mapping)
        assertNotNull("Transaction should not be null", tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(12.50, tx.amount, 0.001)
    }

    @Test
    fun `income row with credit value is INCOME`() {
        val mapping = ColumnMapping(descriptionColumn = 0, dateColumn = 1, debitColumn = 2, creditColumn = 3)
        val tx = parse(listOf("Salary", "2024-05-01", "", "5000.00"), mapping)
        assertNotNull("Income transaction must not be dropped", tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(5000.0, tx.amount, 0.001)
    }

    @Test
    fun `income row is not silently dropped when debit cell is blank`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1, creditColumn = 2)
        val tx = parse(listOf("Bank interest", "", "150.00"), mapping)
        assertNotNull("Income row must not be silently dropped", tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(150.0, tx.amount, 0.001)
    }

    @Test
    fun `row with both debit and credit blank returns null`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1, creditColumn = 2)
        assertNull(parse(listOf("Empty row", "", ""), mapping))
    }

    @Test
    fun `hebrew bank statement row - חובה debit is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1, creditColumn = 2)
        val tx = parse(listOf("סופרמרקט", "230.00", ""), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(230.0, tx.amount, 0.001)
    }

    @Test
    fun `hebrew bank statement row - זכות credit is INCOME`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1, creditColumn = 2)
        val tx = parse(listOf("משכורת", "", "10000.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(10000.0, tx.amount, 0.001)
    }

    // ── Single amount column ───────────────────────────────────────────────────

    @Test
    fun `negative amount in single column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1)
        val tx = parse(listOf("Groceries", "-45.00"), mapping)
        assertNotNull(tx); assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(45.0, tx.amount, 0.001)
    }

    @Test
    fun `positive amount in single column is INCOME`() {
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1)
        val tx = parse(listOf("Freelance payment", "1200.00"), mapping)
        assertNotNull(tx); assertEquals(TransactionType.INCOME, tx!!.type)
        assertEquals(1200.0, tx.amount, 0.001)
    }

    @Test
    fun `accounting notation (amount) in single column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1)
        val tx = parse(listOf("Subscription", "(9.99)"), mapping)
        assertNotNull(tx); assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(9.99, tx.amount, 0.001)
    }

    // ── Only one of debit / credit present ────────────────────────────────────

    @Test
    fun `only debit column maps to EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1)
        val tx = parse(listOf("ATM withdrawal", "200.00"), mapping)
        assertNotNull(tx); assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `only credit column maps to INCOME`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Transfer in", "300.00"), mapping)
        assertNotNull(tx); assertEquals(TransactionType.INCOME, tx!!.type)
    }

    // ── Column header detection ────────────────────────────────────────────────

    @Test
    fun `Credit Balance header is detected as balance, not credit`() {
        val mapping = detect(listOf("Date", "Description", "Debit", "Credit Balance"))
        assertEquals("creditColumn should be null when column is a balance column",
            null, mapping.creditColumn)
        assertNotNull("balanceColumn should be detected", mapping.balanceColumn)
        assertEquals(3, mapping.balanceColumn)
    }

    @Test
    fun `Debit Balance header is detected as balance, not debit`() {
        val mapping = detect(listOf("Date", "Memo", "Debit Balance", "Credit"))
        assertEquals(null, mapping.debitColumn)
        assertNotNull(mapping.balanceColumn)
        assertNotNull(mapping.creditColumn)
    }

    @Test
    fun `outflow header is detected as debit`() {
        val mapping = detect(listOf("Date", "Description", "Outflow", "Inflow", "Balance"))
        assertNotNull("outflow should map to debitColumn", mapping.debitColumn)
        assertNotNull("inflow should map to creditColumn", mapping.creditColumn)
        assertEquals(2, mapping.debitColumn)
        assertEquals(3, mapping.creditColumn)
    }

    @Test
    fun `expenses header is detected as debit`() {
        val mapping = detect(listOf("Date", "Details", "Expenses", "Receipts"))
        assertNotNull(mapping.debitColumn)
        assertNotNull(mapping.creditColumn)
        assertEquals(2, mapping.debitColumn)
        assertEquals(3, mapping.creditColumn)
    }

    @Test
    fun `combined debit-credit header is detected as amountColumn and flagged`() {
        // "זכות/חובה" contains both a credit keyword (זכות) and a debit keyword (חובה)
        val mapping = detect(listOf("תאריך", "סוג תנועה", "זכות/חובה", "יתרה בש\"ח"))
        assertNotNull("combined column should map to amountColumn", mapping.amountColumn)
        assertEquals(2, mapping.amountColumn)
        assertNull("debitColumn should be null for combined header", mapping.debitColumn)
        assertNull("creditColumn should be null for combined header", mapping.creditColumn)
        assertNotNull("balance column should be detected", mapping.balanceColumn)
        assertTrue("isCombinedAmountColumn must be true", mapping.isCombinedAmountColumn)
    }

    @Test
    fun `combined debit-credit header in English is detected as amountColumn and flagged`() {
        val mapping = detect(listOf("Date", "Description", "Debit/Credit", "Balance"))
        assertNotNull(mapping.amountColumn)
        assertEquals(2, mapping.amountColumn)
        assertNull(mapping.debitColumn)
        assertNull(mapping.creditColumn)
        assertTrue(mapping.isCombinedAmountColumn)
    }

    @Test
    fun `single debit column is NOT flagged as combined`() {
        val mapping = detect(listOf("Date", "Description", "Debit", "Credit"))
        assertFalse("separate debit column must not set isCombinedAmountColumn", mapping.isCombinedAmountColumn)
    }

    // ── Combined column: sign is source of truth, description overrides skipped ──

    @Test
    fun `negative amount in combined column is EXPENSE even for credit-card description`() {
        // Israeli bank format: זכות/חובה is a signed combined column.
        // A negative amount must stay EXPENSE regardless of description patterns.
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1, isCombinedAmountColumn = true)
        val tx = parse(listOf("גולד מסטרקרד", "-1500"), mapping)
        assertNotNull(tx)
        assertEquals("negative amount in combined column must be EXPENSE", TransactionType.EXPENSE, tx!!.type)
        assertEquals(1500.0, tx.amount, 0.001)
    }

    @Test
    fun `positive amount in combined column is INCOME even for credit-card description`() {
        // A refund from the credit-card company would appear as a positive entry in זכות/חובה.
        // Sign wins — it must be classified as INCOME.
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1, isCombinedAmountColumn = true)
        val tx = parse(listOf("גולד מסטרקרד", "150"), mapping)
        assertNotNull(tx)
        assertEquals("positive amount in combined column must be INCOME", TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `trailing minus amount is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1, isCombinedAmountColumn = true)
        val tx = parse(listOf("קניות", "1500-"), mapping)
        assertNotNull("trailing-minus row must not be dropped", tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(1500.0, tx.amount, 0.001)
    }

    @Test
    fun `trailing minus with comma-thousands is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, amountColumn = 1, isCombinedAmountColumn = true)
        val tx = parse(listOf("חשמל", "1,234.56-"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(1234.56, tx.amount, 0.01)
    }

    // ── Column-only classification (no description override) ─────────────────

    @Test
    fun `גולד מסטרקרד in credit column is INCOME — column wins, no description override`() {
        // Income/expense is determined solely by column and cell color.
        // A credit-column value is INCOME regardless of description.
        // (For Excel files, cell color would correctly show red = EXPENSE if applicable.)
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("גולד מסטרקרד", "1500"), mapping)
        assertNotNull(tx)
        assertEquals("credit column → INCOME, description not used", TransactionType.INCOME, tx!!.type)
    }

    // ── Cell color helpers ─────────────────────────────────────────────────────

    private fun red(r: Int, g: Int, b: Int)   = isRedColor.invoke(service, r, g, b) as Boolean
    private fun green(r: Int, g: Int, b: Int) = isGreenColor.invoke(service, r, g, b) as Boolean

    @Test
    fun `pure red is detected as red`() {
        assertTrue(red(255, 0, 0))
        assertFalse(green(255, 0, 0))
    }

    @Test
    fun `dark red is detected as red`() {
        assertTrue(red(192, 0, 0))
    }

    @Test
    fun `pure green is detected as green`() {
        assertTrue(green(0, 128, 0))
        assertFalse(red(0, 128, 0))
    }

    @Test
    fun `Excel accent green (0,176,80) is detected as green`() {
        assertTrue(green(0, 176, 80))
    }

    @Test
    fun `black text is neither red nor green`() {
        assertFalse(red(0, 0, 0))
        assertFalse(green(0, 0, 0))
    }

    @Test
    fun `dark navy blue is neither red nor green`() {
        assertFalse(red(0, 0, 128))
        assertFalse(green(0, 0, 128))
    }

    // ── Column-only classification — description never overrides column ──────────
    // Income/expense is determined solely by which column holds the value (and cell
    // color for Excel).  The description/name is never consulted.

    @Test
    fun `העברה באינטרנט in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("העברה באינטרנט", "450.00"), mapping)
        assertNotNull(tx)
        assertEquals("credit column → INCOME regardless of description", TransactionType.INCOME, tx!!.type)
        assertEquals(450.0, tx.amount, 0.001)
    }

    @Test
    fun `העברה באינטרנט in debit column is EXPENSE — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1, creditColumn = 2)
        val tx = parse(listOf("העברה באינטרנט", "200.00", ""), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `Gold Mastercard in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Gold Mastercard", "1500.00"), mapping)
        assertNotNull(tx)
        assertEquals("credit column → INCOME, description not consulted", TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `גולד מסטרקארד in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("גולד מסטרקארד", "2300.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `גולד מאסטרקארד in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("גולד מאסטרקארד", "2300.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `Visa charge in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Visa Classic", "800.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `כרטיס אשראי in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("כרטיס אשראי לאומי", "3200.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `חיוב כרטיס in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("חיוב כרטיס ויזה", "950.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
    }

    @Test
    fun `Diners charge in credit column is INCOME — column wins`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Diners Gold", "600.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.INCOME, tx!!.type)
    }

    // ── Credit card format detection ───────────────────────────────────────────

    private val isCreditCardFormat = FileParserService::class.java
        .getDeclaredMethod("isCreditCardFormat", List::class.java)
        .also { it.isAccessible = true }
    private val detectCreditCardMappingMethod = FileParserService::class.java
        .getDeclaredMethod("detectCreditCardMapping", List::class.java)
        .also { it.isAccessible = true }
    private val parseCreditCardRowMethod = FileParserService::class.java
        .getDeclaredMethod("parseCreditCardRow", List::class.java, CreditCardMapping::class.java)
        .also { it.isAccessible = true }

    private fun isCC(headers: List<String>) =
        isCreditCardFormat.invoke(service, headers) as Boolean
    private fun detectCC(headers: List<String>) =
        detectCreditCardMappingMethod.invoke(service, headers) as CreditCardMapping
    private fun parseCC(cells: List<String>, mapping: CreditCardMapping) =
        parseCreditCardRowMethod.invoke(service, cells, mapping) as? ParsedTransaction

    @Test
    fun `Hebrew CC headers are detected as credit card format`() {
        assertTrue(isCC(listOf("תאריך העסקה", "בית העסק", "סכום עסקה", "מטבע", "סכום החיוב")))
    }

    @Test
    fun `שם בית עסק is detected as credit card format`() {
        assertTrue(isCC(listOf("תאריך", "שם בית עסק", "סכום", "מטבע")))
    }

    @Test
    fun `bank statement headers are NOT detected as credit card format`() {
        assertFalse(isCC(listOf("תאריך", "סוג תנועה", "זכות/חובה", "יתרה בש\"ח")))
    }

    @Test
    fun `standard CC column mapping — finds correct columns`() {
        val mapping = detectCC(listOf("תאריך העסקה", "שם בית עסק", "סכום עסקה", "מטבע", "סכום החיוב", "תאריך חיוב"))
        assertEquals("date column", 0, mapping.dateColumn)
        assertEquals("merchant column", 1, mapping.merchantColumn)
        assertEquals("charge column must be 'סכום החיוב' not 'סכום עסקה'", 4, mapping.chargeColumn)
    }

    @Test
    fun `CC mapping prefers סכום החיוב over סכום עסקה`() {
        // Both contain "סכום"; the more specific "סכום החיוב" must win.
        val mapping = detectCC(listOf("שם בית עסק", "סכום עסקה", "מטבע", "סכום החיוב"))
        assertEquals(3, mapping.chargeColumn)
        assertNotEquals("should not pick the foreign-currency column", 1, mapping.chargeColumn)
    }

    @Test
    fun `CC row is always EXPENSE`() {
        val mapping = CreditCardMapping(dateColumn = 0, merchantColumn = 1, chargeColumn = 2)
        val tx = parseCC(listOf("01/05/2026", "MAE", "120.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(120.0, tx.amount, 0.001)
        assertEquals("MAE", tx.description)
    }

    @Test
    fun `CC row uses merchant name as description`() {
        val mapping = CreditCardMapping(merchantColumn = 0, chargeColumn = 1)
        val tx = parseCC(listOf("עיריית קריית אונו", "350.00"), mapping)
        assertNotNull(tx)
        assertEquals("עיריית קריית אונו", tx!!.description)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `גולד מסטרקרד in CC file is EXPENSE`() {
        val mapping = CreditCardMapping(merchantColumn = 0, chargeColumn = 1)
        val tx = parseCC(listOf("גולד מסטרקרד", "1800.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(1800.0, tx.amount, 0.001)
    }

    @Test
    fun `העברה בBIT in CC file is EXPENSE`() {
        val mapping = CreditCardMapping(merchantColumn = 0, chargeColumn = 1)
        val tx = parseCC(listOf("העברה בBIT", "500.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `הפי האואר in CC file is EXPENSE`() {
        val mapping = CreditCardMapping(merchantColumn = 0, chargeColumn = 1)
        val tx = parseCC(listOf("הפי האואר", "89.90"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(89.9, tx.amount, 0.001)
    }

    @Test
    fun `CC row ignores other columns — amount comes only from chargeColumn`() {
        // Columns: merchant, original-currency amount, currency, ILS charge, billing date
        val mapping = CreditCardMapping(dateColumn = 4, merchantColumn = 0, chargeColumn = 3)
        val tx = parseCC(listOf("Coffee shop", "5.00", "USD", "18.50", "05/2026"), mapping)
        assertNotNull(tx)
        assertEquals(18.5, tx!!.amount, 0.001)  // ILS charge, not USD amount
    }

    @Test
    fun `CC blank charge row is skipped`() {
        val mapping = CreditCardMapping(merchantColumn = 0, chargeColumn = 1)
        assertNull(parseCC(listOf("סיכום חשבון", ""), mapping))
    }

    // ── CC PDF row parser ──────────────────────────────────────────────────────

    private val isCreditCardPdfMethod = FileParserService::class.java
        .getDeclaredMethod("isCreditCardPdf", String::class.java)
        .also { it.isAccessible = true }
    private val parseCreditCardPdfLineMethod = FileParserService::class.java
        .getDeclaredMethod("parseCreditCardPdfLine", String::class.java, Int::class.java)
        .also { it.isAccessible = true }
    private val extractCCFromTextMethod = FileParserService::class.java
        .getDeclaredMethod("extractCreditCardTransactionsFromText", String::class.java)
        .also { it.isAccessible = true }

    private fun isCCPdf(text: String) =
        isCreditCardPdfMethod.invoke(service, text) as Boolean
    private fun parseCCPdfLine(line: String, chargeAbsPos: Int = -1) =
        parseCreditCardPdfLineMethod.invoke(service, line, chargeAbsPos) as? ParsedTransaction
    @Suppress("UNCHECKED_CAST")
    private fun extractCCFromText(text: String) =
        extractCCFromTextMethod.invoke(service, text) as List<ParsedTransaction>

    @Test
    fun `isCreditCardPdf detects בית העסק keyword`() {
        assertTrue(isCCPdf("תאריך העסקה  בית העסק  סכום החיוב"))
    }

    @Test
    fun `isCreditCardPdf does not trigger on bank statement`() {
        assertFalse(isCCPdf("תאריך  סוג תנועה  זכות/חובה  יתרה בש\"ח"))
    }

    @Test
    fun `parseCreditCardPdfLine extracts correct amount — single date`() {
        // Typical CC row: date  merchant  amount
        val tx = parseCCPdfLine("01/05/2026  ארטנובה-שרון מ  150.00")
        assertNotNull(tx)
        assertEquals(150.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `parseCreditCardPdfLine excludes billing-month digit from two-date row`() {
        // Root cause of the ₪5.00 bug: the billing date "05/06/2026" contributes "05"
        // which was the smallest number and was selected as the amount.
        // After the fix, both dates are excluded and 150.00 is correctly chosen.
        val tx = parseCCPdfLine("01/05/2026  05/06/2026  ביר בר  150.00")
        assertNotNull("Row with two dates must not be dropped", tx)
        assertEquals("Month digit from billing date must not be the amount", 150.0, tx!!.amount, 0.001)
    }

    @Test
    fun `parseCreditCardPdfLine with installment row picks correct amount via column position`() {
        // Row: transaction-date  billing-date  merchant  original-amount  installment  total-inst  charge-in-ILS
        // The "5" installment number must not be selected as amount.
        // With chargeRel pointing to the rightmost position (end of line), 245.00 or 20.42 is chosen.
        // We verify it is NOT 5.0 (the installment count).
        val tx = parseCCPdfLine("01/05/2026  05/06/2026  פנגו-חניונים  245.00  5  12  20.42", chargeAbsPos = 0)
        assertNotNull(tx)
        assertNotEquals("Installment count must not be the amount", 5.0, tx!!.amount, 0.001)
    }

    @Test
    fun `parseCreditCardPdfLine with no date returns null`() {
        assertNull(parseCCPdfLine("MAE  150.00  USD"))
    }

    @Test
    fun `parseCreditCardPdfLine with no numbers returns null`() {
        assertNull(parseCCPdfLine("01/05/2026  ביר בר"))
    }

    @Test
    fun `extractCreditCardTransactionsFromText parses full CC text`() {
        val text = """
            תאריך העסקה  בית העסק  סכום החיוב
            01/05/2026  ארטנובה-שרון מ  150.00
            02/05/2026  פנגו-חניונים  25.50
            03/05/2026  MAE  89.90
        """.trimIndent()
        val txs = extractCCFromText(text)
        assertEquals(3, txs.size)
        assertTrue("All must be EXPENSE", txs.all { it.type == TransactionType.EXPENSE })
        assertEquals(150.0, txs[0].amount, 0.001)
        assertEquals(25.5,  txs[1].amount, 0.001)
        assertEquals(89.9,  txs[2].amount, 0.001)
    }

    @Test
    fun `extractCreditCardTransactionsFromText skips summary row`() {
        val text = """
            בית העסק  תאריך העסקה  סכום החיוב
            ביר בר  01/05/2026  75.00
            סה"כ  01/05/2026  75.00
        """.trimIndent()
        val txs = extractCCFromText(text)
        assertEquals("Summary row must be skipped", 1, txs.size)
    }

    @Test
    fun `extractCreditCardTransactionsFromText returns empty for non-CC text`() {
        val text = """
            תאריך  סוג תנועה  חובה  זכות
            01/05/2026  משכורת  0  5000.00
        """.trimIndent()
        val txs = extractCCFromText(text)
        assertEquals("Non-CC text must produce no results", 0, txs.size)
    }

    // ── Bank PDF row parser ────────────────────────────────────────────────────
    //
    // The bank PDF parser assigns numeric tokens to columns by comparing their
    // relative position in the data-row string against the relative position of
    // each column keyword in the header string.  For this to work in unit tests
    // the strings must be column-aligned — i.e. each value must sit at the same
    // relative character offset as its header keyword.  bankHeader() and bankRow()
    // build fixed-width columns (date=12, desc=18, ref=9, debit=11, credit=11,
    // balance=rest) so the relative positions match exactly.

    private val extractBankFromTextMethod = FileParserService::class.java
        .getDeclaredMethod("extractTransactionsFromText", String::class.java, StringBuilder::class.java)
        .also { it.isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun extractBankFromText(text: String): List<ParsedTransaction> =
        extractBankFromTextMethod.invoke(service, text, null) as List<ParsedTransaction>

    private fun bankHeader(): String =
        "תאריך".padEnd(12) + "סוג תנועה".padEnd(18) + "אסמכתה".padEnd(9) +
        "חובה".padEnd(11) + "זכות".padEnd(11) + "יתרה"

    private fun bankRow(date: String, desc: String, ref: String,
                        debit: String, credit: String, balance: String): String =
        date.padEnd(12) + desc.padEnd(18) + ref.padEnd(9) +
        debit.padEnd(11) + credit.padEnd(11) + balance

    @Test
    fun `bank PDF - credit row (זכות) is INCOME`() {
        val text = bankHeader() + "\n" +
            bankRow("01/06/2026", "משכורת", "789456", "", "15000.00", "30000.00")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.INCOME, txs[0].type)
        assertEquals(15000.0, txs[0].amount, 0.001)
    }

    @Test
    fun `bank PDF - debit row (חובה) is EXPENSE`() {
        val text = bankHeader() + "\n" +
            bankRow("03/06/2026", "קניות", "123456", "3500.00", "", "26500.00")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.EXPENSE, txs[0].type)
        assertEquals(3500.0, txs[0].amount, 0.001)
    }

    @Test
    fun `bank PDF - reference number is excluded, not chosen as amount`() {
        // Reference 789012 in the אסמכתה column must be excluded; 250.00 is the debit amount
        val text = bankHeader() + "\n" +
            bankRow("05/06/2026", "סופר", "789012", "250.00", "", "26250.00")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(250.0, txs[0].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, txs[0].type)
    }

    @Test
    fun `bank PDF - large round amount without decimal is not filtered`() {
        // 100000 has 6 stripped digits and no decimal — the old digit-length heuristic
        // dropped it. With column-based exclusion it must be preserved.
        val text = bankHeader() + "\n" +
            bankRow("10/06/2026", "העברה", "456789", "", "100000", "250000")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(100000.0, txs[0].amount, 0.001)
        assertEquals(TransactionType.INCOME, txs[0].type)
    }

    @Test
    fun `bank PDF - decimal amount preferred over integer reference number`() {
        // Regression test for reported bug: row "15/05/26 זיכוי - בנק הפועלים(י) 732.00 99012"
        // was parsed with amount=99012 because the reference integer was larger than the decimal
        // amount and maxByOrNull picked it. With decimal-preference filtering, all integer tokens
        // are excluded when any decimal token is present, so 732.00 is the only candidate.
        val text = bankHeader() + "\n" +
            bankRow("15/05/26", "זיכוי - בנק הפועלים", "99012", "", "732.00", "1000.00")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals("amount must be 732.00, not the reference number 99012", 732.0, txs[0].amount, 0.001)
        assertEquals(TransactionType.INCOME, txs[0].type)
    }

    @Test
    fun `bank PDF - balance is excluded, not chosen as amount`() {
        val text = bankHeader() + "\n" +
            bankRow("03/06/2026", "שירות", "111222", "3500.00", "", "26500.00")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(3500.0, txs[0].amount, 0.001)
    }

    @Test
    fun `bank PDF - row without date is kept with null date`() {
        // Rows that have an amount but no recognisable date are kept so they
        // appear in the Review screen under "Unknown date". The user can approve
        // or delete them; nothing is silently discarded before Review.
        val text = bankHeader() + "\n" +
            "משכורת".padEnd(30) + "789456".padEnd(9) + "".padEnd(11) + "15000.00".padEnd(11) + "30000.00"
        val txs = extractBankFromText(text)
        assertEquals("Row without date must still be extracted", 1, txs.size)
        assertNull("Date should be null for a dateless row", txs[0].date)
        assertEquals(15000.0, txs[0].amount, 0.001)
    }

    @Test
    fun `bank PDF - all rows parsed correctly end to end`() {
        val text = bankHeader() + "\n" +
            bankRow("01/06/2026", "משכורת", "789456", "", "15000.00", "30000.00") + "\n" +
            bankRow("03/06/2026", "קניות", "123456", "3500.00", "", "26500.00") + "\n" +
            bankRow("05/06/2026", "חשמל", "654321", "250.00", "", "26250.00")
        val txs = extractBankFromText(text)
        assertEquals(3, txs.size)
        assertEquals(TransactionType.INCOME,  txs[0].type)
        assertEquals(15000.0, txs[0].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, txs[1].type)
        assertEquals(3500.0,  txs[1].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, txs[2].type)
        assertEquals(250.0,   txs[2].amount, 0.001)
    }

    // ── Bank Hapoalim: reference number must never be used as amount ───────────

    @Test
    fun `reference number in אסמכתה column is never used as amount`() {
        // Bank Hapoalim 6-column format: date | description | reference | debit | credit | balance
        val mapping = detect(listOf("תאריך", "סוג תנועה", "אסמכתה", "חובה", "זכות", "יתרה בשקלים"))
        assertEquals("referenceColumn must be detected", 2, mapping.referenceColumn)
        assertEquals("debitColumn must be 3", 3, mapping.debitColumn)
        assertEquals("creditColumn must be 4", 4, mapping.creditColumn)

        // זיכוי - בנק הפועלים: credit 732, reference 99012
        val tx = parse(
            listOf("12/03/2024", "זיכוי - בנק הפועלים", "99012", "", "732", "15000"),
            mapping
        )
        assertNotNull("Transaction must not be dropped", tx)
        assertEquals("Amount must be 732 (credit), not 99012 (reference)", 732.0, tx!!.amount, 0.001)
        assertEquals("Credit column → INCOME", TransactionType.INCOME, tx.type)
    }

    @Test
    fun `row where amount equals reference number is rejected`() {
        // Simulate a row where columns have shifted: reference number lands in the debit slot.
        val mapping = ColumnMapping(
            dateColumn = 0, descriptionColumn = 1,
            referenceColumn = 2, debitColumn = 3, creditColumn = 4, balanceColumn = 5
        )
        // Both debit (col 3) and reference (col 2) contain 99012 — parser must reject this row.
        val tx = parse(
            listOf("12/03/2024", "זיכוי - בנק הפועלים", "99012", "99012", "", "15000"),
            mapping
        )
        assertNull("Row where amount == reference must be dropped", tx)
    }

    @Test
    fun `Bank Hapoalim 4-column combined column format parses correctly`() {
        // Format: תאריך | סוג תנועה | זכות/חובה | יתרה בש"ח
        val mapping = detect(listOf("תאריך", "סוג תנועה", "זכות/חובה", "יתרה בש\"ח"))
        assertNotNull("amountColumn must be detected", mapping.amountColumn)
        assertEquals(2, mapping.amountColumn)
        assertTrue("isCombinedAmountColumn must be true", mapping.isCombinedAmountColumn)
        assertNull("no separate debitColumn", mapping.debitColumn)
        assertNull("no separate creditColumn", mapping.creditColumn)
        assertNotNull("balanceColumn must be detected", mapping.balanceColumn)

        val tx = parse(listOf("12/03/2024", "זיכוי - בנק הפועלים", "732", "15000"), mapping)
        assertNotNull("Transaction must not be dropped", tx)
        assertEquals("Amount must be 732", 732.0, tx!!.amount, 0.001)
    }

    // ── Mizrahi-Tefahot: combined signed זכות/חובה column ────────────────────
    //
    // Mizrahi uses a single column for both credit and debit; negative amounts
    // (leading '-') are EXPENSE, positive amounts are INCOME.
    //
    // Column order: date | זכות/חובה (amount) | יתרה בש"ח (balance) | אסמכתה (ref) | סוג תנועה (desc)
    // Putting the amount column immediately after the date ensures that even short
    // data rows keep the amount token at the same absolute offset as in the header,
    // so the relative-position classifier stays in the credit zone.

    private fun mizrahiHeader(): String =
        "תאריך".padEnd(12) + "זכות/חובה".padEnd(16) +
        "יתרה בש\"ח".padEnd(14) + "אסמכתה".padEnd(10) + "סוג תנועה"

    // desc is NOT padded — it is the last column, so no trailing spaces and no trim artifact.
    private fun mizrahiRow(date: String, amount: String, balance: String, ref: String, desc: String): String =
        date.padEnd(12) + amount.padEnd(16) + balance.padEnd(14) + ref.padEnd(10) + desc

    @Test
    fun `Mizrahi - negative amount is EXPENSE`() {
        val text = mizrahiHeader() + "\n" +
            mizrahiRow("02/06/26", "-374.00", "25481.96", "6528", "העברה באינטרנט")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals("Leading minus must be EXPENSE", TransactionType.EXPENSE, txs[0].type)
        assertEquals(374.0, txs[0].amount, 0.001)
    }

    @Test
    fun `Mizrahi - balance is not selected as expense amount`() {
        val text = mizrahiHeader() + "\n" +
            mizrahiRow("01/05/26", "-17199.03", "15303.96", "1642", "גולד מסטרקרד")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(17199.03, txs[0].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, txs[0].type)
    }

    @Test
    fun `Mizrahi - positive amount without balance is INCOME`() {
        val text = mizrahiHeader() + "\n" +
            mizrahiRow("01/06/26", "22934.00", "", "99210", "משכורת")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.INCOME, txs[0].type)
        assertEquals(22934.0, txs[0].amount, 0.001)
    }

    @Test
    fun `Mizrahi - positive amount with balance is INCOME and balance is excluded`() {
        val text = mizrahiHeader() + "\n" +
            mizrahiRow("12/05/26", "2100.00", "36279.22", "93824", "כלל בריאות")
        val txs = extractBankFromText(text)
        assertEquals(1, txs.size)
        assertEquals(TransactionType.INCOME, txs[0].type)
        assertEquals(2100.0, txs[0].amount, 0.001)
    }

    @Test
    fun `Mizrahi - full statement all transactions match expected types`() {
        val text = mizrahiHeader() + "\n" +
            mizrahiRow("02/06/26", "-374.00",   "25481.96", "6528",    "העברה באינטרנט") + "\n" +
            mizrahiRow("01/06/26", "22934.00",  "",         "99210",   "משכורת") + "\n" +
            mizrahiRow("01/06/26", "-150.00",   "",         "2105682", "דירה לילד") + "\n" +
            mizrahiRow("01/06/26", "-7500.00",  "",         "31",      "העברה לבנק אחר") + "\n" +
            mizrahiRow("01/05/26", "-17199.03", "15303.96", "1642",    "גולד מסטרקרד") + "\n" +
            mizrahiRow("12/05/26", "9407.69",   "",         "93512",   "איילון ביטוח") + "\n" +
            mizrahiRow("12/05/26", "2100.00",   "36279.22", "93824",   "כלל בריאות") + "\n" +
            mizrahiRow("11/05/26", "9523.04",   "24771.53", "70556",   "הפניקס ביטוח")
        val txs = extractBankFromText(text)
        assertEquals("All 8 rows must be parsed", 8, txs.size)

        data class Expected(val amount: Double, val type: TransactionType)
        val expected = listOf(
            Expected(374.0,     TransactionType.EXPENSE),
            Expected(22934.0,   TransactionType.INCOME),
            Expected(150.0,     TransactionType.EXPENSE),
            Expected(7500.0,    TransactionType.EXPENSE),
            Expected(17199.03,  TransactionType.EXPENSE),
            Expected(9407.69,   TransactionType.INCOME),
            Expected(2100.0,    TransactionType.INCOME),
            Expected(9523.04,   TransactionType.INCOME),
        )
        txs.zip(expected).forEachIndexed { i, (tx, exp) ->
            assertEquals("Row $i amount", exp.amount, tx.amount, 0.001)
            assertEquals("Row $i type",   exp.type,   tx.type)
        }
    }

    // ── CSV header row search (findHeaderRowIndexInCsv) ───────────────────────

    private val findHeaderRowIndexInCsvMethod = FileParserService::class.java
        .getDeclaredMethod("findHeaderRowIndexInCsv", List::class.java)
        .also { it.isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun findHeaderIdx(rows: List<Array<String>>) =
        findHeaderRowIndexInCsvMethod.invoke(service, rows) as Int

    @Test
    fun `findHeaderRowIndexInCsv returns 0 when first row is the header`() {
        val rows = listOf(
            arrayOf("תאריך", "סוג תנועה", "אסמכתה", "חובה", "זכות", "יתרה בשקלים"),
            arrayOf("12/03/2024", "זיכוי - בנק הפועלים", "99012", "", "732", "15000")
        )
        assertEquals(0, findHeaderIdx(rows))
    }

    @Test
    fun `findHeaderRowIndexInCsv skips metadata rows and finds real header`() {
        val rows = listOf(
            arrayOf("בנק הפועלים - תדפיס חשבון"),
            arrayOf("חשבון:", "12345678", "", "", "", ""),
            arrayOf("תאריך", "סוג תנועה", "אסמכתה", "חובה", "זכות", "יתרה בשקלים"),
            arrayOf("12/03/2024", "זיכוי", "99012", "", "732", "15000")
        )
        assertEquals("Real header is at row 2", 2, findHeaderIdx(rows))
    }

    @Test
    fun `findHeaderRowIndexInCsv returns 0 as safe fallback when no header row found`() {
        val rows = listOf(
            arrayOf("foo", "bar", "baz"),
            arrayOf("1", "2", "3")
        )
        assertEquals(0, findHeaderIdx(rows))
    }
}
