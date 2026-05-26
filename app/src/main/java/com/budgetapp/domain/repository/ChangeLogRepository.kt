package com.budgetapp.domain.repository

import com.budgetapp.data.local.entity.ChangeAction
import com.budgetapp.data.local.entity.HistoryEntityType
import com.budgetapp.domain.model.ChangeLogEntry
import kotlinx.coroutines.flow.Flow

interface ChangeLogRepository {
    fun getAll(): Flow<List<ChangeLogEntry>>
    fun getByType(type: HistoryEntityType): Flow<List<ChangeLogEntry>>
    suspend fun log(action: ChangeAction, entityType: HistoryEntityType, entityId: String, displayName: String, snapshot: String)
    suspend fun restore(entry: ChangeLogEntry)
    suspend fun clearAll()
    suspend fun trimToLimit(keepCount: Int = 500)
}
