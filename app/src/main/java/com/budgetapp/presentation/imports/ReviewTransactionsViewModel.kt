package com.budgetapp.presentation.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.PendingTransactionDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.TransactionEntity
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.usecase.ai.LearnFromUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReviewTransactionsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val pendingTransactionDao: PendingTransactionDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val learnFromUserUseCase: LearnFromUserUseCase
) : ViewModel() {

    private val userId = runBlocking { authRepository.getCurrentUserId() } ?: ""

    private val _uiState = MutableStateFlow(ReviewTransactionsUiState())
    val uiState: StateFlow<ReviewTransactionsUiState> = _uiState.asStateFlow()

    init {
        loadPendingTransactions()
        loadCategories()
    }

    private fun loadPendingTransactions() {
        viewModelScope.launch {
            pendingTransactionDao.getAllPending(userId)
                .collect { pending ->
                    val models = pending.map { entity ->
                        val suggestion = learnFromUserUseCase.getSuggestion(userId, entity.description ?: "")
                        val suggestedCategoryName = suggestion?.let {
                            categoryDao.getCategoryById(it.categoryId)?.name
                        }
                        val learningState = when {
                            suggestion == null -> LearningState.Unknown
                            suggestion.isAutomatic -> LearningState.Known(
                                suggestion.categoryId, suggestedCategoryName ?: "", suggestion.usageCount, isAutomatic = true
                            )
                            else -> LearningState.Known(
                                suggestion.categoryId, suggestedCategoryName ?: "", suggestion.usageCount, isAutomatic = false
                            )
                        }
                        PendingTransactionUiModel(
                            id = entity.id,
                            description = entity.description ?: "Unknown",
                            formattedAmount = formatAmount(entity.amount ?: 0.0),
                            formattedDate = entity.date?.let { formatDate(it) },
                            type = entity.type?.name ?: "EXPENSE",
                            suggestedCategoryId = suggestion?.categoryId,
                            suggestedCategoryName = suggestedCategoryName,
                            selectedCategoryId = if (suggestion?.isAutomatic == true) suggestion.categoryId
                                                 else suggestion?.categoryId,
                            selectedCategoryName = suggestedCategoryName,
                            confidence = entity.categoryConfidence ?: 0.5,
                            sourceType = entity.sourceType.name,
                            aiQuestions = entity.aiQuestions?.let { parseQuestions(it) },
                            learningState = learningState,
                            wantsAutomatic = false
                        )
                    }
                    val wasLoading = _uiState.value.isLoading
                    val hadItems = _uiState.value.pendingTransactions.isNotEmpty()
                    _uiState.update { it.copy(pendingTransactions = models, isLoading = false) }
                    // Trigger done when list goes from non-empty → empty (i.e., all approved)
                    if (!wasLoading && hadItems && models.isEmpty()) {
                        _uiState.update { it.copy(allApproved = true) }
                    }
                }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryDao.getAllCategories()
                .collect { categories ->
                    _uiState.update {
                        it.copy(categories = categories.map { cat -> CategoryUiModel(id = cat.id, name = cat.name) })
                    }
                }
        }
    }

    fun selectCategory(pendingId: Long, categoryId: Long, categoryName: String) {
        _uiState.update { state ->
            state.copy(
                pendingTransactions = state.pendingTransactions.map { tx ->
                    if (tx.id == pendingId) tx.copy(selectedCategoryId = categoryId, selectedCategoryName = categoryName)
                    else tx
                }
            )
        }
    }

    fun toggleWantsAutomatic(pendingId: Long) {
        _uiState.update { state ->
            state.copy(
                pendingTransactions = state.pendingTransactions.map { tx ->
                    if (tx.id == pendingId) tx.copy(wantsAutomatic = !tx.wantsAutomatic)
                    else tx
                }
            )
        }
    }

    fun approvePendingTransaction(pendingId: Long) {
        viewModelScope.launch {
            val pending = pendingTransactionDao.getPendingById(pendingId) ?: return@launch
            val uiModel = _uiState.value.pendingTransactions.find { it.id == pendingId } ?: return@launch

            val categoryId = uiModel.selectedCategoryId ?: 1L
            val transaction = TransactionEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = pending.type ?: com.budgetapp.data.local.entity.TransactionType.EXPENSE,
                amount = pending.amount ?: 0.0,
                description = pending.description ?: "",
                categoryId = categoryId,
                date = pending.date ?: System.currentTimeMillis(),
                isRecurring = false,
                recurringId = null,
                syncStatus = com.budgetapp.data.local.entity.SyncStatus.PENDING,
                lastModifiedTimestamp = System.currentTimeMillis(),
                deviceId = UUID.randomUUID().toString(),
                firestoreId = null
            )

            transactionDao.insertTransaction(transaction)

            learnFromUserUseCase(
                userId = userId,
                description = pending.description ?: "",
                selectedCategoryId = categoryId,
                setAutomatic = uiModel.wantsAutomatic
            )

            pendingTransactionDao.deletePendingById(pendingId)
            // allApproved is triggered by loadPendingTransactions() when DB list becomes empty
        }
    }

    fun approveAll() {
        val ids = _uiState.value.pendingTransactions.map { it.id }
        ids.forEach { approvePendingTransaction(it) }
    }

    fun deletePendingTransaction(pendingId: Long) {
        viewModelScope.launch {
            pendingTransactionDao.deletePendingById(pendingId)
        }
    }

    private fun formatAmount(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance()
        formatter.currency = java.util.Currency.getInstance("ILS")
        return formatter.format(amount)
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(timestamp))

    private fun parseQuestions(json: String): List<String>? = try {
        json.trim('[', ']').split(",").map { it.trim('"', ' ') }.filter { it.isNotEmpty() }
    } catch (_: Exception) { null }
}

// ── Learning state ─────────────────────────────────────────────────────────────

sealed class LearningState {
    object Unknown : LearningState()
    data class Known(
        val categoryId: Long,
        val categoryName: String,
        val timesSeenBefore: Int,
        val isAutomatic: Boolean
    ) : LearningState()
}

// ── UI models ──────────────────────────────────────────────────────────────────

data class PendingTransactionUiModel(
    val id: Long,
    val description: String,
    val formattedAmount: String,
    val formattedDate: String?,
    val type: String,
    val suggestedCategoryId: Long?,
    val suggestedCategoryName: String?,
    val selectedCategoryId: Long?,
    val selectedCategoryName: String?,
    val confidence: Double,
    val sourceType: String,
    val aiQuestions: List<String>?,
    val learningState: LearningState,
    val wantsAutomatic: Boolean = false
)

data class CategoryUiModel(
    val id: Long,
    val name: String
)

data class ReviewTransactionsUiState(
    val pendingTransactions: List<PendingTransactionUiModel> = emptyList(),
    val categories: List<CategoryUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val allApproved: Boolean = false
)
