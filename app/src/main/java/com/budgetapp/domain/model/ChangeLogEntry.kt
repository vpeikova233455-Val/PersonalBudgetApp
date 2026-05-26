package com.budgetapp.domain.model

import com.budgetapp.data.local.entity.ChangeAction
import com.budgetapp.data.local.entity.HistoryEntityType

data class ChangeLogEntry(
    val id: Long,
    val timestamp: Long,
    val action: ChangeAction,
    val entityType: HistoryEntityType,
    val entityId: String,
    val displayName: String,
    val snapshot: String
)
