package com.budgetapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetapp.core.util.Result as AppResult
import com.budgetapp.domain.repository.SyncRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun syncRepository(): SyncRepository
    }

    private val syncRepository: SyncRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        ).syncRepository()
    }

    override suspend fun doWork(): Result {
        return try {
            when (syncRepository.syncAll()) {
                is AppResult.Success -> Result.success()
                is AppResult.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
                is AppResult.Loading -> Result.retry()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
