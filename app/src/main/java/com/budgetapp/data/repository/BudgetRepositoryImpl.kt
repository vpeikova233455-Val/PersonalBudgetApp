package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.BudgetDao
import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.data.mapper.toDomain
import com.budgetapp.data.mapper.toEntity
import com.budgetapp.domain.model.Budget
import com.budgetapp.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) : BudgetRepository {

    override fun getAllBudgets(userId: String): Flow<List<Budget>> {
        return budgetDao.getAllBudgets(userId).map { entities ->
            entities.mapNotNull { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                category?.let {
                    val currentSpending = calculateCurrentSpending(userId, entity.categoryId)
                    entity.toDomain(it, currentSpending)
                }
            }
        }
    }

    override suspend fun getBudgetByCategory(userId: String, categoryId: Long): Budget? {
        val entity = budgetDao.getBudgetByCategory(userId, categoryId) ?: return null
        val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain() ?: return null
        val currentSpending = calculateCurrentSpending(userId, categoryId)
        return entity.toDomain(category, currentSpending)
    }

    override suspend fun insertBudget(budget: Budget): Long {
        return budgetDao.insertBudget(budget.toEntity(budget.category.toString()))
    }

    override suspend fun updateBudget(budget: Budget) {
        budgetDao.updateBudget(budget.toEntity(budget.category.toString()))
    }

    override suspend fun deleteBudget(budget: Budget) {
        budgetDao.deleteBudgetById(budget.id)
    }

    private suspend fun calculateCurrentSpending(userId: String, categoryId: Long): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val monthStart = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val monthEnd = calendar.timeInMillis

        val transactions = transactionDao.getTransactionsByDateRange(userId, monthStart, monthEnd)
        // Note: This is a simplified version. In production, use Flow
        return 0.0 // TODO: Calculate from transactions
    }
}
