package com.budgetapp.presentation.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.database.dao.PendingTransactionDao
import com.budgetapp.data.local.entity.ImportSource
import com.budgetapp.data.local.entity.PendingTransactionEntity
import com.budgetapp.data.local.entity.TransactionType as EntityTransactionType
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.remote.gemini.FileParseResult
import com.budgetapp.data.remote.gemini.FileParserService
import com.budgetapp.data.remote.gemini.GeminiOcrService
import com.budgetapp.data.remote.gemini.OcrResult
import com.budgetapp.data.remote.gemini.ParsedTransaction
import com.budgetapp.data.remote.gemini.TransactionType as GeminiTransactionType
import com.budgetapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FileImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileParserService: FileParserService,
    private val geminiOcrService: GeminiOcrService,
    private val pendingTransactionDao: PendingTransactionDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FileImportState>(FileImportState.Idle)
    val state: StateFlow<FileImportState> = _state

    fun parseFile(uri: Uri) {
        viewModelScope.launch {
            _state.value = FileImportState.Parsing
            try {
                val result = withContext(Dispatchers.IO) { fileParserService.parseFile(uri) }
                when (result) {
                    is FileParseResult.Success -> showPreview(result.transactions)
                    is FileParseResult.NeedsOcr -> {
                        _state.value = FileImportState.OcrInProgress
                        val transactions = withContext(Dispatchers.IO) { ocrPdfPages(uri) }
                        if (transactions.isEmpty()) {
                            _state.value = FileImportState.Error(
                                "Could not read transactions from this PDF.\n\n" +
                                "• If it's a scanned PDF, AI processing is required — make sure a Gemini API key is configured in local.properties (gemini.api.key=...).\n" +
                                "• Otherwise, try exporting the statement as Excel or CSV from your bank's website."
                            )
                        } else {
                            showPreview(transactions)
                        }
                    }
                    is FileParseResult.Error -> _state.value = FileImportState.Error(result.message)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                val friendly = if (msg.contains("API key", ignoreCase = true))
                    "AI processing failed — Gemini API key is missing or invalid.\n\nAdd gemini.api.key=YOUR_KEY to local.properties and rebuild."
                else msg
                _state.value = FileImportState.Error(friendly)
            }
        }
    }

    private fun showPreview(transactions: List<ParsedTransaction>) {
        AppLogger.d("FileImport", "Parser returned ${transactions.size} rows")
        transactions.forEach { tx ->
            AppLogger.d("FileImport", "[Pipeline:2a-PostParse] desc='${tx.description}' | type=${tx.type} | amount=${tx.amount} | date=${tx.date}")
        }
        val months = groupByMonth(transactions)
        val shownCount = months.sumOf { it.transactions.size }
        AppLogger.d("FileImport", "Review screen will show $shownCount rows in ${months.size} groups (parser→review delta: ${transactions.size - shownCount})")
        months.flatMap { it.transactions }.forEach { tx ->
            AppLogger.d("FileImport", "[Pipeline:2b-AfterDedup] desc='${tx.description}' | type=${tx.type} | amount=${tx.amount}")
        }
        if (months.isEmpty()) {
            _state.value = FileImportState.Error("No transactions found in this PDF.")
        } else {
            _state.value = FileImportState.Preview(months)
        }
    }

    private suspend fun ocrPdfPages(uri: Uri): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            val renderer = PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, 10)

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                when (val ocr = geminiOcrService.extractTransactionsFromBitmap(bitmap)) {
                    is OcrResult.Success -> transactions.addAll(ocr.transactions)
                    is OcrResult.Error -> {}
                }
                bitmap.recycle()
            }
            renderer.close()
            pfd.close()
        } catch (_: Exception) {}
        return transactions
    }

    fun toggleMonth(key: String) {
        val current = _state.value as? FileImportState.Preview ?: return
        _state.value = current.copy(
            months = current.months.map {
                if (it.key == key) it.copy(selected = !it.selected) else it
            }
        )
    }

    fun selectAll() {
        val current = _state.value as? FileImportState.Preview ?: return
        _state.value = current.copy(months = current.months.map { it.copy(selected = true) })
    }

    fun deselectAll() {
        val current = _state.value as? FileImportState.Preview ?: return
        _state.value = current.copy(months = current.months.map { it.copy(selected = false) })
    }

    fun importSelected() {
        val current = _state.value as? FileImportState.Preview ?: return
        val selectedTransactions = current.months
            .filter { it.selected }
            .flatMap { it.transactions }

        if (selectedTransactions.isEmpty()) return

        viewModelScope.launch {
            _state.value = FileImportState.Importing
            try {
                val userId = authRepository.getCurrentUserId() ?: ""
                val pending = selectedTransactions.map { tx ->
                    PendingTransactionEntity(
                        userId = userId,
                        type = when (tx.type) {
                            GeminiTransactionType.INCOME  -> EntityTransactionType.INCOME
                            GeminiTransactionType.EXPENSE -> EntityTransactionType.EXPENSE
                        },
                        amount = tx.amount,
                        description = tx.description,
                        date = tx.date?.let { parseIsoDate(it) },
                        sourceType = ImportSource.EXCEL,
                        sourceUri = "",
                        categoryConfidence = 0.3
                    )
                }
                pending.forEach { p ->
                    AppLogger.d("FileImport", "[Pipeline:3-DBWrite] desc='${p.description}' | type=${p.type} | amount=${p.amount}")
                }
                withContext(Dispatchers.IO) {
                    pendingTransactionDao.insertPendingList(pending)
                }
                _state.value = FileImportState.Done(pending.size)
            } catch (e: Exception) {
                _state.value = FileImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun resetState() { _state.value = FileImportState.Idle }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun groupByMonth(transactions: List<ParsedTransaction>): List<MonthGroup> {
        val duplicateKeys = transactions
            .groupBy { "${it.date}_${it.amount}" }
            .filter { it.value.size > 1 }
            .keys

        val (dated, undated) = transactions.partition { it.date != null }

        val groups = dated
            .groupBy { yearMonth(it.date!!) }
            .entries
            .sortedBy { it.key }
            .map { (key, txs) ->
                val dupCount = txs.count { "${it.date}_${it.amount}" in duplicateKeys }
                MonthGroup(
                    key = key,
                    displayName = formatMonthName(key),
                    transactions = txs,
                    totalAmount = txs.sumOf { it.amount },
                    expenseCount = txs.count { it.type == GeminiTransactionType.EXPENSE },
                    incomeCount = txs.count { it.type == GeminiTransactionType.INCOME },
                    duplicateCount = dupCount
                )
            }
            .toMutableList()

        if (undated.isNotEmpty()) {
            groups.add(
                MonthGroup(
                    key = "unknown",
                    displayName = "Unknown date",
                    transactions = undated,
                    totalAmount = undated.sumOf { it.amount },
                    expenseCount = undated.count { it.type == GeminiTransactionType.EXPENSE },
                    incomeCount = undated.count { it.type == GeminiTransactionType.INCOME },
                    selected = false
                )
            )
        }

        return groups
    }

    private fun yearMonth(isoDate: String): String =
        isoDate.take(7) // "yyyy-MM"

    private fun formatMonthName(key: String): String {
        if (key == "unknown") return "Unknown date"
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.ENGLISH).parse(key)!!
            SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(date)
        } catch (_: Exception) { key }
    }

    private fun parseIsoDate(isoDate: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time
    } catch (_: Exception) { null }
}

// ── State ─────────────────────────────────────────────────────────────────────

sealed class FileImportState {
    object Idle : FileImportState()
    object Parsing : FileImportState()
    object OcrInProgress : FileImportState()
    data class Preview(val months: List<MonthGroup>) : FileImportState() {
        val selectedCount: Int get() = months.filter { it.selected }.sumOf { it.transactions.size }
        val totalCount: Int get() = months.sumOf { it.transactions.size }
    }
    object Importing : FileImportState()
    data class Done(val importedCount: Int) : FileImportState()
    data class Error(val message: String) : FileImportState()
}

data class MonthGroup(
    val key: String,
    val displayName: String,
    val transactions: List<ParsedTransaction>,
    val totalAmount: Double,
    val expenseCount: Int,
    val incomeCount: Int,
    val selected: Boolean = true,
    val duplicateCount: Int = 0
)
