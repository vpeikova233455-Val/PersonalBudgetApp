package com.budgetapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetapp.core.util.Result as AppResult
import com.budgetapp.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Perform bidirectional sync
            when (val result = syncRepository.syncAll()) {
                is AppResult.Success -> {
                    Result.success()
                }
                is AppResult.Error -> {
                    // Retry on error (max 3 attempts by WorkManager)
                    if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                is AppResult.Loading -> Result.retry()
            }
        } catch (e: Exception) {
            // Retry on exception (max 3 attempts)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
