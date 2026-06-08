package com.budgetapp.data.repository

import android.content.Context
import com.budgetapp.core.constants.Constants
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.data.local.database.dao.*
import com.budgetapp.data.local.entity.SyncStatus as EntitySyncStatus
import com.budgetapp.data.remote.firebase.mapper.*
import com.budgetapp.data.remote.firebase.model.*
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.SyncStatus
import com.budgetapp.core.util.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val pensionAccountDao: PensionAccountDao,
    private val userCategoryPreferenceDao: UserCategoryPreferenceDao
) : SyncRepository {

    private val _syncStatus = MutableStateFlow(SyncStatus())

    companion object {
        private const val PREF_LAST_SYNC = "last_sync_time"
    }

    override fun getSyncStatus(): Flow<SyncStatus> = _syncStatus.asStateFlow()

    override suspend fun getLastSyncTime(): Long? {
        val prefs = EncryptionManager.getEncryptedPreferences(context)
        val lastSync = prefs.getLong(PREF_LAST_SYNC, -1L)
        return if (lastSync > 0) lastSync else null
    }

    override suspend fun getPendingChangesCount(): Int =
        transactionDao.getTransactionsBySyncStatus(EntitySyncStatus.PENDING).size

    // ── syncAll ───────────────────────────────────────────────────────────────

    override suspend fun syncAll(): Result<Unit> {
        return try {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)

            val pushResult = pushLocalChanges()
            if (pushResult is Result.Error) {
                _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = pushResult.exception.message)
                return pushResult
            }

            val pullResult = pullRemoteChanges()
            if (pullResult is Result.Error) {
                _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = pullResult.exception.message)
                return pullResult
            }

            val currentTime = System.currentTimeMillis()
            EncryptionManager.getEncryptedPreferences(context).edit()
                .putLong(PREF_LAST_SYNC, currentTime).apply()

            _syncStatus.value = SyncStatus(isSyncing = false, lastSyncTime = currentTime, pendingChanges = 0)
            Result.Success(Unit)
        } catch (e: Exception) {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = e.message ?: "Sync failed")
            Result.Error(e)
        }
    }

    // ── Push ──────────────────────────────────────────────────────────────────

    override suspend fun pushLocalChanges(): Result<Unit> {
        return try {
            val userId = EncryptionManager.getString(context, Constants.KEY_USER_ID)
                ?: return Result.Success(Unit)

            pushTransactions(userId)
            pushCategories(userId)
            pushBudgets(userId)
            pushRecurring(userId)
            pushPensions(userId)
            pushLearningRules(userId)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun pushTransactions(userId: String) {
        val pending = transactionDao.getTransactionsBySyncStatus(EntitySyncStatus.PENDING)
        pending.forEach { entity ->
            firestore.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_TRANSACTIONS).document(entity.id)
                .set(entity.toFirestore(), SetOptions.merge()).await()
            transactionDao.updateSyncStatus(entity.id, EntitySyncStatus.SYNCED)
        }
    }

    private suspend fun pushCategories(userId: String) {
        val pending = categoryDao.getCustomCategoriesBySyncStatus(EntitySyncStatus.PENDING)
        pending.forEach { entity ->
            firestore.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_CATEGORIES).document(entity.id.toString())
                .set(entity.toFirestore(), SetOptions.merge()).await()
            categoryDao.updateSyncStatus(entity.id, EntitySyncStatus.SYNCED)
        }
    }

    private suspend fun pushBudgets(userId: String) {
        val pending = budgetDao.getBudgetsBySyncStatus(EntitySyncStatus.PENDING)
        pending.forEach { entity ->
            firestore.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_BUDGETS).document(entity.id.toString())
                .set(entity.toFirestore(), SetOptions.merge()).await()
            budgetDao.updateSyncStatus(entity.id, EntitySyncStatus.SYNCED)
        }
    }

    private suspend fun pushRecurring(userId: String) {
        val pending = recurringTransactionDao.getRecurringBySyncStatus(EntitySyncStatus.PENDING)
        pending.forEach { entity ->
            firestore.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_RECURRING).document(entity.id)
                .set(entity.toFirestore(), SetOptions.merge()).await()
            recurringTransactionDao.updateSyncStatus(entity.id, EntitySyncStatus.SYNCED)
        }
    }

    private suspend fun pushPensions(userId: String) {
        val pending = pensionAccountDao.getPensionsBySyncStatus(EntitySyncStatus.PENDING)
        pending.forEach { entity ->
            firestore.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_PENSIONS).document(entity.id.toString())
                .set(entity.toFirestore(), SetOptions.merge()).await()
            pensionAccountDao.updateSyncStatus(entity.id, EntitySyncStatus.SYNCED)
        }
    }

    private suspend fun pushLearningRules(userId: String) {
        val pending = userCategoryPreferenceDao.getPreferencesBySyncStatus(EntitySyncStatus.PENDING)
        pending.forEach { entity ->
            firestore.collection(Constants.COLLECTION_USERS).document(userId)
                .collection(Constants.COLLECTION_LEARNING_RULES).document(entity.id.toString())
                .set(entity.toFirestore(), SetOptions.merge()).await()
            userCategoryPreferenceDao.updateSyncStatus(entity.id, EntitySyncStatus.SYNCED)
        }
    }

    // ── Pull ──────────────────────────────────────────────────────────────────

    override suspend fun pullRemoteChanges(): Result<Unit> {
        return try {
            val userId = EncryptionManager.getString(context, Constants.KEY_USER_ID)
                ?: return Result.Success(Unit)

            pullTransactions(userId)
            pullCategories(userId)
            pullBudgets(userId)
            pullRecurring(userId)
            pullPensions(userId)
            pullLearningRules(userId)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun pullTransactions(userId: String) {
        val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_TRANSACTIONS).get().await()
        snapshot.documents.mapNotNull {
            it.toObject(FirestoreTransaction::class.java)
        }.forEach { remote ->
            val local = transactionDao.getTransactionById(remote.id)
            if (local == null || remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                transactionDao.insertTransaction(remote.toEntity())
            }
        }
    }

    private suspend fun pullCategories(userId: String) {
        val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_CATEGORIES).get().await()
        snapshot.documents.mapNotNull {
            it.toObject(FirestoreCategory::class.java)
        }.filter { it.isCustom }.forEach { remote ->
            val local = categoryDao.getCategoryById(remote.id)
            if (local == null || remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                categoryDao.insertCategory(remote.toEntity())
            }
        }
    }

    private suspend fun pullBudgets(userId: String) {
        val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_BUDGETS).get().await()
        snapshot.documents.mapNotNull {
            it.toObject(FirestoreBudget::class.java)
        }.forEach { remote ->
            val local = budgetDao.getBudgetById(remote.id)
            if (local == null || remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                budgetDao.insertBudget(remote.toEntity())
            }
        }
    }

    private suspend fun pullRecurring(userId: String) {
        val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_RECURRING).get().await()
        snapshot.documents.mapNotNull {
            it.toObject(FirestoreRecurringTransaction::class.java)
        }.forEach { remote ->
            val local = recurringTransactionDao.getRecurringById(remote.id)
            if (local == null || remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                recurringTransactionDao.insertRecurring(remote.toEntity())
            }
        }
    }

    private suspend fun pullPensions(userId: String) {
        val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_PENSIONS).get().await()
        snapshot.documents.mapNotNull {
            it.toObject(FirestorePensionAccount::class.java)
        }.forEach { remote ->
            val local = pensionAccountDao.getPensionAccountById(remote.id)
            if (local == null || remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                pensionAccountDao.insertPensionAccount(remote.toEntity())
            }
        }
    }

    private suspend fun pullLearningRules(userId: String) {
        val snapshot = firestore.collection(Constants.COLLECTION_USERS).document(userId)
            .collection(Constants.COLLECTION_LEARNING_RULES).get().await()
        snapshot.documents.mapNotNull {
            it.toObject(FirestoreLearningRule::class.java)
        }.forEach { remote ->
            val allLocal = userCategoryPreferenceDao.getAllPreferencesSync(userId)
            val local = allLocal.find { it.id == remote.id }
            if (local == null || remote.lastModifiedTimestamp > local.lastModifiedTimestamp) {
                userCategoryPreferenceDao.insertPreference(remote.toEntity())
            }
        }
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    override suspend fun restoreAll(): Result<Unit> {
        return try {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)

            val pullResult = pullRemoteChanges()

            val currentTime = System.currentTimeMillis()
            if (pullResult is Result.Success) {
                EncryptionManager.getEncryptedPreferences(context).edit()
                    .putLong(PREF_LAST_SYNC, currentTime).apply()
            }

            _syncStatus.value = SyncStatus(
                isSyncing = false,
                lastSyncTime = currentTime,
                error = (pullResult as? Result.Error)?.exception?.message
            )

            pullResult
        } catch (e: Exception) {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = false, error = e.message)
            Result.Error(e)
        }
    }
}
