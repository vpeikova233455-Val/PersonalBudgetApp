package com.budgetapp.presentation.history

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
import com.budgetapp.data.local.entity.ChangeAction
import com.budgetapp.data.local.entity.HistoryEntityType
import com.budgetapp.domain.model.ChangeLogEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    var showClearDialog by remember { mutableStateOf(false) }

    uiState.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2_500)
            viewModel.clearMessage()
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("This will permanently delete all history entries. Your actual data is not affected.") },
            confirmButton = {
                Button(onClick = { viewModel.clearHistory(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                    }
                }
            )
        },
        snackbarHost = {
            uiState.message?.let { msg ->
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.values().forEach { filter ->
                    FilterChip(
                        selected = uiState.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (uiState.isRestoring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (uiState.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No history yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Changes to transactions, savings, and categories will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Scaffold
            }

            // Group entries by date
            val grouped = uiState.entries.groupBy { entry ->
                val cal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                val today = Calendar.getInstance()
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                when {
                    isSameDay(cal, today)     -> "Today"
                    isSameDay(cal, yesterday) -> "Yesterday"
                    else                      -> dateFmt.format(Date(entry.timestamp))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (dateLabel, entries) ->
                    item {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            time = timeFmt.format(Date(entry.timestamp)),
                            onRestore = { viewModel.restore(entry) },
                            isRestoring = uiState.isRestoring
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: ChangeLogEntry,
    time: String,
    onRestore: () -> Unit,
    isRestoring: Boolean
) {
    val (actionLabel, actionColor) = when (entry.action) {
        ChangeAction.CREATE -> "Added"   to MaterialTheme.colorScheme.primary
        ChangeAction.UPDATE -> "Edited"  to MaterialTheme.colorScheme.secondary
        ChangeAction.DELETE -> "Deleted" to MaterialTheme.colorScheme.error
    }

    val entityIcon = when (entry.entityType) {
        HistoryEntityType.TRANSACTION -> Icons.Default.Receipt
        HistoryEntityType.SAVINGS     -> Icons.Default.Savings
        HistoryEntityType.CATEGORY    -> Icons.Default.Category
    }

    val canRestore = entry.action == ChangeAction.DELETE || entry.action == ChangeAction.UPDATE

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(entityIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = actionColor.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                actionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = actionColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(entry.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }

            if (canRestore) {
                TextButton(onClick = onRestore, enabled = !isRestoring) {
                    Text(
                        if (entry.action == ChangeAction.DELETE) "Restore" else "Revert",
                        color = if (entry.action == ChangeAction.DELETE)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

private fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
