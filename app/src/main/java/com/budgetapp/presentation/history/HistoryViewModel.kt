package com.budgetapp.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.entity.ChangeAction
import com.budgetapp.data.local.entity.HistoryEntityType
import com.budgetapp.domain.model.ChangeLogEntry
import com.budgetapp.domain.repository.ChangeLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter(val label: String) {
    ALL("All"),
    TRANSACTIONS("Transactions"),
    SAVINGS("Savings"),
    CATEGORIES("Categories")
}

data class HistoryUiState(
    val entries: List<ChangeLogEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    val isRestoring: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val changeLogRepository: ChangeLogRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    private val _isRestoring = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        changeLogRepository.getAll(),
        _filter,
        _isRestoring,
        _message
    ) { entries, filter, restoring, message ->
        val filtered = when (filter) {
            HistoryFilter.ALL          -> entries
            HistoryFilter.TRANSACTIONS -> entries.filter { it.entityType == HistoryEntityType.TRANSACTION }
            HistoryFilter.SAVINGS      -> entries.filter { it.entityType == HistoryEntityType.SAVINGS }
            HistoryFilter.CATEGORIES   -> entries.filter { it.entityType == HistoryEntityType.CATEGORY }
        }
        HistoryUiState(entries = filtered, filter = filter, isRestoring = restoring, message = message)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    fun setFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun restore(entry: ChangeLogEntry) {
        viewModelScope.launch {
            _isRestoring.value = true
            runCatching { changeLogRepository.restore(entry) }
                .onSuccess {
                    val label = when (entry.action) {
                        ChangeAction.DELETE -> "\"${entry.displayName}\" restored"
                        ChangeAction.UPDATE -> "\"${entry.displayName}\" reverted to previous version"
                        ChangeAction.CREATE -> "\"${entry.displayName}\" restored"
                    }
                    _message.value = label
                }
                .onFailure { _message.value = "Restore failed: ${it.message}" }
            _isRestoring.value = false
        }
    }

    fun clearMessage() { _message.value = null }

    fun clearHistory() {
        viewModelScope.launch {
            changeLogRepository.clearAll()
            _message.value = "History cleared"
        }
    }
}
