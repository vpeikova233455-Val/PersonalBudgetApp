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

data class MonthlyBucket(val year: Int, val month: Int, val label: String, val income: Double, val expense: Double)

enum class DashboardTypeFilter { ALL, INCOME, EXPENSE }

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(
        val data: DashboardData,
        val pendingCount: Int = 0,
        val prevMonthIncome: Double = 0.0,
        val prevMonthExpense: Double = 0.0,
        val monthlyTrend: List<MonthlyBucket> = emptyList(),
        val typeFilter: DashboardTypeFilter = DashboardTypeFilter.ALL,
        val customRange: Pair<Long, Long>? = null,    // (startMs, endMsExclusive) — overrides month
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

    // Type filter: scope the dashboard to a single transaction type (income or expense).
    // Driven by the chip row below the BalanceCard.
    private val _typeFilter = MutableStateFlow(DashboardTypeFilter.ALL)

    // Custom date range. When non-null, the dashboard ignores the month selector and
    // scopes every card to this window. When null, the dashboard runs in monthly mode.
    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)

    fun setTypeFilter(f: DashboardTypeFilter) {
        _typeFilter.value = f
        AppLogger.d(TAG, "Type filter set to $f")
    }

    fun setCustomRange(startMs: Long, endMsExclusive: Long) {
        _customRange.value = startMs to endMsExclusive
        AppLogger.d(TAG, "Custom range set: $startMs..$endMsExclusive")
    }

    fun clearCustomRange() {
        _customRange.value = null
        AppLogger.d(TAG, "Custom range cleared — back to monthly view")
    }

    init {
        repairAncientDates()
        jumpToLatestTransactionMonth()
        dumpIncomeDiagnostics()
        observeDashboard()
    }

    /**
     * One-shot repair for transactions stored with year < 1990 (typically caused by
     * the parseDate yyyy-vs-yy bug: "01/05/26" parsed as year 26 AD instead of 2026).
     * Adds 2000 calendar years to bring those dates into the user's actual era. Safe
     * to run on every startup — if nothing is broken, it's a no-op.
     */
    private fun repairAncientDates() {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                // Year 1990 boundary as ms since epoch (UTC). Anything before that is
                // almost certainly corrupted, not a real historical transaction.
                val cutoff = -2208988800000L  // 1900-01-01 UTC
                val broken = transactionDao.getTransactionsWithDateBefore(userId, cutoff)
                if (broken.isEmpty()) return@launch
                AppLogger.w(TAG, "[Repair] Found ${broken.size} transactions with year < 1900 — adding 2000 years")
                broken.forEach { tx ->
                    val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    cal.add(Calendar.YEAR, 2000)
                    AppLogger.d(TAG, "[Repair] ${tx.id} '${tx.description}' ${tx.date} → ${cal.timeInMillis}")
                    transactionDao.updateDateById(tx.id, cal.timeInMillis)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Date repair failed", e)
            }
        }
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

        val allTxs = transactionRepository.getAllTransactions(userId)

        // Trigger on (month, customRange, typeFilter). Type filter is applied in-memory
        // inside the use case via the combine below; date range is determined by either
        // the selected month or the custom range.
        kotlinx.coroutines.flow.combine(
            _selectedYearMonth,
            _customRange,
            _typeFilter
        ) { ym, range, type -> Quadruple(ym.first, ym.second, range, type) }
            .flatMapLatest { (year, month, range, type) ->
                combine(
                    getDashboardDataUseCase(userId, year, month, allTime = false, customRange = range, typeFilter = type.toEntity()),
                    pendingTransactionDao.getPendingCount(userId),
                    allTxs
                ) { data, pending, txs ->
                    val (prevIncome, prevExpense) = if (range == null) previousMonthTotals(txs, year, month) else 0.0 to 0.0
                    val trend = if (range == null) lastNMonthsTrend(txs, year, month, 6) else emptyList()

                    val isRefreshing = (_uiState.value as? DashboardUiState.Success)?.isRefreshing ?: false
                    AppLogger.d(TAG, "Dashboard $year/$month type=$type range=$range: pending=$pending income=${data.totalIncome} expense=${data.totalExpenses} | prev=$prevIncome/$prevExpense | trendBuckets=${trend.size}")

                    DashboardUiState.Success(
                        data,
                        pendingCount = pending,
                        prevMonthIncome = prevIncome,
                        prevMonthExpense = prevExpense,
                        monthlyTrend = trend,
                        typeFilter = type,
                        customRange = range,
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

    private fun previousMonthTotals(
        all: List<com.budgetapp.domain.model.Transaction>,
        year: Int, month: Int
    ): Pair<Double, Double> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -1)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        val prev = all.filter { it.date in start until end }
        val income = prev.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = prev.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        return income to expense
    }

    private fun lastNMonthsTrend(
        all: List<com.budgetapp.domain.model.Transaction>,
        year: Int, month: Int, n: Int
    ): List<MonthlyBucket> {
        val buckets = mutableListOf<MonthlyBucket>()
        for (i in (n - 1) downTo 0) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year); set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -i)
            }
            val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis
            val txs = all.filter { it.date in start until end }
            val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val label = SimpleDateFormat("MMM", Locale.ENGLISH).format(java.util.Date(start))
            buckets += MonthlyBucket(y, m, label, income, expense)
        }
        return buckets
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
        val range = _customRange.value
        if (range != null) {
            val (start, end) = range
            val startCal = Calendar.getInstance().apply { timeInMillis = start }
            // endMs is exclusive; subtract a day for the display end-date
            val endCal = Calendar.getInstance().apply { timeInMillis = end - 1 }
            val sameYear = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)
            val fmtShort = SimpleDateFormat(if (sameYear) "MMM d" else "MMM d, yyyy", Locale.ENGLISH)
            val fmtLong  = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
            return "${fmtShort.format(startCal.time)} – ${fmtLong.format(endCal.time)}"
        }
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

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun DashboardTypeFilter.toEntity(): TransactionType? = when (this) {
    DashboardTypeFilter.INCOME  -> TransactionType.INCOME
    DashboardTypeFilter.EXPENSE -> TransactionType.EXPENSE
    DashboardTypeFilter.ALL     -> null
}
