package com.budgetapp.imports

import android.content.Context
import com.budgetapp.data.remote.gemini.ColumnMapping
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
    fun `combined debit-credit header is detected as amountColumn`() {
        // "זכות/חובה" contains both a credit keyword (זכות) and a debit keyword (חובה)
        val mapping = detect(listOf("תאריך", "סוג תנועה", "זכות/חובה", "יתרה בש\"ח"))
        assertNotNull("combined column should map to amountColumn", mapping.amountColumn)
        assertEquals(2, mapping.amountColumn)
        assertNull("debitColumn should be null for combined header", mapping.debitColumn)
        assertNull("creditColumn should be null for combined header", mapping.creditColumn)
        assertNotNull("balance column should be detected", mapping.balanceColumn)
    }

    @Test
    fun `combined debit-credit header in English is detected as amountColumn`() {
        val mapping = detect(listOf("Date", "Description", "Debit/Credit", "Balance"))
        assertNotNull(mapping.amountColumn)
        assertEquals(2, mapping.amountColumn)
        assertNull(mapping.debitColumn)
        assertNull(mapping.creditColumn)
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

    // ── Description-based overrides ────────────────────────────────────────────

    @Test
    fun `העברה באינטרנט is always EXPENSE even when landed in credit column`() {
        // Simulate a bank file where the column detection puts the row in credit
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("העברה באינטרנט", "450.00"), mapping)
        assertNotNull(tx)
        assertEquals("העברה באינטרנט must always be EXPENSE", TransactionType.EXPENSE, tx!!.type)
        assertEquals(450.0, tx.amount, 0.001)
    }

    @Test
    fun `העברה באינטרנט in debit column stays EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, debitColumn = 1, creditColumn = 2)
        val tx = parse(listOf("העברה באינטרנט", "200.00", ""), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `Gold Mastercard in credit column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Gold Mastercard", "1500.00"), mapping)
        assertNotNull(tx)
        assertEquals("Credit card charges must always be EXPENSE", TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `גולד מסטרקארד in credit column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("גולד מסטרקארד", "2300.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `גולד מאסטרקארד (aleph variant) in credit column is EXPENSE`() {
        // This is the actual spelling used by some Israeli bank exports — note מ-א-ס vs מ-ס
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("גולד מאסטרקארד", "2300.00"), mapping)
        assertNotNull(tx)
        assertEquals("גולד מאסטרקארד must be EXPENSE", TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `Visa charge in credit column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Visa Classic", "800.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `כרטיס אשראי in credit column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("כרטיס אשראי לאומי", "3200.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `חיוב כרטיס in credit column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("חיוב כרטיס ויזה", "950.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }

    @Test
    fun `Diners charge in credit column is EXPENSE`() {
        val mapping = ColumnMapping(descriptionColumn = 0, creditColumn = 1)
        val tx = parse(listOf("Diners Gold", "600.00"), mapping)
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
    }
}
