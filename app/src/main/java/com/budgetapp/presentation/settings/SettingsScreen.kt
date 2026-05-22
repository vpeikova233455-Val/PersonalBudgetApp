package com.budgetapp.presentation.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.data.export.ExportFormat
import com.budgetapp.data.export.ExportRange
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var showBugReportDialog by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf<String?>(null) }

    // Close the bug report dialog as soon as a result (success or error) arrives
    LaunchedEffect(uiState.bugReportStatus) {
        if (uiState.bugReportStatus !is BugReportStatus.Idle &&
            uiState.bugReportStatus !is BugReportStatus.Loading
        ) {
            showBugReportDialog = false
        }
    }

    // GitHub settings fields — initialized once when the ViewModel delivers saved values
    var tokenField by rememberSaveable { mutableStateOf("") }
    var ownerField by rememberSaveable { mutableStateOf("") }
    var repoField by rememberSaveable { mutableStateOf("") }
    var githubFieldsInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.githubToken, uiState.githubOwner, uiState.githubRepo) {
        if (!githubFieldsInitialized) {
            tokenField = uiState.githubToken
            ownerField = uiState.githubOwner
            repoField = uiState.githubRepo
            githubFieldsInitialized = true
        }
    }

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

    if (showBugReportDialog) {
        BugReportDialog(
            isSubmitting = uiState.bugReportStatus is BugReportStatus.Loading,
            onSubmit = { title, description ->
                viewModel.submitBugReport(title, description)
            },
            onDismiss = { showBugReportDialog = false }
        )
    }

    // Bug report result dialogs
    when (val status = uiState.bugReportStatus) {
        is BugReportStatus.Success -> {
            AlertDialog(
                onDismissRequest = viewModel::clearBugReportStatus,
                title = { Text("Bug Reported") },
                text = { Text("Issue created successfully.\n\n${status.issueUrl}") },
                confirmButton = {
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("issue_url", status.issueUrl))
                        viewModel.clearBugReportStatus()
                    }) { Text("Copy Link") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::clearBugReportStatus) { Text("Close") }
                }
            )
        }
        is BugReportStatus.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::clearBugReportStatus,
                title = { Text("Failed to Report Bug") },
                text = { Text(status.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearBugReportStatus) { Text("OK") }
                }
            )
        }
        else -> {}
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
                .verticalScroll(rememberScrollState())
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

                    HorizontalDivider()

                    SettingsRow(
                        icon = Icons.Default.BugReport,
                        label = "Report a Bug",
                        onClick = { showBugReportDialog = true }
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

            // Google Drive Backup
            DriveBackupCard(
                uiState = uiState,
                viewModel = viewModel,
                context = context
            )

            // GitHub Integration
            GitHubSettingsCard(
                tokenField = tokenField,
                ownerField = ownerField,
                repoField = repoField,
                onTokenChange = { tokenField = it },
                onOwnerChange = { ownerField = it },
                onRepoChange = { repoField = it },
                onSave = { viewModel.saveGitHubSettings(tokenField, ownerField, repoField) }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    crashLog?.let { log ->
        AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("Last Crash Log") },
            text = {
                val scrollState = rememberScrollState()
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
private fun DriveBackupCard(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    context: Context
) {
    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
                viewModel.onDriveSignInSuccess(account)
            }
        }
    }

    val timeFmt = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

    // Status dialogs
    when (val status = uiState.driveBackupStatus) {
        is DriveBackupStatus.Error -> AlertDialog(
            onDismissRequest = viewModel::clearDriveStatus,
            title = { Text("Backup Failed") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = viewModel::clearDriveStatus) { Text("OK") } }
        )
        is DriveBackupStatus.NeedsReauth -> AlertDialog(
            onDismissRequest = viewModel::clearDriveStatus,
            title = { Text("Re-authorization Required") },
            text = { Text("Your Google Drive access has expired. Please disconnect and reconnect your Google account.") },
            confirmButton = {
                Button(onClick = { viewModel.disconnectDrive(); viewModel.clearDriveStatus() }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = viewModel::clearDriveStatus) { Text("Cancel") } }
        )
        else -> {}
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Google Drive Backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.driveEmail.isBlank()) {
                Text(
                    "Connect your Google account to automatically back up transactions, categories, savings, and reports to Google Drive every 6 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                            .build()
                        driveSignInLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect Google Drive")
                }
            } else {
                // Connected state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(uiState.driveEmail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val lastBackupText = if (uiState.driveLastBackup > 0L)
                            timeFmt.format(Date(uiState.driveLastBackup))
                        else "Never"
                        Text(
                            "Last backup: $lastBackupText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isRunning = uiState.driveBackupStatus is DriveBackupStatus.Running
                    OutlinedButton(
                        onClick = viewModel::manualDriveBackup,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Backing up…")
                        } else {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Back Up Now")
                        }
                    }
                    TextButton(
                        onClick = {
                            GoogleSignIn.getClient(
                                context,
                                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                            ).signOut()
                            viewModel.disconnectDrive()
                        }
                    ) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
                }

                if (uiState.driveBackupStatus is DriveBackupStatus.Success) {
                    Text(
                        "Backup completed successfully",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubSettingsCard(
    tokenField: String,
    ownerField: String,
    repoField: String,
    onTokenChange: (String) -> Unit,
    onOwnerChange: (String) -> Unit,
    onRepoChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "GitHub Integration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Used to open GitHub issues when you report a bug.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = tokenField,
                onValueChange = {
                    onTokenChange(it)
                    saved = false
                },
                label = { Text("Personal Access Token") },
                placeholder = { Text("ghp_...") },
                visualTransformation = if (tokenVisible) VisualTransformation.None
                                        else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "Hide token" else "Show token"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ownerField,
                onValueChange = {
                    onOwnerChange(it)
                    saved = false
                },
                label = { Text("GitHub Owner") },
                placeholder = { Text("username or org") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = repoField,
                onValueChange = {
                    onRepoChange(it)
                    saved = false
                },
                label = { Text("Repository Name") },
                placeholder = { Text("my-repo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (saved) {
                    Text(
                        "Saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Button(
                    onClick = {
                        onSave()
                        saved = true
                    },
                    enabled = tokenField.isNotBlank() || ownerField.isNotBlank() || repoField.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun BugReportDialog(
    isSubmitting: Boolean,
    onSubmit: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Report a Bug") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Brief description of the issue") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details (optional)") },
                    placeholder = { Text("Steps to reproduce, expected vs actual behaviour…") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(title.trim(), description.trim()) },
                enabled = title.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
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
