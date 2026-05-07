package com.budgetapp.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.export.ExportFormat
import com.budgetapp.data.export.ExportRange
import com.budgetapp.data.export.ExportService
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val isExporting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val exportService: ExportService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val _shareEvent = MutableSharedFlow<Intent>()
    val shareEvent: SharedFlow<Intent> = _shareEvent.asSharedFlow()

    fun export(format: ExportFormat, range: ExportRange) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
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

                val (bytes, mimeType, ext) = withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.CSV -> Triple(
                            exportService.exportToCsv(transactions),
                            "text/csv",
                            "csv"
                        )
                        ExportFormat.EXCEL -> Triple(
                            exportService.exportToExcel(transactions),
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "xlsx"
                        )
                    }
                }

                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val filename = "budget_export_${stamp}.$ext"

                val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
                val file = File(exportDir, filename)
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.update_provider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Budget Export – $stamp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                _shareEvent.emit(Intent.createChooser(shareIntent, "Export ${transactions.size} transactions"))
                _uiState.update { it.copy(isExporting = false) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, error = e.message ?: "Export failed") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
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
