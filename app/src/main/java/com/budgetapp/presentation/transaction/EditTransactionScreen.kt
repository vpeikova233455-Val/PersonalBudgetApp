package com.budgetapp.presentation.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.core.util.toDateString
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.presentation.components.CategoryPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            viewModel.resetSavedState()
            onNavigateBack()
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            selectedCategory = formState.selectedCategory,
            onCategorySelect = {
                viewModel.onCategorySelect(it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
            onCreateCategory = { name, icon ->
                viewModel.createCategory(name, icon)
                showCategoryPicker = false
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.date
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            viewModel.onDateChange(it)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
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
            // Transaction Type
            Text(
                text = "Transaction Type",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = formState.type == TransactionType.INCOME,
                    onClick = { viewModel.onTypeChange(TransactionType.INCOME) },
                    label = { Text("Income") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = formState.type == TransactionType.EXPENSE,
                    onClick = { viewModel.onTypeChange(TransactionType.EXPENSE) },
                    label = { Text("Expense") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Amount
            OutlinedTextField(
                value = formState.amount,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount") },
                prefix = { Text("₪") },
                isError = formState.amountError != null,
                supportingText = formState.amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Description
            OutlinedTextField(
                value = formState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                isError = formState.descriptionError != null,
                supportingText = formState.descriptionError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category
            OutlinedButton(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formState.selectedCategory?.let {
                            "${it.icon} ${it.name}"
                        } ?: "Select Category"
                    )
                }
            }
            if (formState.categoryError != null) {
                Text(
                    text = formState.categoryError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Date
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formState.date.toDateString())
                    Icon(Icons.Default.DateRange, contentDescription = "Select date")
                }
            }

            if (formState.error != null) {
                Text(
                    text = formState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = viewModel::saveTransaction,
                enabled = !formState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (formState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}
