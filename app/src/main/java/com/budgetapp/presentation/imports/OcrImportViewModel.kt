package com.budgetapp.presentation.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.database.dao.PendingTransactionDao
import com.budgetapp.data.local.entity.ImportSource
import com.budgetapp.data.local.entity.PendingTransactionEntity
import com.budgetapp.data.local.entity.TransactionType as EntityTransactionType
import com.budgetapp.data.remote.gemini.GeminiOcrService
import com.budgetapp.data.remote.gemini.OcrResult
import com.budgetapp.data.remote.gemini.TransactionType as GeminiTransactionType
import com.budgetapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class OcrImportViewModel @Inject constructor(
    private val geminiOcrService: GeminiOcrService,
    private val pendingTransactionDao: PendingTransactionDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<OcrImportState>(OcrImportState.Idle)
    val state: StateFlow<OcrImportState> = _state

    fun processScreenshot(imageUri: Uri) {
        viewModelScope.launch {
            _state.value = OcrImportState.Processing
            try {
                val userId = authRepository.getCurrentUserId() ?: ""
                when (val result = geminiOcrService.extractTransactionsFromImage(imageUri)) {
                    is OcrResult.Success -> {
                        val pendingList = result.transactions.map { tx ->
                            PendingTransactionEntity(
                                userId = userId,
                                type = when (tx.type) {
                                    GeminiTransactionType.INCOME -> EntityTransactionType.INCOME
                                    GeminiTransactionType.EXPENSE -> EntityTransactionType.EXPENSE
                                },
                                amount = tx.amount,
                                description = tx.description,
                                date = tx.date?.let { parseDate(it) },
                                sourceType = ImportSource.SCREENSHOT,
                                sourceUri = imageUri.toString(),
                                categoryConfidence = 0.5
                            )
                        }
                        pendingTransactionDao.insertPendingList(pendingList)
                        _state.value = OcrImportState.Done(result.transactions.size)
                    }
                    is OcrResult.Error -> {
                        _state.value = OcrImportState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _state.value = OcrImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _state.value = OcrImportState.Idle
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }
}

sealed class OcrImportState {
    object Idle : OcrImportState()
    object Processing : OcrImportState()
    data class Done(val count: Int) : OcrImportState()
    data class Error(val message: String) : OcrImportState()
}
