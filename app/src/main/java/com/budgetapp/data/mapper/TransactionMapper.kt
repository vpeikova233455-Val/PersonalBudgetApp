package com.budgetapp.data.mapper

import com.budgetapp.data.local.entity.SyncStatus
import com.budgetapp.data.local.entity.TransactionEntity
import com.budgetapp.domain.model.Category
import com.budgetapp.domain.model.Transaction

fun TransactionEntity.toDomain(category: Category): Transaction {
    return Transaction(
        id = id,
        userId = userId,
        type = type,
        amount = amount,
        description = description,
        category = category,
        date = date,
        isRecurring = isRecurring,
        recurringId = recurringId
    )
}

fun Transaction.toEntity(deviceId: String): TransactionEntity {
    return TransactionEntity(
        id = id,
        userId = userId,
        type = type,
        amount = amount,
        description = description,
        categoryId = category.id,
        date = date,
        isRecurring = isRecurring,
        recurringId = recurringId,
        syncStatus = SyncStatus.PENDING,
        lastModifiedTimestamp = System.currentTimeMillis(),
        deviceId = deviceId
    )
}
