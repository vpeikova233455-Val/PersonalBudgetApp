package com.budgetapp.presentation.drilldown

import androidx.compose.foundation.clickable
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
import com.budgetapp.core.util.toCurrency
import com.budgetapp.core.util.toDateString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDrillDownScreen(
    onNavigateBack: () -> Unit,
    onTransactionClick: (String) -> Unit,
    viewModel: TransactionDrillDownViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Header summary
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.total.toCurrency(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${state.count} transaction${if (state.count == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search transactions…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // Grouping chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.grouping == DrillGrouping.CATEGORY,
                    onClick = { viewModel.setGrouping(DrillGrouping.CATEGORY) },
                    label = { Text("By Category") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = state.grouping == DrillGrouping.MONTH,
                    onClick = { viewModel.setGrouping(DrillGrouping.MONTH) },
                    label = { Text("By Month") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions match this view", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.id}") {
                            GroupHeader(
                                label = group.label,
                                subtotal = group.subtotal,
                                count = if (group.subGroups.isNotEmpty()) group.subGroups.sumOf { it.transactions.size } else group.transactions.size,
                                expanded = group.expanded,
                                indent = 0,
                                onClick = { viewModel.toggleExpanded(group.id) }
                            )
                        }
                        if (group.expanded) {
                            // Nested: month → categories → transactions
                            if (group.subGroups.isNotEmpty()) {
                                group.subGroups.forEach { sub ->
                                    item(key = "subheader-${sub.id}") {
                                        GroupHeader(
                                            label = sub.label,
                                            subtotal = sub.subtotal,
                                            count = sub.transactions.size,
                                            expanded = sub.expanded,
                                            indent = 1,
                                            onClick = { viewModel.toggleExpanded(sub.id) }
                                        )
                                    }
                                    if (sub.expanded) {
                                        items(sub.transactions, key = { "tx-${sub.id}-${it.id}" }) { tx ->
                                            TransactionRow(
                                                description = tx.description,
                                                category = tx.category.name,
                                                icon = tx.category.icon,
                                                amount = tx.amount,
                                                date = tx.date,
                                                indent = 2,
                                                onClick = { onTransactionClick(tx.id) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Flat: category → transactions (CATEGORY mode)
                                items(group.transactions, key = { "tx-${group.id}-${it.id}" }) { tx ->
                                    TransactionRow(
                                        description = tx.description,
                                        category = tx.category.name,
                                        icon = tx.category.icon,
                                        amount = tx.amount,
                                        date = tx.date,
                                        indent = 1,
                                        onClick = { onTransactionClick(tx.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    label: String,
    subtotal: Double,
    count: Int,
    expanded: Boolean,
    indent: Int,
    onClick: () -> Unit
) {
    val isTopLevel = indent == 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (isTopLevel) 12.dp else 8.dp),
        color = if (isTopLevel) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isTopLevel) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (isTopLevel) 12.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(if (isTopLevel) 24.dp else 18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = if (isTopLevel) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isTopLevel) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    "$count item${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                subtotal.toCurrency(),
                style = if (isTopLevel) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TransactionRow(
    description: String,
    category: String,
    icon: String,
    amount: Double,
    date: Long,
    indent: Int = 0,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(description, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    "$category · ${date.toDateString("MMM d, yyyy")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(amount.toCurrency(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
