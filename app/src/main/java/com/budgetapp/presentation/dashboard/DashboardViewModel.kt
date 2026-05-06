package com.budgetapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.AppLogger
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.usecase.transaction.GetDashboardDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DashboardViewModel"

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(val data: DashboardData, val isRefreshing: Boolean = false) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getDashboardDataUseCase: GetDashboardDataUseCase,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var userId: String? = null

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "Loading dashboard")
                userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    AppLogger.e(TAG, "No user ID available")
                    _uiState.value = DashboardUiState.Error("User not logged in")
                    return@launch
                }
                AppLogger.d(TAG, "Loading dashboard for user: $userId")
                getDashboardDataUseCase(userId!!).collect { dashboardData ->
                    AppLogger.d(TAG, "Dashboard loaded: ${dashboardData.recentTransactions.size} transactions")
                    _uiState.value = DashboardUiState.Success(dashboardData)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load dashboard", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load dashboard")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                AppLogger.d(TAG, "Refreshing dashboard")
                val currentState = _uiState.value
                if (currentState is DashboardUiState.Success) {
                    _uiState.value = currentState.copy(isRefreshing = true)
                }
                syncRepository.syncAll()
                kotlinx.coroutines.delay(1000)
                val newState = _uiState.value
                if (newState is DashboardUiState.Success) {
                    _uiState.value = newState.copy(isRefreshing = false)
                }
                AppLogger.d(TAG, "Dashboard refresh complete")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Dashboard refresh failed", e)
            }
        }
    }
}
