package com.budgetapp.domain.repository

import com.budgetapp.core.util.Result
import kotlinx.coroutines.flow.Flow

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val pendingChanges: Int = 0,
    val error: String? = null
)

interface SyncRepository {
    suspend fun syncAll(): Result<Unit>
    suspend fun pushLocalChanges(): Result<Unit>
    suspend fun pullRemoteChanges(): Result<Unit>
    fun getSyncStatus(): Flow<SyncStatus>
    suspend fun getLastSyncTime(): Long?
    suspend fun getPendingChangesCount(): Int
}
