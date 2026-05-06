package com.budgetapp.domain.model

import com.budgetapp.data.local.entity.TransactionType

data class Transaction(
    val id: String,
    val userId: String,
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val category: Category,
    val date: Long,
    val isRecurring: Boolean = false,
    val recurringId: String? = null
)
