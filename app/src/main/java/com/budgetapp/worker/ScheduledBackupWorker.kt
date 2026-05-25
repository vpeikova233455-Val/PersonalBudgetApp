package com.budgetapp.worker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetapp.core.constants.Constants.KEY_BACKUP_FOLDER_URI
import com.budgetapp.core.constants.Constants.KEY_BACKUP_LAST_RUN
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.core.util.AppLogger
import com.budgetapp.data.export.ExportService
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ScheduledBackupWorker"

class ScheduledBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupEntryPoint {
        fun transactionRepository(): TransactionRepository
        fun authRepository(): AuthRepository
        fun exportService(): ExportService
    }

    private val entry by lazy {
        EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
    }

    override suspend fun doWork(): Result {
        val folderUriStr = EncryptionManager.getString(applicationContext, KEY_BACKUP_FOLDER_URI)
        if (folderUriStr.isNullOrBlank()) {
            AppLogger.w(TAG, "No backup folder configured — skipping")
            return Result.success()
        }

        return try {
            val userId = entry.authRepository().getCurrentUserId()
            if (userId == null) {
                AppLogger.w(TAG, "No logged-in user — skipping backup")
                return Result.success()
            }

            val transactions = entry.transactionRepository()
                .getAllTransactions(userId).first()
                .sortedByDescending { it.date }

            if (transactions.isEmpty()) {
                AppLogger.d(TAG, "No transactions to export")
                return Result.success()
            }

            val csvBytes = entry.exportService().exportToCsv(transactions)
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val fileName = "transactions_$timestamp.csv"

            val folderUri = Uri.parse(folderUriStr)
            val folder = DocumentFile.fromTreeUri(applicationContext, folderUri)
                ?: throw Exception("Could not access backup folder — permission may have been revoked")

            val file = folder.createFile("text/csv", fileName)
                ?: throw Exception("Could not create file in backup folder")

            applicationContext.contentResolver.openOutputStream(file.uri)?.use { it.write(csvBytes) }
                ?: throw Exception("Could not open output stream")

            EncryptionManager.saveString(
                applicationContext, KEY_BACKUP_LAST_RUN,
                System.currentTimeMillis().toString()
            )

            AppLogger.i(TAG, "Backup saved: $fileName (${transactions.size} transactions)")
            Result.success()

        } catch (e: Exception) {
            AppLogger.e(TAG, "Backup failed: ${e.message}", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
