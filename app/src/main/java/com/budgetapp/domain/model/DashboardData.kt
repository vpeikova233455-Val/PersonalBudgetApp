package com.budgetapp.domain.model

data class DashboardData(
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double,
    val recentTransactions: List<Transaction>,
    val categoryBreakdown: Map<Category, Double>
)
