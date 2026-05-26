package com.budgetapp.core.di

import com.budgetapp.data.local.database.dao.*
import com.budgetapp.data.repository.*
import com.budgetapp.domain.repository.*
import com.budgetapp.data.local.database.dao.ChangeLogDao
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideDeviceId(): String {
        return UUID.randomUUID().toString()
    }

    @Provides
    @Singleton
    fun provideChangeLogRepository(
        changeLogDao: ChangeLogDao,
        transactionDao: TransactionDao,
        categoryDao: CategoryDao,
        pensionAccountDao: PensionAccountDao
    ): ChangeLogRepository {
        return ChangeLogRepositoryImpl(changeLogDao, transactionDao, categoryDao, pensionAccountDao)
    }

    @Provides
    @Singleton
    fun provideTransactionRepository(
        transactionDao: TransactionDao,
        categoryDao: CategoryDao,
        changeLogDao: ChangeLogDao,
        deviceId: String
    ): TransactionRepository {
        return TransactionRepositoryImpl(transactionDao, categoryDao, changeLogDao, deviceId)
    }

    @Provides
    @Singleton
    fun provideCategoryRepository(
        categoryDao: CategoryDao,
        changeLogDao: ChangeLogDao
    ): CategoryRepository {
        return CategoryRepositoryImpl(categoryDao, changeLogDao)
    }

    @Provides
    @Singleton
    fun provideBudgetRepository(
        budgetDao: BudgetDao,
        categoryDao: CategoryDao,
        transactionDao: TransactionDao
    ): BudgetRepository {
        return BudgetRepositoryImpl(budgetDao, categoryDao, transactionDao)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth
    ): AuthRepository {
        return AuthRepositoryImpl(firebaseAuth)
    }

    @Provides
    @Singleton
    fun provideSavingsRepository(
        pensionAccountDao: PensionAccountDao,
        changeLogDao: ChangeLogDao
    ): SavingsRepository {
        return SavingsRepositoryImpl(pensionAccountDao, changeLogDao)
    }
}
