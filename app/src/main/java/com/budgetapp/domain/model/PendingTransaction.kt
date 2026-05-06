package com.budgetapp.domain.model

import com.budgetapp.data.local.entity.ImportSource
import com.budgetapp.data.local.entity.TransactionType

data class PendingTransaction(
    val id: Long,
    val type: TransactionType?,
    val amount: Double?,
    val description: String?,
    val suggestedCategory: Category?,
    val categoryConfidence: Double?,
    val date: Long?,
    val sourceType: ImportSource,
    val aiQuestions: List<String>?
)
