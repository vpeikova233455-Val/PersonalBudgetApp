package com.budgetapp.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.domain.model.Budget
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.BudgetRepository
import com.budgetapp.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val editingBudget: Budget? = null,
    val categories: List<Category> = emptyList()
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadBudgets()
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not logged in") }
                return@launch
            }
            combine(
                budgetRepository.getAllBudgets(userId),
                categoryRepository.getAllCategories()
            ) { budgets, categories ->
                budgets to categories
            }.collect { (budgets, categories) ->
                _uiState.update { it.copy(budgets = budgets, categories = categories, isLoading = false) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingBudget = null) }
    }

    fun showEditDialog(budget: Budget) {
        _uiState.update { it.copy(showAddDialog = true, editingBudget = budget) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingBudget = null) }
    }

    fun saveBudget(category: Category, monthlyLimit: Double, alertThreshold: Double = 0.8) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            val editing = _uiState.value.editingBudget
            if (editing != null) {
                budgetRepository.updateBudget(
                    editing.copy(
                        category = category,
                        monthlyLimit = monthlyLimit,
                        alertThreshold = alertThreshold
                    )
                )
            } else {
                budgetRepository.insertBudget(
                    Budget(
                        id = 0,
                        category = category,
                        monthlyLimit = monthlyLimit,
                        currentSpending = 0.0,
                        alertThreshold = alertThreshold
                    )
                )
            }
            dismissDialog()
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(budget)
        }
    }
}
