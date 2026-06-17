package com.budgetapp.domain.usecase.transaction

import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class GetDashboardDataUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(
        userId: String,
        year: Int,
        month: Int,
        allTime: Boolean = false,
        customRange: Pair<Long, Long>? = null,
        typeFilter: TransactionType? = null
    ): Flow<DashboardData> {
        val monthStart: Long
        val monthEnd: Long
        when {
            customRange != null -> {
                monthStart = customRange.first
                monthEnd   = customRange.second
            }
            allTime -> {
                monthStart = Long.MIN_VALUE / 2
                monthEnd   = Long.MAX_VALUE / 2
            }
            else -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                monthStart = start.timeInMillis
                monthEnd = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
            }
        }

        AppLogger.d(TAG, "Querying dashboard data for year=$year month=$month allTime=$allTime customRange=$customRange typeFilter=$typeFilter [$monthStart..$monthEnd)")

        // Recurring detection needs the full transaction history, not just the selected
        // month — a "monthly" pattern by definition appears across multiple months.
        return transactionRepository.getTransactionsByDateRange(userId, monthStart, monthEnd)
            .combine(transactionRepository.getAllTransactions(userId)) { monthTxs, allTxs -> monthTxs to allTxs }
            .map { (rawTxs, allTxs) ->
                // Type filter applied AFTER fetching: BalanceCard always shows both income
                // and expense totals as reference, while breakdowns scope to the filter.
                val transactions = if (typeFilter != null) rawTxs.filter { it.type == typeFilter } else rawTxs
                val incomeList   = transactions.filter { it.type == TransactionType.INCOME }
                val expenseList  = transactions.filter { it.type == TransactionType.EXPENSE }
                val totalIncome  = incomeList.sumOf  { it.amount }
                val totalExpenses = expenseList.sumOf { it.amount }
                val categoryBreakdown = expenseList
                    .groupBy { it.category }
                    .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                val incomeBreakdown = incomeList
                    .groupBy { it.category }
                    .mapValues { (_, txns) -> txns.sumOf { it.amount } }

                AppLogger.d(TAG, "Dashboard $year/$month: " +
                    "total=${transactions.size} income=${incomeList.size} ($totalIncome) " +
                    "expense=${expenseList.size} ($totalExpenses)")
                if (transactions.isNotEmpty() && incomeList.isEmpty()) {
                    AppLogger.w(TAG, "Month $year/$month has ${transactions.size} transactions but ZERO income — types: " +
                        transactions.joinToString(",") { "${it.type}:${it.amount}" }.take(500))
                }

                // Recurring detection over full history; surface only patterns with ≥2
                // occurrences so we don't show one-off transactions as "recurring".
                val patterns = TransactionAnalytics.detectRecurring(allTxs)
                val recurringIncome = patterns.filter { it.type == TransactionType.INCOME }
                val recurringExpenses = patterns.filter { it.type == TransactionType.EXPENSE }
                // Expected this month: patterns whose nextExpectedDate falls within the
                // currently-selected month window. Skip in all-time mode.
                val expectedThisMonth = if (allTime) emptyList()
                    else patterns.filter { it.nextExpectedDate in monthStart until monthEnd }

                DashboardData(
                    totalIncome = totalIncome,
                    totalExpenses = totalExpenses,
                    balance = totalIncome - totalExpenses,
                    recentTransactions = transactions,
                    categoryBreakdown = categoryBreakdown,
                    incomeBreakdown = incomeBreakdown,
                    transactionCount = transactions.size,
                    recurringIncome = recurringIncome,
                    recurringExpenses = recurringExpenses,
                    expectedThisMonth = expectedThisMonth
                )
            }
    }

    companion object { private const val TAG = "DashboardData" }
}
