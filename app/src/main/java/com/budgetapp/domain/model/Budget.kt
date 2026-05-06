package com.budgetapp.domain.model

data class Budget(
    val id: Long,
    val category: Category,
    val monthlyLimit: Double,
    val currentSpending: Double,
    val alertThreshold: Double = 0.8
) {
    val remainingBudget: Double
        get() = monthlyLimit - currentSpending

    val percentageUsed: Double
        get() = if (monthlyLimit > 0) currentSpending / monthlyLimit else 0.0

    val isOverBudget: Boolean
        get() = currentSpending > monthlyLimit

    val shouldAlert: Boolean
        get() = percentageUsed >= alertThreshold
}
