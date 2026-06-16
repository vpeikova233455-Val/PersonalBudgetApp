package com.budgetapp.presentation.recurring

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
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.usecase.transaction.TransactionAnalytics
import com.budgetapp.presentation.theme.ExpenseRed
import com.budgetapp.presentation.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onNavigateBack: () -> Unit,
    onPatternClick: (TransactionAnalytics.RecurringPattern) -> Unit,
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring & Expected", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.expectedThisMonth.isEmpty() && state.income.isEmpty() && state.expenses.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No recurring patterns detected yet", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "After you've imported a couple of months of transactions, salary deposits, mortgage payments, and subscriptions show up here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.expectedThisMonth.isNotEmpty()) {
                        item { SectionHeader("Expected This Month", subtitle = "Based on your past patterns") }
                        items(state.expectedThisMonth, key = { "expected-${it.descriptionKey}" }) { p ->
                            ExpectedRow(p, onClick = { onPatternClick(p) })
                        }
                    }
                    if (state.income.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)) }
                        item { SectionHeader("Recurring Income", subtitle = "Salary, refunds, regular deposits", accent = IncomeGreen) }
                        items(state.income, key = { "income-${it.descriptionKey}" }) { p ->
                            PatternRow(p, accent = IncomeGreen, onClick = { onPatternClick(p) })
                        }
                    }
                    if (state.expenses.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)) }
                        item { SectionHeader("Subscriptions & Bills", subtitle = "Mortgage, rent, fund contributions, subscriptions", accent = ExpenseRed) }
                        items(state.expenses, key = { "expense-${it.descriptionKey}" }) { p ->
                            PatternRow(p, accent = ExpenseRed, onClick = { onPatternClick(p) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, accent: androidx.compose.ui.graphics.Color? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpectedRow(p: TransactionAnalytics.RecurringPattern, onClick: () -> Unit) {
    val isIncome = p.type == TransactionType.INCOME
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.sampleDescription, style = MaterialTheme.typography.bodyMedium, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(
                    "${p.cadence.displayName} · due ${p.nextExpectedDate.toDateString("MMM d")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = (if (isIncome) "+" else "-") + p.avgAmount.toCurrency(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isIncome) IncomeGreen else ExpenseRed
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PatternRow(
    p: TransactionAnalytics.RecurringPattern,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.sampleDescription, style = MaterialTheme.typography.bodyMedium, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(
                    "${p.cadence.displayName} · seen ${p.occurrences}× · last ${p.lastDate.toDateString("MMM d")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                p.avgAmount.toCurrency(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
