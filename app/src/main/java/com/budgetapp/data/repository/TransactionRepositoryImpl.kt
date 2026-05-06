package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.data.mapper.toDomain
import com.budgetapp.data.mapper.toEntity
import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val deviceId: String
) : TransactionRepository {

    override fun getAllTransactions(userId: String): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions(userId).map { entities ->
            entities.mapNotNull { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                category?.let { entity.toDomain(it) }
            }
        }
    }

    override fun getTransactionsByDateRange(
        userId: String,
        startDate: Long,
        endDate: Long
    ): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByDateRange(userId, startDate, endDate).map { entities ->
            entities.mapNotNull { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                category?.let { entity.toDomain(it) }
            }
        }
    }

    override fun getTransactionsByCategory(userId: String, categoryId: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByCategory(userId, categoryId).map { entities ->
            entities.mapNotNull { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                category?.let { entity.toDomain(it) }
            }
        }
    }

    override fun getTransactionsByType(userId: String, type: TransactionType): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByType(userId, type).map { entities ->
            entities.mapNotNull { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                category?.let { entity.toDomain(it) }
            }
        }
    }

    override suspend fun getTransactionById(transactionId: String): Transaction? {
        val entity = transactionDao.getTransactionById(transactionId) ?: return null
        val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain() ?: return null
        return entity.toDomain(category)
    }

    override fun getTotalByType(
        userId: String,
        type: TransactionType,
        startDate: Long,
        endDate: Long
    ): Flow<Double> {
        return transactionDao.getTotalByType(userId, type, startDate, endDate).map { it ?: 0.0 }
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction.toEntity(deviceId))
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction.toEntity(deviceId))
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransactionById(transaction.id)
    }
}
