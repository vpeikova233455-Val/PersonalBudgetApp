package com.budgetapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgetapp.presentation.auth.SplashScreen
import com.budgetapp.presentation.budget.BudgetScreen
import com.budgetapp.presentation.bugreport.BugReportButton
import com.budgetapp.presentation.category.CategoriesScreen
import com.budgetapp.presentation.components.BottomNavBar
import com.budgetapp.presentation.dashboard.DashboardScreen
import com.budgetapp.presentation.imports.ImportOptionsScreen
import com.budgetapp.presentation.imports.OcrImportViewModel
import com.budgetapp.presentation.imports.ReviewTransactionsScreen
import com.budgetapp.presentation.savings.SavingsScreen
import com.budgetapp.presentation.history.HistoryScreen
import com.budgetapp.presentation.settings.SettingsScreen
import com.budgetapp.presentation.transaction.AddTransactionScreen
import com.budgetapp.presentation.transaction.EditTransactionScreen
import com.budgetapp.presentation.transactions.TransactionListScreen
import com.budgetapp.presentation.update.UpdateDialog
import com.budgetapp.presentation.update.UpdateState
import com.budgetapp.presentation.update.UpdateViewModel

private val bottomNavRoutes = setOf("dashboard", "transactions", "budget", "savings", "settings")

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(navController = navController)
                }
            }
        ) { innerPadding ->
            // consumeWindowInsets prevents each screen's Scaffold from double-applying
            // the same system insets that the outer Scaffold already accounted for.
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                composable("splash") {
                    SplashScreen(
                        onNavigateToLogin = {
                            navController.navigate("dashboard") {
                                popUpTo("splash") { inclusive = true }
                            }
                        },
                        onNavigateToDashboard = {
                            navController.navigate("dashboard") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    )
                }

                composable("dashboard") {
                    DashboardScreen(
                        onNavigateToAddTransaction = { navController.navigate("add_transaction") },
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToImport = { navController.navigate("import_options") },
                        onNavigateToReview = { navController.navigate("review_transactions") },
                        onTransactionClick = { navController.navigate("edit_transaction/$it") }
                    )
                }

                composable("transactions") {
                    TransactionListScreen(
                        onNavigateToAddTransaction = { navController.navigate("add_transaction") },
                        onTransactionClick = { navController.navigate("edit_transaction/$it") }
                    )
                }

                composable("budget") {
                    BudgetScreen()
                }

                composable("savings") {
                    SavingsScreen()
                }

                composable("add_transaction") {
                    AddTransactionScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable(
                    route = "edit_transaction/{transactionId}",
                    arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                    EditTransactionScreen(
                        transactionId = transactionId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("categories") {
                    CategoriesScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable("settings") {
                    SettingsScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate("dashboard") { launchSingleTop = true }
                            }
                        },
                        onNavigateToCategories = { navController.navigate("categories") },
                        onNavigateToHistory = { navController.navigate("history") }
                    )
                }

                composable("history") {
                    HistoryScreen(onNavigateBack = { navController.popBackStack() })
                }

                composable("import_options") {
                    val ocrViewModel: OcrImportViewModel = hiltViewModel()
                    ImportOptionsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        ocrViewModel = ocrViewModel,
                        onNavigateToReview = { navController.navigate("review_transactions") }
                    )
                }

                composable("review_transactions") {
                    ReviewTransactionsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onAllApproved = { navController.popBackStack() }
                    )
                }
            }
        }

        BugReportButton(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    bottom = if (showBottomBar) 72.dp else 16.dp
                )
        )
    }

    if (updateState !is UpdateState.Idle) {
        UpdateDialog(state = updateState, viewModel = updateViewModel)
    }
}
