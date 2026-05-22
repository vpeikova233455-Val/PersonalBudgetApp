package com.budgetapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.remote.drive.BackupResult
import com.budgetapp.data.remote.drive.DriveBackupOrchestrator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val TAG = "DriveBackupWorker"

class DriveBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DriveBackupEntryPoint {
        fun orchestrator(): DriveBackupOrchestrator
    }

    private val orchestrator: DriveBackupOrchestrator by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DriveBackupEntryPoint::class.java
        ).orchestrator()
    }

    override suspend fun doWork(): Result {
        AppLogger.i(TAG, "Drive backup worker started (attempt ${runAttemptCount + 1})")
        return when (val result = orchestrator.performBackup()) {
            is BackupResult.Success      -> { AppLogger.i(TAG, "Drive backup succeeded"); Result.success() }
            is BackupResult.NotConfigured -> { AppLogger.d(TAG, "Drive not connected — skipping"); Result.success() }
            is BackupResult.NeedsReauth  -> { AppLogger.w(TAG, "Drive needs re-auth — will not retry"); Result.failure() }
            is BackupResult.Error        -> {
                AppLogger.e(TAG, "Drive backup error: ${result.message}")
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }
    }
}
