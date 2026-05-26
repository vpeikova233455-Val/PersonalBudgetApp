package com.budgetapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "change_log")
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,         // ChangeAction.name
    val entityType: String,     // HistoryEntityType.name
    val entityId: String,
    val displayName: String,
    val snapshot: String        // JSON — full entity state, or {"old":…,"new":…} for UPDATE
)
