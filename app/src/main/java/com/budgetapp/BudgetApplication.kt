package com.budgetapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetapp.core.constants.Constants.DRIVE_BACKUP_INTERVAL_HOURS
import com.budgetapp.core.constants.Constants.DRIVE_BACKUP_WORK_TAG
import com.budgetapp.worker.DriveBackupWorker
import com.budgetapp.worker.SyncWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class BudgetApplication : Application() {

    override fun onCreate() {
        setupCrashHandler()
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        setupBackgroundSync()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                java.io.File(filesDir, "last_crash.txt").writeText("Thread: ${thread.name}\n$sw")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun setupBackgroundSync() {
        val workManager = WorkManager.getInstance(this)

        workManager.enqueueUniquePeriodicWork(
            "budget_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        )

        workManager.enqueueUniquePeriodicWork(
            DRIVE_BACKUP_WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveBackupWorker>(DRIVE_BACKUP_INTERVAL_HOURS, TimeUnit.HOURS)
                .addTag(DRIVE_BACKUP_WORK_TAG)
                .build()
        )
    }
}
