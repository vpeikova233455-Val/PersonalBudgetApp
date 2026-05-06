package com.budgetapp.presentation.bugreport

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.budgetapp.core.bugreport.BugReportManager
import com.budgetapp.core.util.AppLogger
import kotlinx.coroutines.launch

@Composable
fun BugReportButton(modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    SmallFloatingActionButton(
        onClick = {
            if (!isCapturing && !showDialog) {
                isCapturing = true
                scope.launch {
                    AppLogger.d("BugReportButton", "Capturing screenshot")
                    capturedBitmap = BugReportManager.captureScreenshot(view)
                    isCapturing = false
                    showDialog = true
                }
            }
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            Icon(Icons.Default.BugReport, contentDescription = "Report Bug")
        }
    }

    if (showDialog) {
        BugReportDialog(
            screenshot = capturedBitmap,
            onDismiss = {
                showDialog = false
                capturedBitmap = null
            }
        )
    }
}
