package com.budgetapp.presentation.import

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
                    _uiState.update {
                        it.copy(
                            pendingTransactions = pending.map { entity ->
                                PendingTransactionUiModel(
                                    id = entity.id,
                                    description = entity.description ?: "Unknown",
                                    formattedAmount = formatAmount(entity.amount ?: 0.0),
                                    formattedDate = entity.date?.let { formatDate(it) },
                                    type = entity.type?.name ?: "EXPENSE",
                                    suggestedCategory = null,
                                    confidence = entity.categoryConfidence ?: 0.5,
                                    sourceType = entity.sourceType.name,
                                    aiQuestions = entity.aiQuestions?.let { parseQuestions(it) }
                                )
                            },
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryDao.getAllCategories()
                .collect { categories ->
                    _uiState.update {
                        it.copy(
                            categories = categories.map { cat ->
                                CategoryUiModel(id = cat.id, name = cat.name)
                            }
                        )
                    }
                }
        }
    }

    fun approvePendingTransaction(pendingId: Long) {
        viewModelScope.launch {
            val pending = pendingTransactionDao.getPendingById(pendingId) ?: return@launch

            // Create actual transaction
            val transaction = TransactionEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = pending.type ?: com.budgetapp.data.local.entity.TransactionType.EXPENSE,
                amount = pending.amount ?: 0.0,
                description = pending.description ?: "",
                categoryId = pending.suggestedCategory ?: 1L,
                date = pending.date ?: System.currentTimeMillis(),
                isRecurring = false,
                recurringId = null,
                syncStatus = com.budgetapp.data.local.entity.SyncStatus.PENDING,
                lastModifiedTimestamp = System.currentTimeMillis(),
                deviceId = UUID.randomUUID().toString(),
                firestoreId = null
            )

            transactionDao.insertTransaction(transaction)

            // Learn from user's approval
            if (pending.suggestedCategory != null) {
                learnFromUserUseCase(
                    userId = userId,
                    description = pending.description ?: "",
                    selectedCategoryId = pending.suggestedCategory
                )
            }

            // Delete pending
            pendingTransactionDao.deletePendingById(pendingId)

            // Check if all approved
            if (_uiState.value.pendingTransactions.size == 1) {
                _uiState.update { it.copy(allApproved = true) }
            }
        }
    }

    fun deletePendingTransaction(pendingId: Long) {
        viewModelScope.launch {
            pendingTransactionDao.deletePendingById(pendingId)
        }
    }

    fun startEditingTransaction(pendingId: Long) {
        // TODO: Navigate to edit screen
    }

    private fun formatAmount(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.US)
        return formatter.format(amount)
    }

    private fun formatDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        return formatter.format(Date(timestamp))
    }

    private fun parseQuestions(json: String): List<String>? {
        return try {
            // Simple JSON parsing - in production use kotlinx.serialization
            json.trim('[', ']')
                .split(",")
                .map { it.trim('"', ' ') }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

data class ReviewTransactionsUiState(
    val pendingTransactions: List<PendingTransactionUiModel> = emptyList(),
    val categories: List<CategoryUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val allApproved: Boolean = false
)
