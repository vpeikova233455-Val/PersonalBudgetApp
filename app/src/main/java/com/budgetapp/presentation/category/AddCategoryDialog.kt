package com.budgetapp.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    viewModel: CategoryViewModel
) {
    val formState by viewModel.formState.collectAsState()

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Category") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Category Name") },
                    isError = formState.nameError != null,
                    supportingText = formState.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon Picker
                Text(
                    text = "Select Icon",
                    style = MaterialTheme.typography.labelLarge
                )
                IconPicker(
                    selectedIcon = formState.icon,
                    onIconSelect = viewModel::onIconChange
                )
                if (formState.iconError != null) {
                    Text(
                        text = formState.iconError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Color Picker
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelLarge
                )
                ColorPicker(
                    selectedColor = formState.color,
                    onColorSelect = viewModel::onColorChange
                )
                if (formState.colorError != null) {
                    Text(
                        text = formState.colorError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (formState.error != null) {
                    Text(
                        text = formState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::saveCategory,
                enabled = !formState.isLoading
            ) {
                if (formState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun IconPicker(
    selectedIcon: String,
    onIconSelect: (String) -> Unit
) {
    val icons = listOf(
        "💰", "💼", "📈", "🏠", "🍽️", "🚗", "🛍️", "🎬",
        "⚕️", "💡", "🛡️", "📚", "💎", "📋", "🎮", "✈️",
        "🏋️", "🎨", "🐕", "☕", "🍺", "🎵", "📱", "💻"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.height(120.dp),
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
                    .clickable { onIconSelect(icon) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun ColorPicker(
    selectedColor: String,
    onColorSelect: (String) -> Unit
) {
    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
        "#795548", "#607D8B"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { colorHex ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color(android.graphics.Color.parseColor(colorHex)),
                        shape = CircleShape
                    )
                    .border(
                        width = if (colorHex == selectedColor) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clickable { onColorSelect(colorHex) }
            )
        }
    }
}
