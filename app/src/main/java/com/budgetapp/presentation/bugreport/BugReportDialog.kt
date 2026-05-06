package com.budgetapp.presentation.bugreport

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.budgetapp.BuildConfig
import com.budgetapp.core.bugreport.BugReportManager
import com.budgetapp.core.util.AppLogger
import kotlinx.coroutines.launch

private val LABELS = listOf("ui", "crash", "data", "performance", "feature-request")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportDialog(
    screenshot: Bitmap?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedLabels by remember { mutableStateOf(setOf<String>()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var submitSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Report a Bug") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                resultMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (submitSuccess)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    enabled = !isSubmitting && !submitSuccess,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isSubmitting && !submitSuccess,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Labels:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LABELS.forEach { label ->
                        FilterChip(
                            selected = label in selectedLabels,
                            onClick = {
                                selectedLabels = if (label in selectedLabels)
                                    selectedLabels - label else selectedLabels + label
                            },
                            label = {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = if (label in selectedLabels) {
                                { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                            } else null,
                            enabled = !isSubmitting && !submitSuccess
                        )
                    }
                }

                if (screenshot != null) {
                    Text(
                        "Screenshot captured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!submitSuccess) {
                Button(
                    onClick = {
                        AppLogger.i("BugReportDialog", "Submitting bug report: $title")
                        isSubmitting = true
                        scope.launch {
                            val result = BugReportManager.submitBugReport(
                                title = title.trim(),
                                description = description.trim(),
                                labels = selectedLabels.toList(),
                                screenshot = screenshot,
                                deviceInfo = BugReportManager.getDeviceInfo(BuildConfig.VERSION_NAME),
                                logs = AppLogger.getLogs()
                            )
                            isSubmitting = false
                            submitSuccess = result.success
                            resultMessage = if (result.success)
                                "Issue created:\n${result.issueUrl}"
                            else
                                result.error ?: "Submission failed"
                        }
                    },
                    enabled = !isSubmitting && title.isNotBlank()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Submit")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(if (submitSuccess) "Close" else "Cancel")
            }
        }
    )
}
