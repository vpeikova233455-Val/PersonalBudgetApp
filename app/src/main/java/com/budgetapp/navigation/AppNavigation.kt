package com.budgetapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgetapp.presentation.auth.SplashScreen
import com.budgetapp.presentation.bugreport.BugReportButton
import com.budgetapp.presentation.category.CategoriesScreen
import com.budgetapp.presentation.dashboard.DashboardScreen
import com.budgetapp.presentation.settings.SettingsScreen
import com.budgetapp.presentation.transaction.AddTransactionScreen
import com.budgetapp.presentation.transaction.EditTransactionScreen
import com.budgetapp.presentation.update.UpdateDialog
import com.budgetapp.presentation.update.UpdateState
import com.budgetapp.presentation.update.UpdateViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "splash"
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
                    onNavigateToAddTransaction = {
                        navController.navigate("add_transaction")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onTransactionClick = { transactionId ->
                        navController.navigate("edit_transaction/$transactionId")
                    }
                )
            }

            composable("add_transaction") {
                AddTransactionScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "edit_transaction/{transactionId}",
                arguments = listOf(
                    navArgument("transactionId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
                EditTransactionScreen(
                    transactionId = transactionId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("categories") {
                CategoriesScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToCategories = {
                        navController.navigate("categories")
                    }
                )
            }
        }

        BugReportButton(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 16.dp)
        )
    }

    if (updateState !is UpdateState.Idle) {
        UpdateDialog(state = updateState, viewModel = updateViewModel)
    }
}
