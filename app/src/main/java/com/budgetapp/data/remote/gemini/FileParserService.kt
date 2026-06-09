package com.budgetapp.data.remote.gemini

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.budgetapp.core.util.AppLogger
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

            AppLogger.d(IMPORT_TAG, "Sorted extraction: ${sortedTx.size} rows | Natural extraction: ${naturalTx.size} rows")
            val transactions = if (sortedTx.size >= naturalTx.size) sortedTx else naturalTx
            AppLogger.d(IMPORT_TAG, "Using ${if (sortedTx.size >= naturalTx.size) "sorted" else "natural"}: ${transactions.size} transactions total")

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

        val header    = if (headerIdx >= 0) lines[headerIdx] else null
        val layout    = header?.let { buildBankLayout(it) }
        val startIdx  = if (headerIdx >= 0) headerIdx + 1 else 0
        val dataLines = lines.drop(startIdx)

        val result     = mutableListOf<ParsedTransaction>()
        val discardLog = mutableListOf<Pair<String, String>>()

        for (line in dataLines) {
            val tx = parseBankStatementLine(line, layout, discardLog)
            if (tx != null) result.add(tx)
        }

        AppLogger.d(IMPORT_TAG, "PDF extraction: ${lines.size} total lines, header at $headerIdx, ${dataLines.size} data lines → ${result.size} kept, ${discardLog.size} discarded")
        discardLog.forEachIndexed { i, (ln, reason) ->
            AppLogger.d(IMPORT_TAG, "  discard[$i] reason=$reason | ${ln.take(120)}")
        }

        return result
    }

    // Stores column positions in two forms:
    //   rel  — position as a fraction of the header length (0.0–1.0).  Used for the
    //           main nearest-column assignment, where normalised fractions keep the
    //           debit column's amounts mapping to "debit" even when data rows are
    //           slightly shorter than the header (shorter descriptions).
    //   abs  — raw character index in the header string.  Used ONLY in the fallback
    //           branch, where relative normalisation has already proven incorrect
    //           for that specific row (the token was pushed past a column boundary).
    //
    // Why two forms?  Numbers in data rows are right-aligned within their column cells
    // while keywords are left-aligned, so a debit amount starts at a position BETWEEN
    // the credit and debit keyword positions.  With absolute positions the amount
    // looks "closer to credit", causing every expense to be classified as income (the
    // regression introduced in v3.1.6).  Relative positions work correctly for normal
    // rows because dividing by the (shorter) data line length inflates token positions
    // rightward, past the credit/debit midpoint toward the correct debit header.
    private data class BankLayout(
        val debitRel:    Double = -1.0,  // חובה / debit  relative pos (-1 = absent)
        val creditRel:   Double = -1.0,  // זכות / credit relative pos
        val balanceRel:  Double = -1.0,  // יתרה / balance relative pos
        val referenceRel: Double = -1.0, // אסמכתה / ref  relative pos
        // Absolute positions — for the fallback path only
        val debitAbsPos:     Int = -1,
        val creditAbsPos:    Int = -1,
        val balanceAbsPos:   Int = -1,
        val referenceAbsPos: Int = -1
    ) {
        val hasDebit    get() = debitRel    >= 0
        val hasCredit   get() = creditRel   >= 0
        val hasBalance  get() = balanceRel  >= 0
        val hasRef      get() = referenceRel >= 0
    }

    private fun buildBankLayout(header: String): BankLayout {
        val hLen = header.length.coerceAtLeast(1).toDouble()
        fun absPos(keywords: List<String>): Int =
            keywords.mapNotNull { kw ->
                val idx = header.indexOf(kw)
                if (idx >= 0) idx else null
            }.minOrNull() ?: -1
        fun relPos(keywords: List<String>): Double {
            val abs = absPos(keywords)
            return if (abs >= 0) abs / hLen else -1.0
        }
        val layout = BankLayout(
            debitRel     = relPos(DEBIT_KEYWORDS),     debitAbsPos     = absPos(DEBIT_KEYWORDS),
            creditRel    = relPos(CREDIT_KEYWORDS),    creditAbsPos    = absPos(CREDIT_KEYWORDS),
            balanceRel   = relPos(BALANCE_KEYWORDS),   balanceAbsPos   = absPos(BALANCE_KEYWORDS),
            referenceRel = relPos(REFERENCE_KEYWORDS), referenceAbsPos = absPos(REFERENCE_KEYWORDS)
        )
        AppLogger.d(IMPORT_TAG,
            "[BankLayout] headerLen=${hLen.toInt()} " +
            "debit=${layout.debitAbsPos}(rel=${"%.3f".format(layout.debitRel)}) " +
            "credit=${layout.creditAbsPos}(rel=${"%.3f".format(layout.creditRel)}) " +
            "balance=${layout.balanceAbsPos}(rel=${"%.3f".format(layout.balanceRel)}) " +
            "ref=${layout.referenceAbsPos}(rel=${"%.3f".format(layout.referenceRel)}) " +
            "| header='${header.take(120)}'")
        return layout
    }

    // Parses one data row from a bank-account PDF statement.
    // Only three columns matter: date (תאריך), description (סוג תנועה), and
    // debit/credit amount (חובה / זכות). Balance (יתרה) and reference (אסמכתה)
    // columns are identified by position and discarded so their numbers never
    // become the transaction amount.
    //
    // Date is OPTIONAL — rows without a recognisable date are kept with date=null
    // so they still appear in the Review screen (shown under "Unknown date").
    // Nothing is silently dropped; every discard is recorded in discardLog.
    private fun parseBankStatementLine(
        line: String,
        layout: BankLayout?,
        discardLog: MutableList<Pair<String, String>> = mutableListOf()
    ): ParsedTransaction? {
        // Summary rows (totals, page headers, etc.) are the only hard filter before Review.
        if (isSummaryRow(line)) {
            val kw = SUMMARY_KEYWORDS.firstOrNull { line.lowercase().contains(it) } ?: "?"
            discardLog.add(line to "summary keyword '$kw'")
            return null
        }

        // Date is optional — null means "show as Unknown date" in the Review screen.
        val allDates      = PDF_DATE_RE.findAll(line).toList()
        val date          = allDates.firstOrNull()?.let { parseDate(it.value) }
        val allDateRanges = allDates.map { it.range }

        // Each token carries both forms so they are available for the matching path
        // that needs them: rel for the main nearest-column check, absPos for the fallback.
        val lLen = line.length.coerceAtLeast(1).toDouble()
        data class Tok(val v: Double, val rel: Double, val absPos: Int, val rawDigits: String)

        AppLogger.d(IMPORT_TAG, "[ParseRow] lineLen=${line.length} | '${line.take(120)}'")

        // Collect every non-date numeric token.
        val allNumToks = PDF_NUM_FINDER.findAll(line).mapNotNull { m ->
            if (allDateRanges.any { r -> m.range.first in r || m.range.last in r }) return@mapNotNull null
            val v = parseAmount(m.value) ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            Tok(v, m.range.first / lLen, m.range.first, m.value.replace(",", ""))
        }.toList()

        // Israeli bank amounts always use decimal notation (e.g. "732.00").
        // Reference numbers (אסמכתה) are always plain integers (e.g. "99012").
        // When any decimal token is present, exclude all integer tokens so they
        // can never be mistaken for an amount — regardless of column proximity.
        // Only fall back to integers when the row contains no decimal tokens at all
        // (some non-standard bank exports omit the ".00" suffix on round amounts).
        val decimalToks = allNumToks.filter {  it.rawDigits.contains('.') }
        val integerToks = allNumToks.filter { !it.rawDigits.contains('.') }
        val toks = decimalToks.ifEmpty { allNumToks }

        if (decimalToks.isNotEmpty() && integerToks.isNotEmpty()) {
            integerToks.forEach { t ->
                AppLogger.d(IMPORT_TAG, "  skipped integer '${t.rawDigits}' — cannot be amount when decimal tokens are present (likely אסמכתה/balance)")
            }
        }

        if (toks.isEmpty()) {
            discardLog.add(line to "no numeric amount found")
            return null
        }

        val amount: Double
        val type: TransactionType

        if (layout != null && (layout.hasDebit || layout.hasCredit)) {
            // Main column assignment uses RELATIVE (normalised) positions.
            // Numbers in data rows are right-aligned within their column cells; Hebrew
            // keywords are left-aligned.  A 7-char debit amount therefore starts between
            // the credit and debit keyword positions in absolute terms — absolute distances
            // make it appear "closer to credit" for every expense, causing the v3.1.6
            // regression where all expenses became income.  Relative normalisation avoids
            // this: a shorter-than-header data line divides by a smaller denominator,
            // inflating each token's position rightward and past the credit/debit midpoint
            // toward the correct debit header position.  (The one failure case — aiylon:
            // a very short line that inflates the credit token all the way into the balance
            // zone — is caught by the fallback below, which re-evaluates using ABSOLUTE
            // positions where normalisation has already proved wrong.)
            data class Tagged(val tok: Tok, val col: String)
            val tagged = toks.map { t ->
                val dDist = if (layout.hasDebit)    kotlin.math.abs(t.rel - layout.debitRel)    else Double.MAX_VALUE
                val cDist = if (layout.hasCredit)   kotlin.math.abs(t.rel - layout.creditRel)   else Double.MAX_VALUE
                val bDist = if (layout.hasBalance)  kotlin.math.abs(t.rel - layout.balanceRel)  else Double.MAX_VALUE
                val rDist = if (layout.hasRef)      kotlin.math.abs(t.rel - layout.referenceRel) else Double.MAX_VALUE
                val col   = when (minOf(dDist, cDist, bDist, rDist)) {
                    dDist -> "debit"
                    cDist -> "credit"
                    bDist -> "balance"
                    else  -> "ref"
                }
                AppLogger.d(IMPORT_TAG,
                    "  [TokenCol] val=${t.rawDigits} absPos=${t.absPos} rel=${"%.3f".format(t.rel)} → $col " +
                    "(dRel=${"%.3f".format(dDist)} cRel=${"%.3f".format(cDist)} " +
                    "bRel=${"%.3f".format(bDist)} rRel=${"%.3f".format(rDist)}) " +
                    "[abs dDist=${kotlin.math.abs(t.absPos - layout.debitAbsPos).takeIf { layout.hasDebit } ?: "—"} " +
                    "cDist=${kotlin.math.abs(t.absPos - layout.creditAbsPos).takeIf { layout.hasCredit } ?: "—"}]")
                Tagged(t, col)
            }

            val debitTok  = tagged.filter { it.col == "debit"  }.maxByOrNull { it.tok.v }
            val creditTok = tagged.filter { it.col == "credit" }.maxByOrNull { it.tok.v }

            when {
                (debitTok?.tok?.v  ?: 0.0) > 0.01 && (creditTok?.tok?.v ?: 0.0) < 0.01 -> {
                    amount = debitTok!!.tok.v;  type = TransactionType.EXPENSE
                    AppLogger.d(IMPORT_TAG, "  [Verdict] EXPENSE via debit col (rel) amt=${amount}")
                }
                (creditTok?.tok?.v ?: 0.0) > 0.01 && (debitTok?.tok?.v  ?: 0.0) < 0.01 -> {
                    amount = creditTok!!.tok.v; type = TransactionType.INCOME
                    AppLogger.d(IMPORT_TAG, "  [Verdict] INCOME via credit col (rel) amt=${amount}")
                }
                debitTok != null && creditTok != null -> {
                    AppLogger.d(IMPORT_TAG, "  [BothCols] debit=${debitTok.tok.v} credit=${creditTok.tok.v} → taking larger")
                    if (creditTok.tok.v >= debitTok.tok.v) {
                        amount = creditTok.tok.v; type = TransactionType.INCOME
                        AppLogger.d(IMPORT_TAG, "  [Verdict] INCOME via larger-of-both credit=${amount}")
                    } else {
                        amount = debitTok.tok.v;  type = TransactionType.EXPENSE
                        AppLogger.d(IMPORT_TAG, "  [Verdict] EXPENSE via larger-of-both debit=${amount}")
                    }
                }
                debitTok != null -> {
                    amount = debitTok.tok.v;  type = TransactionType.EXPENSE
                    AppLogger.d(IMPORT_TAG, "  [Verdict] EXPENSE via debit-only (credit=null) amt=${amount}")
                }
                creditTok != null -> {
                    amount = creditTok.tok.v; type = TransactionType.INCOME
                    AppLogger.d(IMPORT_TAG, "  [Verdict] INCOME via credit-only (debit=null) amt=${amount}")
                }
                else -> {
                    // All tokens mapped to balance/reference by relative positions — this is
                    // the aiylon case: very short line caused relative positions to inflate
                    // the credit token past the balance boundary.  Re-evaluate using ABSOLUTE
                    // positions where normalisation is no longer distorting the comparison.
                    val txToks = if (toks.size >= 2) toks.dropLast(1) else toks
                    val fallback = txToks.minByOrNull { it.v }
                    if (fallback == null) {
                        discardLog.add(line to "all amounts mapped to balance/reference columns; no fallback token available")
                        return null
                    }
                    val dDist2 = if (layout.hasDebit)  kotlin.math.abs(fallback.absPos - layout.debitAbsPos).toDouble()  else Double.MAX_VALUE
                    val cDist2 = if (layout.hasCredit) kotlin.math.abs(fallback.absPos - layout.creditAbsPos).toDouble() else Double.MAX_VALUE
                    val reType = if (cDist2 < dDist2) TransactionType.INCOME else TransactionType.EXPENSE
                    AppLogger.d(IMPORT_TAG,
                        "  [Fallback] all→balance/ref (short line inflated rel pos); " +
                        "re-eval by ABS: absPos=${fallback.absPos} " +
                        "dDist2=${dDist2.toInt()} cDist2=${cDist2.toInt()} → $reType " +
                        "[lineLen=${line.length} debitAbs=${layout.debitAbsPos} creditAbs=${layout.creditAbsPos}]")
                    amount = fallback.v
                    type   = reType
                }
            }
        } else {
            // No column layout. Drop the rightmost token (running balance) and take the
            // smallest remaining decimal value.
            AppLogger.d(IMPORT_TAG,
                "  [NoLayout] layout=${if (layout == null) "null" else "hasDebit=${layout.hasDebit} hasCredit=${layout.hasCredit}"} " +
                "lineLen=${line.length} — type defaults to EXPENSE")
            val txToks = if (toks.size >= 2) toks.dropLast(1) else toks
            val fallback = txToks.minByOrNull { it.v }
            if (fallback == null) {
                discardLog.add(line to "no non-reference amount found (no-layout fallback)")
                return null
            }
            amount = fallback.v
            type   = TransactionType.EXPENSE
        }

        // Validation: if the selected amount equals any skipped integer token value,
        // log a warning — this indicates a possible reference-number / balance misidentification.
        if (integerToks.any { it.v == amount }) {
            AppLogger.d(IMPORT_TAG, "  WARNING: selected amount $amount equals a skipped integer token — possible reference-number misidentification. Row: ${line.take(120)}")
        }

        if (amount == 0.0) {
            discardLog.add(line to "amount is zero")
            return null
        }

        val keep = BooleanArray(line.length) { true }
        allDateRanges.forEach { r -> r.forEach { if (it < line.length) keep[it] = false } }
        PDF_NUM_FINDER.findAll(line).forEach { m -> m.range.forEach { if (it < line.length) keep[it] = false } }

        val desc = line.filterIndexed { i, _ -> keep[i] }
            .replace(Regex("[₪\$€£₽]|ILS|NIS|USD|EUR|RUB"), "")
            .replace(Regex("[|/\\\\]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        if (desc.isBlank()) {
            discardLog.add(line to "description blank after removing dates and numbers")
            return null
        }
        if (isSummaryRow(desc)) {
            val kw = SUMMARY_KEYWORDS.firstOrNull { desc.lowercase().contains(it) } ?: "?"
            discardLog.add(line to "description matches summary keyword '$kw'")
            return null
        }

        AppLogger.d(IMPORT_TAG, "[Pipeline:1-PDF] desc='$desc' | type=$type | amount=$amount | date=$date")
        return ParsedTransaction(
            description = desc,
            amount      = amount,
            date        = date,
            type        = type,
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

        // Absolute character position of the charge column inside the header string.
        // Using absolute positions (not normalised fractions) mirrors the bank-statement
        // parser fix: CC rows are often shorter than the header (shorter merchant names),
        // so normalising by line length would shift token positions rightward.
        fun headerPos(keywords: List<String>): Int =
            keywords.mapNotNull { kw -> header.indexOf(kw).takeIf { it >= 0 } }.minOrNull() ?: -1

        val chargeAbsPos = headerPos(CC_CHARGE_KEYWORDS)
        AppLogger.d(IMPORT_TAG, "[CC-Layout] chargeAbsPos=$chargeAbsPos | header='${header.take(120)}'")

        return lines.drop(headerIdx + 1)
            .filter { !isSummaryRow(it) }
            .mapNotNull { line -> parseCreditCardPdfLine(line, chargeAbsPos) }
    }

    // Parses one row from a credit card PDF.  Picks the amount by proximity to the
    // charge column position recorded from the header; falls back to the last token.
    // All dates on the line are excluded from number candidates — this prevents the
    // billing-month digit (e.g. "05" in "05/06/2026") from being chosen as the amount.
    private fun parseCreditCardPdfLine(line: String, chargeAbsPos: Int): ParsedTransaction? {
        val allDates = PDF_DATE_RE.findAll(line).toList()
        if (allDates.isEmpty()) return null

        val date          = parseDate(allDates.first().value)
        val allDateRanges = allDates.map { it.range }

        data class Tok(val v: Double, val pos: Int, val rawDigits: String)
        val allNumToks = PDF_NUM_FINDER.findAll(line).mapNotNull { m ->
            if (allDateRanges.any { r -> m.range.first in r || m.range.last in r }) return@mapNotNull null
            val v = parseAmount(m.value) ?: return@mapNotNull null
            if (v <= 0) return@mapNotNull null
            Tok(v, m.range.first, m.value.replace(",", ""))
        }.toList()
        val decimalToks = allNumToks.filter {  it.rawDigits.contains('.') }
        val toks = decimalToks.ifEmpty { allNumToks }
        if (decimalToks.isNotEmpty() && allNumToks.size > decimalToks.size) {
            allNumToks.filter { !it.rawDigits.contains('.') }.forEach { t ->
                AppLogger.d(IMPORT_TAG, "  CC: skipped integer '${t.rawDigits}' — decimal tokens present")
            }
        }

        if (toks.isEmpty()) return null

        // Amount = token whose ABSOLUTE position is closest to the charge column.
        // Using absolute positions avoids the normalisation problem: a row with a
        // shorter merchant name would be shorter than the header, shifting normalised
        // positions rightward and causing the wrong token to be selected.
        // Falls back to the last token when no column layout is available (amounts
        // tend to appear at the end of the line in natural RTL reading order).
        val amount: Double = when {
            chargeAbsPos >= 0 && toks.size > 1 ->
                toks.minByOrNull { kotlin.math.abs(it.pos - chargeAbsPos) }?.v
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
        val colorType = detectTypeFromCellColors(row, mapping)
        val final = if (colorType != null) parsed.copy(type = colorType) else parsed
        AppLogger.d(IMPORT_TAG, "[Pipeline:1-Excel] desc='${final.description}' | type=${final.type} | colorOverride=$colorType | amount=${final.amount}")
        return final
    }

    // Returns the transaction type implied by the font color of the amount cell,
    // or null if the color is not a recognisable red or green.
    private fun detectTypeFromCellColors(
        row: org.apache.poi.ss.usermodel.Row,
        mapping: ColumnMapping
    ): TransactionType? {
        // For separate debit/credit columns, select the cell that holds the actual
        // non-zero transaction value — NOT whichever text is non-blank.  A "0" in the
        // debit column is non-blank but irrelevant; checking its color would wrongly
        // classify an income transaction as expense.
        if (mapping.debitColumn != null && mapping.creditColumn != null) {
            val debitCell  = row.getCell(mapping.debitColumn!!)
            val creditCell = row.getCell(mapping.creditColumn!!)
            val debitVal   = parseAmount(cellText(debitCell))  ?: 0.0
            val creditVal  = parseAmount(cellText(creditCell)) ?: 0.0
            AppLogger.d(IMPORT_TAG, "  [ColorDetect] debitCol=${mapping.debitColumn} debitVal=$debitVal | creditCol=${mapping.creditColumn} creditVal=$creditVal")
            return when {
                // Credit cell has a positive value → income; use its color if available.
                creditVal > 0.01 -> {
                    val color = cellFontColorType(creditCell)
                    AppLogger.d(IMPORT_TAG, "  [ColorDetect] creditVal=$creditVal → color=$color → ${color ?: TransactionType.INCOME}")
                    color ?: TransactionType.INCOME
                }
                // Debit cell has a positive value → expense; use its color if available.
                debitVal  > 0.01 -> {
                    val color = cellFontColorType(debitCell)
                    AppLogger.d(IMPORT_TAG, "  [ColorDetect] debitVal=$debitVal → color=$color → ${color ?: TransactionType.EXPENSE}")
                    color ?: TransactionType.EXPENSE
                }
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
                AppLogger.d(IMPORT_TAG, "    [CellColor] fontRGB=($r,$g,$b) isRed=${isRedColor(r,g,b)} isGreen=${isGreenColor(r,g,b)}")
                if (isRedColor(r, g, b))   return TransactionType.EXPENSE
                if (isGreenColor(r, g, b)) return TransactionType.INCOME
            } else {
                AppLogger.d(IMPORT_TAG, "    [CellColor] fontColor present but rgb=null (theme color or auto)")
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
                AppLogger.d(IMPORT_TAG, "    [CellColor] fillRGB=($r,$g,$b) isRed=${isRedColor(r,g,b)} isGreen=${isGreenColor(r,g,b)}")
                if (isRedColor(r, g, b))   return TransactionType.EXPENSE
                if (isGreenColor(r, g, b)) return TransactionType.INCOME
            }
        }

        AppLogger.d(IMPORT_TAG, "    [CellColor] no recognisable color → returning null")
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

            // Bank exports (e.g. Bank Hapoalim) often include 1-3 metadata lines
            // before the real column header row.  Search for it instead of blindly
            // assuming row 0 is the header.
            val headerRowIndex = findHeaderRowIndexInCsv(allRows)
            val headerRow = allRows[headerRowIndex].toList()
            AppLogger.d(IMPORT_TAG, "[CSV] Header row at index $headerRowIndex: ${headerRow.joinToString(" | ")}")
            val transactions = mutableListOf<ParsedTransaction>()

            if (isCreditCardFormat(headerRow)) {
                val ccMapping = detectCreditCardMapping(headerRow)
                if (!ccMapping.hasEnoughColumns())
                    return FileParseResult.Error("Could not identify transaction columns in this file")
                for (i in (headerRowIndex + 1) until allRows.size) {
                    try {
                        val parsed = parseCreditCardRow(allRows[i].toList(), ccMapping)
                        if (parsed != null) transactions.add(parsed)
                    } catch (_: Exception) { }
                }
            } else {
                val mapping = detectColumnMapping(headerRow)
                if (!mapping.hasEnoughColumns())
                    return FileParseResult.Error("Could not identify transaction columns in this file")
                for (i in (headerRowIndex + 1) until allRows.size) {
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

    /**
     * Searches the first 10 rows of a CSV for the header row — the first row where at
     * least 2 cells contain recognised column-header keywords.  This matches the logic
     * used by [findHeaderRow] for Excel sheets so both formats behave consistently.
     * Returns 0 if no row scores ≥ 2 (safe fallback: treat first row as header).
     */
    private fun findHeaderRowIndexInCsv(rows: List<Array<String>>): Int {
        for (i in 0..minOf(9, rows.size - 1)) {
            val score = rows[i].count { cell ->
                val h = cell.trim().lowercase()
                DATE_KEYWORDS.any  { h.contains(it) } ||
                DESC_KEYWORDS.any  { h.contains(it) } ||
                AMOUNT_KEYWORDS.any{ h.contains(it) } ||
                DEBIT_KEYWORDS.any { h.contains(it) } ||
                CREDIT_KEYWORDS.any{ h.contains(it) } ||
                CC_MERCHANT_KEYWORDS.any { h.contains(it) }
            }
            if (score >= 2) return i
        }
        return 0
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
                DATE_KEYWORDS.any { h.contains(it) }      -> if (mapping.dateColumn == null) mapping.dateColumn = index
                DESC_KEYWORDS.any { h.contains(it) }      -> if (mapping.descriptionColumn == null) mapping.descriptionColumn = index
                // Balance must be checked before debit/credit — "credit balance" / "debit balance"
                // are balance columns, not transaction columns.
                BALANCE_KEYWORDS.any { h.contains(it) }   -> if (mapping.balanceColumn == null) mapping.balanceColumn = index
                // Reference/document number — must be tracked so it is never mistaken for an
                // amount when columns shift.  Checked before debit/credit so that a header like
                // "אסמכתה" is not accidentally matched by a substring of another keyword.
                REFERENCE_KEYWORDS.any { h.contains(it) } -> if (mapping.referenceColumn == null) mapping.referenceColumn = index
                // Combined debit+credit header (e.g. "זכות/חובה") — type determined by cell
                // color (green = INCOME, red = EXPENSE); sign used only as last resort.
                DEBIT_KEYWORDS.any { h.contains(it) } && CREDIT_KEYWORDS.any { h.contains(it) } -> {
                    if (mapping.amountColumn == null) {
                        mapping.amountColumn = index
                        mapping.isCombinedAmountColumn = true
                    }
                }
                DEBIT_KEYWORDS.any { h.contains(it) }     -> if (mapping.debitColumn == null) mapping.debitColumn = index
                CREDIT_KEYWORDS.any { h.contains(it) }    -> if (mapping.creditColumn == null) mapping.creditColumn = index
                AMOUNT_KEYWORDS.any { h.contains(it) }    -> if (mapping.amountColumn == null) mapping.amountColumn = index
            }
        }

        // Log the resolved mapping so import issues can be diagnosed from logcat.
        // Amount must come from a named debit/credit/amount column — never from a
        // position-based fallback.  If hasEnoughColumns() is false the caller will
        // return an error rather than guessing the wrong column.
        AppLogger.d(IMPORT_TAG, "[Column Mapping] " +
            "date=${mapping.dateColumn} " +
            "desc=${mapping.descriptionColumn} " +
            "debit=${mapping.debitColumn} " +
            "credit=${mapping.creditColumn} " +
            "amount=${mapping.amountColumn}(combined=${mapping.isCombinedAmountColumn}) " +
            "ref=${mapping.referenceColumn} " +
            "balance=${mapping.balanceColumn} " +
            "| headers: ${headers.joinToString(" | ")}")

        return mapping
    }

    // ── Row parsing ───────────────────────────────────────────────────────────

    private fun parseRowData(cells: List<String>, mapping: ColumnMapping): ParsedTransaction? {
        val description = mapping.descriptionColumn
            ?.let { cells.getOrNull(it)?.trim() }
            ?.takeIf { it.isNotBlank() && !isSummaryRow(it) }
            ?: return null

        // Capture the reference number early so we can validate against it later.
        // The reference column (אסמכתה) must NEVER be used as an amount source.
        val referenceVal = mapping.referenceColumn?.let { parseAmount(cells.getOrNull(it) ?: "") }

        // Evaluate every available source up front, then pick with a single priority order:
        //   explicit debit value > explicit credit value > signed/unsigned amount column.
        // This correctly handles all formats:
        //   • separate debit + credit columns (most Israeli/EU bank exports)
        //   • single signed/unsigned amount column
        //   • only-debit or only-credit column
        val debitVal  = mapping.debitColumn?.let  { parseAmount(cells.getOrNull(it) ?: "") }
        val creditVal = mapping.creditColumn?.let { parseAmount(cells.getOrNull(it) ?: "") }
        val amtStr    = mapping.amountColumn?.let { cells.getOrNull(it)?.trim() }
        val amtVal    = amtStr?.let { parseAmount(it) }

        val amount: Double
        val type: TransactionType
        val amountSourceDesc: String

        when {
            (debitVal ?: 0.0) > 0.0 -> {
                amount = debitVal!!
                type   = TransactionType.EXPENSE
                amountSourceDesc = "חובה/debit col=${mapping.debitColumn}"
            }
            (creditVal ?: 0.0) > 0.0 -> {
                amount = creditVal!!
                type   = TransactionType.INCOME
                amountSourceDesc = "זכות/credit col=${mapping.creditColumn}"
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
                amountSourceDesc = "amount col=${mapping.amountColumn}"
            }
            else -> return null
        }

        if (amount == 0.0) return null

        // Safety check: if the resolved amount matches the reference number, the parser
        // has almost certainly read the wrong column (e.g. due to a shifted row).
        // Log the error and drop this row rather than importing a garbage amount.
        if (referenceVal != null && referenceVal > 0.0 && amount == referenceVal) {
            AppLogger.e(IMPORT_TAG, "[Import Error] amount ($amount) equals reference number ($referenceVal) " +
                "for '$description' — likely column shift. Dropping row. Raw: ${cells.joinToString(" | ")}")
            return null
        }

        val typeSourceDesc = when {
            (debitVal  ?: 0.0) > 0.0 -> "debit column"
            (creditVal ?: 0.0) > 0.0 -> "credit column"
            mapping.amountColumn != null -> "amount-column sign"
            mapping.debitColumn  != null -> "debit-only column"
            mapping.creditColumn != null -> "credit-only column"
            else                         -> "default"
        }

        AppLogger.d(IMPORT_TAG, "[Import Debug] desc='$description' | amount=$amount | type=$type | " +
            "amountFrom=$amountSourceDesc | typeFrom=$typeSourceDesc")

        val dateStr = mapping.dateColumn?.let { cells.getOrNull(it) }
        val date = parseDate(dateStr)

        // Type is determined solely by which column contains the value and/or by cell color
        // (applied by parseExcelRow for Excel files).  Description-based overrides are
        // intentionally omitted — the column position and cell color are authoritative.
        return ParsedTransaction(
            description = description,
            amount = amount,
            date = date,
            type = type,
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
        private const val IMPORT_TAG = "PDF_Import"

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
    // Reference/document-number column (אסמכתה).  Tracked so that it is never
    // accidentally used as an amount source, and so the validation check can compare
    // the resolved amount against the reference value to detect column-shift errors.
    var referenceColumn: Int? = null,
    // True when amountColumn was detected from a combined debit+credit header
    // (e.g. "זכות/חובה"). In this case cell color is the primary authority for
    // expense/income classification; sign is used only as a last resort.
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
