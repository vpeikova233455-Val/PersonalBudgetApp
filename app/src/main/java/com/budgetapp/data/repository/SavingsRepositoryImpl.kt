package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.PensionAccountDao
import com.budgetapp.data.local.entity.PensionAccountEntity
import com.budgetapp.data.local.entity.SyncStatus
import com.budgetapp.domain.model.PensionAccount
import com.budgetapp.domain.repository.SavingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SavingsRepositoryImpl @Inject constructor(
    private val dao: PensionAccountDao
) : SavingsRepository {

    override fun getAllAccounts(userId: String): Flow<List<PensionAccount>> =
        dao.getAllPensionAccounts(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAccountById(id: Long): PensionAccount? =
        dao.getPensionAccountById(id)?.toDomain()

    override suspend fun insertAccount(account: PensionAccount, userId: String): Long =
        dao.insertPensionAccount(account.toEntity(userId))

    override suspend fun updateAccount(account: PensionAccount, userId: String) =
        dao.updatePensionAccount(account.toEntity(userId))

    override suspend fun deleteAccount(id: Long) =
        dao.deletePensionAccountById(id)

    private fun PensionAccountEntity.toDomain() = PensionAccount(
        id = id,
        accountName = accountName,
        provider = provider,
        currentValue = currentValue,
        contributionAmount = contributionAmount,
        employerContribution = employerContribution,
        contributionFrequency = contributionFrequency,
        accountType = accountType,
        notes = notes
    )

    private fun PensionAccount.toEntity(userId: String) = PensionAccountEntity(
        id = id,
        userId = userId,
        accountName = accountName,
        provider = provider,
        currentValue = currentValue,
        contributionAmount = contributionAmount,
        employerContribution = employerContribution,
        contributionFrequency = contributionFrequency,
        accountType = accountType,
        notes = notes,
        syncStatus = SyncStatus.PENDING,
        lastModifiedTimestamp = System.currentTimeMillis()
    )
}
