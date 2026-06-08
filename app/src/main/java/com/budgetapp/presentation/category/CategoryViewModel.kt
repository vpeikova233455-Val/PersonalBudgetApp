package com.budgetapp.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.CategoryRepository
import com.budgetapp.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeleteTransactionOption { REASSIGN, DELETE_ALL }

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // Add / edit dialog
    val editingCategory: Category? = null,
    val showEditDialog: Boolean = false,
    // Delete dialog
    val deletingCategory: Category? = null,
    val showDeleteDialog: Boolean = false,
    val deleteOption: DeleteTransactionOption = DeleteTransactionOption.REASSIGN,
    val reassignTargetId: Long? = null,
    // Merge dialog
    val mergingFrom: Category? = null,
    val mergeTargetId: Long? = null,
    val showMergeDialog: Boolean = false
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats, isLoading = false) }
            }
        }
    }

    // ── Edit / Add ────────────────────────────────────────────────────────────

    fun openAddDialog() {
        _uiState.update { it.copy(editingCategory = null, showEditDialog = true) }
    }

    fun openEditDialog(category: Category) {
        _uiState.update { it.copy(editingCategory = category, showEditDialog = true) }
    }

    fun closeEditDialog() {
        _uiState.update { it.copy(editingCategory = null, showEditDialog = false) }
    }

    fun saveCategory(name: String, icon: String, color: String) {
        val editing = _uiState.value.editingCategory
        val nextOrder = _uiState.value.categories.size
        viewModelScope.launch {
            try {
                val category = Category(
                    id           = editing?.id ?: 0L,
                    name         = name.trim(),
                    icon         = icon,
                    color        = color,
                    isCustom     = editing?.isCustom ?: true,
                    displayOrder = editing?.displayOrder ?: nextOrder
                )
                if (editing == null) categoryRepository.insertCategory(category)
                else                 categoryRepository.updateCategory(category)
                closeEditDialog()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Reorder ───────────────────────────────────────────────────────────────

    fun moveCategory(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.categories.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        current.add(toIndex, current.removeAt(fromIndex))
        _uiState.update { it.copy(categories = current) }
    }

    fun commitReorder() {
        val orderedIds = _uiState.value.categories.map { it.id }
        viewModelScope.launch { categoryRepository.reorderCategories(orderedIds) }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun openDeleteDialog(category: Category) {
        val defaultTarget = _uiState.value.categories.firstOrNull { it.id != category.id }
        _uiState.update {
            it.copy(
                deletingCategory  = category,
                showDeleteDialog  = true,
                deleteOption      = DeleteTransactionOption.REASSIGN,
                reassignTargetId  = defaultTarget?.id
            )
        }
    }

    fun setDeleteOption(option: DeleteTransactionOption) {
        _uiState.update { it.copy(deleteOption = option) }
    }

    fun setReassignTarget(categoryId: Long) {
        _uiState.update { it.copy(reassignTargetId = categoryId) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val category = state.deletingCategory ?: return
        viewModelScope.launch {
            try {
                when (state.deleteOption) {
                    DeleteTransactionOption.REASSIGN -> {
                        val targetId = state.reassignTargetId ?: return@launch
                        transactionRepository.reassignCategory(category.id, targetId)
                    }
                    DeleteTransactionOption.DELETE_ALL -> {
                        transactionRepository.deleteTransactionsByCategory(category.id)
                    }
                }
                categoryRepository.deleteCategory(category)
                _uiState.update { it.copy(deletingCategory = null, showDeleteDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deletingCategory = null, showDeleteDialog = false) }
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    fun openMergeDialog(category: Category) {
        val defaultTarget = _uiState.value.categories.firstOrNull { it.id != category.id }
        _uiState.update {
            it.copy(mergingFrom = category, showMergeDialog = true, mergeTargetId = defaultTarget?.id)
        }
    }

    fun setMergeTarget(categoryId: Long) {
        _uiState.update { it.copy(mergeTargetId = categoryId) }
    }

    fun confirmMerge() {
        val state = _uiState.value
        val from = state.mergingFrom ?: return
        val toId = state.mergeTargetId ?: return
        viewModelScope.launch {
            try {
                transactionRepository.reassignCategory(from.id, toId)
                categoryRepository.deleteCategory(from)
                _uiState.update { it.copy(mergingFrom = null, mergeTargetId = null, showMergeDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissMergeDialog() {
        _uiState.update { it.copy(mergingFrom = null, mergeTargetId = null, showMergeDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
