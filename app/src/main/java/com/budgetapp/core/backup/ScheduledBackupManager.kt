package com.budgetapp.core.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetapp.core.constants.Constants.BACKUP_WORK_NAME
import com.budgetapp.worker.ScheduledBackupWorker
import java.util.concurrent.TimeUnit

object ScheduledBackupManager {

    fun schedule(context: Context, intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        ).addTag(BACKUP_WORK_NAME).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BACKUP_WORK_NAME)
    }

    fun runNow(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<ScheduledBackupWorker>()
            .addTag(BACKUP_WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
