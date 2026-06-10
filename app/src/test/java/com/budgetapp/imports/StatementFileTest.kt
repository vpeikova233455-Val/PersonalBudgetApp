package com.budgetapp.imports

import android.content.Context
import com.budgetapp.data.remote.gemini.ColumnMapping
import com.budgetapp.data.remote.gemini.CreditCardMapping
import com.budgetapp.data.remote.gemini.FileParserService
import com.budgetapp.data.remote.gemini.ParsedTransaction
import com.budgetapp.data.remote.gemini.TransactionType
import io.mockk.mockk
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Opens real bank/CC statement files and runs them through the same parsing
 * pipeline as FileParserService.parseExcelFile, then asserts correct types.
 *
 * Run: ./gradlew testDebugUnitTest --tests "*.StatementFileTest" --info
 */
class StatementFileTest {

    private val service = FileParserService(mockk<Context>(relaxed = true))

    // ── Reflection accessors matching FileParserService.parseExcelFile flow ──

    private val cellText = FileParserService::class.java
        .getDeclaredMethod("cellText", org.apache.poi.ss.usermodel.Cell::class.java)
        .also { it.isAccessible = true }

    private val findHeaderRow = FileParserService::class.java
        .getDeclaredMethod("findHeaderRow", org.apache.poi.ss.usermodel.Sheet::class.java)
        .also { it.isAccessible = true }

    private val isCreditCardFormat = FileParserService::class.java
        .getDeclaredMethod("isCreditCardFormat", List::class.java)
        .also { it.isAccessible = true }

    private val detectColumnMapping = FileParserService::class.java
        .getDeclaredMethod("detectColumnMapping", List::class.java)
        .also { it.isAccessible = true }

    private val detectCreditCardMapping = FileParserService::class.java
        .getDeclaredMethod("detectCreditCardMapping", List::class.java)
        .also { it.isAccessible = true }

    private val parseExcelRow = FileParserService::class.java
        .getDeclaredMethod("parseExcelRow",
            org.apache.poi.ss.usermodel.Row::class.java,
            ColumnMapping::class.java)
        .also { it.isAccessible = true }

    private val parseCreditCardRow = FileParserService::class.java
        .getDeclaredMethod("parseCreditCardRow", List::class.java, CreditCardMapping::class.java)
        .also { it.isAccessible = true }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cellTextOf(cell: org.apache.poi.ss.usermodel.Cell?) =
        cellText.invoke(service, cell) as String

    private fun headerRow(idx: Int, sheet: org.apache.poi.ss.usermodel.Sheet) =
        sheet.getRow(idx)?.map { cellTextOf(it) } ?: emptyList()

    /**
     * Mirrors FileParserService.parseExcelFile — open file, find headers,
     * branch on CC vs bank statement, parse all rows.
     */
    private fun parseFile(file: File): List<ParsedTransaction> {
        val workbook = WorkbookFactory.create(file)
        val transactions = mutableListOf<ParsedTransaction>()

        for (si in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(si)
            if (sheet.physicalNumberOfRows == 0) continue

            val headerIdx = findHeaderRow.invoke(service, sheet) as Int
            val headers   = headerRow(headerIdx, sheet)
            if (headers.size < 2) continue

            val isCC = isCreditCardFormat.invoke(service, headers) as Boolean

            if (isCC) {
                val ccMapping = detectCreditCardMapping.invoke(service, headers) as CreditCardMapping
                println("  [CC] header=$headers ccMapping=$ccMapping")
                for (rowIdx in (headerIdx + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIdx) ?: continue
                    if (row.physicalNumberOfCells == 0) continue
                    val cells = (0 until row.lastCellNum).map { i -> cellTextOf(row.getCell(i)) }
                    val tx = parseCreditCardRow.invoke(service, cells, ccMapping) as? ParsedTransaction
                    if (tx != null) transactions.add(tx)
                }
            } else {
                val mapping = detectColumnMapping.invoke(service, headers) as ColumnMapping
                println("  [Bank] header=$headers mapping=$mapping")
                for (rowIdx in (headerIdx + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIdx) ?: continue
                    if (row.physicalNumberOfCells == 0) continue
                    val tx = parseExcelRow.invoke(service, row, mapping) as? ParsedTransaction
                    if (tx != null) transactions.add(tx)
                }
            }
        }
        workbook.close()
        return transactions
    }

    /**
     * Dumps all cell info for a row — useful for diagnosing color / format issues.
     */
    private fun dumpRow(row: Row) {
        for (i in 0 until row.lastCellNum) {
            val cell = row.getCell(i) ?: continue
            val text      = cellTextOf(cell)
            val formatted = try { DataFormatter().formatCellValue(cell).trim() } catch (_: Exception) { "" }
            val numVal    = try { cell.numericCellValue } catch (_: Exception) { null }
            val fmtStr    = cell.cellStyle?.dataFormatString ?: ""
            val fontRgb   = (cell as? XSSFCell)?.let { xc ->
                val xf  = (xc.cellStyle as? XSSFCellStyle)?.font as? XSSFFont
                val fc  = xf?.xssfColor
                val rgb = fc?.rgb
                    ?: fc?.argb?.let { if (it.size >= 4) it.copyOfRange(1, 4) else null }
                    ?: try { fc?.getRGBWithTint() } catch (_: Exception) { null }
                rgb?.let { "(${it[0].toInt() and 0xFF},${it[1].toInt() and 0xFF},${it[2].toInt() and 0xFF})" }
            }
            println("    col$i: text='$text' formatted='$formatted' numVal=$numVal fmtStr='$fmtStr' fontRgb=$fontRgb")
        }
    }

    // ── AccountActivity.xls ───────────────────────────────────────────────────

    @Test
    fun `AccountActivity xls - cannot be opened by POI (HTML file)`() {
        val file = File(System.getProperty("user.home"), "Downloads/AccountActivity.xls")
        if (!file.exists()) { println("SKIP: file not found"); return }

        println("\n=== AccountActivity.xls ===")
        println("First 80 chars: ${String(file.readBytes().take(80).toByteArray(), Charsets.UTF_8)}")
        try {
            WorkbookFactory.create(file)
            fail("Expected POI to throw on HTML file, but it succeeded")
        } catch (e: Exception) {
            println("POI threw: ${e.javaClass.simpleName}: ${e.message}")
            println("CONCLUSION: This file is HTML-as-XLS; app shows an error when importing it.")
            println("The actual statement file with test transactions is not in ~/Downloads.")
        }
    }

    // ── כל הכרטיסים (1).xlsx — Mastercard Gold credit card statement ─────────

    @Test
    fun `credit card xlsx - all transactions classified as EXPENSE`() {
        val file = File(System.getProperty("user.home"), "Downloads/כל הכרטיסים (1).xlsx")
        if (!file.exists()) { println("SKIP: file not found"); return }

        println("\n=== כל הכרטיסים (1).xlsx ===")
        val txs = parseFile(file)
        println("Total transactions: ${txs.size}")
        txs.forEach { println("  '${it.description}' amount=${it.amount} type=${it.type}") }

        assertTrue("Expected at least 1 transaction", txs.isNotEmpty())

        val wrongType = txs.filter { it.type != TransactionType.EXPENSE }
        if (wrongType.isNotEmpty()) {
            println("\nFAIL — these were classified as ${wrongType.first().type} instead of EXPENSE:")
            wrongType.forEach { println("  '${it.description}' amount=${it.amount}") }
        }
        assertTrue("All CC transactions must be EXPENSE", wrongType.isEmpty())
    }

    // ── כל הכרטיסים.xlsx — second CC file ────────────────────────────────────

    @Test
    fun `credit card xlsx v2 - all transactions classified as EXPENSE`() {
        val file = File(System.getProperty("user.home"), "Downloads/כל הכרטיסים.xlsx")
        if (!file.exists()) { println("SKIP: file not found"); return }

        println("\n=== כל הכרטיסים.xlsx ===")
        val txs = parseFile(file)
        println("Total transactions: ${txs.size}")
        txs.forEach { println("  '${it.description}' amount=${it.amount} type=${it.type}") }

        assertTrue("Expected at least 1 transaction", txs.isNotEmpty())

        val wrongType = txs.filter { it.type != TransactionType.EXPENSE }
        assertTrue("All CC transactions must be EXPENSE", wrongType.isEmpty())
    }

    // ── Mizrahi HTML statement diagnostic ────────────────────────────────────

    @Test
    fun `AccountActivity xls - diagnose what a CSV fallback would produce`() {
        val file = File(System.getProperty("user.home"), "Downloads/AccountActivity.xls")
        if (!file.exists()) { println("SKIP"); return }

        // Parse as HTML to simulate what the app sees
        val html = file.readText(Charsets.UTF_8)
        val rowRegex = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
        val tagRegex  = Regex("<[^>]+>")
        val rows = rowRegex.findAll(html).toList()
        println("\n=== AccountActivity.xls as HTML ===")
        println("Total <tr> rows: ${rows.size}")
        rows.forEachIndexed { i, mr ->
            val cells = cellRegex.findAll(mr.groupValues[1])
                .map { tagRegex.replace(it.groupValues[1], "").trim() }
                .filter { it.isNotEmpty() && it != "&nbsp;" }
                .toList()
            if (cells.size >= 3) println("Row $i: $cells")
        }
    }
}
