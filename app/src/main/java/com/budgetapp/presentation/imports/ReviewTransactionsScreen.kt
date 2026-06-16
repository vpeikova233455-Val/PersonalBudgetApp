package com.budgetapp.presentation.imports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.presentation.components.CategoryPickerDialog
import com.budgetapp.presentation.transaction.NotesField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTransactionsScreen(
    onNavigateBack: () -> Unit,
    onAllApproved: () -> Unit,
    viewModel: ReviewTransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.allApproved) {
        if (uiState.allApproved) onAllApproved()
    }

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
                        TextButton(onClick = { viewModel.approveAll() }) {
                            Text("Approve All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.pendingTransactions.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("All transactions reviewed!", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.pendingTransactions, key = { it.id }) { pending ->
                        PendingTransactionCard(
                            pending = pending,
                            categories = uiState.categories,
                            onApprove = { viewModel.approvePendingTransaction(pending.id) },
                            onDelete = { viewModel.deletePendingTransaction(pending.id) },
                            onSelectCategory = { catId, catName ->
                                viewModel.selectCategory(pending.id, catId, catName)
                            },
                            onCreateCategory = { name, icon ->
                                viewModel.createCategory(pending.id, name, icon)
                            },
                            onToggleAutomatic = { viewModel.toggleWantsAutomatic(pending.id) },
                            onNotesChange = { notes -> viewModel.updateNotes(pending.id, notes) }
                        )
                    }
                }
            }
        }
    }
}

// ── Pending card ──────────────────────────────────────────────────────────────

@Composable
private fun PendingTransactionCard(
    pending: PendingTransactionUiModel,
    categories: List<CategoryUiModel>,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
    onSelectCategory: (Long, String) -> Unit,
    onCreateCategory: (name: String, icon: String) -> Unit,
    onToggleAutomatic: () -> Unit,
    onNotesChange: (String) -> Unit
) {
    var showCategoryPicker by remember { mutableStateOf(false) }

    if (showCategoryPicker) {
        val domainCategories = categories.map {
            com.budgetapp.domain.model.Category(id = it.id, name = it.name, icon = it.icon, color = "#607D8B")
        }
        CategoryPickerDialog(
            categories = domainCategories,
            selectedCategory = domainCategories.find { it.id == pending.selectedCategoryId },
            onCategorySelect = { cat ->
                onSelectCategory(cat.id, cat.name)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
            onCreateCategory = { name, icon ->
                onCreateCategory(name, icon)
                showCategoryPicker = false
            }
        )
    }

    val isAutoApplied = pending.learningState is LearningState.Known &&
        (pending.learningState as LearningState.Known).isAutomatic

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoApplied)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: source + amount + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pending.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        pending.formattedDate ?: "Date unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    pending.formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (pending.type == "INCOME")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            // Duplicate-detection warning (when a likely match exists in the
            // already-approved transactions table).
            pending.duplicateOf?.let { dupDesc ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Possible duplicate of \"$dupDesc\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (pending.duplicateReasons.isNotEmpty()) {
                            Text(
                                pending.duplicateReasons.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Learning-state-aware category section ──────────────────────────

            when (val ls = pending.learningState) {
                is LearningState.Unknown -> {
                    // First time — show picker button
                    if (pending.selectedCategoryId != null) {
                        CategoryChip(
                            name = pending.selectedCategoryName ?: "",
                            onClick = { showCategoryPicker = true }
                        )
                    } else {
                        OutlinedButton(
                            onClick = { showCategoryPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Category, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Assign Category")
                        }
                    }
                }

                is LearningState.Known -> {
                    if (ls.isAutomatic) {
                        // Auto-applied
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Auto: ${ls.categoryName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            TextButton(onClick = { showCategoryPicker = true }) {
                                Text("Change")
                            }
                        }
                    } else {
                        // Seen before — suggest and offer confirm/change
                        val timesLabel = if (ls.timesSeenBefore == 1) "last time" else "${ls.timesSeenBefore} times before"
                        Text(
                            "You assigned \"${ls.categoryName}\" $timesLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        if (pending.selectedCategoryId != null && pending.selectedCategoryId != ls.categoryId) {
                            // User changed the category
                            CategoryChip(name = pending.selectedCategoryName ?: "", onClick = { showCategoryPicker = true })
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    onSelectCategory(ls.categoryId, ls.categoryName)
                                }, modifier = Modifier.weight(1f)) {
                                    Text("Same: ${ls.categoryName}")
                                }
                                OutlinedButton(onClick = { showCategoryPicker = true }, modifier = Modifier.weight(1f)) {
                                    Text("Change")
                                }
                            }
                        }

                        // On 3rd+ encounter offer "always auto"
                        if (ls.timesSeenBefore >= 2) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = pending.wantsAutomatic,
                                    onCheckedChange = { onToggleAutomatic() }
                                )
                                Text(
                                    "Always auto-categorize as \"${pending.selectedCategoryName ?: ls.categoryName}\"",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // AI questions (if any)
            pending.aiQuestions?.takeIf { it.isNotEmpty() }?.let { questions ->
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("AI notes:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        questions.forEach { q ->
                            Text("• $q", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Optional note
            NotesField(notes = pending.notes, onChange = onNotesChange)

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    enabled = pending.selectedCategoryId != null || isAutoApplied
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(name: String, onClick: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClick,
        label = { Text(name) },
        leadingIcon = { Icon(Icons.Default.Category, null, modifier = Modifier.size(16.dp)) },
        trailingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp)) }
    )
}
