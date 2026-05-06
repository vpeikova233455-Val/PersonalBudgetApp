package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1 ORDER BY startDate DESC")
    fun getAllActiveRecurring(userId: String): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId ORDER BY startDate DESC")
    fun getAllRecurring(userId: String): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :recurringId")
    suspend fun getRecurringById(recurringId: String): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND (endDate IS NULL OR endDate > :currentTime)")
    suspend fun getDueRecurringTransactions(currentTime: Long): List<RecurringTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringTransactionEntity)

    @Update
    suspend fun updateRecurring(recurring: RecurringTransactionEntity)

    @Delete
    suspend fun deleteRecurring(recurring: RecurringTransactionEntity)

    @Query("UPDATE recurring_transactions SET isActive = 0 WHERE id = :recurringId")
    suspend fun deactivateRecurring(recurringId: String)
}
