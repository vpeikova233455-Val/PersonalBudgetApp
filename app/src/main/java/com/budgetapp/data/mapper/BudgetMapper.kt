package com.budgetapp.data.mapper

import com.budgetapp.data.local.entity.BudgetEntity
import com.budgetapp.domain.model.Budget
import com.budgetapp.domain.model.Category

fun BudgetEntity.toDomain(category: Category, currentSpending: Double): Budget {
    return Budget(
        id = id,
        category = category,
        monthlyLimit = monthlyLimit,
        currentSpending = currentSpending,
        alertThreshold = alertThreshold
    )
}

fun Budget.toEntity(userId: String): BudgetEntity {
    return BudgetEntity(
        id = id,
        userId = userId,
        categoryId = category.id,
        monthlyLimit = monthlyLimit,
        alertThreshold = alertThreshold
    )
}
