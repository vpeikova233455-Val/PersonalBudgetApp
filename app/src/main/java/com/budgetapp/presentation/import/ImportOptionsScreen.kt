package com.budgetapp.presentation.import

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionsScreen(
    onNavigateBack: () -> Unit,
    onImportScreenshot: () -> Unit,
    onImportFile: (String) -> Unit, // "CSV" or "EXCEL"
    onNavigateToReview: () -> Unit
) {
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

            // Screenshot option
            ImportOptionCard(
                icon = Icons.Default.CameraAlt,
                title = "Bank Statement Screenshot",
                description = "Take or upload a photo of your bank statement. AI will extract all transactions.",
                onClick = onImportScreenshot
            )

            // Excel option
            ImportOptionCard(
                icon = Icons.Default.Description,
                title = "Excel File (.xlsx, .xls)",
                description = "Upload an Excel file with your transaction history.",
                onClick = { onImportFile("EXCEL") }
            )

            // CSV option
            ImportOptionCard(
                icon = Icons.Default.InsertDriveFile,
                title = "CSV File",
                description = "Upload a CSV file exported from your bank or finance app.",
                onClick = { onImportFile("CSV") }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Info card
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

            // View pending button
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
