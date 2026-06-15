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
            AppLogger.d("FileImport", "parseFile start: uri=$uri")
            try {
                val result = withContext(Dispatchers.IO) { fileParserService.parseFile(uri) }
                AppLogger.d("FileImport", "parseFile result: ${result::class.simpleName}")
                when (result) {
                    is FileParseResult.Success -> {
                        AppLogger.d("FileImport", "parseFile success: ${result.transactions.size} transactions")
                        showPreview(result.transactions, result.diagnosticReport)
                    }
                    is FileParseResult.NeedsOcr -> {
                        AppLogger.d("FileImport", "parseFile NeedsOcr — starting OCR")
                        _state.value = FileImportState.OcrInProgress
                        val transactions = withContext(Dispatchers.IO) { ocrPdfPages(uri) }
                        AppLogger.d("FileImport", "OCR returned ${transactions.size} transactions")
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
                    is FileParseResult.Error -> {
                        AppLogger.e("FileImport", "parseFile error: ${result.message}")
                        _state.value = FileImportState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("FileImport", "parseFile exception: ${e.javaClass.simpleName}: ${e.message}", e)
                val msg = e.message ?: "Unknown error"
                val friendly = if (msg.contains("API key", ignoreCase = true))
                    "AI processing failed — Gemini API key is missing or invalid.\n\nAdd gemini.api.key=YOUR_KEY to local.properties and rebuild."
                else msg
                _state.value = FileImportState.Error(friendly)
            }
        }
    }

    private fun showPreview(transactions: List<ParsedTransaction>, diagnosticReport: String? = null) {
        AppLogger.d("FileImport", "Parser returned ${transactions.size} rows")
        transactions.forEach { tx ->
            AppLogger.d("FileImport", "[Stage1-Parsed] desc='${tx.description}' | type=${tx.type} | amount=${tx.amount} | date=${tx.date}")
        }
        if (transactions.isEmpty()) {
            _state.value = FileImportState.Error("No transactions found in this PDF.")
            return
        }
        // Save all parsed transactions to the DB immediately so they persist across app restarts.
        viewModelScope.launch {
            _state.value = FileImportState.Importing
            try {
                val userId = authRepository.getCurrentUserId() ?: ""
                val pending = transactions.map { tx ->
                    AppLogger.d("FileImport", "[Stage7-DBWrite] desc='${tx.description}' | type=${tx.type} | amount=${tx.amount}")
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
                withContext(Dispatchers.IO) {
                    pendingTransactionDao.insertPendingList(pending)
                }
                _state.value = FileImportState.Done(pending.size)
            } catch (e: Exception) {
                _state.value = FileImportState.Error(e.message ?: "Import failed")
            }
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

    fun resetState() { _state.value = FileImportState.Idle }

    private fun parseIsoDate(isoDate: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time
    } catch (_: Exception) { null }
}

// ── State ─────────────────────────────────────────────────────────────────────

sealed class FileImportState {
    object Idle : FileImportState()
    object Parsing : FileImportState()
    object OcrInProgress : FileImportState()
    object Importing : FileImportState()
    data class Done(val importedCount: Int) : FileImportState()
    data class Error(val message: String) : FileImportState()
}
