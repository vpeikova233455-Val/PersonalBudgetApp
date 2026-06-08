package com.budgetapp.data.remote.firebase.mapper

import com.budgetapp.data.local.entity.*
import com.budgetapp.data.remote.firebase.model.*

// ── Transactions ──────────────────────────────────────────────────────────────

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

// ── Categories ────────────────────────────────────────────────────────────────

fun CategoryEntity.toFirestore(): FirestoreCategory {
    return FirestoreCategory(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isCustom = isCustom,
        userId = userId,
        lastModifiedTimestamp = lastModifiedTimestamp,
        displayOrder = displayOrder
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
        lastModifiedTimestamp = lastModifiedTimestamp,
        displayOrder = displayOrder
    )
}

// ── Budgets ───────────────────────────────────────────────────────────────────

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

// ── Recurring transactions ────────────────────────────────────────────────────

fun RecurringTransactionEntity.toFirestore(): FirestoreRecurringTransaction {
    return FirestoreRecurringTransaction(
        id = id,
        userId = userId,
        type = type.name,
        amount = amount,
        description = description,
        categoryId = categoryId,
        frequency = frequency.name,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        lastGeneratedDate = lastGeneratedDate,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

fun FirestoreRecurringTransaction.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): RecurringTransactionEntity {
    return RecurringTransactionEntity(
        id = id,
        userId = userId,
        type = TransactionType.valueOf(type),
        amount = amount,
        description = description,
        categoryId = categoryId,
        frequency = RecurrenceFrequency.valueOf(frequency),
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        lastGeneratedDate = lastGeneratedDate,
        syncStatus = syncStatus,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

// ── Pension / savings accounts ────────────────────────────────────────────────

fun PensionAccountEntity.toFirestore(): FirestorePensionAccount {
    return FirestorePensionAccount(
        id = id,
        userId = userId,
        accountName = accountName,
        provider = provider,
        currentValue = currentValue,
        contributionAmount = contributionAmount,
        employerContribution = employerContribution,
        contributionFrequency = contributionFrequency.name,
        accountType = accountType.name,
        notes = notes,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

fun FirestorePensionAccount.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): PensionAccountEntity {
    return PensionAccountEntity(
        id = id,
        userId = userId,
        accountName = accountName,
        provider = provider,
        currentValue = currentValue,
        contributionAmount = contributionAmount,
        employerContribution = employerContribution,
        contributionFrequency = RecurrenceFrequency.valueOf(contributionFrequency),
        accountType = AccountType.valueOf(accountType),
        notes = notes,
        syncStatus = syncStatus,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

// ── Learning rules (UserCategoryPreference) ───────────────────────────────────

fun UserCategoryPreference.toFirestore(): FirestoreLearningRule {
    return FirestoreLearningRule(
        id = id,
        userId = userId,
        merchantPattern = merchantPattern,
        categoryId = categoryId,
        usageCount = usageCount,
        lastUsedTimestamp = lastUsedTimestamp,
        isAutomatic = isAutomatic,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}

fun FirestoreLearningRule.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): UserCategoryPreference {
    return UserCategoryPreference(
        id = id,
        userId = userId,
        merchantPattern = merchantPattern,
        categoryId = categoryId,
        usageCount = usageCount,
        lastUsedTimestamp = lastUsedTimestamp,
        isAutomatic = isAutomatic,
        syncStatus = syncStatus,
        lastModifiedTimestamp = lastModifiedTimestamp
    )
}
