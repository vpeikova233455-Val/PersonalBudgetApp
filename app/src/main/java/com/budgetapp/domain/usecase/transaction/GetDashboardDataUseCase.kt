package com.budgetapp.domain.usecase.transaction

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
    operator fun invoke(userId: String, year: Int, month: Int): Flow<DashboardData> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = start.timeInMillis
        val monthEnd = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis

        // Single-flow approach: all three numbers (income, expenses, balance) are derived
        // from the same list emission so they are always in sync with each other and with
        // the transaction list shown on screen.
        return transactionRepository.getTransactionsByDateRange(userId, monthStart, monthEnd)
            .map { transactions ->
                val totalIncome = transactions
                    .filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amount }
                val totalExpenses = transactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }
                val categoryBreakdown = transactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.category }
                    .mapValues { (_, txns) -> txns.sumOf { it.amount } }

                DashboardData(
                    totalIncome = totalIncome,
                    totalExpenses = totalExpenses,
                    balance = totalIncome - totalExpenses,
                    recentTransactions = transactions,
                    categoryBreakdown = categoryBreakdown
                )
            }
    }
}
