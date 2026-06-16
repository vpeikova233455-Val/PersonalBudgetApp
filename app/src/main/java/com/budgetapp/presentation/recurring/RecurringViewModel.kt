package com.budgetapp.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.TransactionRepository
import com.budgetapp.domain.usecase.transaction.TransactionAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import javax.inject.Inject

data class RecurringUiState(
    val isLoading: Boolean = true,
    val expectedThisMonth: List<TransactionAnalytics.RecurringPattern> = emptyList(),
    val income: List<TransactionAnalytics.RecurringPattern> = emptyList(),
    val expenses: List<TransactionAnalytics.RecurringPattern> = emptyList(),
    val monthLabel: String = "this month"
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val userId: String = runBlocking { authRepository.getCurrentUserId() } ?: ""

    private val _ui = MutableStateFlow(RecurringUiState())
    val uiState: StateFlow<RecurringUiState> = _ui.asStateFlow()

    init { load() }

    private fun load() {
        if (userId.isEmpty()) {
            _ui.value = _ui.value.copy(isLoading = false)
            return
        }
        viewModelScope.launch {
            try {
                val all = transactionRepository.getAllTransactions(userId).first()
                val patterns = TransactionAnalytics.detectRecurring(all)
                AppLogger.d(TAG, "Detected ${patterns.size} recurring patterns from ${all.size} transactions")

                val income = patterns.filter { it.type == TransactionType.INCOME }
                val expenses = patterns.filter { it.type == TransactionType.EXPENSE }

                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val monthStart = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val monthEnd = cal.timeInMillis
                val expected = patterns.filter { it.nextExpectedDate in monthStart until monthEnd }

                _ui.value = RecurringUiState(
                    isLoading = false,
                    expectedThisMonth = expected,
                    income = income,
                    expenses = expenses
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load recurring patterns", e)
                _ui.value = _ui.value.copy(isLoading = false)
            }
        }
    }

    companion object { private const val TAG = "Recurring" }
}
