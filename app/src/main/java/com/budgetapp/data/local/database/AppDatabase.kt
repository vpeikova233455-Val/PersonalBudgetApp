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
        PensionAccountEntity::class,
        ChangeLogEntity::class
    ],
    version = 5,
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
    abstract fun changeLogDao(): ChangeLogDao

    companion object {
        const val DATABASE_NAME = "budget_app_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN bankName TEXT")
                database.execSQL("ALTER TABLE user_category_preferences ADD COLUMN isAutomatic INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pension_accounts ADD COLUMN accountType TEXT NOT NULL DEFAULT 'PENSION'")
                database.execSQL("ALTER TABLE pension_accounts ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `change_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `action` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `snapshot` TEXT NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE categories ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
