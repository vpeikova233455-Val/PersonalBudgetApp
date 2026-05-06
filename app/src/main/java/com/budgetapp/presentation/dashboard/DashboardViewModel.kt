package com.budgetapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = DashboardUiState.Error("User not logged in")
                    return@launch
                }

                getDashboardDataUseCase(userId!!).collect { dashboardData ->
                    _uiState.value = DashboardUiState.Success(dashboardData)
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load dashboard")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // Set refreshing state
            val currentState = _uiState.value
            if (currentState is DashboardUiState.Success) {
                _uiState.value = currentState.copy(isRefreshing = true)
            }

            // Trigger sync
            syncRepository.syncAll()

            // Refresh will happen automatically through the dashboard data flow
            // Reset refreshing state after a delay
            kotlinx.coroutines.delay(1000)
            val newState = _uiState.value
            if (newState is DashboardUiState.Success) {
                _uiState.value = newState.copy(isRefreshing = false)
            }
        }
    }
}
