package com.budgetapp.domain.usecase.transaction

import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject

class GetDashboardDataUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(userId: String): Flow<DashboardData> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val monthStart = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val monthEnd = calendar.timeInMillis

        val incomeFlow = transactionRepository.getTotalByType(userId, TransactionType.INCOME, monthStart, monthEnd)
        val expensesFlow = transactionRepository.getTotalByType(userId, TransactionType.EXPENSE, monthStart, monthEnd)
        val transactionsFlow = transactionRepository.getAllTransactions(userId)

        return combine(incomeFlow, expensesFlow, transactionsFlow) { income, expenses, transactions ->
            val totalIncome = income ?: 0.0
            val totalExpenses = expenses ?: 0.0
            val recentTransactions = transactions.take(10)

            val categoryBreakdown = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }

            DashboardData(
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                balance = totalIncome - totalExpenses,
                recentTransactions = recentTransactions,
                categoryBreakdown = categoryBreakdown
            )
        }
    }
}
