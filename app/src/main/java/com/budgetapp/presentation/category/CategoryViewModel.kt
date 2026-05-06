package com.budgetapp.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.CategoryRepository
import com.budgetapp.domain.usecase.category.GetAllCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryFormState(
    val id: Long = 0,
    val name: String = "",
    val icon: String = "",
    val color: String = "",
    val nameError: String? = null,
    val iconError: String? = null,
    val colorError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

sealed class CategoryUiState {
    data object Loading : CategoryUiState()
    data class Success(val categories: List<Category>) : CategoryUiState()
    data class Error(val message: String) : CategoryUiState()
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    getAllCategoriesUseCase: GetAllCategoriesUseCase
) : ViewModel() {

    val categories: StateFlow<CategoryUiState> = getAllCategoriesUseCase()
        .map<List<Category>, CategoryUiState> { CategoryUiState.Success(it) }
        .catch { emit(CategoryUiState.Error(it.message ?: "Failed to load categories")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoryUiState.Loading
        )

    private val _formState = MutableStateFlow(CategoryFormState())
    val formState: StateFlow<CategoryFormState> = _formState.asStateFlow()

    fun loadCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                val category = categoryRepository.getCategoryById(categoryId)
                if (category != null) {
                    _formState.update {
                        it.copy(
                            id = category.id,
                            name = category.name,
                            icon = category.icon,
                            color = category.color
                        )
                    }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(error = "Failed to load category") }
            }
        }
    }

    fun onNameChange(name: String) {
        _formState.update { it.copy(name = name, nameError = null) }
    }

    fun onIconChange(icon: String) {
        _formState.update { it.copy(icon = icon, iconError = null) }
    }

    fun onColorChange(color: String) {
        _formState.update { it.copy(color = color, colorError = null) }
    }

    fun saveCategory() {
        if (!validateForm()) return

        viewModelScope.launch {
            try {
                _formState.update { it.copy(isLoading = true, error = null) }

                val userId = authRepository.getCurrentUserId()
                    ?: throw Exception("User not logged in")

                val category = Category(
                    id = _formState.value.id,
                    name = _formState.value.name,
                    icon = _formState.value.icon,
                    color = _formState.value.color,
                    isCustom = true
                )

                if (_formState.value.id == 0L) {
                    categoryRepository.insertCategory(category)
                } else {
                    categoryRepository.updateCategory(category)
                }

                _formState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to save category"
                    )
                }
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                if (!category.isCustom) {
                    throw Exception("Cannot delete built-in categories")
                }
                categoryRepository.deleteCategory(category)
            } catch (e: Exception) {
                _formState.update { it.copy(error = e.message ?: "Failed to delete category") }
            }
        }
    }

    fun resetFormState() {
        _formState.value = CategoryFormState()
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (_formState.value.name.isBlank()) {
            _formState.update { it.copy(nameError = "Category name is required") }
            isValid = false
        }

        if (_formState.value.icon.isBlank()) {
            _formState.update { it.copy(iconError = "Please select an icon") }
            isValid = false
        }

        if (_formState.value.color.isBlank()) {
            _formState.update { it.copy(colorError = "Please select a color") }
            isValid = false
        }

        return isValid
    }
}
