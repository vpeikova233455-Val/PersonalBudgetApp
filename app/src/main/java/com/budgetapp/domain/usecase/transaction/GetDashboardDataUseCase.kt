package com.budgetapp.domain.usecase.transaction

import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class GetDashboardDataUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(userId: String, year: Int, month: Int, allTime: Boolean = false): Flow<DashboardData> {
        val monthStart: Long
        val monthEnd: Long
        if (allTime) {
            // Half the long range to keep arithmetic safe; covers any real timestamp.
            monthStart = Long.MIN_VALUE / 2
            monthEnd   = Long.MAX_VALUE / 2
        } else {
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

        AppLogger.d(TAG, "Querying dashboard data for year=$year month=$month allTime=$allTime [$monthStart..$monthEnd)")

        return transactionRepository.getTransactionsByDateRange(userId, monthStart, monthEnd)
            .map { transactions ->
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

                DashboardData(
                    totalIncome = totalIncome,
                    totalExpenses = totalExpenses,
                    balance = totalIncome - totalExpenses,
                    recentTransactions = transactions,
                    categoryBreakdown = categoryBreakdown,
                    incomeBreakdown = incomeBreakdown,
                    transactionCount = transactions.size
                )
            }
    }

    companion object { private const val TAG = "DashboardData" }
}
