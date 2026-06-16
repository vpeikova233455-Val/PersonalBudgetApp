package com.budgetapp.domain.model

data class DashboardData(
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double,
    val recentTransactions: List<Transaction>,
    val categoryBreakdown: Map<Category, Double>,
    val incomeBreakdown: Map<Category, Double> = emptyMap(),
    val transactionCount: Int = 0,
    val recurringIncome: List<com.budgetapp.domain.usecase.transaction.TransactionAnalytics.RecurringPattern> = emptyList(),
    val recurringExpenses: List<com.budgetapp.domain.usecase.transaction.TransactionAnalytics.RecurringPattern> = emptyList(),
    val expectedThisMonth: List<com.budgetapp.domain.usecase.transaction.TransactionAnalytics.RecurringPattern> = emptyList()
)
