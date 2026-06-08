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
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
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

            // Credit card PDFs need a dedicated row-based parser — never use the generic
            // bank-statement extractor for CC files, as it mixes numbers across columns.
            val isCc = isCreditCardPdf(sortedText) || isCreditCardPdf(naturalText)
            val sortedTx = if (sortedText.isNotBlank()) {
                if (isCc) extractCreditCardTransactionsFromText(sortedText)
                else extractTransactionsFromText(sortedText)
            } else emptyList()
            val naturalTx = if (naturalText.isNotBlank()) {
                if (isCc) extractCreditCardTransactionsFromText(naturalText)
                else extractTransactionsFromText(naturalText)
            } else emptyList()

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
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Find the header row: prefer one that has BOTH a debit AND credit keyword (e.g. חובה + זכות).
        // Fall back to any row with a date keyword + any amount keyword.
        val headerIdx = lines.indexOfFirst { line ->
            DEBIT_KEYWORDS.any { line.contains(it) } && CREDIT_KEYWORDS.any { line.contains(it) }
        }.takeIf { it >= 0 } ?: lines.indexOfFirst { line ->
            val l = line.lowercase()
            DATE_KEYWORDS.any { l.contains(it) } &&
            (AMOUNT_KEYWORDS + DEBIT_KEYWORDS + CREDIT_KEYWORDS).any { l.contains(it) }
        }

        val header   = if (headerIdx >= 0) lines[headerIdx] else null
        val layout   = header?.let { buildBankLayout(it) }
        val startIdx = if (headerIdx >= 0) headerIdx + 1 else 0

        return lines.drop(startIdx)
            .filter { !isSummaryRow(it) }
            .mapNotNull { parseBankStatementLine(it, layout) }
    }

    // Stores normalised (0.0–1.0) column positions derived from the header string.
    // Relative positions let us compare header offsets against token offsets in
    // data rows that may be a different character length.
    private data class BankLayout(
        val debitRel:     Double = -1.0,  // חובה / debit      (-1 = absent)
        val creditRel:    Double = -1.0,  // זכות / credit     (-1 = absent)
        val balanceRel:   Double = -1.0,  // יתרה / balance    (-1 = absent)
        val referenceRel: Double = -1.0   // אסמכתה / ref no   (-1 = absent)
    ) {
        val hasDebit    get() = debitRel    >= 0
        val hasCredit   get() = creditRel   >= 0
        val hasBalance  get() = balanceRel  >= 0
        val hasRef      get() = referenceRel >= 0
    }

    private fun buildBankLayout(header: String): BankLayout {
        val hLen = header.length.coerceAtLeast(1).toDouble()
        fun rel(keywords: List<String>): Double {
            val pos = keywords.mapNotNull { kw ->
                val idx = header.indexOf(kw)
                if (idx >= 0) idx else null
            }.minOrNull() ?: return -1.0
            return pos / hLen
        }
        return BankLayout(
            debitRel     = rel(DEBIT_KEYWORDS),
            creditRel    = rel(CREDIT_KEYWORDS),
            balanceRel   = rel(BALANCE_KEYWORDS),
            referenceRel = rel(REFERENCE_KEYWORDS)
        )
    }

    // Parses one data row from a bank-account PDF statement.
    // Only three columns matter: date (תאריך), description (סוג תנועה), and
    // debit/credit amount (חובה / זכות). Balance (יתרה) and reference (אסמכתה)
    // columns are identified by position and discarded so their numbers never
    // become the transaction amount.
    private fun parseBankStatementLine(line: String, layout: BankLayout?): ParsedTransaction? {
        val allDates = PDF_DATE_RE.findAll(line).toList()
        if (allDates.isEmpty()) return null

        val date          = parseDate(allDates.first().value)
        val allDateRanges = allDates.map { it.range }
        val lLen          = line.length.coerceAtLeast(1).toDouble()

        data class Tok(val v: Double, val rel: Double, val rawDigits: String)
        val toks = PDF_NUM_FINDER.findAll(line).mapNotNull { m ->
            if (allDateRanges.any { r -> m.range.first in r || m.range.last in r }) return@mapNotNull null
            val v = parseAmount(m.value) ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            Tok(v, m.range.first / lLen, m.value.replace(",", ""))
        }.toList()

        if (toks.isEmpty()) return null

        val amount: Double
        val type: TransactionType

        if (layout != null && (layout.hasDebit || layout.hasCredit)) {
            // Assign every numeric token to the nearest layout column by relative position.
            // Tokens that land on balance or reference columns are discarded — this handles
            // both explicit reference numbers (e.g. "789456") and large round amounts like
            // "100,000" without relying on a digit-length heuristic.
            data class Tagged(val tok: Tok, val col: String)
            val tagged = toks.map { t ->
                val dDist = if (layout.hasDebit)    kotlin.math.abs(t.rel - layout.debitRel)     else Double.MAX_VALUE
                val cDist = if (layout.hasCredit)   kotlin.math.abs(t.rel - layout.creditRel)    else Double.MAX_VALUE
                val bDist = if (layout.hasBalance)  kotlin.math.abs(t.rel - layout.balanceRel)   else Double.MAX_VALUE
                val rDist = if (layout.hasRef)      kotlin.math.abs(t.rel - layout.referenceRel) else Double.MAX_VALUE
                Tagged(t, when (minOf(dDist, cDist, bDist, rDist)) {
                    dDist -> "debit"
                    cDist -> "credit"
                    bDist -> "balance"
                    else  -> "ref"
                })
            }

            val debitTok  = tagged.filter { it.col == "debit"  }.maxByOrNull { it.tok.v }
            val creditTok = tagged.filter { it.col == "credit" }.maxByOrNull { it.tok.v }

            when {
                (debitTok?.tok?.v  ?: 0.0) > 0.01 && (creditTok?.tok?.v ?: 0.0) < 0.01 ->
                    { amount = debitTok!!.tok.v;  type = TransactionType.EXPENSE }
                (creditTok?.tok?.v ?: 0.0) > 0.01 && (debitTok?.tok?.v  ?: 0.0) < 0.01 ->
                    { amount = creditTok!!.tok.v; type = TransactionType.INCOME  }
                debitTok != null ->
                    { amount = debitTok.tok.v;  type = TransactionType.EXPENSE }
                creditTok != null ->
                    { amount = creditTok.tok.v; type = TransactionType.INCOME  }
                else -> return null
            }
        } else {
            // No column layout: fall back to digit-length heuristic to filter reference
            // numbers (typically 6+ digit integers), then drop the rightmost token
            // (running balance) and take the smallest remaining value.
            val filtered = toks.filterNot { t -> t.rawDigits.length >= 6 && !t.rawDigits.contains('.') }
            val txToks   = if (filtered.size >= 2) filtered.dropLast(1) else filtered
            amount = txToks.minByOrNull { it.v }?.v ?: return null
            type   = TransactionType.EXPENSE
        }

        if (amount == 0.0) return null

        val keep = BooleanArray(line.length) { true }
        allDateRanges.forEach { r -> r.forEach { if (it < line.length) keep[it] = false } }
        PDF_NUM_FINDER.findAll(line).forEach { m -> m.range.forEach { if (it < line.length) keep[it] = false } }

        val desc = line.filterIndexed { i, _ -> keep[i] }
            .replace(Regex("[₪\$€£₽]|ILS|NIS|USD|EUR|RUB"), "")
            .replace(Regex("[|/\\\\]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        if (desc.isBlank() || isSummaryRow(desc)) return null

        return ParsedTransaction(
            description = desc,
            amount      = amount,
            date        = date,
            type        = overrideTypeByDescription(desc, type),
            rawData     = line
        )
    }

    // ── Credit card PDF ───────────────────────────────────────────────────────

    // True if the extracted text contains a credit-card-specific column header.
    private fun isCreditCardPdf(text: String): Boolean =
        CC_SIGNATURE_KEYWORDS.any { sig -> text.contains(sig) }

    // Dedicated row-by-row parser for credit card PDF statements.
    // Every parsed transaction is an EXPENSE.  Only three columns are used:
    // transaction date, merchant name, and charge amount (סכום החיוב).
    private fun extractCreditCardTransactionsFromText(text: String): List<ParsedTransaction> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Locate the header line (first line that contains a CC-specific keyword).
        val headerIdx = lines.indexOfFirst { line ->
            CC_SIGNATURE_KEYWORDS.any { line.contains(it) }
        }.takeIf { it >= 0 } ?: return emptyList()

        val header = lines[headerIdx]
        val hLen   = header.length.coerceAtLeast(1).toDouble()

        // Character position of the charge column inside the header string.
        fun headerPos(keywords: List<String>): Int =
            keywords.mapNotNull { kw -> header.indexOf(kw).takeIf { it >= 0 } }.minOrNull() ?: -1

        val chargePos  = headerPos(CC_CHARGE_KEYWORDS)
        val chargeRel  = if (chargePos >= 0) chargePos / hLen else -1.0

        return lines.drop(headerIdx + 1)
            .filter { !isSummaryRow(it) }
            .mapNotNull { line -> parseCreditCardPdfLine(line, chargeRel) }
    }

    // Parses one row from a credit card PDF.  Picks the amount by proximity to the
    // charge column position recorded from the header; falls back to the last token.
    // All dates on the line are excluded from number candidates — this prevents the
    // billing-month digit (e.g. "05" in "05/06/2026") from being chosen as the amount.
    private fun parseCreditCardPdfLine(line: String, chargeColumnRel: Double): ParsedTransaction? {
        val allDates = PDF_DATE_RE.findAll(line).toList()
        if (allDates.isEmpty()) return null

        val date          = parseDate(allDates.first().value)
        val allDateRanges = allDates.map { it.range }
        val lLen          = line.length.coerceAtLeast(1).toDouble()

        data class Tok(val v: Double, val pos: Int)
        val toks = PDF_NUM_FINDER.findAll(line).mapNotNull { m ->
            if (allDateRanges.any { r -> m.range.first in r || m.range.last in r }) return@mapNotNull null
            val digits = m.value.replace(",", "")
            if (digits.length >= 6 && !digits.contains('.')) return@mapNotNull null
            val v = parseAmount(m.value) ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            Tok(v, m.range.first)
        }.toList()

        if (toks.isEmpty()) return null

        // Amount = token whose relative position is closest to the charge column.
        // When no column layout is available, use the last token (amounts tend to
        // appear at the end of the line in natural RTL reading order).
        val amount: Double = when {
            chargeColumnRel >= 0 && toks.size > 1 ->
                toks.minByOrNull { kotlin.math.abs(it.pos / lLen - chargeColumnRel) }?.v
                    ?: return null
            else -> toks.last().v
        }

        if (amount == 0.0) return null

        // Build description: remove all dates and all numeric tokens.
        val keep = BooleanArray(line.length) { true }
        allDateRanges.forEach { r -> r.forEach { if (it < line.length) keep[it] = false } }
        PDF_NUM_FINDER.findAll(line).forEach { m ->
            m.range.forEach { if (it < line.length) keep[it] = false }
        }

        val desc = line.filterIndexed { i, _ -> keep[i] }
            .replace(Regex("[₪\$€£₽]|ILS|NIS|USD|EUR"), "")
            .replace(Regex("[|/\\\\]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        if (desc.isBlank() || isSummaryRow(desc)) return null

        return ParsedTransaction(
            description = desc,
            amount      = amount,
            date        = date,
            type        = TransactionType.EXPENSE,
            rawData     = line
        )
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

                if (isCreditCardFormat(headers)) {
                    // Credit card statement: every row is an EXPENSE;
                    // use merchant + charge columns only, ignore everything else.
                    val ccMapping = detectCreditCardMapping(headers)
                    if (!ccMapping.hasEnoughColumns()) continue
                    for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        if (row.physicalNumberOfCells == 0) continue
                        try {
                            val cells = (0 until row.lastCellNum).map { i -> cellText(row.getCell(i)) }
                            val parsed = parseCreditCardRow(cells, ccMapping)
                            if (parsed != null) transactions.add(parsed)
                        } catch (_: Exception) { }
                    }
                } else {
                    // Bank account statement
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
                CREDIT_KEYWORDS.any { h.contains(it) } ||
                CC_MERCHANT_KEYWORDS.any { h.contains(it) }
            }
            if (score >= 2) return i
        }
        return 0
    }

    private fun parseExcelRow(row: org.apache.poi.ss.usermodel.Row, mapping: ColumnMapping): ParsedTransaction? {
        val cells = (0 until row.lastCellNum).map { i -> cellText(row.getCell(i)) }
        val parsed = parseRowData(cells, mapping) ?: return null
        // Color detection: red font = expense, green font = income.
        // For combined debit/credit columns the sign is the source of truth —
        // description overrides are intentionally skipped so a correctly-signed
        // value is never flipped to the wrong type.
        val colorType = detectTypeFromCellColors(row, mapping) ?: return parsed
        val type = if (mapping.isCombinedAmountColumn) colorType
                   else overrideTypeByDescription(parsed.description, colorType)
        return parsed.copy(type = type)
    }

    // Returns the transaction type implied by the font color of the amount cell,
    // or null if the color is not a recognisable red or green.
    private fun detectTypeFromCellColors(
        row: org.apache.poi.ss.usermodel.Row,
        mapping: ColumnMapping
    ): TransactionType? {
        // For separate debit/credit columns, check whichever cell has a value.
        if (mapping.debitColumn != null && mapping.creditColumn != null) {
            val debitCell  = row.getCell(mapping.debitColumn!!)
            val creditCell = row.getCell(mapping.creditColumn!!)
            val debitText  = cellText(debitCell)
            val creditText = cellText(creditCell)
            return when {
                debitText.isNotBlank()  -> cellFontColorType(debitCell)
                creditText.isNotBlank() -> cellFontColorType(creditCell)
                else -> null
            }
        }
        // Single amount or one-sided column.
        val colIdx = mapping.amountColumn ?: mapping.debitColumn ?: mapping.creditColumn ?: return null
        val cell = row.getCell(colIdx)
        val fromColor = cellFontColorType(cell)
        if (fromColor != null) return fromColor
        // For combined debit/credit columns: if color detection failed, use numeric sign
        // (negative value = expense, positive = income). Doesn't apply to single-sided columns
        // because there sign has no meaning — debit-only is always expense, credit-only is income.
        if (mapping.amountColumn != null && mapping.debitColumn == null && mapping.creditColumn == null) {
            val v = try { (cell as? XSSFCell)?.numericCellValue } catch (_: Exception) { null }
            if (v != null && v != 0.0) return if (v < 0) TransactionType.EXPENSE else TransactionType.INCOME
        }
        return null
    }

    // Reads the XLSX font color (then fill color) of a cell and maps it to
    // EXPENSE (red) or INCOME (green). Tries direct RGB first, then ARGB hex
    // (needed for some theme-based colors), then cell fill foreground.
    // Returns null for HSSFCell (XLS), automatic/black text, or unrecognised colors.
    private fun cellFontColorType(cell: Cell?): TransactionType? {
        val xCell  = cell as? XSSFCell ?: return null
        val xStyle = xCell.cellStyle as? XSSFCellStyle ?: return null

        // 1. Font color — direct RGB (3 bytes), then ARGB (4 bytes: alpha,R,G,B)
        val xFont = xStyle.font as? XSSFFont
        val fontColor = xFont?.xssfColor
        if (fontColor != null) {
            val rgb = fontColor.rgb ?: fontColor.argb?.let { if (it.size >= 4) it.copyOfRange(1, 4) else null }
            if (rgb != null) {
                val r = rgb[0].toInt() and 0xFF
                val g = rgb[1].toInt() and 0xFF
                val b = rgb[2].toInt() and 0xFF
                if (isRedColor(r, g, b))   return TransactionType.EXPENSE
                if (isGreenColor(r, g, b)) return TransactionType.INCOME
            }
        }

        // 2. Cell fill foreground color — some formats color the cell background instead of font
        val fillColor = xStyle.fillForegroundXSSFColor
        if (fillColor != null) {
            val rgb = fillColor.rgb ?: fillColor.argb?.let { if (it.size >= 4) it.copyOfRange(1, 4) else null }
            if (rgb != null) {
                val r = rgb[0].toInt() and 0xFF
                val g = rgb[1].toInt() and 0xFF
                val b = rgb[2].toInt() and 0xFF
                if (isRedColor(r, g, b))   return TransactionType.EXPENSE
                if (isGreenColor(r, g, b)) return TransactionType.INCOME
            }
        }

        return null
    }

    // Red: red channel dominates (covers pure red, dark red, crimson, etc.)
    private fun isRedColor(r: Int, g: Int, b: Int) = r > 150 && r > g * 2 && r > b * 2

    // Green: green channel dominates (covers pure green, dark green, lime, etc.)
    private fun isGreenColor(r: Int, g: Int, b: Int) = g > 80 && g > r * 1.5 && g > b * 1.5

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
            val transactions = mutableListOf<ParsedTransaction>()

            if (isCreditCardFormat(headerRow)) {
                val ccMapping = detectCreditCardMapping(headerRow)
                if (!ccMapping.hasEnoughColumns())
                    return FileParseResult.Error("Could not identify transaction columns in this file")
                for (i in 1 until allRows.size) {
                    try {
                        val parsed = parseCreditCardRow(allRows[i].toList(), ccMapping)
                        if (parsed != null) transactions.add(parsed)
                    } catch (_: Exception) { }
                }
            } else {
                val mapping = detectColumnMapping(headerRow)
                if (!mapping.hasEnoughColumns())
                    return FileParseResult.Error("Could not identify transaction columns in this file")
                for (i in 1 until allRows.size) {
                    try {
                        val parsed = parseRowData(allRows[i].toList(), mapping)
                        if (parsed != null) transactions.add(parsed)
                    } catch (_: Exception) { }
                }
            }

            if (transactions.isEmpty())
                FileParseResult.Error("No valid transactions found in file")
            else
                FileParseResult.Success(transactions)

        } catch (e: Exception) {
            FileParseResult.Error("Failed to parse CSV: ${e.message}")
        }
    }

    // ── Credit card format detection and parsing ─────────────────────────────

    // Returns true if the header row contains at least one credit-card-specific
    // column name that does not appear in bank account statements.
    private fun isCreditCardFormat(headers: List<String>): Boolean {
        val lower = headers.map { it.trim().lowercase() }
        return CC_SIGNATURE_KEYWORDS.any { sig -> lower.any { it.contains(sig.lowercase()) } }
    }

    // Maps the three columns we care about in a CC statement.
    // Keywords are in priority order: the first keyword that matches a header wins,
    // ensuring "סכום החיוב" is chosen over generic "סכום" when both are present.
    private fun detectCreditCardMapping(headers: List<String>): CreditCardMapping {
        fun bestCol(keywords: List<String>): Int? {
            for (kw in keywords) {
                val idx = headers.indexOfFirst { it.trim().lowercase().contains(kw.lowercase()) }
                if (idx >= 0) return idx
            }
            return null
        }
        return CreditCardMapping(
            dateColumn     = bestCol(CC_DATE_KEYWORDS),
            merchantColumn = bestCol(CC_MERCHANT_KEYWORDS),
            chargeColumn   = bestCol(CC_CHARGE_KEYWORDS)
        )
    }

    // Parses a single credit card statement row.  Every row is an EXPENSE —
    // no income/credit logic applies.  Only merchant name + charge amount are used.
    private fun parseCreditCardRow(cells: List<String>, mapping: CreditCardMapping): ParsedTransaction? {
        val merchant = mapping.merchantColumn
            ?.let { cells.getOrNull(it)?.trim() }
            ?.takeIf { it.isNotBlank() && !isSummaryRow(it) }
            ?: return null
        val amtStr = mapping.chargeColumn?.let { cells.getOrNull(it)?.trim() } ?: return null
        val amount = parseAmount(amtStr) ?: return null
        if (amount == 0.0) return null
        val date = parseDate(mapping.dateColumn?.let { cells.getOrNull(it) })
        return ParsedTransaction(
            description = merchant,
            amount      = amount,
            date        = date,
            type        = TransactionType.EXPENSE,
            rawData     = cells.joinToString(" | ")
        )
    }

    // ── Column detection ──────────────────────────────────────────────────────

    private fun detectColumnMapping(headers: List<String>): ColumnMapping {
        val mapping = ColumnMapping()
        headers.forEachIndexed { index, raw ->
            val h = raw.trim().lowercase()
            when {
                DATE_KEYWORDS.any { h.contains(it) }    -> if (mapping.dateColumn == null) mapping.dateColumn = index
                DESC_KEYWORDS.any { h.contains(it) }    -> if (mapping.descriptionColumn == null) mapping.descriptionColumn = index
                // Balance must be checked before debit/credit — "credit balance" / "debit balance"
                // are balance columns, not transaction columns.
                BALANCE_KEYWORDS.any { h.contains(it) } -> if (mapping.balanceColumn == null) mapping.balanceColumn = index
                // Combined debit+credit header (e.g. "זכות/חובה") — treat as a single
                // signed amount column; type is determined by sign (negative = expense,
                // positive = income) and optionally confirmed by font/fill color.
                // Description-based overrides are NOT applied for this column type.
                DEBIT_KEYWORDS.any { h.contains(it) } && CREDIT_KEYWORDS.any { h.contains(it) } -> {
                    if (mapping.amountColumn == null) {
                        mapping.amountColumn = index
                        mapping.isCombinedAmountColumn = true
                    }
                }
                DEBIT_KEYWORDS.any { h.contains(it) }   -> if (mapping.debitColumn == null) mapping.debitColumn = index
                CREDIT_KEYWORDS.any { h.contains(it) }  -> if (mapping.creditColumn == null) mapping.creditColumn = index
                AMOUNT_KEYWORDS.any { h.contains(it) }  -> if (mapping.amountColumn == null) mapping.amountColumn = index
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

        // Evaluate every available source up front, then pick with a single priority order:
        //   explicit debit value > explicit credit value > signed/unsigned amount column.
        // This correctly handles all formats:
        //   • separate debit + credit columns (most Israeli/EU bank exports)
        //   • single signed/unsigned amount column
        //   • only-debit or only-credit column
        //   • amount + credit (no debit) — income rows have creditVal > 0; expense rows fall
        //     through to the amountVal branch where sign or single-column context decides
        val debitVal  = mapping.debitColumn?.let  { parseAmount(cells.getOrNull(it) ?: "") }
        val creditVal = mapping.creditColumn?.let { parseAmount(cells.getOrNull(it) ?: "") }
        val amtStr    = mapping.amountColumn?.let { cells.getOrNull(it)?.trim() }
        val amtVal    = amtStr?.let { parseAmount(it) }

        val amount: Double
        val type: TransactionType

        when {
            (debitVal ?: 0.0) > 0.0 -> {
                amount = debitVal!!
                type   = TransactionType.EXPENSE
            }
            (creditVal ?: 0.0) > 0.0 -> {
                amount = creditVal!!
                type   = TransactionType.INCOME
            }
            (amtVal ?: 0.0) > 0.0 -> {
                amount = amtVal!!
                val isNegative = amtStr!!.let { s ->
                    val t = s.trim()
                    // Leading minus, trailing minus (e.g. "1500-" used by some Israeli banks),
                    // or accounting parenthesis notation (1,500.00)
                    t.startsWith("-") || (t.endsWith("-") && t.length > 1) || t.contains("(") ||
                    t.contains("debit", ignoreCase = true)
                }
                // When only a credit column is mapped (no debit, no amount), any
                // positive value is income. When only a debit column is mapped,
                // it's an expense. When an explicit amount column is present, use sign.
                type = when {
                    mapping.amountColumn != null -> if (isNegative) TransactionType.EXPENSE else TransactionType.INCOME
                    mapping.debitColumn  != null -> TransactionType.EXPENSE
                    mapping.creditColumn != null -> TransactionType.INCOME
                    else -> TransactionType.EXPENSE
                }
            }
            else -> return null
        }

        if (amount == 0.0) return null

        val dateStr = mapping.dateColumn?.let { cells.getOrNull(it) }
        val date = parseDate(dateStr)

        return ParsedTransaction(
            description = description,
            amount = amount,
            date = date,
            // For combined debit/credit columns the numeric sign is authoritative —
            // skip description overrides so a correctly-signed transaction is never
            // reclassified. Description overrides remain active for separate
            // debit/credit column formats where credit card charges appear in the
            // "credit" column and would otherwise be classified as income.
            type = if (mapping.isCombinedAmountColumn) type
                   else overrideTypeByDescription(description, type),
            rawData = cells.joinToString(" | ")
        )
    }

    // Applies hard-coded description overrides that take priority over column-based detection.
    private fun overrideTypeByDescription(description: String, detected: TransactionType): TransactionType {
        val d = description.trim()
        if (ALWAYS_EXPENSE_DESC_PATTERNS.any { d.contains(it, ignoreCase = true) }) return TransactionType.EXPENSE
        if (ALWAYS_INCOME_DESC_PATTERNS.any  { d.contains(it, ignoreCase = true) }) return TransactionType.INCOME
        return detected
    }

    private fun isSummaryRow(text: String): Boolean {
        val lower = text.lowercase()
        return SUMMARY_KEYWORDS.any { lower.contains(it) }
    }

    // ── Amount parsing ────────────────────────────────────────────────────────

    private fun parseAmount(raw: String): Double? {
        if (raw.isBlank()) return null
        return try {
            var s = raw.trim()
            // Trailing minus sign — common in Hebrew/Israeli financial exports:
            // "1,500.00-" means -1,500.00.  Must be checked before stripping non-numeric
            // chars so the sign information isn't lost.
            if (s.length > 1 && s.endsWith("-") && s[0].isDigit()) s = "-${s.dropLast(1)}"
            // Accounting notation (1,234.56) = negative
            s = s.replace("(", "-").replace(")", "")
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
        // Finds individual numeric tokens, including comma-thousands and decimals
        val PDF_NUM_FINDER = Regex("""[\d,]+(?:\.\d{1,2})?""")

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
            "description", "details", "memo", "narrative", "payee", "merchant",
            "transaction", "particulars",
            // Hebrew — סוג תנועה is the primary transaction-type column in Israeli bank statements
            "סוג תנועה", "תנועה", "תיאור", "פרטים", "שם בית עסק", "מוטב", "הערות",
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
            "debit", "withdrawal", "charge", "payment", "expense", "expenses",
            "outflow", "outgoing", "debits",
            // Hebrew — חובה is the primary debit column in Israeli bank statements
            "חובה", "חיוב", "משיכה", "הוצאה",
            // Russian
            "дебет", "расход", "списание", "расходы"
        )
        private val CREDIT_KEYWORDS = listOf(
            // English
            "credit", "deposit", "income", "inflow", "incoming", "receipts", "credits",
            // Hebrew — זכות is the primary credit column in Israeli bank statements
            "זכות", "זיכוי", "הפקדה", "הכנסה",
            // Russian
            "кредит", "доход", "поступление", "приход", "зачисление"
        )
        private val BALANCE_KEYWORDS = listOf(
            "balance", "יתרה", "остаток", "баланс"
        )
        private val REFERENCE_KEYWORDS = listOf(
            // Hebrew — primary reference/document column in Israeli bank statements
            "אסמכתה", "מסמך",
            // English
            "reference", "ref no", "ref.", "cheque no", "check no", "voucher"
        )
        // Description-based type overrides — applied after column detection.
        // Use these for transaction labels that are unambiguous regardless of which column
        // they were found in. Credit card charges from Israeli bank statements often appear
        // in the credit column (the bank "credits" the card company), so they must be
        // overridden to EXPENSE here.
        private val ALWAYS_EXPENSE_DESC_PATTERNS = listOf(
            // Internet bank transfers — always outgoing
            "העברה באינטרנט",
            // Goldmaster store
            "גולדמסטר",
            // ── Credit / charge cards ─────────────────────────────────────────
            // Monthly credit-card billing entries in Israeli bank statements are
            // deducted from the checking account (= expense) but often appear in
            // a "credit" column, so they need an explicit override.
            "mastercard",           // Gold Mastercard, Mastercard Gold, etc.
            "מסטרקרד",              // Hebrew transliteration — short variant (גולד מסטרקרד)
            "מסטרקארד",             // Hebrew transliteration — variant with aleph before ד
            "מאסטרקארד",            // Hebrew transliteration — variant with aleph after מ
            "גולד מסטרקרד",         // "Gold Mastercard" — short variant
            "גולד מסטרקארד",        // "Gold Mastercard" — variant 1
            "גולד מאסטרקארד",       // "Gold Mastercard" — variant 2 (bank statement spelling)
            "visa",                 // Visa card charge
            "ויזה",                 // Hebrew for Visa
            "diners",               // Diners Club
            "דיינרס",               // Hebrew for Diners
            "american express",     // Amex
            "אמריקן אקספרס",        // Hebrew for American Express
            "amex",
            // Generic credit-card charge labels used by Israeli banks
            "חיוב כרטיס",           // "card charge"
            "כרטיס אשראי",          // "credit card"
            "חיוב אשראי"            // "credit charge"
        )
        private val ALWAYS_INCOME_DESC_PATTERNS = listOf<String>()

        // ── Credit card statement column keywords ────────────────────────────────
        // A file is identified as a credit card statement when any header contains
        // one of these signature terms (none appear in bank account statements).
        private val CC_SIGNATURE_KEYWORDS = listOf(
            "בית העסק", "שם בית עסק",          // merchant column
            "תאריך העסקה", "תאריך פעולה", "תאריך רכישה",  // CC-specific date
            "סכום החיוב", "סכום לחיוב"          // CC-specific charge amount
        )
        // Transaction date column — CC-specific names first, generic "תאריך" as fallback
        private val CC_DATE_KEYWORDS = listOf(
            "תאריך העסקה", "תאריך פעולה", "תאריך רכישה",
            "transaction date", "purchase date",
            "תאריך"
        )
        // Merchant / store name column — unique to credit card exports
        private val CC_MERCHANT_KEYWORDS = listOf(
            "שם בית עסק", "בית העסק", "בית עסק",
            "merchant name", "merchant", "retailer", "vendor", "store name"
        )
        // Charge amount column — ordered most-specific first so "סכום החיוב" (charge
        // in ILS) wins over "סכום עסקה" (original-currency transaction amount).
        private val CC_CHARGE_KEYWORDS = listOf(
            "סכום החיוב", "סכום לחיוב", "סכום חיוב",
            "חיוב בש\"ח", "סכום בש\"ח",
            "charge amount", "amount charged", "billing amount",
            "סכום עסקה",
            "סכום", "amount"
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
    var balanceColumn: Int? = null,
    // True when amountColumn was detected from a combined debit+credit header
    // (e.g. "זכות/חובה"). In this case the numeric sign is the source of truth
    // for expense/income classification; description-based overrides are skipped.
    var isCombinedAmountColumn: Boolean = false
) {
    fun hasEnoughColumns() =
        descriptionColumn != null &&
        (amountColumn != null || debitColumn != null || creditColumn != null)
}

// Minimal mapping for credit card statements: date + merchant + charge amount.
// Everything else in the file is ignored.
data class CreditCardMapping(
    val dateColumn: Int? = null,
    val merchantColumn: Int? = null,
    val chargeColumn: Int? = null
) {
    fun hasEnoughColumns() = merchantColumn != null && chargeColumn != null
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
