package com.budgetapp.presentation.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.export.ExportFormat
import com.budgetapp.data.export.ExportRange
import com.budgetapp.data.export.ExportService
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.CategoryRepository
import com.budgetapp.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val error: String? = null,
    val isRestoring: Boolean = false,
    val restoreSuccess: Int? = null,   // count of restored transactions
    val restoreError: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    private val exportService: ExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun suggestedFileName(format: ExportFormat): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val ext = if (format == ExportFormat.CSV) "csv" else "xlsx"
        return "budget_export_$stamp.$ext"
    }

    fun saveToUri(uri: Uri, contentResolver: ContentResolver, format: ExportFormat, range: ExportRange) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null, exportSuccess = false) }
            try {
                val userId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
                val (startDate, endDate) = dateRangeFor(range)

                val transactions = withContext(Dispatchers.IO) {
                    if (startDate == null) {
                        transactionRepository.getAllTransactions(userId).first()
                    } else {
                        transactionRepository.getTransactionsByDateRange(userId, startDate, endDate!!).first()
                    }
                }.sortedByDescending { it.date }

                if (transactions.isEmpty()) {
                    _uiState.update { it.copy(isExporting = false, error = "No transactions found for this period.") }
                    return@launch
                }

                val bytes = withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.CSV -> exportService.exportToCsv(transactions)
                        ExportFormat.EXCEL -> exportService.exportToExcel(transactions)
                    }
                }

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw Exception("Could not open output stream for the selected file.")
                }

                _uiState.update { it.copy(isExporting = false, exportSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message ?: "Export failed") }
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(exportSuccess = false, error = null, restoreSuccess = null, restoreError = null) }
    }

    fun restoreFromBackup(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, restoreError = null, restoreSuccess = null) }
            try {
                val userId = authRepository.getCurrentUserId() ?: throw Exception("Not logged in")
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Could not read file")
                }
                val lines = bytes.toString(Charsets.UTF_8)
                    .lines()
                    .drop(1)           // skip header
                    .filter { it.isNotBlank() }

                val allCategories = withContext(Dispatchers.IO) {
                    categoryRepository.getAllCategories().first()
                }
                val categoryByName = allCategories.associateBy { it.name.lowercase() }
                val fallbackCategory = allCategories.firstOrNull { it.name == "Other" }
                    ?: allCategories.firstOrNull()
                    ?: throw Exception("No categories found — please seed categories first")

                var imported = 0
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                withContext(Dispatchers.IO) {
                    lines.forEach { line ->
                        runCatching {
                            val cols = parseCsvLine(line)
                            if (cols.size < 5) return@runCatching
                            val date   = stamp.parse(cols[0])?.time ?: return@runCatching
                            val desc   = cols[1]
                            val catName = cols[2].lowercase()
                            val type   = TransactionType.valueOf(cols[3].uppercase())
                            val amount = cols[4].toDoubleOrNull() ?: return@runCatching
                            val bank   = cols.getOrNull(5)?.takeIf { it.isNotBlank() }

                            val category = categoryByName[catName] ?: fallbackCategory

                            transactionRepository.insertTransaction(
                                Transaction(
                                    id = java.util.UUID.randomUUID().toString(),
                                    userId = userId,
                                    type = type,
                                    amount = amount,
                                    description = desc,
                                    category = category,
                                    date = date,
                                    bankName = bank
                                )
                            )
                            imported++
                        }
                    }
                }

                _uiState.update { it.copy(isRestoring = false, restoreSuccess = imported) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRestoring = false, restoreError = e.message ?: "Restore failed") }
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        for (ch in line) {
            when {
                ch == '"'           -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else                -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun dateRangeFor(range: ExportRange): Pair<Long?, Long?> {
        if (range == ExportRange.ALL) return null to null
        val cal = Calendar.getInstance()

        return when (range) {
            ExportRange.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                start to cal.timeInMillis
            }
            ExportRange.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                start to cal.timeInMillis
            }
            ExportRange.LAST_3_MONTHS -> {
                val end = cal.timeInMillis
                cal.add(Calendar.MONTH, -3)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to end
            }
            ExportRange.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                start to cal.timeInMillis
            }
            ExportRange.ALL -> null to null
        }
    }
}
