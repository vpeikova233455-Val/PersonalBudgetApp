package com.budgetapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.budgetapp.navigation.AppNavigation
import com.budgetapp.presentation.theme.BudgetAppTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPreviousCrashIfAny()
        enableEdgeToEdge()
        setContent {
            BudgetAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun showPreviousCrashIfAny() {
        val crashFile = File(filesDir, "last_crash.txt")
        if (!crashFile.exists()) return
        val crashLog = runCatching { crashFile.readText() }.getOrNull() ?: return
        crashFile.delete()
        android.app.AlertDialog.Builder(this)
            .setTitle("Previous Crash")
            .setMessage(crashLog.take(3000))
            .setPositiveButton("Copy & Close") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", crashLog))
            }
            .setNegativeButton("Close", null)
            .setCancelable(false)
            .show()
    }
}
