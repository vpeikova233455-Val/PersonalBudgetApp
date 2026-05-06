package com.budgetapp.data.remote.gemini

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for parsing bank statement files (Excel, CSV)
 * Extracts transaction data for AI review
 */
@Singleton
class FileParserService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Parse transactions from a file URI
     * Supports .xlsx, .xls, and .csv formats
     */
    suspend fun parseFile(uri: Uri): FileParseResult {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            when {
                mimeType?.contains("spreadsheet") == true ||
                uri.path?.endsWith(".xlsx") == true ||
                uri.path?.endsWith(".xls") == true -> parseExcelFile(uri)

                mimeType?.contains("csv") == true ||
                uri.path?.endsWith(".csv") == true -> parseCsvFile(uri)

                else -> FileParseResult.Error("Unsupported file type: $mimeType")
            }
        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse file: ${e.message}")
        }
    }

    private fun parseExcelFile(uri: Uri): FileParseResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return FileParseResult.Error("Could not open file")

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val transactions = mutableListOf<ParsedTransaction>()

            // Try to detect header row
            val headerRow = sheet.getRow(0)
            val columnMapping = detectColumnMapping(headerRow?.map { it.toString() } ?: emptyList())

            // Parse data rows (skip header)
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                if (row.physicalNumberOfCells == 0) continue

                try {
                    val parsed = parseExcelRow(row, columnMapping)
                    if (parsed != null) {
                        transactions.add(parsed)
                    }
                } catch (e: Exception) {
                    // Skip invalid rows
                    continue
                }
            }

            workbook.close()
            inputStream.close()

            if (transactions.isEmpty()) {
                FileParseResult.Error("No valid transactions found in file")
            } else {
                FileParseResult.Success(transactions)
            }
        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse Excel: ${e.message}")
        }
    }

    private fun parseCsvFile(uri: Uri): FileParseResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return FileParseResult.Error("Could not open file")

            val reader = CSVReader(InputStreamReader(inputStream))
            val allRows = reader.readAll()
            reader.close()

            if (allRows.isEmpty()) {
                return FileParseResult.Error("File is empty")
            }

            // Detect column mapping from header
            val headerRow = allRows[0].toList()
            val columnMapping = detectColumnMapping(headerRow)

            val transactions = mutableListOf<ParsedTransaction>()

            // Parse data rows (skip header)
            for (i in 1 until allRows.size) {
                val row = allRows[i]
                try {
                    val parsed = parseCsvRow(row, columnMapping)
                    if (parsed != null) {
                        transactions.add(parsed)
                    }
                } catch (e: Exception) {
                    // Skip invalid rows
                    continue
                }
            }

            if (transactions.isEmpty()) {
                FileParseResult.Error("No valid transactions found in file")
            } else {
                FileParseResult.Success(transactions)
            }
        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse CSV: ${e.message}")
        }
    }

    private fun detectColumnMapping(headers: List<String>): ColumnMapping {
        val mapping = ColumnMapping()

        headers.forEachIndexed { index, header ->
            val headerLower = header.lowercase()
            when {
                headerLower.contains("date") -> mapping.dateColumn = index
                headerLower.contains("description") ||
                headerLower.contains("memo") ||
                headerLower.contains("details") -> mapping.descriptionColumn = index

                headerLower.contains("amount") ||
                headerLower.contains("value") -> mapping.amountColumn = index

                headerLower.contains("debit") ||
                headerLower.contains("withdrawal") -> mapping.debitColumn = index

                headerLower.contains("credit") ||
                headerLower.contains("deposit") -> mapping.creditColumn = index

                headerLower.contains("balance") -> mapping.balanceColumn = index
            }
        }

        return mapping
    }

    private fun parseExcelRow(row: org.apache.poi.ss.usermodel.Row, mapping: ColumnMapping): ParsedTransaction? {
        val cells = (0 until row.lastCellNum).map { index ->
            val cell = row.getCell(index)
            cell?.toString() ?: ""
        }

        return parseRowData(cells, mapping)
    }

    private fun parseCsvRow(row: Array<String>, mapping: ColumnMapping): ParsedTransaction? {
        return parseRowData(row.toList(), mapping)
    }

    private fun parseRowData(cells: List<String>, mapping: ColumnMapping): ParsedTransaction? {
        // Extract description
        val description = mapping.descriptionColumn?.let { cells.getOrNull(it) }
            ?.takeIf { it.isNotBlank() } ?: return null

        // Extract amount (from either amount column or debit/credit columns)
        val (amount, type) = when {
            mapping.amountColumn != null -> {
                val col = mapping.amountColumn!!
                val amountStr = cells.getOrNull(col)
                val amt = parseAmount(amountStr)
                val isExpense = amountStr?.startsWith("-") == true ||
                    amountStr?.contains("debit", ignoreCase = true) == true
                amt to if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
            }

            mapping.debitColumn != null -> {
                val amt = parseAmount(cells.getOrNull(mapping.debitColumn!!))
                amt to TransactionType.EXPENSE
            }

            mapping.creditColumn != null -> {
                val amt = parseAmount(cells.getOrNull(mapping.creditColumn!!))
                amt to TransactionType.INCOME
            }

            else -> return null
        }

        if (amount == null || amount == 0.0) return null

        // Extract date
        val dateStr = mapping.dateColumn?.let { cells.getOrNull(it) }
        val date = parseDate(dateStr)

        return ParsedTransaction(
            description = description,
            amount = amount,
            date = date,
            type = type,
            rawData = cells.joinToString(" | ")
        )
    }

    private fun parseAmount(amountStr: String?): Double? {
        if (amountStr.isNullOrBlank()) return null

        return try {
            // Remove currency symbols, commas, and extra spaces
            val cleaned = amountStr
                .replace(Regex("[^0-9.-]"), "")
                .trim()

            if (cleaned.isEmpty()) null
            else Math.abs(cleaned.toDouble()) // Always positive, type determines income/expense
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDate(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null

        // Try common date formats
        val formats = listOf(
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "MMM dd, yyyy",
            "dd-MMM-yyyy"
        )

        for (format in formats) {
            try {
                val parser = SimpleDateFormat(format, Locale.US)
                parser.isLenient = false
                val date = parser.parse(dateStr)
                if (date != null) {
                    // Return in standard format
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                }
            } catch (e: Exception) {
                continue
            }
        }

        return null // Could not parse date
    }
}

// Data classes
data class ColumnMapping(
    var dateColumn: Int? = null,
    var descriptionColumn: Int? = null,
    var amountColumn: Int? = null,
    var debitColumn: Int? = null,
    var creditColumn: Int? = null,
    var balanceColumn: Int? = null
)

data class ParsedTransaction(
    val description: String,
    val amount: Double,
    val date: String?,
    val type: TransactionType,
    val rawData: String
)

sealed class FileParseResult {
    data class Success(val transactions: List<ParsedTransaction>) : FileParseResult()
    data class Error(val message: String) : FileParseResult()
}
