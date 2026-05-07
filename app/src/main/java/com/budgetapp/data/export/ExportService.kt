package com.budgetapp.data.export

import com.budgetapp.core.util.toDateString
import com.budgetapp.domain.model.Transaction
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportService @Inject constructor() {

    fun exportToCsv(transactions: List<Transaction>): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("Date,Description,Category,Type,Amount,Bank")
        transactions.forEach { t ->
            sb.appendLine(
                listOf(
                    t.date.toDateString("yyyy-MM-dd"),
                    t.description.escapeCsv(),
                    t.category.name.escapeCsv(),
                    t.type.name,
                    t.amount.toString(),
                    (t.bankName ?: "").escapeCsv()
                ).joinToString(",")
            )
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun exportToExcel(transactions: List<Transaction>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Transactions")

        val headerStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
            }
            setFont(font)
        }

        val headers = listOf("Date", "Description", "Category", "Type", "Amount", "Bank")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            headerRow.createCell(i).apply {
                setCellValue(h)
                cellStyle = headerStyle
            }
        }

        transactions.forEachIndexed { index, t ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(t.date.toDateString("yyyy-MM-dd"))
            row.createCell(1).setCellValue(t.description)
            row.createCell(2).setCellValue(t.category.name)
            row.createCell(3).setCellValue(t.type.name)
            row.createCell(4).setCellValue(t.amount)
            row.createCell(5).setCellValue(t.bankName ?: "")
        }

        headers.indices.forEach { sheet.autoSizeColumn(it) }

        val bos = ByteArrayOutputStream()
        workbook.write(bos)
        workbook.close()
        return bos.toByteArray()
    }

    private fun String.escapeCsv(): String {
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }
}

enum class ExportFormat { CSV, EXCEL }

enum class ExportRange {
    ALL,
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    THIS_YEAR
}
