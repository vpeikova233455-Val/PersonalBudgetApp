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
    onNavigateToRecurring: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsState()
    var showRangePicker by remember { mutableStateOf(false) }

    if (showRangePicker) {
        DateRangePickerDialog(
            initialRange = (uiState as? DashboardUiState.Success)?.customRange,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showRangePicker = false
            }
        )
    }

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
                    TextButton(onClick = onNavigateToRecurring) {
                        Text("Recurring", style = MaterialTheme.typography.labelMedium)
                    }
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

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            val (yr, mo) = selectedYearMonth
                            val hasCustomRange = state.customRange != null
                            val typeFilter = state.typeFilter
                            val showExpenseCards = typeFilter != DashboardTypeFilter.INCOME
                            val showIncomeCards = typeFilter != DashboardTypeFilter.EXPENSE

                            // (1) Period selector — month nav arrows + month label live inside the
                            // BalanceCard. In custom-range mode the arrows are disabled and the
                            // label switches to the range string.
                            item {
                                BalanceCard(
                                    balance = data.balance,
                                    income = data.totalIncome,
                                    expenses = data.totalExpenses,
                                    monthLabel = viewModel.selectedMonthLabel(),
                                    onPreviousMonth = if (hasCustomRange) null else viewModel::previousMonth,
                                    onNextMonth = if (hasCustomRange) null else viewModel::nextMonth,
                                    onIncomeClick = if (data.totalIncome > 0) ({
                                        onNavigateToDrillDown(drillRoute(
                                            type = "INCOME",
                                            year = if (hasCustomRange) -1 else yr,
                                            month = if (hasCustomRange) -1 else mo,
                                            title = "Income · ${viewModel.selectedMonthLabel()}"
                                        ))
                                    }) else null,
                                    onExpensesClick = if (data.totalExpenses > 0) ({
                                        onNavigateToDrillDown(drillRoute(
                                            type = "EXPENSE",
                                            year = if (hasCustomRange) -1 else yr,
                                            month = if (hasCustomRange) -1 else mo,
                                            title = "Expenses · ${viewModel.selectedMonthLabel()}"
                                        ))
                                    }) else null,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }

                            // (2) Filter row — type chips + custom-range button.
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                FilterRow(
                                    typeFilter = state.typeFilter,
                                    onTypeChange = viewModel::setTypeFilter,
                                    customRange = state.customRange,
                                    onCustomRangeClick = { showRangePicker = true },
                                    onClearCustomRange = viewModel::clearCustomRange,
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

                            // (3) vs Previous Month — only meaningful in monthly mode.
                            if (!hasCustomRange &&
                                (state.prevMonthIncome > 0 || state.prevMonthExpense > 0 || data.totalIncome > 0 || data.totalExpenses > 0)
                            ) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    MonthlyComparisonCard(
                                        currentIncome = data.totalIncome,
                                        prevIncome = state.prevMonthIncome,
                                        currentExpense = data.totalExpenses,
                                        prevExpense = state.prevMonthExpense,
                                        showIncome = showIncomeCards,
                                        showExpense = showExpenseCards,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // (4) Expense categories — ranked by amount, every row clickable.
                            if (showExpenseCards && data.totalExpenses > 0 && data.categoryBreakdown.isNotEmpty()) {
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
                                                year = if (hasCustomRange) -1 else yr,
                                                month = if (hasCustomRange) -1 else mo,
                                                categoryId = cat.id,
                                                title = "${cat.icon} ${cat.name}"
                                            ))
                                        },
                                        onMoreClick = {
                                            onNavigateToDrillDown(drillRoute(
                                                type = "EXPENSE",
                                                year = if (hasCustomRange) -1 else yr,
                                                month = if (hasCustomRange) -1 else mo,
                                                title = "All Expense Categories"
                                            ))
                                        },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // (5) Income categories — ranked by amount, every row clickable.
                            if (showIncomeCards && data.totalIncome > 0 && data.incomeBreakdown.isNotEmpty()) {
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
                                                year = if (hasCustomRange) -1 else yr,
                                                month = if (hasCustomRange) -1 else mo,
                                                categoryId = cat.id,
                                                title = "${cat.icon} ${cat.name}"
                                            ))
                                        },
                                        onMoreClick = {
                                            onNavigateToDrillDown(drillRoute(
                                                type = "INCOME",
                                                year = if (hasCustomRange) -1 else yr,
                                                month = if (hasCustomRange) -1 else mo,
                                                title = "All Income Categories"
                                            ))
                                        },
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }

                            // (6) 6-month trend — only meaningful in monthly mode.
                            if (!hasCustomRange && state.monthlyTrend.size >= 2) {
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

                            // Empty state — only when nothing was found at all.
                            if (data.totalIncome == 0.0 && data.totalExpenses == 0.0 && state.pendingCount == 0) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    EmptyState(
                                        onImport = onNavigateToImport,
                                        onAdd = onNavigateToAddTransaction,
                                        modifier = Modifier.padding(horizontal = 20.dp)
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
private fun FilterRow(
    typeFilter: DashboardTypeFilter,
    onTypeChange: (DashboardTypeFilter) -> Unit,
    customRange: Pair<Long, Long>?,
    onCustomRangeClick: () -> Unit,
    onClearCustomRange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == DashboardTypeFilter.ALL,
                onClick = { onTypeChange(DashboardTypeFilter.ALL) },
                label = { Text("All") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = typeFilter == DashboardTypeFilter.INCOME,
                onClick = { onTypeChange(DashboardTypeFilter.INCOME) },
                label = { Text("Income") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = typeFilter == DashboardTypeFilter.EXPENSE,
                onClick = { onTypeChange(DashboardTypeFilter.EXPENSE) },
                label = { Text("Expenses") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        if (customRange == null) {
            OutlinedButton(
                onClick = onCustomRangeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Custom date range…", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            // Active custom-range chip with a clear button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Custom range active",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCustomRangeClick) { Text("Change") }
                    TextButton(onClick = onClearCustomRange) { Text("Clear") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialog(
    initialRange: Pair<Long, Long>?,
    onDismiss: () -> Unit,
    onConfirm: (startMs: Long, endMsExclusive: Long) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange?.first,
        initialSelectedEndDateMillis = initialRange?.second?.let { it - 1 } // end is exclusive; picker uses inclusive
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis
                    if (s != null && e != null) {
                        // Convert inclusive end → exclusive end (next-day-midnight)
                        val cal = java.util.Calendar.getInstance().apply {
                            timeInMillis = e
                            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                            add(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                        onConfirm(s, cal.timeInMillis)
                    }
                },
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, title = { Text("Select date range", modifier = Modifier.padding(16.dp)) })
    }
}

@Composable
private fun EmptyState(
    onImport: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nothing to show for this period", style = MaterialTheme.typography.titleSmall)
            Text(
                "Import a bank statement or add transactions manually to see your monthly summary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
                Button(
                    onClick = onAdd,
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
}

@Composable
private fun MonthlyComparisonCard(
    currentIncome: Double,
    prevIncome: Double,
    currentExpense: Double,
    prevExpense: Double,
    showIncome: Boolean = true,
    showExpense: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showIncome && !showExpense) return
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
                if (showIncome) {
                    ComparisonCell(
                        label = "Income",
                        accent = IncomeGreen,
                        current = currentIncome,
                        previous = prevIncome,
                        higherIsGood = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (showIncome && showExpense) Spacer(Modifier.width(12.dp))
                if (showExpense) {
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
    @Suppress("UNUSED_PARAMETER") total: Double,  // kept for callsite symmetry; no longer rendered as %
    accent: Color,
    onRowClick: ((com.budgetapp.domain.model.Category) -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (onRowClick != null) it.clickable { onRowClick(category) } else it }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(text = category.icon, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    Text(
                        amount.toCurrency(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    if (onRowClick != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (breakdown.size > 8) {
                Spacer(Modifier.height(6.dp))
                // "+N more categories" — tappable row that opens the full drill-down
                // (categories sorted by amount, each expandable to its transactions).
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (onMoreClick != null) it.clickable(onClick = onMoreClick) else it },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "+${breakdown.size - 8} more categories",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "View all",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
                    }
                }
            }
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
