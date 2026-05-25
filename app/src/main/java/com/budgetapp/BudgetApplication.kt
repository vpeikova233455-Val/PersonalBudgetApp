package com.budgetapp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "budget_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}
