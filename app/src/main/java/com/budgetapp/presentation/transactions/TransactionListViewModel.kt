package com.budgetapp.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class TransactionFilter { ALL, INCOME, EXPENSE }

data class TransactionListUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val filter: TransactionFilter = TransactionFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val showDeleteConfirmation: Boolean = false
)

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                return@launch
            }
            transactionRepository.getAllTransactions(userId).collect { transactions ->
                val sorted = transactions.sortedByDescending { it.date }
                _uiState.update { state ->
                    val filtered = applyFilter(sorted, state.filter, state.searchQuery)
                    state.copy(transactions = sorted, filteredTransactions = filtered, isLoading = false)
                }
            }
        }
    }

    fun setFilter(filter: TransactionFilter) {
        _uiState.update { state ->
            val filtered = applyFilter(state.transactions, filter, state.searchQuery)
            state.copy(filter = filter, filteredTransactions = filtered)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilter(state.transactions, state.filter, query)
            state.copy(searchQuery = query, filteredTransactions = filtered)
        }
    }

    private fun applyFilter(
        transactions: List<Transaction>,
        filter: TransactionFilter,
        query: String
    ): List<Transaction> {
        var result = transactions
        if (filter == TransactionFilter.INCOME) result = result.filter { it.type == TransactionType.INCOME }
        if (filter == TransactionFilter.EXPENSE) result = result.filter { it.type == TransactionType.EXPENSE }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            result = result.filter {
                it.description.lowercase().contains(q) ||
                it.category.name.lowercase().contains(q)
            }
        }
        return result
    }

    // ── Multi-select ──────────────────────────────────────────────────────────

    fun enterSelectionMode(id: String) {
        _uiState.update { it.copy(isSelectionMode = true, selectedIds = setOf(id)) }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val updated = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            state.copy(selectedIds = updated, isSelectionMode = updated.isNotEmpty())
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.filteredTransactions.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet(), showDeleteConfirmation = false) }
    }

    fun requestDeleteSelected() {
        if (_uiState.value.selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDeleteSelected() {
        val idsToDelete = _uiState.value.selectedIds
        val toDelete = _uiState.value.transactions.filter { it.id in idsToDelete }
        _uiState.update { it.copy(showDeleteConfirmation = false) }
        viewModelScope.launch {
            transactionRepository.deleteTransactions(toDelete)
            _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
        }
    }

    fun groupByDate(transactions: List<Transaction>): Map<String, List<Transaction>> {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val yesterday = today - 86_400_000L

        return transactions.groupBy { t ->
            when {
                t.date >= today -> "Today"
                t.date >= yesterday -> "Yesterday"
                else -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = t.date }
                    val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
                    "$month ${cal.get(Calendar.DAY_OF_MONTH)}, ${cal.get(Calendar.YEAR)}"
                }
            }
        }
    }
}
