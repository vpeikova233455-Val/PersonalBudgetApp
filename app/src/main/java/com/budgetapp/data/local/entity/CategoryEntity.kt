package com.budgetapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val isCustom: Boolean = false,
    val userId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "display_order") val displayOrder: Int = 0
)
