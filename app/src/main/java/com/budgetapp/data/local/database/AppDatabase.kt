package com.budgetapp.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN bankName TEXT")
                database.execSQL("ALTER TABLE user_category_preferences ADD COLUMN isAutomatic INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
