package com.budgetapp.presentation.import

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionsScreen(
    onNavigateBack: () -> Unit,
    ocrViewModel: OcrImportViewModel,
    onNavigateToReview: () -> Unit,
    onImportFile: (String) -> Unit = {}
) {
    val ocrState by ocrViewModel.state.collectAsState()
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { ocrViewModel.processScreenshot(it) }
    }

    LaunchedEffect(ocrState) {
        when (val s = ocrState) {
            is OcrImportState.Done -> {
                ocrViewModel.resetState()
                onNavigateToReview()
            }
            is OcrImportState.Error -> {
                errorMessage = s.message
                showErrorDialog = true
            }
            else -> {}
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                ocrViewModel.resetState()
            },
            title = { Text("Import Failed") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = false
                    ocrViewModel.resetState()
                }) { Text("OK") }
            }
        )
    }

    if (ocrState is OcrImportState.Processing) {
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
                    Text("Extracting transactions with AI…")
                }
            }
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose Import Method",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Our AI will extract transaction data and help you categorize them intelligently.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ImportOptionCard(
                icon = Icons.Default.CameraAlt,
                title = "Bank Statement Screenshot",
                description = "Upload a photo of your bank statement. AI will extract all transactions.",
                onClick = { imagePickerLauncher.launch("image/*") }
            )

            ImportOptionCard(
                icon = Icons.Default.Description,
                title = "Excel File (.xlsx, .xls)",
                description = "Upload an Excel file with your transaction history.",
                onClick = { onImportFile("EXCEL") }
            )

            ImportOptionCard(
                icon = Icons.Default.InsertDriveFile,
                title = "CSV File",
                description = "Upload a CSV file exported from your bank or finance app.",
                onClick = { onImportFile("CSV") }
            )

            Spacer(modifier = Modifier.weight(1f))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Tip: AI learns from your choices to improve future suggestions!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedButton(
                onClick = onNavigateToReview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RateReview, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review Pending Transactions")
            }
        }
    }
}

@Composable
fun ImportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
