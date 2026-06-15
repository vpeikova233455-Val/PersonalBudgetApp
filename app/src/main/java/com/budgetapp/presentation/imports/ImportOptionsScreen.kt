package com.budgetapp.presentation.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel

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

    val pdfPickerLauncher = rememberLauncherForActivityResult(
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
                       fileState is FileImportState.OcrInProgress ||
                       fileState is FileImportState.Importing
    if (isProcessing) {
        val message = when {
            ocrState is OcrImportState.Processing        -> "Extracting transactions with AI…"
            fileState is FileImportState.Parsing         -> "Reading file…"
            fileState is FileImportState.OcrInProgress   -> "Scanned PDF — reading with AI…"
            fileState is FileImportState.Importing       -> "Saving transactions…"
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
                title = { Text("Import Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        OptionsContent(
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
            onPdf = {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
            },
            onReview = onNavigateToReview
        )
    }
}

// ── Options list ──────────────────────────────────────────────────────────────

@Composable
private fun OptionsContent(
    modifier: Modifier,
    onScreenshot: () -> Unit,
    onExcel: () -> Unit,
    onCsv: () -> Unit,
    onPdf: () -> Unit,
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
            "Import from a screenshot, Excel, CSV, or PDF. All transactions are saved immediately and available in the review queue — even across app restarts.",
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
        ImportOptionCard(
            icon = Icons.Default.PictureAsPdf,
            title = "PDF Bank Statement",
            description = "Digital bank statement PDF. Text-based PDFs are read directly; scanned PDFs are processed with AI.",
            onClick = onPdf
        )

        Spacer(modifier = Modifier.weight(1f))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Supported formats: Excel, CSV, PDF (text & scanned). Banks: Hapoalim, Leumi, Discount, Mizrahi, Isracard, CAL, Max, and standard exports.",
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
