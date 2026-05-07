package com.budgetapp.domain.usecase.transaction

import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val monthEnd = end.timeInMillis

        val incomeFlow = transactionRepository.getTotalByType(userId, TransactionType.INCOME, monthStart, monthEnd)
        val expensesFlow = transactionRepository.getTotalByType(userId, TransactionType.EXPENSE, monthStart, monthEnd)
        val transactionsFlow = transactionRepository.getTransactionsByDateRange(userId, monthStart, monthEnd)

        return combine(incomeFlow, expensesFlow, transactionsFlow) { income, expenses, transactions ->
            val totalIncome = income
            val totalExpenses = expenses

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
