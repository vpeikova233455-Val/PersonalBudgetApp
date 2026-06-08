package com.budgetapp.data.remote.firebase.mapper

import com.budgetapp.data.local.entity.*
import com.budgetapp.data.remote.firebase.model.*

// Transaction mappers
fun TransactionEntity.toFirestore(): FirestoreTransaction {
    return FirestoreTransaction(
        id = id,
        userId = userId,
        type = type.name,
        amount = amount,
        description = description,
        categoryId = categoryId,
        date = date,
        isRecurring = isRecurring,
        recurringId = recurringId,
        lastModifiedTimestamp = lastModifiedTimestamp,
        deviceId = deviceId,
        notes = notes
    )
}

fun FirestoreTransaction.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): TransactionEntity {
    return TransactionEntity(
        id = id,
        userId = userId,
        type = TransactionType.valueOf(type),
        amount = amount,
        description = description,
        categoryId = categoryId,
        date = date,
        isRecurring = isRecurring,
        recurringId = recurringId,
        syncStatus = syncStatus,
        lastModifiedTimestamp = lastModifiedTimestamp,
        deviceId = deviceId,
        firestoreId = id,
        notes = notes
    )
}

// Category mappers
fun CategoryEntity.toFirestore(): FirestoreCategory {
    return FirestoreCategory(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isCustom = isCustom,
        userId = userId,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

fun FirestoreCategory.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isCustom = isCustom,
        userId = userId,
        syncStatus = syncStatus,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

// Budget mappers
fun BudgetEntity.toFirestore(): FirestoreBudget {
    return FirestoreBudget(
        id = id,
        userId = userId,
        categoryId = categoryId,
        monthlyLimit = monthlyLimit,
        alertThreshold = alertThreshold,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

fun FirestoreBudget.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): BudgetEntity {
    return BudgetEntity(
        id = id,
        userId = userId,
        categoryId = categoryId,
        monthlyLimit = monthlyLimit,
        alertThreshold = alertThreshold,
        syncStatus = syncStatus,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}
