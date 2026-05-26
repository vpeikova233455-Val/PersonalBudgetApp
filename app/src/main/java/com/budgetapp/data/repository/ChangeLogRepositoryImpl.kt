package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.ChangeLogDao
import com.budgetapp.data.local.database.dao.PensionAccountDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.*
import com.budgetapp.domain.model.ChangeLogEntry
import com.budgetapp.domain.repository.ChangeLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject

class ChangeLogRepositoryImpl @Inject constructor(
    private val changeLogDao: ChangeLogDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val pensionAccountDao: PensionAccountDao
) : ChangeLogRepository {

    override fun getAll(): Flow<List<ChangeLogEntry>> =
        changeLogDao.getAll().map { it.map(::toDomain) }

    override fun getByType(type: HistoryEntityType): Flow<List<ChangeLogEntry>> =
        changeLogDao.getByType(type.name).map { it.map(::toDomain) }

    override suspend fun log(
        action: ChangeAction,
        entityType: HistoryEntityType,
        entityId: String,
        displayName: String,
        snapshot: String
    ) {
        changeLogDao.insert(
            ChangeLogEntity(
                action = action.name,
                entityType = entityType.name,
                entityId = entityId,
                displayName = displayName,
                snapshot = snapshot
            )
        )
        // Keep history bounded to avoid unbounded DB growth
        if (changeLogDao.count() > 600) changeLogDao.trimToLimit(500)
    }

    override suspend fun restore(entry: ChangeLogEntry) {
        val json = when (entry.action) {
            ChangeAction.UPDATE -> JSONObject(entry.snapshot).optString("old").takeIf { it.isNotBlank() }
                ?: entry.snapshot
            else -> entry.snapshot
        }
        when (entry.entityType) {
            HistoryEntityType.TRANSACTION -> restoreTransaction(json)
            HistoryEntityType.CATEGORY    -> restoreCategory(json)
            HistoryEntityType.SAVINGS     -> restoreSavings(json)
        }
        log(
            action = ChangeAction.CREATE,
            entityType = entry.entityType,
            entityId = entry.entityId,
            displayName = "${entry.displayName} (restored)",
            snapshot = json
        )
    }

    override suspend fun clearAll() = changeLogDao.clearAll()

    override suspend fun trimToLimit(keepCount: Int) = changeLogDao.trimToLimit(keepCount)

    // ── Restore helpers ───────────────────────────────────────────────────────

    private suspend fun restoreTransaction(json: String) {
        val o = JSONObject(json)
        transactionDao.insertTransaction(
            TransactionEntity(
                id = o.getString("id"),
                userId = o.getString("userId"),
                type = TransactionType.valueOf(o.getString("type")),
                amount = o.getDouble("amount"),
                description = o.getString("description"),
                categoryId = o.getLong("categoryId"),
                date = o.getLong("date"),
                isRecurring = o.optBoolean("isRecurring", false),
                recurringId = o.optString("recurringId").takeIf { it.isNotBlank() },
                deviceId = o.optString("deviceId", "restored"),
                firestoreId = o.optString("firestoreId").takeIf { it.isNotBlank() },
                bankName = o.optString("bankName").takeIf { it.isNotBlank() },
                syncStatus = SyncStatus.PENDING,
                lastModifiedTimestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun restoreCategory(json: String) {
        val o = JSONObject(json)
        categoryDao.insertCategory(
            CategoryEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                icon = o.optString("icon", "📋"),
                color = o.optString("color", "#607D8B"),
                isCustom = o.optBoolean("isCustom", true),
                userId = o.optString("userId").takeIf { it.isNotBlank() },
                syncStatus = SyncStatus.PENDING,
                lastModifiedTimestamp = System.currentTimeMillis()
            )
        )
    }

    private suspend fun restoreSavings(json: String) {
        val o = JSONObject(json)
        pensionAccountDao.insertPensionAccount(
            PensionAccountEntity(
                id = o.getLong("id"),
                userId = o.getString("userId"),
                accountName = o.getString("accountName"),
                provider = o.optString("provider", ""),
                currentValue = o.getDouble("currentValue"),
                contributionAmount = o.optDouble("contributionAmount", 0.0),
                employerContribution = if (o.isNull("employerContribution")) null else o.optDouble("employerContribution"),
                contributionFrequency = RecurrenceFrequency.valueOf(o.optString("contributionFrequency", "MONTHLY")),
                accountType = AccountType.valueOf(o.optString("accountType", "SAVINGS")),
                notes = o.optString("notes", ""),
                syncStatus = SyncStatus.PENDING,
                lastModifiedTimestamp = System.currentTimeMillis()
            )
        )
    }

    private fun toDomain(e: ChangeLogEntity) = ChangeLogEntry(
        id = e.id,
        timestamp = e.timestamp,
        action = ChangeAction.valueOf(e.action),
        entityType = HistoryEntityType.valueOf(e.entityType),
        entityId = e.entityId,
        displayName = e.displayName,
        snapshot = e.snapshot
    )
}
