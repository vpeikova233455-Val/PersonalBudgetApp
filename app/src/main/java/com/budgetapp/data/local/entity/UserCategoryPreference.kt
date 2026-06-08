package com.budgetapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_category_preferences")
data class UserCategoryPreference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val merchantPattern: String,
    val categoryId: Long,
    val usageCount: Int = 1,
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
    val isAutomatic: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
