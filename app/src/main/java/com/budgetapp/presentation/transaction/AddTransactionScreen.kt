package com.budgetapp.presentation.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.core.util.toDateString
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Category
import com.budgetapp.presentation.components.CategoryPickerDialog
import com.budgetapp.presentation.theme.BrandBlue
import com.budgetapp.presentation.theme.ExpenseRed
import com.budgetapp.presentation.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

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
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = formState.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.onDateChange(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::saveTransaction,
                        enabled = !formState.isLoading
                    ) {
                        if (formState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save",
                                tint = BrandBlue
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Type toggle + Amount area (blue/teal header block)
            val headerColor = if (formState.type == TransactionType.EXPENSE) BrandBlue else IncomeGreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Expense / Income segmented toggle
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.2f),
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            ToggleChip(
                                label = "Expense",
                                selected = formState.type == TransactionType.EXPENSE,
                                onClick = { viewModel.onTypeChange(TransactionType.EXPENSE) }
                            )
                            ToggleChip(
                                label = "Income",
                                selected = formState.type == TransactionType.INCOME,
                                onClick = { viewModel.onTypeChange(TransactionType.INCOME) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Amount display
                    Text(
                        text = "₪",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = formState.amount,
                        onValueChange = viewModel::onAmountChange,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        ),
                        placeholder = {
                            Text(
                                "0.00",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        isError = formState.amountError != null
                    )
                    if (formState.amountError != null) {
                        Text(
                            text = formState.amountError!!,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Form fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
                OutlinedTextField(
                    value = formState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Description / Note") },
                    isError = formState.descriptionError != null,
                    supportingText = formState.descriptionError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category section
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick category pills
                if (categories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        items(categories.take(6)) { category ->
                            CategoryPill(
                                category = category,
                                selected = formState.selectedCategory?.id == category.id,
                                onClick = { viewModel.onCategorySelect(category) }
                            )
                        }
                        item {
                            CategoryPill(
                                category = null,
                                selected = false,
                                label = "More…",
                                onClick = { showCategoryPicker = true }
                            )
                        }
                    }
                }

                if (formState.selectedCategory == null || categories.isEmpty()) {
                    OutlinedButton(
                        onClick = { showCategoryPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(formState.selectedCategory?.let { "${it.icon} ${it.name}" } ?: "Select Category")
                    }
                }

                if (formState.categoryError != null) {
                    Text(
                        text = formState.categoryError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Date picker
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formState.date.toDateString("MMM d, yyyy"))
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                }

                if (formState.error != null) {
                    Text(
                        text = formState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Notes — collapsible, shown only on demand
                NotesField(
                    notes    = formState.notes,
                    onChange = viewModel::onNotesChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Save button
                Button(
                    onClick = viewModel::saveTransaction,
                    enabled = !formState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Save Transaction", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// Shared by AddTransactionScreen and EditTransactionScreen
@Composable
fun NotesField(notes: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(notes.isNotBlank()) }

    // Auto-expand when an existing note is loaded asynchronously
    LaunchedEffect(notes) { if (notes.isNotBlank() && !expanded) expanded = true }

    if (!expanded) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Icon(
                Icons.Default.Notes,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Add note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    AnimatedVisibility(visible = expanded) {
        OutlinedTextField(
            value = notes,
            onValueChange = onChange,
            label = { Text("Note (optional)") },
            trailingIcon = {
                IconButton(onClick = { onChange(""); expanded = false }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove note",
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) BrandBlue else Color.White
        )
    }
}

@Composable
private fun CategoryPill(
    category: Category?,
    selected: Boolean,
    label: String? = null,
    onClick: () -> Unit
) {
    val text = label ?: category?.let { "${it.icon} ${it.name}" } ?: "?"
    val borderColor = if (selected) BrandBlue else MaterialTheme.colorScheme.outline
    val bgColor = if (selected) BrandBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurface
        )
    }
}
