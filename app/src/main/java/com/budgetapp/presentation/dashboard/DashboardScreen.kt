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
    onNavigateToReview: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onNavigateToDrillDown: (route: String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsState()

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
                                Button(onClick = viewModel::retry) { Text("Retry") }
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
                            // Monthly / All-Time toggle — gives users a one-tap way to see
                            // totals regardless of how transaction dates ended up stored.
                            item {
                                ScopeToggle(
                                    isAllTime = state.isAllTimeMode,
                                    onToggle = viewModel::toggleAllTimeMode,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }

                            // Balance card — income / expense numbers are tappable and open
                            // the drill-down screen scoped to the current month (or all-time).
                            item {
                                val (yr, mo) = selectedYearMonth
                                BalanceCard(
                                    balance = data.balance,
                                    income = data.totalIncome,
                                    expenses = data.totalExpenses,
                                    monthLabel = viewModel.selectedMonthLabel(),
                                    onPreviousMonth = if (state.isAllTimeMode) null else viewModel::previousMonth,
                                    onNextMonth = if (state.isAllTimeMode) null else viewModel::nextMonth,
                                    onIncomeClick = if (data.totalIncome > 0) ({
                                        onNavigateToDrillDown(drillRoute(
                                            type = "INCOME",
                                            year = if (state.isAllTimeMode) -1 else yr,
                                            month = if (state.isAllTimeMode) -1 else mo,
                                            allTime = state.isAllTimeMode,
                                            title = "Income · ${viewModel.selectedMonthLabel()}"
                                        ))
                                    }) else null,
                                    onExpensesClick = if (data.totalExpenses > 0) ({
                                        onNavigateToDrillDown(drillRoute(
                                            type = "EXPENSE",
                                            year = if (state.isAllTimeMode) -1 else yr,
                                            month = if (state.isAllTimeMode) -1 else mo,
                                            allTime = state.isAllTimeMode,
                                            title = "Expenses · ${viewModel.selectedMonthLabel()}"
                                        ))
                                    }) else null,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }

                            // Pending review banner — visible when there are unapproved imports.
                            if (state.pendingCount > 0) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    PendingReviewBanner(
                                        count = state.pendingCount,
                                        onClick = onNavigateToReview,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // All-time totals — visible when in monthly mode (in all-time mode the
                            // BalanceCard above already shows all-time numbers, so this card would
                            // be redundant). Each line drills into the all-time list for that type.
                            if (!state.isAllTimeMode && (state.allTimeIncome > 0 || state.allTimeExpense > 0)) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AllTimeTotalsCard(
                                        income = state.allTimeIncome,
                                        expense = state.allTimeExpense,
                                        onIncomeClick = if (state.allTimeIncome > 0) ({
                                            onNavigateToDrillDown(drillRoute("INCOME", -1, -1, true, title = "All-Time Income"))
                                        }) else null,
                                        onExpenseClick = if (state.allTimeExpense > 0) ({
                                            onNavigateToDrillDown(drillRoute("EXPENSE", -1, -1, true, title = "All-Time Expenses"))
                                        }) else null,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // "Income exists but not in this month" hint — show when there's
                            // all-time income but this month has none (only in monthly mode).
                            if (!state.isAllTimeMode && data.totalIncome == 0.0 && state.allTimeIncome > 0) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    IncomeElsewhereBanner(
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            } else if (!state.isAllTimeMode && data.totalExpenses > 0 && data.totalIncome == 0.0 && state.allTimeIncome == 0.0 && state.pendingCount == 0) {
                                // No income anywhere — invite user to import bank statement.
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    NoIncomeHintBanner(
                                        onImport = onNavigateToImport,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
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

                            // Monthly comparison — current vs previous month delta.
                            if (!state.isAllTimeMode && (state.prevMonthIncome > 0 || state.prevMonthExpense > 0 || data.totalIncome > 0 || data.totalExpenses > 0)) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    MonthlyComparisonCard(
                                        currentIncome = data.totalIncome,
                                        prevIncome = state.prevMonthIncome,
                                        currentExpense = data.totalExpenses,
                                        prevExpense = state.prevMonthExpense,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // 6-month trend chart — tap any bar to drill into that month.
                            if (state.monthlyTrend.size >= 2) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SixMonthTrendCard(
                                        trend = state.monthlyTrend,
                                        onBarClick = { bucket ->
                                            onNavigateToDrillDown(drillRoute(
                                                type = "ALL",
                                                year = bucket.year,
                                                month = bucket.month,
                                                title = bucket.label
                                            ))
                                        },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // Category breakdown — tappable rows: open inline subset of category
                            // transactions, or jump to the full drill-down screen.
                            val (yr, mo) = selectedYearMonth
                            if (data.totalExpenses > 0 && data.categoryBreakdown.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CategoryBreakdownCard(
                                        title = "Where Your Money Goes",
                                        breakdown = data.categoryBreakdown,
                                        total = data.totalExpenses,
                                        accent = ExpenseRed,
                                        onRowClick = { cat ->
                                            onNavigateToDrillDown(drillRoute(
                                                type = "EXPENSE",
                                                year = if (state.isAllTimeMode) -1 else yr,
                                                month = if (state.isAllTimeMode) -1 else mo,
                                                allTime = state.isAllTimeMode,
                                                categoryId = cat.id,
                                                title = "${cat.icon} ${cat.name}"
                                            ))
                                        },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // Income sources — same treatment.
                            if (data.totalIncome > 0 && data.incomeBreakdown.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    CategoryBreakdownCard(
                                        title = "Where Your Money Comes From",
                                        breakdown = data.incomeBreakdown,
                                        total = data.totalIncome,
                                        accent = IncomeGreen,
                                        onRowClick = { cat ->
                                            onNavigateToDrillDown(drillRoute(
                                                type = "INCOME",
                                                year = if (state.isAllTimeMode) -1 else yr,
                                                month = if (state.isAllTimeMode) -1 else mo,
                                                allTime = state.isAllTimeMode,
                                                categoryId = cat.id,
                                                title = "${cat.icon} ${cat.name}"
                                            ))
                                        },
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
    onPreviousMonth: (() -> Unit)?,
    onNextMonth: (() -> Unit)?,
    onIncomeClick: (() -> Unit)? = null,
    onExpensesClick: (() -> Unit)? = null,
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
                if (onPreviousMonth != null) {
                    IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White.copy(alpha = 0.8f))
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
                if (onNextMonth != null) {
                    IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White.copy(alpha = 0.8f))
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
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
                // Income — tap to drill down to underlying transactions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .let { if (onIncomeClick != null) it.clickable(onClick = onIncomeClick) else it }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Income", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                        if (onIncomeClick != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        }
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .let { if (onExpensesClick != null) it.clickable(onClick = onExpensesClick) else it }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Expenses", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                        if (onExpensesClick != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        }
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

@Composable
private fun MonthlyComparisonCard(
    currentIncome: Double,
    prevIncome: Double,
    currentExpense: Double,
    prevExpense: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("vs Previous Month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                ComparisonCell(
                    label = "Income",
                    accent = IncomeGreen,
                    current = currentIncome,
                    previous = prevIncome,
                    higherIsGood = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                ComparisonCell(
                    label = "Expenses",
                    accent = ExpenseRed,
                    current = currentExpense,
                    previous = prevExpense,
                    higherIsGood = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ComparisonCell(
    label: String,
    accent: Color,
    current: Double,
    previous: Double,
    higherIsGood: Boolean,
    modifier: Modifier = Modifier
) {
    val delta = current - previous
    val pct = if (previous > 0.005) ((current - previous) / previous * 100).toInt() else null
    val deltaUp = delta > 0
    val isGood = (deltaUp && higherIsGood) || (!deltaUp && !higherIsGood)
    val deltaColor = if (delta == 0.0) MaterialTheme.colorScheme.onSurfaceVariant
                     else if (isGood) IncomeGreen else ExpenseRed
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(current.toCurrency(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.height(2.dp))
        if (previous > 0.005 || current > 0.005) {
            val arrow = when {
                delta > 0.005 -> "↑"
                delta < -0.005 -> "↓"
                else -> "="
            }
            val pctLabel = pct?.let { "$arrow ${kotlin.math.abs(it)}%" } ?: arrow
            Text(
                "$pctLabel vs ${previous.toCurrency()}",
                style = MaterialTheme.typography.bodySmall,
                color = deltaColor
            )
        }
    }
}

@Composable
private fun SixMonthTrendCard(
    trend: List<MonthlyBucket>,
    onBarClick: (MonthlyBucket) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxValue = trend.maxOfOrNull { kotlin.math.max(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("6-Month Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Income vs expenses by month — tap a bar to drill in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { bucket ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBarClick(bucket) }
                            .padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.height(100.dp)
                        ) {
                            val incomeHeight = (100 * (bucket.income / maxValue)).toInt().coerceAtLeast(if (bucket.income > 0) 2 else 0)
                            val expenseHeight = (100 * (bucket.expense / maxValue)).toInt().coerceAtLeast(if (bucket.expense > 0) 2 else 0)
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(incomeHeight.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(IncomeGreen)
                            )
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(expenseHeight.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(ExpenseRed)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(bucket.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen))
                Spacer(Modifier.width(4.dp))
                Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed))
                Spacer(Modifier.width(4.dp))
                Text("Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryBreakdownCard(
    title: String,
    breakdown: Map<com.budgetapp.domain.model.Category, Double>,
    total: Double,
    accent: Color,
    onRowClick: ((com.budgetapp.domain.model.Category) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sorted = breakdown.entries.sortedByDescending { it.value }.take(8)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Tap a category to see its transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            sorted.forEach { (category, amount) ->
                val pct = if (total > 0) (amount / total).toFloat() else 0f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (onRowClick != null) it.clickable { onRowClick(category) } else it }
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = category.icon, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${amount.toCurrency()} · ${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (onRowClick != null) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.15f)
                    )
                }
            }
            if (breakdown.size > 8) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "+${breakdown.size - 8} more categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScopeToggle(isAllTime: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = !isAllTime,
            onClick = { if (isAllTime) onToggle() },
            label = { Text("This Month") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = isAllTime,
            onClick = { if (!isAllTime) onToggle() },
            label = { Text("All Time") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AllTimeTotalsCard(
    income: Double,
    expense: Double,
    onIncomeClick: (() -> Unit)? = null,
    onExpenseClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("All-Time Totals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.weight(1f).let { if (onIncomeClick != null) it.clickable(onClick = onIncomeClick) else it }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen))
                        Spacer(Modifier.width(6.dp))
                        Text("Income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onIncomeClick != null) Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("+${income.toCurrency()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = IncomeGreen)
                }
                Column(
                    modifier = Modifier.weight(1f).let { if (onExpenseClick != null) it.clickable(onClick = onExpenseClick) else it }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed))
                        Spacer(Modifier.width(6.dp))
                        Text("Expenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onExpenseClick != null) Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("-${expense.toCurrency()}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = ExpenseRed)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Net", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        (income - expense).toCurrency(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (income - expense >= 0) IncomeGreen else ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeElsewhereBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = IncomeGreen.copy(alpha = 0.1f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = IncomeGreen)
            Spacer(Modifier.width(12.dp))
            Text(
                "You have income recorded — but not in this month. Use the arrows above to navigate to another month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PendingReviewBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = WarningOrange.copy(alpha = 0.12f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = WarningOrange)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$count pending transaction${if (count == 1) "" else "s"} to review",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Approve them to include income and expenses in your totals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarningOrange)
        }
    }
}

@Composable
private fun NoIncomeHintBanner(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = BrandBlue)
                Spacer(Modifier.width(8.dp))
                Text(
                    "No income recorded this month",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "If you imported a credit-card statement, import your bank statement too — that's where salary and other income are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import bank statement")
            }
        }
    }
}

private val WarningOrange = Color(0xFFFF9800)

/**
 * Builds the nav route for the drill-down screen. URI-encodes the title since it may
 * contain spaces or non-ASCII characters (Hebrew descriptions, currency symbols, etc.).
 */
private fun drillRoute(
    type: String = "ALL",
    year: Int = -1,
    month: Int = -1,
    allTime: Boolean = false,
    categoryId: Long = -1L,
    descPattern: String = "",
    title: String = "Transactions"
): String {
    val safeTitle = android.net.Uri.encode(title)
    val safeDesc = android.net.Uri.encode(descPattern)
    return "drilldown?type=$type&year=$year&month=$month&allTime=$allTime&categoryId=$categoryId&descPattern=$safeDesc&title=$safeTitle"
}

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
