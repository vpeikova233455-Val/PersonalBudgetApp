package com.budgetapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_transactions")
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val type: TransactionType? = null,
    val amount: Double? = null,
    val description: String? = null,
    val suggestedCategory: Long? = null,
    val categoryConfidence: Double? = null, // 0.0-1.0
    val date: Long? = null,
    val sourceType: ImportSource,
    val sourceUri: String,
    val aiQuestions: String? = null, // JSON array of questions
    val createdTimestamp: Long = System.currentTimeMillis()
)
