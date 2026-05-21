package com.budgetapp.presentation.savings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.data.local.entity.AccountType
import com.budgetapp.data.local.entity.RecurrenceFrequency
import com.budgetapp.domain.model.PensionAccount
import java.text.NumberFormat
import java.util.Locale

private val currencyFmt = NumberFormat.getCurrencyInstance(Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(viewModel: SavingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddEdit by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PensionAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<PensionAccount?>(null) }

    if (showAddEdit) {
        AddEditAccountDialog(
            initial = editing,
            onSave = { name, provider, value, contrib, employer, freq, type, notes ->
                viewModel.saveAccount(
                    id = editing?.id ?: 0L,
                    accountName = name,
                    provider = provider,
                    currentValue = value,
                    contributionAmount = contrib,
                    employerContribution = employer,
                    contributionFrequency = freq,
                    accountType = type,
                    notes = notes
                )
                showAddEdit = false
                editing = null
            },
            onDismiss = { showAddEdit = false; editing = null }
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Account") },
            text = { Text("Delete \"${account.accountName}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(account.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    uiState.error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Savings & Investments") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showAddEdit = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Summary card
            item {
                SummaryCard(
                    totalValue = uiState.totalValue,
                    totalMonthly = uiState.totalMonthlyContribution,
                    valueByType = uiState.valueByType
                )
            }

            if (uiState.accounts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Savings,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No accounts yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Tap + to add your first savings or investment account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Group by type
                val grouped = uiState.accounts.groupBy { it.accountType }
                AccountType.values().forEach { type ->
                    val list = grouped[type] ?: return@forEach
                    item {
                        Text(
                            type.displayName(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(list, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onEdit = { editing = account; showAddEdit = true },
                            onDelete = { deleteTarget = account }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // FAB clearance
        }
    }
}

@Composable
private fun SummaryCard(
    totalValue: Double,
    totalMonthly: Double,
    valueByType: Map<AccountType, Double>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total Portfolio", style = MaterialTheme.typography.labelMedium)
            Text(
                currencyFmt.format(totalValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (totalMonthly > 0) {
                Text(
                    "${currencyFmt.format(totalMonthly)} / month contributed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (valueByType.size > 1) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                valueByType.entries.sortedByDescending { it.value }.forEach { (type, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(type.displayName(), style = MaterialTheme.typography.bodySmall)
                        Text(currencyFmt.format(value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: PensionAccount,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = account.accountType.icon(),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = account.accountType.color()
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(account.accountName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (account.provider.isNotBlank()) {
                    Text(account.provider, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (account.contributionAmount > 0) {
                    Text(
                        "${currencyFmt.format(account.totalMonthlyContribution)} / month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (account.notes.isNotBlank()) {
                    Text(
                        account.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    currencyFmt.format(account.currentValue),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditAccountDialog(
    initial: PensionAccount?,
    onSave: (String, String, Double, Double, Double?, RecurrenceFrequency, AccountType, String) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initial != null
    var name by rememberSaveable { mutableStateOf(initial?.accountName ?: "") }
    var provider by rememberSaveable { mutableStateOf(initial?.provider ?: "") }
    var valueStr by rememberSaveable { mutableStateOf(if (initial != null && initial.currentValue > 0) initial.currentValue.toString() else "") }
    var contribStr by rememberSaveable { mutableStateOf(if (initial != null && initial.contributionAmount > 0) initial.contributionAmount.toString() else "") }
    var employerStr by rememberSaveable { mutableStateOf(initial?.employerContribution?.toString() ?: "") }
    var freq by rememberSaveable { mutableStateOf(initial?.contributionFrequency ?: RecurrenceFrequency.MONTHLY) }
    var accountType by rememberSaveable { mutableStateOf(initial?.accountType ?: AccountType.SAVINGS) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var valueError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Account" else "Add Account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Account type chips
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccountType.values().forEach { type ->
                        FilterChip(
                            selected = accountType == type,
                            onClick = { accountType = type },
                            label = { Text(type.displayName(), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Account Name *") },
                    placeholder = { Text("e.g. Emergency Fund, S&P 500 ETF") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Required") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text("Institution / Provider") },
                    placeholder = { Text("e.g. Vanguard, Fidelity, Chase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = valueStr,
                    onValueChange = { valueStr = it; valueError = false },
                    label = { Text("Current Value *") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = valueError,
                    supportingText = if (valueError) {{ Text("Enter a valid amount") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contribStr,
                    onValueChange = { contribStr = it },
                    label = { Text("Monthly Contribution") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (accountType == AccountType.PENSION) {
                    OutlinedTextField(
                        value = employerStr,
                        onValueChange = { employerStr = it },
                        label = { Text("Employer Contribution") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (contribStr.isNotBlank() && contribStr.toDoubleOrNull() != null && contribStr.toDouble() > 0) {
                    Text("Contribution frequency", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(RecurrenceFrequency.WEEKLY, RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY)
                            .forEach { f ->
                                FilterChip(
                                    selected = freq == f,
                                    onClick = { freq = f },
                                    label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Optional notes") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                nameError = name.isBlank()
                valueError = valueStr.toDoubleOrNull() == null
                if (nameError || valueError) return@Button
                onSave(
                    name,
                    provider,
                    valueStr.toDouble(),
                    contribStr.toDoubleOrNull() ?: 0.0,
                    employerStr.toDoubleOrNull(),
                    freq,
                    accountType,
                    notes
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun AccountType.displayName() = when (this) {
    AccountType.SAVINGS -> "Savings"
    AccountType.INVESTMENT -> "Investments"
    AccountType.PENSION -> "Pension"
    AccountType.CRYPTO -> "Crypto"
    AccountType.OTHER -> "Other"
}

private fun AccountType.icon() = when (this) {
    AccountType.SAVINGS -> Icons.Default.Savings
    AccountType.INVESTMENT -> Icons.Default.TrendingUp
    AccountType.PENSION -> Icons.Default.AccountBalance
    AccountType.CRYPTO -> Icons.Default.CurrencyBitcoin
    AccountType.OTHER -> Icons.Default.Wallet
}

private fun AccountType.color(): Color = when (this) {
    AccountType.SAVINGS -> Color(0xFF2196F3)
    AccountType.INVESTMENT -> Color(0xFF4CAF50)
    AccountType.PENSION -> Color(0xFF9C27B0)
    AccountType.CRYPTO -> Color(0xFFFF9800)
    AccountType.OTHER -> Color(0xFF607D8B)
}
