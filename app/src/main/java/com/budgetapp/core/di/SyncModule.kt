package com.budgetapp.core.di

import android.content.Context
import com.budgetapp.data.local.database.dao.*
import com.budgetapp.data.repository.SyncRepositoryImpl
import com.budgetapp.domain.repository.SyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncRepository(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        transactionDao: TransactionDao,
        categoryDao: CategoryDao,
        budgetDao: BudgetDao
    ): SyncRepository {
        return SyncRepositoryImpl(context, firestore, transactionDao, categoryDao, budgetDao)
    }
}
