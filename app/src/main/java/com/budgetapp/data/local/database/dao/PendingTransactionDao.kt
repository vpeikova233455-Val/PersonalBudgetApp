package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {

    @Query("SELECT * FROM pending_transactions WHERE userId = :userId ORDER BY createdTimestamp DESC")
    fun getAllPending(userId: String): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions WHERE id = :pendingId")
    suspend fun getPendingById(pendingId: Long): PendingTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPending(pending: PendingTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingList(pendingList: List<PendingTransactionEntity>)

    @Update
    suspend fun updatePending(pending: PendingTransactionEntity)

    @Delete
    suspend fun deletePending(pending: PendingTransactionEntity)

    @Query("DELETE FROM pending_transactions WHERE id = :pendingId")
    suspend fun deletePendingById(pendingId: Long)

    @Query("DELETE FROM pending_transactions WHERE userId = :userId")
    suspend fun deleteAllPending(userId: String)
}
