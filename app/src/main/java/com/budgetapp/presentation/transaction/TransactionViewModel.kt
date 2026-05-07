package com.budgetapp.presentation.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.generateUUID
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.CategoryRepository
import com.budgetapp.domain.repository.TransactionRepository
import com.budgetapp.domain.usecase.category.GetAllCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionFormState(
    val id: String = generateUUID(),
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val description: String = "",
    val selectedCategory: Category? = null,
    val date: Long = System.currentTimeMillis(),
    val amountError: String? = null,
    val descriptionError: String? = null,
    val categoryError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val categoryRepository: CategoryRepository,
    getAllCategoriesUseCase: GetAllCategoriesUseCase
) : ViewModel() {

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    val categories: StateFlow<List<Category>> = getAllCategoriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadTransaction(transactionId: String) {
        viewModelScope.launch {
            try {
                val transaction = transactionRepository.getTransactionById(transactionId)
                if (transaction != null) {
                    _formState.update {
                        it.copy(
                            id = transaction.id,
                            type = transaction.type,
                            amount = transaction.amount.toString(),
                            description = transaction.description,
                            selectedCategory = transaction.category,
                            date = transaction.date
                        )
                    }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(error = "Failed to load transaction") }
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        _formState.update { it.copy(type = type) }
    }

    fun onAmountChange(amount: String) {
        // Only allow valid decimal numbers
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _formState.update { it.copy(amount = amount, amountError = null) }
        }
    }

    fun onDescriptionChange(description: String) {
        _formState.update { it.copy(description = description, descriptionError = null) }
    }

    fun onCategorySelect(category: Category) {
        _formState.update { it.copy(selectedCategory = category, categoryError = null) }
    }

    fun createCategory(name: String, icon: String) {
        viewModelScope.launch {
            try {
                val newId = categoryRepository.insertCategory(
                    Category(id = 0, name = name, icon = icon, color = "#607D8B", isCustom = true)
                )
                val created = categoryRepository.getCategoryById(newId)
                if (created != null) {
                    _formState.update { it.copy(selectedCategory = created, categoryError = null) }
                }
            } catch (_: Exception) {}
        }
    }

    fun onDateChange(date: Long) {
        _formState.update { it.copy(date = date) }
    }

    fun saveTransaction() {
        if (!validateForm()) return

        viewModelScope.launch {
            try {
                _formState.update { it.copy(isLoading = true, error = null) }

                val userId = authRepository.getCurrentUserId()
                    ?: throw Exception("User not logged in")

                val transaction = Transaction(
                    id = _formState.value.id,
                    userId = userId,
                    type = _formState.value.type,
                    amount = _formState.value.amount.toDouble(),
                    description = _formState.value.description,
                    category = _formState.value.selectedCategory!!,
                    date = _formState.value.date
                )

                transactionRepository.insertTransaction(transaction)
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save transaction"
                    )
                }
            }
        }
    }

    fun deleteTransaction() {
        viewModelScope.launch {
            try {
                _formState.update { it.copy(isLoading = true, error = null) }

                val userId = authRepository.getCurrentUserId()
                    ?: throw Exception("User not logged in")

                val transaction = Transaction(
                    id = _formState.value.id,
                    userId = userId,
                    type = _formState.value.type,
                    amount = _formState.value.amount.toDoubleOrNull() ?: 0.0,
                    description = _formState.value.description,
                    category = _formState.value.selectedCategory
                        ?: categories.value.firstOrNull()
                        ?: throw Exception("No category selected"),
                    date = _formState.value.date
                )

                transactionRepository.deleteTransaction(transaction)
                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to delete transaction"
                    )
                }
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (_formState.value.amount.isBlank() || _formState.value.amount.toDoubleOrNull() == null) {
            _formState.update { it.copy(amountError = "Please enter a valid amount") }
            isValid = false
        } else if (_formState.value.amount.toDouble() <= 0) {
            _formState.update { it.copy(amountError = "Amount must be greater than 0") }
            isValid = false
        }

        if (_formState.value.description.isBlank()) {
            _formState.update { it.copy(descriptionError = "Description is required") }
            isValid = false
        }

        if (_formState.value.selectedCategory == null) {
            _formState.update { it.copy(categoryError = "Please select a category") }
            isValid = false
        }

        return isValid
    }

    fun resetSavedState() {
        _formState.update { it.copy(isSaved = false) }
    }
}
