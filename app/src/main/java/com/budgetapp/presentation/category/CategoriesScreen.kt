package com.budgetapp.presentation.category

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.domain.model.Category
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Edit / Add dialog
    if (uiState.showEditDialog) {
        EditCategoryDialog(
            editingCategory = uiState.editingCategory,
            onDismiss       = viewModel::closeEditDialog,
            onSave          = { name, icon, color -> viewModel.saveCategory(name, icon, color) }
        )
    }

    // Delete dialog
    if (uiState.showDeleteDialog && uiState.deletingCategory != null) {
        DeleteCategoryDialog(
            category        = uiState.deletingCategory!!,
            allCategories   = uiState.categories,
            option          = uiState.deleteOption,
            reassignTargetId = uiState.reassignTargetId,
            onOptionChange  = viewModel::setDeleteOption,
            onTargetChange  = viewModel::setReassignTarget,
            onConfirm       = viewModel::confirmDelete,
            onDismiss       = viewModel::dismissDeleteDialog
        )
    }

    // Merge dialog
    if (uiState.showMergeDialog && uiState.mergingFrom != null) {
        MergeCategoryDialog(
            source          = uiState.mergingFrom!!,
            allCategories   = uiState.categories,
            targetId        = uiState.mergeTargetId,
            onTargetChange  = viewModel::setMergeTarget,
            onConfirm       = viewModel::confirmMerge,
            onDismiss       = viewModel::dismissMergeDialog
        )
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            snackbarHostState.showSnackbar(uiState.error!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddDialog) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            viewModel.moveCategory(from.index, to.index)
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Long-press the drag handle to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(uiState.categories, key = { it.id }) { category ->
                ReorderableItem(reorderState, key = category.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 1.dp, label = "elevation")
                    CategoryManageItem(
                        category    = category,
                        isDragging  = isDragging,
                        elevation   = elevation,
                        dragHandle  = {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle(
                                        onDragStopped = { viewModel.commitReorder() }
                                    )
                                    .padding(end = 4.dp)
                            )
                        },
                        onEdit      = { viewModel.openEditDialog(category) },
                        onMerge     = { viewModel.openMergeDialog(category) },
                        onDelete    = { viewModel.openDeleteDialog(category) }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CategoryManageItem(
    category:   Category,
    isDragging: Boolean,
    elevation:  androidx.compose.ui.unit.Dp,
    dragHandle: @Composable () -> Unit,
    onEdit:     () -> Unit,
    onMerge:    () -> Unit,
    onDelete:   () -> Unit
) {
    Surface(
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(12.dp),
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dragHandle()

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(category.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(category.icon, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text     = category.name,
                style    = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Edit, "Edit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onMerge, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.MergeType, "Merge into…",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Delete, "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Delete dialog ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteCategoryDialog(
    category:         Category,
    allCategories:    List<Category>,
    option:           DeleteTransactionOption,
    reassignTargetId: Long?,
    onOptionChange:   (DeleteTransactionOption) -> Unit,
    onTargetChange:   (Long) -> Unit,
    onConfirm:        () -> Unit,
    onDismiss:        () -> Unit
) {
    val otherCategories = allCategories.filter { it.id != category.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${category.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What should happen to transactions currently in this category?")

                // Option A: Reassign
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == DeleteTransactionOption.REASSIGN,
                            onClick  = { onOptionChange(DeleteTransactionOption.REASSIGN) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = option == DeleteTransactionOption.REASSIGN,
                        onClick  = { onOptionChange(DeleteTransactionOption.REASSIGN) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Move transactions to another category")
                }

                // Target category dropdown (only shown when REASSIGN is selected)
                if (option == DeleteTransactionOption.REASSIGN && otherCategories.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val target = otherCategories.firstOrNull { it.id == reassignTargetId }
                        ?: otherCategories.first()

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = "${target.icon} ${target.name}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Move to") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            otherCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text("${cat.icon} ${cat.name}") },
                                    onClick = { onTargetChange(cat.id); expanded = false }
                                )
                            }
                        }
                    }
                }

                // Option B: Delete transactions
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == DeleteTransactionOption.DELETE_ALL,
                            onClick  = { onOptionChange(DeleteTransactionOption.DELETE_ALL) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = option == DeleteTransactionOption.DELETE_ALL,
                        onClick  = { onOptionChange(DeleteTransactionOption.DELETE_ALL) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Delete all transactions in this category")
                        Text(
                            "This cannot be undone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Merge dialog ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeCategoryDialog(
    source:          Category,
    allCategories:   List<Category>,
    targetId:        Long?,
    onTargetChange:  (Long) -> Unit,
    onConfirm:       () -> Unit,
    onDismiss:       () -> Unit
) {
    val otherCategories = allCategories.filter { it.id != source.id }
    var expanded by remember { mutableStateOf(false) }
    val target = otherCategories.firstOrNull { it.id == targetId } ?: otherCategories.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge \"${source.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("All transactions in \"${source.name}\" will be moved to the selected category, then \"${source.name}\" will be deleted.")

                if (target != null) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = "${target.icon} ${target.name}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Merge into") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            otherCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text("${cat.icon} ${cat.name}") },
                                    onClick = { onTargetChange(cat.id); expanded = false }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "No other categories available.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = onConfirm,
                enabled  = target != null,
                colors   = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Merge & Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Gray
}
