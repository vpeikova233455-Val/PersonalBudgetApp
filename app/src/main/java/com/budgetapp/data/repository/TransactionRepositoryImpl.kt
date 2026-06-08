package com.budgetapp.data.repository

import com.budgetapp.data.local.database.dao.CategoryDao
import com.budgetapp.data.local.database.dao.ChangeLogDao
import com.budgetapp.data.local.database.dao.TransactionDao
import com.budgetapp.data.local.entity.ChangeAction
import com.budgetapp.data.local.entity.ChangeLogEntity
import com.budgetapp.data.local.entity.HistoryEntityType
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.data.mapper.toDomain
import com.budgetapp.data.mapper.toEntity
import com.budgetapp.domain.model.Transaction
import com.budgetapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val changeLogDao: ChangeLogDao,
    private val deviceId: String
) : TransactionRepository {

    override fun getAllTransactions(userId: String): Flow<List<Transaction>> =
        transactionDao.getAllTransactions(userId).map { entities ->
            entities.mapNotNull { entity ->
                categoryDao.getCategoryById(entity.categoryId)?.toDomain()?.let { entity.toDomain(it) }
            }
        }

    override fun getTransactionsByDateRange(userId: String, startDate: Long, endDate: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(userId, startDate, endDate).map { entities ->
            entities.map { entity ->
                val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain()
                    ?: com.budgetapp.domain.model.Category(
                        id = entity.categoryId, name = "Other", icon = "📋", color = "#607D8B"
                    )
                entity.toDomain(category)
            }
        }

    override fun getTransactionsByCategory(userId: String, categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(userId, categoryId).map { entities ->
            entities.mapNotNull { entity ->
                categoryDao.getCategoryById(entity.categoryId)?.toDomain()?.let { entity.toDomain(it) }
            }
        }

    override fun getTransactionsByType(userId: String, type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(userId, type).map { entities ->
            entities.mapNotNull { entity ->
                categoryDao.getCategoryById(entity.categoryId)?.toDomain()?.let { entity.toDomain(it) }
            }
        }

    override suspend fun getTransactionById(transactionId: String): Transaction? {
        val entity = transactionDao.getTransactionById(transactionId) ?: return null
        val category = categoryDao.getCategoryById(entity.categoryId)?.toDomain() ?: return null
        return entity.toDomain(category)
    }

    override fun getTotalByType(userId: String, type: TransactionType, startDate: Long, endDate: Long): Flow<Double> =
        transactionDao.getTotalByType(userId, type, startDate, endDate).map { it ?: 0.0 }

    override suspend fun insertTransaction(transaction: Transaction) {
        val entity = transaction.toEntity(deviceId)
        transactionDao.insertTransaction(entity)
        logChange(ChangeAction.CREATE, transaction.id, transaction.toDisplayName(), entity.toSnapshot())
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val oldEntity = transactionDao.getTransactionById(transaction.id)
        val newEntity = transaction.toEntity(deviceId)
        transactionDao.updateTransaction(newEntity)
        val snapshot = if (oldEntity != null) buildUpdateSnapshot(oldEntity.toSnapshot(), newEntity.toSnapshot())
                       else newEntity.toSnapshot()
        logChange(ChangeAction.UPDATE, transaction.id, transaction.toDisplayName(), snapshot)
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        val entity = transaction.toEntity(deviceId)
        logChange(ChangeAction.DELETE, transaction.id, transaction.toDisplayName(), entity.toSnapshot())
        transactionDao.deleteTransactionById(transaction.id)
    }

    override suspend fun deleteTransactions(transactions: List<Transaction>) {
        transactions.forEach { tx ->
            logChange(ChangeAction.DELETE, tx.id, tx.toDisplayName(), tx.toEntity(deviceId).toSnapshot())
        }
        transactionDao.deleteTransactionsByIds(transactions.map { it.id })
    }

    override suspend fun reassignCategory(fromCategoryId: Long, toCategoryId: Long) {
        transactionDao.reassignCategory(fromCategoryId, toCategoryId)
    }

    override suspend fun deleteTransactionsByCategory(categoryId: Long) {
        transactionDao.deleteTransactionsByCategory(categoryId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun logChange(action: ChangeAction, id: String, displayName: String, snapshot: String) {
        runCatching {
            changeLogDao.insert(
                ChangeLogEntity(action = action.name, entityType = HistoryEntityType.TRANSACTION.name,
                    entityId = id, displayName = displayName, snapshot = snapshot)
            )
        }
    }

    private fun Transaction.toDisplayName() =
        "$description — ${String.format("%.2f", amount)}"

    private fun com.budgetapp.data.local.entity.TransactionEntity.toSnapshot() = JSONObject().apply {
        put("id", id); put("userId", userId); put("type", type.name); put("amount", amount)
        put("description", description); put("categoryId", categoryId); put("date", date)
        put("isRecurring", isRecurring); put("recurringId", recurringId); put("deviceId", deviceId)
        put("firestoreId", firestoreId); put("bankName", bankName)
    }.toString()

    private fun buildUpdateSnapshot(old: String, new: String) =
        JSONObject().put("old", old).put("new", new).toString()
}
