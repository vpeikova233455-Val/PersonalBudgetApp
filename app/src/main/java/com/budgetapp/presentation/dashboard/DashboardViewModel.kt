package com.budgetapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.local.database.dao.PendingTransactionDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.TransactionRepository
import com.budgetapp.domain.usecase.transaction.GetDashboardDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private const val TAG = "DashboardViewModel"

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(
        val data: DashboardData,
        val pendingCount: Int = 0,
        val allTimeIncome: Double = 0.0,
        val allTimeExpense: Double = 0.0,
        val isRefreshing: Boolean = false
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val transactionRepository: TransactionRepository,
    private val pendingTransactionDao: PendingTransactionDao,
    private val transactionDao: TransactionDao
) : ViewModel() {

    private val userId: String = runBlocking { authRepository.getCurrentUserId() } ?: ""

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val now = Calendar.getInstance()
    private val _selectedYearMonth = MutableStateFlow(
        Pair(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    )
    val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

    init {
        jumpToLatestTransactionMonth()
        dumpIncomeDiagnostics()
        observeDashboard()
    }

    /**
     * Dumps every income transaction with its date to the log so bug reports can pinpoint
     * exactly where income transactions live. Without this, an "income exists but doesn't
     * show on dashboard" report is impossible to diagnose because we can't see the stored
     * date values.
     */
    private fun dumpIncomeDiagnostics() {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                val income = transactionDao.getAllByTypeForDebug(userId, TransactionType.INCOME)
                val expense = transactionDao.getAllByTypeForDebug(userId, TransactionType.EXPENSE)
                AppLogger.d(TAG, "[Diag] DB has ${income.size} income, ${expense.size} expense transactions")
                if (income.isNotEmpty()) {
                    val minDate = income.minOf { it.date }
                    val maxDate = income.maxOf { it.date }
                    AppLogger.d(TAG, "[Diag] Income date range: $minDate .. $maxDate")
                    income.take(20).forEach { tx ->
                        AppLogger.d(TAG, "[Diag] INCOME date=${tx.date} amount=${tx.amount} desc='${tx.description}'")
                    }
                    if (income.size > 20) AppLogger.d(TAG, "[Diag] (...and ${income.size - 20} more income rows)")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Diagnostic dump failed", e)
            }
        }
    }

    /**
     * On startup, if the user has transactions in a past month (typical after importing
     * an older bank statement), default the dashboard to that month — otherwise the
     * current-month view shows empty and users think their import was lost.
     */
    private fun jumpToLatestTransactionMonth() {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                val latest = transactionRepository.getLatestTransactionDate(userId)
                if (latest != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = latest }
                    val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
                    AppLogger.d(TAG, "Latest transaction date=$latest → year=$y month=$m " +
                        "(current: ${now.get(Calendar.YEAR)}/${now.get(Calendar.MONTH)})")
                    _selectedYearMonth.value = Pair(y, m)
                } else {
                    AppLogger.d(TAG, "No transactions yet — staying on current month")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to query latest transaction date", e)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard() {
        if (userId.isEmpty()) {
            _uiState.value = DashboardUiState.Error("User not logged in")
            return
        }

        val incomeAllTime  = transactionDao.getAllTimeTotalByType(userId, TransactionType.INCOME).map { it ?: 0.0 }
        val expenseAllTime = transactionDao.getAllTimeTotalByType(userId, TransactionType.EXPENSE).map { it ?: 0.0 }

        _selectedYearMonth
            .flatMapLatest { (year, month) ->
                combine(
                    getDashboardDataUseCase(userId, year, month),
                    pendingTransactionDao.getPendingCount(userId),
                    incomeAllTime,
                    expenseAllTime
                ) { data, pending, allIncome, allExpense ->
                    val isRefreshing =
                        (_uiState.value as? DashboardUiState.Success)?.isRefreshing ?: false
                    AppLogger.d(TAG, "Dashboard $year/$month: pending=$pending income=${data.totalIncome} expense=${data.totalExpenses} | allTime income=$allIncome expense=$allExpense")
                    if (allIncome > 0 && data.totalIncome == 0.0) {
                        AppLogger.w(TAG, "All-time income=$allIncome but this month=0 — income transactions exist with dates outside the current month range")
                    }
                    DashboardUiState.Success(
                        data, pendingCount = pending,
                        allTimeIncome = allIncome, allTimeExpense = allExpense,
                        isRefreshing = isRefreshing
                    ) as DashboardUiState
                }
                    .catch { e ->
                        AppLogger.e(TAG, "Dashboard data error", e)
                        emit(DashboardUiState.Error(e.message ?: "Failed to load dashboard"))
                    }
            }
            .onEach { state -> _uiState.value = state }
            .catch { e ->
                AppLogger.e(TAG, "Dashboard observer error", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load dashboard")
            }
            .launchIn(viewModelScope)
    }

    fun previousMonth() {
        val (year, month) = _selectedYearMonth.value
        _selectedYearMonth.value = if (month == 0) Pair(year - 1, 11) else Pair(year, month - 1)
    }

    fun nextMonth() {
        val (year, month) = _selectedYearMonth.value
        _selectedYearMonth.value = if (month == 11) Pair(year + 1, 0) else Pair(year, month + 1)
    }

    fun selectedMonthLabel(): String {
        val (year, month) = _selectedYearMonth.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "Refreshing dashboard")
                val current = _uiState.value
                if (current is DashboardUiState.Success) {
                    _uiState.value = current.copy(isRefreshing = true)
                }
                syncRepository.syncAll()
                delay(1000)
                val updated = _uiState.value
                if (updated is DashboardUiState.Success) {
                    _uiState.value = updated.copy(isRefreshing = false)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Dashboard refresh failed", e)
                val updated = _uiState.value
                if (updated is DashboardUiState.Success) {
                    _uiState.value = updated.copy(isRefreshing = false)
                }
            }
        }
    }

    // Called from the Error state Retry button — restarts the data observer.
    fun retry() {
        _uiState.value = DashboardUiState.Loading
        observeDashboard()
    }
}
