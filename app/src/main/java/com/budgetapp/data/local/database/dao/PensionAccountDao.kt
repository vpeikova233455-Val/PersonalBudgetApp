package com.budgetapp.data.local.database.dao

import androidx.room.*
import com.budgetapp.data.local.entity.PensionAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PensionAccountDao {

    @Query("SELECT * FROM pension_accounts WHERE userId = :userId ORDER BY accountName ASC")
    fun getAllPensionAccounts(userId: String): Flow<List<PensionAccountEntity>>

    @Query("SELECT * FROM pension_accounts WHERE id = :accountId")
    suspend fun getPensionAccountById(accountId: Long): PensionAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPensionAccount(account: PensionAccountEntity): Long

    @Update
    suspend fun updatePensionAccount(account: PensionAccountEntity)

    @Delete
    suspend fun deletePensionAccount(account: PensionAccountEntity)

    @Query("DELETE FROM pension_accounts WHERE id = :accountId")
    suspend fun deletePensionAccountById(accountId: Long)
}
