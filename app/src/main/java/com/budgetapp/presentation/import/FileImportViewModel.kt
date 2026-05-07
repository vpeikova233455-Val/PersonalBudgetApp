package com.budgetapp.presentation.import

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.database.dao.PendingTransactionDao
import com.budgetapp.data.local.entity.ImportSource
import com.budgetapp.data.local.entity.PendingTransactionEntity
import com.budgetapp.data.local.entity.TransactionType as EntityTransactionType
import com.budgetapp.data.remote.gemini.FileParseResult
import com.budgetapp.data.remote.gemini.FileParserService
import com.budgetapp.data.remote.gemini.ParsedTransaction
import com.budgetapp.data.remote.gemini.TransactionType as GeminiTransactionType
import com.budgetapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val fileParserService: FileParserService,
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
                    is FileParseResult.Success -> {
                        val months = groupByMonth(result.transactions)
                        if (months.isEmpty()) {
                            _state.value = FileImportState.Error("No transactions with recognisable dates found.")
                        } else {
                            _state.value = FileImportState.Preview(months)
                        }
                    }
                    is FileParseResult.Error -> _state.value = FileImportState.Error(result.message)
                }
            } catch (e: Exception) {
                _state.value = FileImportState.Error(e.message ?: "Unknown error")
            }
        }
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
        val (dated, undated) = transactions.partition { it.date != null }

        val groups = dated
            .groupBy { yearMonth(it.date!!) }
            .entries
            .sortedBy { it.key }
            .map { (key, txs) ->
                MonthGroup(
                    key = key,
                    displayName = formatMonthName(key),
                    transactions = txs,
                    totalAmount = txs.sumOf { it.amount },
                    expenseCount = txs.count { it.type == GeminiTransactionType.EXPENSE },
                    incomeCount = txs.count { it.type == GeminiTransactionType.INCOME }
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
    val selected: Boolean = true
)
