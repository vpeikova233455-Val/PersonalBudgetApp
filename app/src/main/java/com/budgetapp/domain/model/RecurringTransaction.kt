package com.budgetapp.domain.model

import com.budgetapp.data.local.entity.RecurrenceFrequency
import com.budgetapp.data.local.entity.TransactionType

data class RecurringTransaction(
    val id: String,
    val type: TransactionType,
    val amount: Double,
    val description: String,
    val category: Category,
    val frequency: RecurrenceFrequency,
    val startDate: Long,
    val endDate: Long? = null,
    val isActive: Boolean = true
)
