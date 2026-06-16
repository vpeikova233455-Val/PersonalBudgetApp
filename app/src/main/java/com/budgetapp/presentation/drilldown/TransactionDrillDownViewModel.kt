package com.budgetapp.presentation.drilldown

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.TransactionRepository
import com.budgetapp.domain.usecase.transaction.TransactionAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class DrillGrouping { CATEGORY, MONTH }

data class DrillDownUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    val grouping: DrillGrouping = DrillGrouping.CATEGORY,
    val total: Double = 0.0,
    val count: Int = 0,
    val searchQuery: String = "",
    val groups: List<DrillGroupUi> = emptyList()
)

data class DrillGroupUi(
    val label: String,
    val subtotal: Double,
    val transactions: List<Transaction>,
    val expanded: Boolean = false
)

@HiltViewModel
class TransactionDrillDownViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    // Filter spec carried via nav args. Each arg has a sentinel default so missing args don't break parsing.
    private val typeArg: String   = savedStateHandle["type"]      ?: "ALL"        // INCOME | EXPENSE | ALL
    private val yearArg: Int      = savedStateHandle["year"]      ?: -1           // -1 means "any year"
    private val monthArg: Int     = savedStateHandle["month"]     ?: -1           // 0-based, -1 means "any month"
    private val allTimeArg: Boolean = savedStateHandle["allTime"] ?: false
    private val categoryIdArg: Long = savedStateHandle["categoryId"] ?: -1L
    private val descPatternArg: String = savedStateHandle["descPattern"] ?: ""
    private val titleArg: String  = savedStateHandle["title"]     ?: "Transactions"

    private val userId: String = runBlocking { authRepository.getCurrentUserId() } ?: ""

    private val _ui = MutableStateFlow(DrillDownUiState(title = titleArg))
    val uiState: StateFlow<DrillDownUiState> = _ui.asStateFlow()

    init { load() }

    private fun load() {
        if (userId.isEmpty()) {
            _ui.value = _ui.value.copy(isLoading = false)
            return
        }
        viewModelScope.launch {
            val type = when (typeArg) { "INCOME" -> TransactionType.INCOME; "EXPENSE" -> TransactionType.EXPENSE; else -> null }
            // Use the existing all-transactions flow then filter in memory — simpler than
            // composing multiple Room queries for every filter combination, and the dataset
            // here is bounded by the user's history (max a few thousand rows).
            val all = transactionRepository.getAllTransactions(userId).first()
            AppLogger.d(TAG, "Drill-down loading: type=$typeArg year=$yearArg month=$monthArg allTime=$allTimeArg categoryId=$categoryIdArg descPattern='$descPatternArg' total=${all.size}")

            val (rangeStart, rangeEnd) = monthBounds(yearArg, monthArg, allTimeArg)
            val descNorm = if (descPatternArg.isNotEmpty()) TransactionAnalytics.normalizeDescription(descPatternArg) else ""

            val filtered = all.filter { tx ->
                (type == null || tx.type == type) &&
                (allTimeArg || (tx.date in rangeStart until rangeEnd)) &&
                (categoryIdArg == -1L || tx.category.id == categoryIdArg) &&
                (descNorm.isEmpty() || TransactionAnalytics.normalizeDescription(tx.description) == descNorm)
            }
            AppLogger.d(TAG, "Drill-down filtered ${all.size} → ${filtered.size}")

            updateState(filtered)
        }
    }

    fun toggleExpanded(groupLabel: String) {
        _ui.update { st ->
            st.copy(groups = st.groups.map { g ->
                if (g.label == groupLabel) g.copy(expanded = !g.expanded) else g
            })
        }
    }

    fun setGrouping(g: DrillGrouping) {
        viewModelScope.launch {
            val all = transactionRepository.getAllTransactions(userId).first()
            val type = when (typeArg) { "INCOME" -> TransactionType.INCOME; "EXPENSE" -> TransactionType.EXPENSE; else -> null }
            val (rangeStart, rangeEnd) = monthBounds(yearArg, monthArg, allTimeArg)
            val descNorm = if (descPatternArg.isNotEmpty()) TransactionAnalytics.normalizeDescription(descPatternArg) else ""
            val filtered = all.filter { tx ->
                (type == null || tx.type == type) &&
                (allTimeArg || (tx.date in rangeStart until rangeEnd)) &&
                (categoryIdArg == -1L || tx.category.id == categoryIdArg) &&
                (descNorm.isEmpty() || TransactionAnalytics.normalizeDescription(tx.description) == descNorm)
            }
            _ui.value = _ui.value.copy(grouping = g)
            updateState(filtered)
        }
    }

    fun setSearchQuery(q: String) {
        _ui.value = _ui.value.copy(searchQuery = q)
        viewModelScope.launch {
            val all = transactionRepository.getAllTransactions(userId).first()
            val type = when (typeArg) { "INCOME" -> TransactionType.INCOME; "EXPENSE" -> TransactionType.EXPENSE; else -> null }
            val (rangeStart, rangeEnd) = monthBounds(yearArg, monthArg, allTimeArg)
            val descNorm = if (descPatternArg.isNotEmpty()) TransactionAnalytics.normalizeDescription(descPatternArg) else ""
            val filtered = all.filter { tx ->
                (type == null || tx.type == type) &&
                (allTimeArg || (tx.date in rangeStart until rangeEnd)) &&
                (categoryIdArg == -1L || tx.category.id == categoryIdArg) &&
                (descNorm.isEmpty() || TransactionAnalytics.normalizeDescription(tx.description) == descNorm) &&
                (q.isBlank() || tx.description.contains(q, ignoreCase = true) || tx.category.name.contains(q, ignoreCase = true))
            }
            updateState(filtered)
        }
    }

    private fun updateState(filtered: List<Transaction>) {
        val grouping = _ui.value.grouping
        val groups = when (grouping) {
            DrillGrouping.CATEGORY -> filtered
                .groupBy { it.category }
                .entries
                .sortedByDescending { it.value.sumOf { tx -> tx.amount } }
                .map { (cat, txs) ->
                    DrillGroupUi(
                        label = "${cat.icon} ${cat.name}",
                        subtotal = txs.sumOf { it.amount },
                        transactions = txs.sortedByDescending { it.date }
                    )
                }
            DrillGrouping.MONTH -> filtered
                .groupBy { monthLabel(it.date) }
                .entries
                .sortedByDescending { it.value.firstOrNull()?.date ?: 0L }
                .map { (label, txs) ->
                    DrillGroupUi(
                        label = label,
                        subtotal = txs.sumOf { it.amount },
                        transactions = txs.sortedByDescending { it.date }
                    )
                }
        }
        // Preserve expansion state across re-loads when the label is unchanged.
        val previousExpanded = _ui.value.groups.filter { it.expanded }.map { it.label }.toSet()
        val groupsWithExpansion = groups.map { it.copy(expanded = it.label in previousExpanded || groups.size <= 3) }

        _ui.value = _ui.value.copy(
            isLoading = false,
            total = filtered.sumOf { it.amount },
            count = filtered.size,
            groups = groupsWithExpansion
        )
    }

    private fun monthBounds(year: Int, month: Int, allTime: Boolean): Pair<Long, Long> {
        if (allTime || year < 0 || month < 0) return Long.MIN_VALUE / 2 to Long.MAX_VALUE / 2
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    private fun monthLabel(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) { value = block(value) }

    companion object { private const val TAG = "DrillDown" }
}
