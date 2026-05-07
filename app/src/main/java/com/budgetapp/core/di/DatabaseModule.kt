package com.budgetapp.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budgetapp.data.local.database.AppDatabase
import com.budgetapp.data.local.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        lateinit var instance: AppDatabase
        instance = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        instance.categoryDao().insertCategories(
                            com.budgetapp.data.repository.CategoryRepositoryImpl.defaultCategories()
                        )
                    }
                }
            })
            .build()
        return instance
    }

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideBudgetDao(database: AppDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    fun provideRecurringTransactionDao(database: AppDatabase): RecurringTransactionDao {
        return database.recurringTransactionDao()
    }

    @Provides
    fun providePendingTransactionDao(database: AppDatabase): PendingTransactionDao {
        return database.pendingTransactionDao()
    }

    @Provides
    fun provideUserCategoryPreferenceDao(database: AppDatabase): UserCategoryPreferenceDao {
        return database.userCategoryPreferenceDao()
    }

    @Provides
    fun providePensionAccountDao(database: AppDatabase): PensionAccountDao {
        return database.pensionAccountDao()
    }
}
