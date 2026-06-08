package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.SyncStatus
import com.budgetapp.data.local.entity.TransactionEntity
import com.budgetapp.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun getAllTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date >= :startDate AND date < :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(userId: String, startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND categoryId = :categoryId ORDER BY date DESC")
    fun getTransactionsByCategory(userId: String, categoryId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = :type ORDER BY date DESC")
    fun getTransactionsByType(userId: String, type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE syncStatus = :status")
    suspend fun getTransactionsBySyncStatus(status: SyncStatus): List<TransactionEntity>

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = :type AND date >= :startDate AND date < :endDate")
    fun getTotalByType(userId: String, type: TransactionType, startDate: Long, endDate: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: String)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<String>)

    @Query("UPDATE transactions SET syncStatus = :status WHERE id = :transactionId")
    suspend fun updateSyncStatus(transactionId: String, status: SyncStatus)
}
