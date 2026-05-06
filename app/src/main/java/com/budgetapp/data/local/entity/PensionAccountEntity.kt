package com.budgetapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pension_accounts")
data class PensionAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val accountName: String,
    val provider: String,
    val currentValue: Double,
    val contributionAmount: Double,
    val employerContribution: Double? = null,
    val contributionFrequency: RecurrenceFrequency,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
