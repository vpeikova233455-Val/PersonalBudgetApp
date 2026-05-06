package com.budgetapp.presentation.import

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
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTransactionsScreen(
    onNavigateBack: () -> Unit,
    onAllApproved: () -> Unit,
    viewModel: ReviewTransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.pendingTransactions.isNotEmpty()) {
                        Text(
                            text = "${uiState.pendingTransactions.size} pending",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.pendingTransactions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All transactions reviewed!",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.pendingTransactions) { pending ->
                        PendingTransactionCard(
                            pending = pending,
                            categories = uiState.categories,
                            onApprove = { viewModel.approvePendingTransaction(pending.id) },
                            onEdit = { viewModel.startEditingTransaction(pending.id) },
                            onDelete = { viewModel.deletePendingTransaction(pending.id) }
                        )
                    }
                }
            }
        }

        // Show success message when all approved
        LaunchedEffect(uiState.pendingTransactions.isEmpty() && !uiState.isLoading) {
            if (uiState.allApproved) {
                onAllApproved()
            }
        }
    }
}

@Composable
fun PendingTransactionCard(
    pending: PendingTransactionUiModel,
    categories: List<CategoryUiModel>,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Confidence indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            pending.confidence >= 0.8 -> Icons.Default.CheckCircle
                            pending.confidence >= 0.5 -> Icons.Default.Warning
                            else -> Icons.Default.HelpOutline
                        },
                        contentDescription = null,
                        tint = when {
                            pending.confidence >= 0.8 -> MaterialTheme.colorScheme.primary
                            pending.confidence >= 0.5 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${(pending.confidence * 100).toInt()}% confident",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    pending.sourceType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                pending.description,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Amount and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    pending.formattedAmount,
                    style = MaterialTheme.typography.titleLarge,
                    color = when (pending.type) {
                        "INCOME" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    pending.formattedDate ?: "Date unknown",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Suggested category
            if (pending.suggestedCategory != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Suggested: ${pending.suggestedCategory}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // AI questions (if any)
            pending.aiQuestions?.let { questions ->
                if (questions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "AI needs clarification:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            questions.forEach { question ->
                                Text(
                                    "• $question",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

// UI Models
data class PendingTransactionUiModel(
    val id: Long,
    val description: String,
    val formattedAmount: String,
    val formattedDate: String?,
    val type: String,
    val suggestedCategory: String?,
    val confidence: Double,
    val sourceType: String,
    val aiQuestions: List<String>?
)

data class CategoryUiModel(
    val id: Long,
    val name: String
)
