package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.ChangeLogDao
import com.budgetapp.data.local.database.dao.PensionAccountDao
import com.budgetapp.data.local.entity.*
import com.budgetapp.domain.model.PensionAccount
import com.budgetapp.domain.repository.SavingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject

class SavingsRepositoryImpl @Inject constructor(
    private val dao: PensionAccountDao,
    private val changeLogDao: ChangeLogDao
) : SavingsRepository {

    override fun getAllAccounts(userId: String): Flow<List<PensionAccount>> =
        dao.getAllPensionAccounts(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAccountById(id: Long): PensionAccount? =
        dao.getPensionAccountById(id)?.toDomain()

    override suspend fun insertAccount(account: PensionAccount, userId: String): Long {
        val entity = account.toEntity(userId)
        val id = dao.insertPensionAccount(entity)
        logChange(ChangeAction.CREATE, id.toString(), account.accountName, entity.copy(id = id).toSnapshot())
        return id
    }

    override suspend fun updateAccount(account: PensionAccount, userId: String) {
        val oldEntity = dao.getPensionAccountById(account.id)
        val newEntity = account.toEntity(userId)
        dao.updatePensionAccount(newEntity)
        val snapshot = if (oldEntity != null) buildUpdateSnapshot(oldEntity.toSnapshot(), newEntity.toSnapshot())
                       else newEntity.toSnapshot()
        logChange(ChangeAction.UPDATE, account.id.toString(), account.accountName, snapshot)
    }

    override suspend fun deleteAccount(id: Long) {
        val entity = dao.getPensionAccountById(id)
        if (entity != null) {
            logChange(ChangeAction.DELETE, id.toString(), entity.accountName, entity.toSnapshot())
        }
        dao.deletePensionAccountById(id)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun logChange(action: ChangeAction, id: String, displayName: String, snapshot: String) {
        runCatching {
            changeLogDao.insert(
                ChangeLogEntity(action = action.name, entityType = HistoryEntityType.SAVINGS.name,
                    entityId = id, displayName = displayName, snapshot = snapshot)
            )
        }
    }

    private fun PensionAccountEntity.toSnapshot() = JSONObject().apply {
        put("id", id); put("userId", userId); put("accountName", accountName)
        put("provider", provider); put("currentValue", currentValue)
        put("contributionAmount", contributionAmount); put("employerContribution", employerContribution)
        put("contributionFrequency", contributionFrequency.name); put("accountType", accountType.name)
        put("notes", notes)
    }.toString()

    private fun buildUpdateSnapshot(old: String, new: String) =
        JSONObject().put("old", old).put("new", new).toString()

    private fun PensionAccountEntity.toDomain() = PensionAccount(
        id = id, accountName = accountName, provider = provider, currentValue = currentValue,
        contributionAmount = contributionAmount, employerContribution = employerContribution,
        contributionFrequency = contributionFrequency, accountType = accountType, notes = notes
    )

    private fun PensionAccount.toEntity(userId: String) = PensionAccountEntity(
        id = id, userId = userId, accountName = accountName, provider = provider,
        currentValue = currentValue, contributionAmount = contributionAmount,
        employerContribution = employerContribution, contributionFrequency = contributionFrequency,
        accountType = accountType, notes = notes, syncStatus = SyncStatus.PENDING,
        lastModifiedTimestamp = System.currentTimeMillis()
    )
}
