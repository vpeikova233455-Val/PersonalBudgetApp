package com.budgetapp.domain.repository

import com.budgetapp.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getAllBudgets(userId: String): Flow<List<Budget>>
    suspend fun getBudgetByCategory(userId: String, categoryId: Long): Budget?
    suspend fun insertBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(budget: Budget)
}
