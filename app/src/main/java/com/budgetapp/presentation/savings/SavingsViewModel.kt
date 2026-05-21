package com.budgetapp.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.entity.AccountType
import com.budgetapp.data.local.entity.RecurrenceFrequency
import com.budgetapp.domain.model.PensionAccount
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SavingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavingsUiState(
    val accounts: List<PensionAccount> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalValue: Double = 0.0,
    val totalMonthlyContribution: Double = 0.0,
    val valueByType: Map<AccountType, Double> = emptyMap()
)

@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val repository: SavingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavingsUiState())
    val uiState: StateFlow<SavingsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                return@launch
            }
            repository.getAllAccounts(userId)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { accounts ->
                    _uiState.update {
                        it.copy(
                            accounts = accounts,
                            isLoading = false,
                            totalValue = accounts.sumOf { a -> a.currentValue },
                            totalMonthlyContribution = accounts.sumOf { a -> a.totalMonthlyContribution },
                            valueByType = accounts.groupBy { a -> a.accountType }
                                .mapValues { (_, list) -> list.sumOf { a -> a.currentValue } }
                        )
                    }
                }
        }
    }

    fun saveAccount(
        id: Long,
        accountName: String,
        provider: String,
        currentValue: Double,
        contributionAmount: Double,
        employerContribution: Double?,
        contributionFrequency: RecurrenceFrequency,
        accountType: AccountType,
        notes: String
    ) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            val account = PensionAccount(
                id = id,
                accountName = accountName.trim(),
                provider = provider.trim(),
                currentValue = currentValue,
                contributionAmount = contributionAmount,
                employerContribution = employerContribution,
                contributionFrequency = contributionFrequency,
                accountType = accountType,
                notes = notes.trim()
            )
            if (id == 0L) repository.insertAccount(account, userId)
            else repository.updateAccount(account, userId)
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch { repository.deleteAccount(id) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
