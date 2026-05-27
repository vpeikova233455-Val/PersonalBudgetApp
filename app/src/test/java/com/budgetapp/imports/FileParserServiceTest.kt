package com.budgetapp.imports

import android.content.Context
import com.budgetapp.data.remote.gemini.ColumnMapping
import com.budgetapp.data.remote.gemini.FileParserService
import com.budgetapp.data.remote.gemini.ParsedTransaction
import com.budgetapp.data.remote.gemini.TransactionType
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

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
}
