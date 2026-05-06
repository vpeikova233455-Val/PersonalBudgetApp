package com.budgetapp.data.repository

import android.content.Context
import com.budgetapp.core.constants.Constants
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.data.local.database.dao.*
import com.budgetapp.data.local.entity.SyncStatus as EntitySyncStatus
import com.budgetapp.data.remote.firebase.mapper.*
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
    private val budgetDao: BudgetDao
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

    override suspend fun getPendingChangesCount(): Int {
        val pendingTransactions = transactionDao.getTransactionsBySyncStatus(EntitySyncStatus.PENDING)
        return pendingTransactions.size
    }

    override suspend fun syncAll(): Result<Unit> {
        return try {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true, error = null)

            // First push local changes, then pull remote changes
            when (val pushResult = pushLocalChanges()) {
                is Result.Error -> {
                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        error = pushResult.exception.message
                    )
                    return pushResult
                }
                else -> {}
            }

            when (val pullResult = pullRemoteChanges()) {
                is Result.Error -> {
                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        error = pullResult.exception.message
                    )
                    return pullResult
                }
                else -> {}
            }

            // Update last sync time
            val currentTime = System.currentTimeMillis()
            EncryptionManager.getEncryptedPreferences(context)
                .edit()
                .putLong(PREF_LAST_SYNC, currentTime)
                .apply()

            _syncStatus.value = SyncStatus(
                isSyncing = false,
                lastSyncTime = currentTime,
                pendingChanges = 0,
                error = null
            )

            Result.Success(Unit)
        } catch (e: Exception) {
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                error = e.message ?: "Sync failed"
            )
            Result.Error(e)
        }
    }

    override suspend fun pushLocalChanges(): Result<Unit> {
        return try {
            // Get all pending transactions
            val pendingTransactions = transactionDao.getTransactionsBySyncStatus(EntitySyncStatus.PENDING)

            // Push transactions to Firestore
            pendingTransactions.forEach { transaction ->
                val firestoreTransaction = transaction.toFirestore()
                firestore.collection(Constants.COLLECTION_USERS)
                    .document(transaction.userId)
                    .collection(Constants.COLLECTION_TRANSACTIONS)
                    .document(transaction.id)
                    .set(firestoreTransaction, SetOptions.merge())
                    .await()

                // Mark as synced
                transactionDao.updateSyncStatus(transaction.id, EntitySyncStatus.SYNCED)
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun pullRemoteChanges(): Result<Unit> {
        return try {
            // This would normally get userId from AuthRepository
            // For now, we'll skip pulling if no userId is available
            val userId = EncryptionManager.getString(context, Constants.KEY_USER_ID)
                ?: return Result.Success(Unit)

            // Get remote transactions
            val snapshot = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .collection(Constants.COLLECTION_TRANSACTIONS)
                .get()
                .await()

            val remoteTransactions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(com.budgetapp.data.remote.firebase.model.FirestoreTransaction::class.java)
            }

            // Merge with local database using last-write-wins strategy
            remoteTransactions.forEach { remoteTransaction ->
                val localTransaction = transactionDao.getTransactionById(remoteTransaction.id)

                if (localTransaction == null) {
                    // New transaction from remote - insert it
                    transactionDao.insertTransaction(remoteTransaction.toEntity(EntitySyncStatus.SYNCED))
                } else {
                    // Conflict resolution: last-write-wins
                    if (remoteTransaction.lastModifiedTimestamp > localTransaction.lastModifiedTimestamp) {
                        // Remote is newer - update local
                        transactionDao.updateTransaction(remoteTransaction.toEntity(EntitySyncStatus.SYNCED))
                    }
                    // If local is newer, we already pushed it, so no action needed
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
