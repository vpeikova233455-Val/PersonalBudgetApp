package com.budgetapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.budgetapp.domain.model.Category

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelect: (Category) -> Unit,
    onDismiss: () -> Unit,
    onCreateCategory: ((name: String, icon: String) -> Unit)? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, icon ->
                onCreateCategory?.invoke(name, icon)
                showCreateDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelect(category) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCategory?.id == category.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.icon,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                if (onCreateCategory != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateDialog = true },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Create new category",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("📋") }
    var nameError by remember { mutableStateOf<String?>(null) }

    val icons = listOf(
        "💰", "💼", "📈", "🏠", "🍽️", "🚗", "🛍️", "🎬",
        "⚕️", "💡", "🛡️", "📚", "💎", "📋", "🎮", "✈️",
        "🏋️", "🎨", "🐕", "☕", "🍺", "🎵", "📱", "💻",
        "💳", "🔌", "⚡", "🏫", "🔥", "🏦", "🧸", "💸",
        "🏘️", "🍕", "💊", "🚌", "💧", "🏃", "🏛️", "💄"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Category Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Icon", style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(icons) { icon ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = if (icon == selectedIcon)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                                .border(
                                    width = if (icon == selectedIcon) 2.dp else 1.dp,
                                    color = if (icon == selectedIcon)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = icon, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) {
                    nameError = "Name is required"
                } else {
                    onCreate(name.trim(), selectedIcon)
                }
            }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
