package com.budgetapp.presentation.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetapp.domain.model.Category

@Composable
fun EditCategoryDialog(
    editingCategory: Category?,
    onDismiss:       () -> Unit,
    onSave:          (name: String, icon: String, color: String) -> Unit
) {
    val isEditing = editingCategory != null

    var name  by remember { mutableStateOf(editingCategory?.name  ?: "") }
    var icon  by remember { mutableStateOf(editingCategory?.icon  ?: "") }
    var color by remember { mutableStateOf(editingCategory?.color ?: "") }

    var nameError  by remember { mutableStateOf<String?>(null) }
    var iconError  by remember { mutableStateOf<String?>(null) }
    var colorError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        nameError  = if (name.isBlank())  "Required" else null
        iconError  = if (icon.isBlank())  "Select an icon" else null
        colorError = if (color.isBlank()) "Select a color" else null
        return nameError == null && iconError == null && colorError == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Category" else "New Category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview chip
                if (icon.isNotBlank() || color.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (color.isNotBlank()) parseHexColor(color) else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (icon.isNotBlank()) Text(icon, fontSize = 20.sp)
                        }
                        if (name.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Category name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon picker
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                IconPicker(selectedIcon = icon, onIconSelect = { icon = it; iconError = null })
                if (iconError != null) {
                    Text(iconError!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                // Color picker
                Text("Color", style = MaterialTheme.typography.labelLarge)
                ColorPicker(selectedColor = color, onColorSelect = { color = it; colorError = null })
                if (colorError != null) {
                    Text(colorError!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (validate()) onSave(name, icon, color) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Icon picker ───────────────────────────────────────────────────────────────

@Composable
fun IconPicker(selectedIcon: String, onIconSelect: (String) -> Unit) {
    val icons = listOf(
        // Finance
        "💰", "💳", "💵", "💴", "💸", "🏦", "📈", "📉", "🪙", "💎",
        // Home & living
        "🏠", "🏘️", "🏗️", "🛋️", "🔌", "⚡", "💧", "🔥", "🗑️", "🛁",
        // Food & drink
        "🍽️", "🍕", "🍔", "🥗", "☕", "🍺", "🧃", "🛒", "🧺", "🍞",
        // Transport
        "🚗", "🚌", "✈️", "🚂", "🚲", "🛵", "⛽", "🅿️", "🚕", "🚐",
        // Health & wellness
        "⚕️", "💊", "🏥", "🧪", "🏃", "🏋️", "🧘", "🩺", "💆", "🦷",
        // Education & work
        "📚", "🏫", "💼", "🖥️", "📱", "🖨️", "📝", "🎓", "🔬", "📐",
        // Entertainment & leisure
        "🎬", "🎵", "🎮", "🎨", "🎭", "📷", "🎙️", "🎲", "🏖️", "🏕️",
        // Shopping
        "🛍️", "👗", "👟", "💄", "💍", "🧴", "🪒", "🕶️", "🧢", "👜",
        // People & family
        "👶", "🧒", "👦", "👧", "👨", "👩", "👴", "👵", "👨‍👩‍👧", "🐕",
        // Misc
        "🎁", "🌱", "🌍", "🏛️", "🛡️", "📋", "⭐", "🔧", "🧸", "🎀"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        items(icons) { ico ->
            val selected = ico == selectedIcon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        width  = if (selected) 2.dp else 1.dp,
                        color  = if (selected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape  = CircleShape
                    )
                    .clickable { onIconSelect(ico) },
                contentAlignment = Alignment.Center
            ) {
                Text(ico, fontSize = 16.sp)
            }
        }
    }
}

// ── Color picker ──────────────────────────────────────────────────────────────

@Composable
fun ColorPicker(selectedColor: String, onColorSelect: (String) -> Unit) {
    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
        "#795548", "#607D8B"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { hex ->
            val selected = hex == selectedColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(parseHexColor(hex), CircleShape)
                    .border(
                        width  = if (selected) 3.dp else 0.dp,
                        color  = MaterialTheme.colorScheme.primary,
                        shape  = CircleShape
                    )
                    .clickable { onColorSelect(hex) }
            )
        }
    }
}
