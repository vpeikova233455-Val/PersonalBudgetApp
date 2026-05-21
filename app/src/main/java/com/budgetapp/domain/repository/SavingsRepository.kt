package com.budgetapp.domain.repository

import com.budgetapp.domain.model.PensionAccount
import kotlinx.coroutines.flow.Flow

interface SavingsRepository {
    fun getAllAccounts(userId: String): Flow<List<PensionAccount>>
    suspend fun getAccountById(id: Long): PensionAccount?
    suspend fun insertAccount(account: PensionAccount, userId: String): Long
    suspend fun updateAccount(account: PensionAccount, userId: String)
    suspend fun deleteAccount(id: Long)
}
