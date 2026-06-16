package com.budgetapp.presentation.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.PendingTransactionDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.CategoryEntity
import com.budgetapp.data.local.entity.SyncStatus
import com.budgetapp.data.local.entity.TransactionEntity
import com.budgetapp.data.mapper.toDomain
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.core.util.AppLogger
import com.budgetapp.domain.usecase.ai.LearnFromUserUseCase
import com.budgetapp.domain.usecase.transaction.TransactionAnalytics
import kotlinx.coroutines.flow.first
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

    private suspend fun computeDuplicateCandidates(
        pending: List<com.budgetapp.data.local.entity.PendingTransactionEntity>
    ): Map<Long, TransactionAnalytics.DuplicateCandidate> {
        if (pending.isEmpty()) return emptyMap()
        val existing = try {
            transactionDao.getAllTransactions(userId).first().map { entity ->
                val cat = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                    ?: com.budgetapp.domain.model.Category(
                        id = entity.categoryId, name = "Other", icon = "📋", color = "#607D8B"
                    )
                entity.toDomain(cat)
            }
        } catch (e: Exception) {
            AppLogger.e("ReviewTransactions", "Failed to load existing transactions for dup check", e)
            return emptyMap()
        }
        val projections = pending.map { p ->
            TransactionAnalytics.PendingForDup(
                id = p.id, description = p.description, amount = p.amount, date = p.date, type = p.type
            )
        }
        return TransactionAnalytics.detectDuplicates(projections, existing)
            .associateBy { it.pendingId }
    }

    private fun loadPendingTransactions() {
        viewModelScope.launch {
            pendingTransactionDao.getAllPending(userId)
                .collect { pending ->
                    val dupMap = computeDuplicateCandidates(pending)
                    val models = pending.map { entity ->
                        // Stage 6 — type read directly from DB; no history lookup modifies it
                        AppLogger.d("ReviewTransactions", "[Stage6-DBRead] desc='${entity.description}' | entityType=${entity.type} | amount=${entity.amount}")
                        if (entity.type == null) {
                            AppLogger.e("ReviewTransactions", "[Stage6-NullType] CRITICAL: type is NULL for '${entity.description}' — DB stored null, will show as EXPENSE")
                        }

                        // Stage 4/5 — learning rules / auto-categorization: affects CATEGORY ONLY, never type
                        val suggestion = learnFromUserUseCase.getSuggestion(userId, entity.description ?: "")
                        val suggestedCategoryName = suggestion?.let {
                            categoryDao.getCategoryById(it.categoryId)?.name
                        }
                        AppLogger.d("ReviewTransactions", "[Stage5-Learning] desc='${entity.description}' | suggestedCategory='$suggestedCategoryName' | TYPE UNCHANGED=${entity.type}")
                        val learningState = when {
                            suggestion == null -> LearningState.Unknown
                            suggestion.isAutomatic -> LearningState.Known(
                                suggestion.categoryId, suggestedCategoryName ?: "", suggestion.usageCount, isAutomatic = true
                            )
                            else -> LearningState.Known(
                                suggestion.categoryId, suggestedCategoryName ?: "", suggestion.usageCount, isAutomatic = false
                            )
                        }

                        // Stage 8 — final UI model; type comes ONLY from entity.type read above
                        val uiType = entity.type?.name ?: "EXPENSE"
                        AppLogger.d("ReviewTransactions", "[Stage8-UIModel] desc='${entity.description}' | uiType=$uiType | entityType=${entity.type}")
                        val dup = dupMap[entity.id]
                        PendingTransactionUiModel(
                            id = entity.id,
                            description = entity.description ?: "Unknown",
                            formattedAmount = formatAmount(entity.amount ?: 0.0),
                            formattedDate = entity.date?.let { formatDate(it) },
                            type = uiType,
                            suggestedCategoryId = suggestion?.categoryId,
                            suggestedCategoryName = suggestedCategoryName,
                            selectedCategoryId = if (suggestion?.isAutomatic == true) suggestion.categoryId
                                                 else suggestion?.categoryId,
                            selectedCategoryName = suggestedCategoryName,
                            confidence = entity.categoryConfidence ?: 0.5,
                            sourceType = entity.sourceType.name,
                            aiQuestions = entity.aiQuestions?.let { parseQuestions(it) },
                            learningState = learningState,
                            wantsAutomatic = false,
                            duplicateOf = dup?.existingDescription,
                            duplicateReasons = dup?.reasons ?: emptyList()
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
                        it.copy(categories = categories.map { cat ->
                            CategoryUiModel(id = cat.id, name = cat.name, icon = cat.icon)
                        })
                    }
                }
        }
    }

    fun createCategory(pendingId: Long, name: String, icon: String) {
        viewModelScope.launch {
            // Pick a color deterministically from the name so the same category always looks the same.
            val palette = listOf(
                "#607D8B", "#795548", "#9C27B0", "#3F51B5", "#009688",
                "#FF5722", "#FFC107", "#4CAF50", "#2196F3", "#E91E63"
            )
            val color = palette[Math.abs(name.hashCode()) % palette.size]
            val entity = CategoryEntity(
                name = name.trim(),
                icon = icon,
                color = color,
                isCustom = true,
                userId = userId,
                syncStatus = SyncStatus.PENDING,
                lastModifiedTimestamp = System.currentTimeMillis()
            )
            val newId = categoryDao.insertCategory(entity)
            // Auto-select the new category for this transaction so the user doesn't
            // have to tap it again in the picker.
            selectCategory(pendingId, newId, name.trim())
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

    fun updateNotes(pendingId: Long, notes: String) {
        _uiState.update { state ->
            state.copy(
                pendingTransactions = state.pendingTransactions.map { tx ->
                    if (tx.id == pendingId) tx.copy(notes = notes) else tx
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
                firestoreId = null,
                notes = uiModel.notes.ifBlank { null }
            )

            transactionDao.insertTransaction(transaction)
            AppLogger.d("ReviewTransactions", "[Stage9-Approved] id=${transaction.id} " +
                "desc='${transaction.description}' type=${transaction.type} amount=${transaction.amount} " +
                "date=${transaction.date} categoryId=${transaction.categoryId} (pending.type was ${pending.type})")
            if (pending.type != transaction.type) {
                AppLogger.e("ReviewTransactions", "[Stage9-TypeChanged] pending.type=${pending.type} → saved=${transaction.type} — INCOME/EXPENSE may have been lost!")
            }

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
    val wantsAutomatic: Boolean = false,
    val notes: String = "",
    val duplicateOf: String? = null,
    val duplicateReasons: List<String> = emptyList()
)

data class CategoryUiModel(
    val id: Long,
    val name: String,
    val icon: String = "📋"
)

data class ReviewTransactionsUiState(
    val pendingTransactions: List<PendingTransactionUiModel> = emptyList(),
    val categories: List<CategoryUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val allApproved: Boolean = false
)
