package com.budgetapp.presentation.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.core.util.toCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionsScreen(
    onNavigateBack: () -> Unit,
    ocrViewModel: OcrImportViewModel,
    onNavigateToReview: () -> Unit,
    fileImportViewModel: FileImportViewModel = hiltViewModel()
) {
    val ocrState by ocrViewModel.state.collectAsState()
    val fileState by fileImportViewModel.state.collectAsState()

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // ── Launchers ──────────────────────────────────────────────────────────────

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { ocrViewModel.processScreenshot(it) } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { fileImportViewModel.parseFile(it) } }

    // ── Side effects ──────────────────────────────────────────────────────────

    LaunchedEffect(ocrState) {
        when (val s = ocrState) {
            is OcrImportState.Done  -> { ocrViewModel.resetState(); onNavigateToReview() }
            is OcrImportState.Error -> { errorMessage = s.message; showErrorDialog = true }
            else -> {}
        }
    }

    LaunchedEffect(fileState) {
        when (val s = fileState) {
            is FileImportState.Done  -> { fileImportViewModel.resetState(); onNavigateToReview() }
            is FileImportState.Error -> { errorMessage = s.message; showErrorDialog = true }
            else -> {}
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false; ocrViewModel.resetState(); fileImportViewModel.resetState() },
            title = { Text("Import Failed") },
            text  = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false; ocrViewModel.resetState(); fileImportViewModel.resetState() }) {
                    Text("OK")
                }
            }
        )
    }

    val isProcessing = ocrState is OcrImportState.Processing ||
                       fileState is FileImportState.Parsing  ||
                       fileState is FileImportState.Importing
    if (isProcessing) {
        val message = when {
            ocrState is OcrImportState.Processing    -> "Extracting transactions with AI…"
            fileState is FileImportState.Parsing     -> "Reading file…"
            fileState is FileImportState.Importing   -> "Saving transactions…"
            else -> "Processing…"
        }
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(message)
                }
            }
        }
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (fileState is FileImportState.Preview) "Select Months to Import"
                        else "Import Transactions"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (fileState is FileImportState.Preview) fileImportViewModel.resetState()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->

        when (val fs = fileState) {
            is FileImportState.Preview -> MonthPreviewContent(
                preview = fs,
                viewModel = fileImportViewModel,
                modifier = Modifier.padding(padding)
            )
            else -> OptionsContent(
                modifier = Modifier.padding(padding),
                onScreenshot = { imagePickerLauncher.launch("image/*") },
                onExcel = {
                    filePickerLauncher.launch(arrayOf(
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/octet-stream",
                        "*/*"
                    ))
                },
                onCsv = {
                    filePickerLauncher.launch(arrayOf(
                        "text/csv",
                        "text/plain",
                        "text/comma-separated-values",
                        "application/csv",
                        "*/*"
                    ))
                },
                onReview = onNavigateToReview
            )
        }
    }
}

// ── Options list ──────────────────────────────────────────────────────────────

@Composable
private fun OptionsContent(
    modifier: Modifier,
    onScreenshot: () -> Unit,
    onExcel: () -> Unit,
    onCsv: () -> Unit,
    onReview: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Choose Import Method",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Import from a screenshot, Excel, or CSV. The app will split transactions by month and let you choose which ones to import.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        ImportOptionCard(
            icon = Icons.Default.CameraAlt,
            title = "Bank Statement Screenshot",
            description = "Photo of your bank statement — AI extracts all transactions.",
            onClick = onScreenshot
        )
        ImportOptionCard(
            icon = Icons.Default.Description,
            title = "Excel File (.xlsx, .xls)",
            description = "Export from your bank or credit card portal and upload here. Multi-month files are split automatically.",
            onClick = onExcel
        )
        ImportOptionCard(
            icon = Icons.Default.InsertDriveFile,
            title = "CSV File",
            description = "Comma-separated export from your bank or finance app.",
            onClick = onCsv
        )

        Spacer(modifier = Modifier.weight(1f))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Supported banks: Hapoalim, Leumi, Discount, Mizrahi, Isracard, CAL, Max, and any standard export format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.RateReview, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Review Pending Transactions")
        }
    }
}

// ── Month preview ─────────────────────────────────────────────────────────────

@Composable
private fun MonthPreviewContent(
    preview: FileImportState.Preview,
    viewModel: FileImportViewModel,
    modifier: Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Summary banner
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "${preview.totalCount} transactions found across ${preview.months.size} month${if (preview.months.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Select the months you want to import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Select all / none row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = viewModel::selectAll)   { Text("Select all") }
            TextButton(onClick = viewModel::deselectAll) { Text("Deselect all") }
        }

        // Month list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(preview.months, key = { it.key }) { month ->
                MonthCard(month = month, onToggle = { viewModel.toggleMonth(month.key) })
            }
        }

        // Import button
        Surface(shadowElevation = 8.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::importSelected,
                    enabled = preview.selectedCount > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (preview.selectedCount > 0)
                            "Import ${preview.selectedCount} transactions"
                        else
                            "Select at least one month"
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCard(month: MonthGroup, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (month.selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = month.selected, onCheckedChange = { onToggle() })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(month.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (month.expenseCount > 0)
                            Text("${month.expenseCount} expenses", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        if (month.incomeCount > 0)
                            Text("${month.incomeCount} income", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        if (month.duplicateCount > 0)
                            Text("${month.duplicateCount} possible duplicates", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            Text(
                month.totalAmount.toCurrency(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (month.selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Shared card ───────────────────────────────────────────────────────────────

@Composable
fun ImportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
