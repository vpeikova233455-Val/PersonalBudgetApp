package com.budgetapp.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.budgetapp.data.local.database.dao.*
import com.budgetapp.data.local.entity.*

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        PendingTransactionEntity::class,
        UserCategoryPreference::class,
        PensionAccountEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun userCategoryPreferenceDao(): UserCategoryPreferenceDao
    abstract fun pensionAccountDao(): PensionAccountDao

    companion object {
        const val DATABASE_NAME = "budget_app_database"
    }
}
