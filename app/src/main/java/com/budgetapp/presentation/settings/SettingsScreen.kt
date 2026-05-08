package com.budgetapp.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.data.export.ExportFormat
import com.budgetapp.data.export.ExportRange
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportState by exportViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showExportDialog by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf<String?>(null) }

    // Launch share sheet whenever the ViewModel emits a share intent
    LaunchedEffect(Unit) {
        exportViewModel.shareEvent.collect { intent ->
            context.startActivity(intent)
        }
    }

    if (showExportDialog) {
        ExportDialog(
            isExporting = exportState.isExporting,
            onExport = { format, range ->
                showExportDialog = false
                exportViewModel.export(format, range)
            },
            onDismiss = { showExportDialog = false }
        )
    }

    if (exportState.error != null) {
        AlertDialog(
            onDismissRequest = exportViewModel::clearError,
            title = { Text("Export failed") },
            text = { Text(exportState.error!!) },
            confirmButton = {
                TextButton(onClick = exportViewModel::clearError) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.userEmail,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Sync Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Last Sync", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                uiState.lastSyncFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.syncStatus.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = viewModel::manualSync) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                            }
                        }
                    }
                    if (uiState.syncStatus.pendingChanges > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${uiState.syncStatus.pendingChanges} pending changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (uiState.syncStatus.error != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uiState.syncStatus.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Language
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Language / שפה / Язык",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("en", "English", "🇺🇸"),
                            Triple("he", "עברית", "🇮🇱"),
                            Triple("ru", "Русский", "🇷🇺")
                        ).forEach { (tag, label, flag) ->
                            val selected = uiState.currentLanguage == tag
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setLanguage(tag) },
                                label = {
                                    Text(
                                        "$flag $label",
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (uiState.currentLanguage != "en") {
                        Text(
                            "The app will switch language immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // App Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "App Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SettingsRow(
                        icon = Icons.Default.List,
                        label = "Manage Categories",
                        onClick = onNavigateToCategories
                    )

                    HorizontalDivider()

                    SettingsRow(
                        icon = Icons.Default.FileDownload,
                        label = if (exportState.isExporting) "Exporting…" else "Export Transactions",
                        onClick = { if (!exportState.isExporting) showExportDialog = true },
                        trailing = {
                            if (exportState.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    )

                    val crashFile = File(context.filesDir, "last_crash.txt")
                    if (crashFile.exists()) {
                        HorizontalDivider()
                        SettingsRow(
                            icon = Icons.Default.BugReport,
                            label = "View Last Crash Log",
                            onClick = { crashLog = crashFile.readText() }
                        )
                    }
                }
            }
        }
    }

    crashLog?.let { log ->
        AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("Last Crash Log") },
            text = {
                val scrollState = androidx.compose.foundation.rememberScrollState()
                Text(
                    text = log.take(3000),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", log))
                    crashLog = null
                }) { Text("Copy to Clipboard") }
            },
            dismissButton = {
                TextButton(onClick = { crashLog = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(label)
            }
            trailing?.invoke() ?: Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun ExportDialog(
    isExporting: Boolean,
    onExport: (ExportFormat, ExportRange) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var selectedRange by remember { mutableStateOf(ExportRange.THIS_MONTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Transactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Format
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.values().forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = {
                                Text(when (fmt) {
                                    ExportFormat.CSV -> "CSV"
                                    ExportFormat.EXCEL -> "Excel (.xlsx)"
                                })
                            }
                        )
                    }
                }

                // Range
                Text("Period", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExportRange.values().forEach { rng ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedRange == rng,
                                onClick = { selectedRange = rng }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (rng) {
                                    ExportRange.ALL -> "All time"
                                    ExportRange.THIS_MONTH -> "This month"
                                    ExportRange.LAST_MONTH -> "Last month"
                                    ExportRange.LAST_3_MONTHS -> "Last 3 months"
                                    ExportRange.THIS_YEAR -> "This year"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExport(selectedFormat, selectedRange) },
                enabled = !isExporting
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
