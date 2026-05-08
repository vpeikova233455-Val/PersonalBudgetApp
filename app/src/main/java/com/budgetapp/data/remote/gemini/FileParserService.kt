package com.budgetapp.data.remote.gemini

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileParserService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun parseFile(uri: Uri): FileParseResult {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            val path = uri.path?.lowercase() ?: ""
            when {
                mimeType == "application/pdf" || path.endsWith(".pdf") ->
                    parsePdfFile(uri)

                mimeType?.contains("spreadsheet") == true ||
                mimeType?.contains("excel") == true ||
                path.endsWith(".xlsx") || path.endsWith(".xls") -> parseExcelFile(uri)

                mimeType?.contains("csv") == true ||
                mimeType?.contains("plain") == true ||
                path.endsWith(".csv") -> parseCsvFile(uri)

                else -> tryExcelThenCsv(uri)
            }
        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse file: ${e.message}")
        }
    }

    private fun tryExcelThenCsv(uri: Uri): FileParseResult {
        val excelResult = parseExcelFile(uri)
        if (excelResult is FileParseResult.Success) return excelResult
        return parseCsvFile(uri)
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    private fun parsePdfFile(uri: Uri): FileParseResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return FileParseResult.Error("Could not open PDF")
            val doc = PDDocument.load(inputStream)

            // Try sorted extraction first (better column alignment for LTR/mixed content)
            val sortedText = PDFTextStripper().apply {
                sortByPosition = true
                addMoreFormatting = true
            }.getText(doc)

            // Also try natural order (can be better for RTL/Hebrew PDFs)
            val naturalText = PDFTextStripper().apply {
                sortByPosition = false
            }.getText(doc)

            doc.close()
            inputStream.close()

            val sortedTx = if (sortedText.isNotBlank()) extractTransactionsFromText(sortedText) else emptyList()
            val naturalTx = if (naturalText.isNotBlank()) extractTransactionsFromText(naturalText) else emptyList()

            val transactions = if (sortedTx.size >= naturalTx.size) sortedTx else naturalTx

            when {
                transactions.isNotEmpty() -> FileParseResult.Success(transactions)
                sortedText.isNotBlank() ->
                    // Return raw text so the error dialog shows it for debugging
                    FileParseResult.Error(
                        "Could not parse transactions from this PDF.\n\n" +
                        "Raw text (first 600 chars — paste this to get help tuning the parser):\n\n" +
                        sortedText.take(600)
                    )
                else -> FileParseResult.NeedsOcr
            }
        } catch (e: Exception) {
            FileParseResult.NeedsOcr
        }
    }

    private fun extractTransactionsFromText(text: String): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Find header row — contains date keyword AND at least one amount/debit/credit keyword
        val headerIdx = lines.indexOfFirst { line ->
            val l = line.lowercase()
            DATE_KEYWORDS.any { l.contains(it) } &&
            (AMOUNT_KEYWORDS + DEBIT_KEYWORDS + CREDIT_KEYWORDS).any { l.contains(it) }
        }

        val layout: PdfColumnLayout? = if (headerIdx >= 0) detectLayout(lines[headerIdx]) else null
        val startIdx = if (headerIdx >= 0) headerIdx + 1 else 0

        for (i in startIdx until lines.size) {
            val line = lines[i]
            if (isSummaryRow(line)) continue
            if (headerIdx >= 0 && lines[headerIdx].lowercase().let { h ->
                DATE_KEYWORDS.any { h.contains(it) }
            } && line.lowercase().let { l -> DATE_KEYWORDS.any { l.contains(it) } }) continue

            val tx = if (layout != null) parseColumnLine(line, layout) else parseFreeformLine(line)
            if (tx != null) transactions.add(tx)
        }
        return transactions
    }

    private data class PdfColumnLayout(
        val hasDebit: Boolean,
        val hasCredit: Boolean,
        val debitBeforeCredit: Boolean
    )

    private fun detectLayout(header: String): PdfColumnLayout {
        val l = header.lowercase()
        val hasDebit  = DEBIT_KEYWORDS.any  { l.contains(it) }
        val hasCredit = CREDIT_KEYWORDS.any { l.contains(it) }
        val debitPos  = DEBIT_KEYWORDS.mapNotNull  { if (l.contains(it)) l.indexOf(it) else null }.minOrNull() ?: Int.MAX_VALUE
        val creditPos = CREDIT_KEYWORDS.mapNotNull { if (l.contains(it)) l.indexOf(it) else null }.minOrNull() ?: Int.MAX_VALUE
        return PdfColumnLayout(hasDebit, hasCredit, debitPos < creditPos)
    }

    /** Parse a line from a structured table (header was detected). */
    private fun parseColumnLine(line: String, layout: PdfColumnLayout): ParsedTransaction? {
        // Split on 2+ spaces or tabs — these are the column boundaries
        val cols = line.split(Regex("\\t| {2,}")).map { it.trim() }.filter { it.isNotBlank() }
        if (cols.size < 2) return null

        var date: String? = null
        var description: String? = null
        val numbers = mutableListOf<Double>()

        for (col in cols) {
            when {
                date == null && PDF_DATE_RE.containsMatchIn(col) -> {
                    date = parseDate(PDF_DATE_RE.find(col)!!.value)
                    val rest = PDF_DATE_RE.replace(col, "").trim()
                    if (rest.isNotBlank() && !rest.matches(PDF_NUM_RE)) description = rest
                }
                col.matches(PDF_NUM_RE) -> parseAmount(col)?.takeIf { it > 0 }?.let { numbers.add(it) }
                description == null && !isSummaryRow(col) -> description = col
            }
        }

        if (description.isNullOrBlank() || numbers.isEmpty()) return null
        if (isSummaryRow(description!!)) return null

        // Last number is usually the running balance — drop it when ≥3 numbers present
        val txNumbers = if (numbers.size >= 3) numbers.dropLast(1) else numbers

        val amount: Double
        val type: TransactionType

        if (layout.hasDebit && layout.hasCredit && txNumbers.size >= 2) {
            // Two amounts: whichever is non-zero in its column wins
            val first  = txNumbers[0]
            val second = txNumbers[1]
            when {
                first > 0 && second == 0.0 -> { amount = first;  type = if (layout.debitBeforeCredit) TransactionType.EXPENSE else TransactionType.INCOME }
                second > 0 && first == 0.0 -> { amount = second; type = if (layout.debitBeforeCredit) TransactionType.INCOME  else TransactionType.EXPENSE }
                else -> { amount = first; type = TransactionType.EXPENSE }
            }
        } else {
            amount = txNumbers.first()
            type = when {
                layout.hasDebit && !layout.hasCredit  -> TransactionType.EXPENSE
                layout.hasCredit && !layout.hasDebit  -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
        }

        if (amount == 0.0) return null
        return ParsedTransaction(description = description!!, amount = amount, date = date, type = type, rawData = line)
    }

    /** Parse a line with no detected header (freeform). */
    private fun parseFreeformLine(line: String): ParsedTransaction? {
        val amountMatch = PDF_TRAILING_AMOUNT_RE.find(line) ?: return null
        val amount = parseAmount(amountMatch.groupValues[1]) ?: return null
        if (amount == 0.0) return null

        val beforeAmount = line.substring(0, amountMatch.range.first).trim()
        val dateMatch = PDF_DATE_RE.find(beforeAmount)
        val date = dateMatch?.let { parseDate(it.value) }
        val description = if (dateMatch != null)
            beforeAmount.removeRange(dateMatch.range).trim()
        else beforeAmount

        if (description.isBlank() || isSummaryRow(description)) return null

        val type = if (amountMatch.groupValues[1].startsWith("-")) TransactionType.EXPENSE else TransactionType.INCOME
        return ParsedTransaction(description = description, amount = amount, date = date, type = type, rawData = line)
    }

    // ── Excel ──────────────────────────────────────────────────────────────────

    private fun parseExcelFile(uri: Uri): FileParseResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return FileParseResult.Error("Could not open file")

            val workbook = WorkbookFactory.create(inputStream)
            val transactions = mutableListOf<ParsedTransaction>()

            // Iterate every sheet (banks sometimes export one sheet per month)
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                if (sheet.physicalNumberOfRows == 0) continue

                // Find the header row (first row that looks like column headers)
                val headerRowIndex = findHeaderRow(sheet)
                val headerRow = sheet.getRow(headerRowIndex)
                val headers = headerRow?.map { cellText(it) } ?: emptyList()
                val mapping = detectColumnMapping(headers)

                if (!mapping.hasEnoughColumns()) continue

                for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    if (row.physicalNumberOfCells == 0) continue
                    try {
                        val parsed = parseExcelRow(row, mapping)
                        if (parsed != null) transactions.add(parsed)
                    } catch (_: Exception) { }
                }
            }

            workbook.close()
            inputStream.close()

            if (transactions.isEmpty())
                FileParseResult.Error("No valid transactions found in file")
            else
                FileParseResult.Success(transactions)

        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse Excel: ${e.message}")
        }
    }

    /** Scan the first 10 rows for the one most likely to be a header. */
    private fun findHeaderRow(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        for (i in 0..minOf(9, sheet.lastRowNum)) {
            val row = sheet.getRow(i) ?: continue
            val texts = row.map { cellText(it).lowercase() }
            val score = texts.count { h ->
                DATE_KEYWORDS.any { h.contains(it) } ||
                DESC_KEYWORDS.any { h.contains(it) } ||
                AMOUNT_KEYWORDS.any { h.contains(it) } ||
                DEBIT_KEYWORDS.any { h.contains(it) } ||
                CREDIT_KEYWORDS.any { h.contains(it) }
            }
            if (score >= 2) return i
        }
        return 0
    }

    private fun parseExcelRow(row: org.apache.poi.ss.usermodel.Row, mapping: ColumnMapping): ParsedTransaction? {
        val cells = (0 until row.lastCellNum).map { i -> cellText(row.getCell(i)) }
        return parseRowData(cells, mapping)
    }

    /** Extract a clean string from any cell type, preserving dates as yyyy-MM-dd. */
    private fun cellText(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cell.dateCellValue)
                    } catch (_: Exception) { cell.toString() }
                } else {
                    // Avoid scientific notation for large transaction amounts
                    val d = cell.numericCellValue
                    if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toLong().toString()
                    else d.toString()
                }
            }
            CellType.FORMULA -> {
                try {
                    if (DateUtil.isCellDateFormatted(cell))
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cell.dateCellValue)
                    else
                        cell.numericCellValue.let { d ->
                            if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toLong().toString()
                            else d.toString()
                        }
                } catch (_: Exception) { cell.toString() }
            }
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.BLANK, CellType._NONE, null -> ""
            else -> cell.toString().trim()
        }
    }

    // ── CSV ───────────────────────────────────────────────────────────────────

    private fun parseCsvFile(uri: Uri): FileParseResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return FileParseResult.Error("Could not open file")

            val reader = CSVReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val allRows = reader.readAll()
            reader.close()

            if (allRows.isEmpty()) return FileParseResult.Error("File is empty")

            val headerRow = allRows[0].toList()
            val mapping = detectColumnMapping(headerRow)

            if (!mapping.hasEnoughColumns())
                return FileParseResult.Error("Could not identify transaction columns in this file")

            val transactions = mutableListOf<ParsedTransaction>()
            for (i in 1 until allRows.size) {
                try {
                    val parsed = parseRowData(allRows[i].toList(), mapping)
                    if (parsed != null) transactions.add(parsed)
                } catch (_: Exception) { }
            }

            if (transactions.isEmpty())
                FileParseResult.Error("No valid transactions found in file")
            else
                FileParseResult.Success(transactions)

        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse CSV: ${e.message}")
        }
    }

    // ── Column detection ──────────────────────────────────────────────────────

    private fun detectColumnMapping(headers: List<String>): ColumnMapping {
        val mapping = ColumnMapping()
        headers.forEachIndexed { index, raw ->
            val h = raw.trim().lowercase()
            when {
                DATE_KEYWORDS.any { h.contains(it) }   -> if (mapping.dateColumn == null) mapping.dateColumn = index
                DESC_KEYWORDS.any { h.contains(it) }   -> if (mapping.descriptionColumn == null) mapping.descriptionColumn = index
                DEBIT_KEYWORDS.any { h.contains(it) }  -> if (mapping.debitColumn == null) mapping.debitColumn = index
                CREDIT_KEYWORDS.any { h.contains(it) } -> if (mapping.creditColumn == null) mapping.creditColumn = index
                AMOUNT_KEYWORDS.any { h.contains(it) } -> if (mapping.amountColumn == null) mapping.amountColumn = index
                BALANCE_KEYWORDS.any { h.contains(it) }-> if (mapping.balanceColumn == null) mapping.balanceColumn = index
            }
        }

        // If no amount column found, try to infer from position: if there's a description and
        // a numeric column near the end, assume it's the amount
        if (!mapping.hasEnoughColumns() && mapping.descriptionColumn != null) {
            val lastIndex = headers.size - 1
            if (mapping.amountColumn == null && mapping.debitColumn == null) {
                mapping.amountColumn = lastIndex
            }
        }

        return mapping
    }

    // ── Row parsing ───────────────────────────────────────────────────────────

    private fun parseRowData(cells: List<String>, mapping: ColumnMapping): ParsedTransaction? {
        val description = mapping.descriptionColumn
            ?.let { cells.getOrNull(it)?.trim() }
            ?.takeIf { it.isNotBlank() && !isSummaryRow(it) }
            ?: return null

        val amountStr = when {
            mapping.amountColumn != null -> cells.getOrNull(mapping.amountColumn!!)
            mapping.debitColumn != null  -> cells.getOrNull(mapping.debitColumn!!)
            mapping.creditColumn != null -> cells.getOrNull(mapping.creditColumn!!)
            else -> return null
        }

        val rawAmount = amountStr?.trim() ?: return null
        val amount = parseAmount(rawAmount) ?: return null
        if (amount == 0.0) return null

        val type = when {
            mapping.amountColumn != null -> {
                val isNegative = rawAmount.startsWith("-") ||
                    rawAmount.contains("(") ||
                    rawAmount.contains("debit", ignoreCase = true)
                if (isNegative) TransactionType.EXPENSE else TransactionType.INCOME
            }
            mapping.debitColumn != null  -> TransactionType.EXPENSE
            mapping.creditColumn != null -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }

        // If both debit and credit columns exist, determine type from which has a value
        val finalType = if (mapping.debitColumn != null && mapping.creditColumn != null) {
            val debit = parseAmount(cells.getOrNull(mapping.debitColumn!!) ?: "")
            val credit = parseAmount(cells.getOrNull(mapping.creditColumn!!) ?: "")
            when {
                (debit ?: 0.0) > 0.0  -> TransactionType.EXPENSE
                (credit ?: 0.0) > 0.0 -> TransactionType.INCOME
                else -> type
            }
        } else type

        val dateStr = mapping.dateColumn?.let { cells.getOrNull(it) }
        val date = parseDate(dateStr)

        return ParsedTransaction(
            description = description,
            amount = amount,
            date = date,
            type = finalType,
            rawData = cells.joinToString(" | ")
        )
    }

    private fun isSummaryRow(text: String): Boolean {
        val lower = text.lowercase()
        return SUMMARY_KEYWORDS.any { lower.contains(it) }
    }

    // ── Amount parsing ────────────────────────────────────────────────────────

    private fun parseAmount(raw: String): Double? {
        if (raw.isBlank()) return null
        return try {
            // Handle accounting notation like (1,234.56) = negative
            val s = raw
                .replace("(", "-").replace(")", "")
                .replace(Regex("[^0-9.\\-]"), "")
                .trim()
            if (s.isEmpty() || s == "-") null
            else Math.abs(s.toDouble())
        } catch (_: Exception) { null }
    }

    // ── Date parsing ──────────────────────────────────────────────────────────

    private fun parseDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()

        // Already in yyyy-MM-dd (from Excel date cell handling)
        if (cleaned.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return cleaned

        val formats = listOf(
            "dd/MM/yyyy", "d/M/yyyy",       // Israeli / European
            "MM/dd/yyyy", "M/d/yyyy",       // US
            "dd.MM.yyyy", "d.M.yyyy",       // Israeli / Russian dot-separated
            "dd-MM-yyyy", "d-M-yyyy",
            "yyyy-MM-dd", "yyyy/MM/dd",
            "dd/MM/yy",   "d/M/yy",
            "dd.MM.yy",   "d.M.yy",         // Russian short year
            "MMM dd, yyyy", "dd MMM yyyy",
            "dd-MMM-yyyy",
            "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd.MM.yyyy HH:mm",             // Russian with time
            "dd.MM.yyyy HH:mm:ss"
        )

        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                sdf.isLenient = false
                val date = sdf.parse(cleaned) ?: continue
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            } catch (_: Exception) { }
        }
        return null
    }

    // ── Keywords ──────────────────────────────────────────────────────────────

    companion object {
        val PDF_DATE_RE = Regex("""\d{1,2}[./\-]\d{1,2}[./\-]\d{2,4}|\d{4}[./\-]\d{2}[./\-]\d{2}""")
        val PDF_NUM_RE  = Regex("""^[-+]?[\d,]+(?:\.\d{1,2})?$""")
        val PDF_TRAILING_AMOUNT_RE = Regex("""([-+]?\d[\d,]*(?:\.\d+)?)\s*(?:[₪${'$'}€£₽]|ILS|NIS)?\s*$""")

        // English + Hebrew + Russian column header keywords
        private val DATE_KEYWORDS = listOf(
            // English
            "date", "datum", "fecha",
            // Hebrew
            "תאריך",
            // Russian
            "дата", "дата операции", "дата проведения"
        )
        private val DESC_KEYWORDS = listOf(
            // English
            "description", "details", "memo", "narrative", "payee", "merchant", "reference",
            // Hebrew
            "תיאור", "פרטים", "שם בית עסק", "מוטב", "הערות", "אסמכתא",
            // Russian
            "описание", "назначение", "контрагент", "получатель", "отправитель",
            "детали", "наименование", "комментарий"
        )
        private val AMOUNT_KEYWORDS = listOf(
            // English
            "amount", "sum", "total",
            // Hebrew
            "סכום", "סה\"כ",
            // Russian
            "сумма", "итого", "сумма операции"
        )
        private val DEBIT_KEYWORDS = listOf(
            // English
            "debit", "withdrawal", "charge", "payment",
            // Hebrew
            "חיוב", "משיכה", "הוצאה",
            // Russian
            "дебет", "расход", "списание", "расходы"
        )
        private val CREDIT_KEYWORDS = listOf(
            // English
            "credit", "deposit", "income",
            // Hebrew
            "זיכוי", "הפקדה", "הכנסה",
            // Russian
            "кредит", "доход", "поступление", "приход", "зачисление"
        )
        private val BALANCE_KEYWORDS = listOf(
            "balance", "יתרה", "остаток", "баланс"
        )
        private val SUMMARY_KEYWORDS = listOf(
            // English
            "total", "subtotal", "grand total", "balance", "opening", "closing",
            // Hebrew
            "סה\"כ", "סיכום", "יתרה", "פתיחה", "סגירה",
            // Russian
            "итого", "баланс", "остаток", "исходящий", "входящий"
        )
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

data class ColumnMapping(
    var dateColumn: Int? = null,
    var descriptionColumn: Int? = null,
    var amountColumn: Int? = null,
    var debitColumn: Int? = null,
    var creditColumn: Int? = null,
    var balanceColumn: Int? = null
) {
    fun hasEnoughColumns() =
        descriptionColumn != null &&
        (amountColumn != null || debitColumn != null || creditColumn != null)
}

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
    object NeedsOcr : FileParseResult()
}
