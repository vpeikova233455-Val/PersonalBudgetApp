package com.budgetapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val categoryId: Long,
    val date: Long,
    val isRecurring: Boolean = false,
    val recurringId: String? = null,

    // Sync fields
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val firestoreId: String? = null
)
