package com.budgetapp.domain.repository

import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAllTransactions(userId: String): Flow<List<Transaction>>
    fun getTransactionsByDateRange(userId: String, startDate: Long, endDate: Long): Flow<List<Transaction>>
    fun getTransactionsByCategory(userId: String, categoryId: Long): Flow<List<Transaction>>
    fun getTransactionsByType(userId: String, type: TransactionType): Flow<List<Transaction>>
    suspend fun getTransactionById(transactionId: String): Transaction?
    fun getTotalByType(userId: String, type: TransactionType, startDate: Long, endDate: Long): Flow<Double>
    suspend fun insertTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transaction: Transaction)
    suspend fun deleteTransactions(transactions: List<Transaction>)
    suspend fun reassignCategory(fromCategoryId: Long, toCategoryId: Long)
    suspend fun deleteTransactionsByCategory(categoryId: Long)
}
