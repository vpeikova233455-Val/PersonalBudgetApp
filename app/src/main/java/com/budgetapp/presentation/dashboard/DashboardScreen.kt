package com.budgetapp.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetapp.core.util.toCurrency
import com.budgetapp.core.util.toDateString
import com.budgetapp.data.local.entity.TransactionType
import com.budgetapp.domain.model.Transaction
import com.budgetapp.presentation.theme.BrandBlue
import com.budgetapp.presentation.theme.BrandBlueDark
import com.budgetapp.presentation.theme.CategoryBlue
import com.budgetapp.presentation.theme.CategoryEmerald
import com.budgetapp.presentation.theme.CategoryGreen
import com.budgetapp.presentation.theme.CategoryOrange
import com.budgetapp.presentation.theme.CategoryPurple
import com.budgetapp.presentation.theme.CategoryRed
import com.budgetapp.presentation.theme.CategoryYellow
import com.budgetapp.presentation.theme.ExpenseRed
import com.budgetapp.presentation.theme.IncomeGreen
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAddTransaction: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToImport: () -> Unit,
    onTransactionClick: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) viewModel.refresh()
    }
    LaunchedEffect(uiState) {
        if (uiState is DashboardUiState.Success && !(uiState as DashboardUiState.Success).isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddTransaction,
                containerColor = BrandBlue,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Fixed header — lives outside the PTR Box so the PTR indicator
            // (which animates with graphicsLayer and keeps its layout position)
            // can never block these buttons.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good day!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "My Wallet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onNavigateToImport) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = "Import",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Scrollable area — nestedScroll scoped only to this Box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                when (val state = uiState) {
                    is DashboardUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = BrandBlue) }
                    }

                    is DashboardUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = viewModel::refresh) { Text("Retry") }
                            }
                        }
                    }

                    is DashboardUiState.Success -> {
                        val data = state.data
                        val spendingRatio = if (data.totalIncome > 0)
                            min(data.totalExpenses / data.totalIncome, 1.0).toFloat()
                        else 0f

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            // Balance card
                            item {
                                BalanceCard(
                                    balance = data.balance,
                                    income = data.totalIncome,
                                    expenses = data.totalExpenses,
                                    monthLabel = viewModel.selectedMonthLabel(),
                                    onPreviousMonth = viewModel::previousMonth,
                                    onNextMonth = viewModel::nextMonth,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }

                            // Spending progress card
                            if (data.totalIncome > 0 || data.totalExpenses > 0) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    SpendingProgressCard(
                                        totalExpenses = data.totalExpenses,
                                        totalIncome = data.totalIncome,
                                        spendingRatio = spendingRatio,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // Recent transactions header
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Recent Activity",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (data.recentTransactions.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "No transactions yet",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = onNavigateToImport,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Import")
                                            }
                                            Button(
                                                onClick = onNavigateToAddTransaction,
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(data.recentTransactions) { transaction ->
                                    StyledTransactionItem(
                                        transaction = transaction,
                                        onClick = { onTransactionClick(transaction.id) },
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    income: Double,
    expenses: Double,
    monthLabel: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1142D4), Color(0xFF0A2A9F)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .padding(24.dp)
    ) {
        Column {
            // Month navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White.copy(alpha = 0.8f))
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White.copy(alpha = 0.8f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = balance.toCurrency(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Income
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IncomeGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+${income.toCurrency()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                // Expenses
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ExpenseRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Expenses",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-${expenses.toCurrency()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingProgressCard(
    totalExpenses: Double,
    totalIncome: Double,
    spendingRatio: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Spending",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(spendingRatio * 100).toInt()}% of income",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (spendingRatio > 0.9f) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { spendingRatio },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = when {
                    spendingRatio > 0.9f -> ExpenseRed
                    spendingRatio > 0.7f -> WarningOrange
                    else -> BrandBlue
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${totalExpenses.toCurrency()} spent of ${totalIncome.toCurrency()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StyledTransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = categoryColor(transaction.category.name)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = transaction.category.icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${transaction.category.name} · ${transaction.date.toDateString("MMM d")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (transaction.type == TransactionType.INCOME) "+${transaction.amount.toCurrency()}"
                else "-${transaction.amount.toCurrency()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.type == TransactionType.INCOME) IncomeGreen else ExpenseRed
            )
        }
    }
}

private val WarningOrange = Color(0xFFFF9800)

fun categoryColor(categoryName: String): Color {
    val name = categoryName.lowercase()
    return when {
        name.contains("food") || name.contains("grocer") || name.contains("restaurant") -> CategoryOrange
        name.contains("transport") || name.contains("travel") || name.contains("uber") -> CategoryBlue
        name.contains("shop") || name.contains("cloth") -> CategoryPurple
        name.contains("health") || name.contains("medical") -> CategoryRed
        name.contains("income") || name.contains("salary") || name.contains("dining") -> CategoryEmerald
        name.contains("saving") || name.contains("invest") -> CategoryGreen
        name.contains("util") || name.contains("bill") || name.contains("electric") -> CategoryRed
        else -> CategoryYellow
    }
}
