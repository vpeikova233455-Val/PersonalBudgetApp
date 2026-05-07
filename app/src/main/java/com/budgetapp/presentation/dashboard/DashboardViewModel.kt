package com.budgetapp.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.AppLogger
import com.budgetapp.domain.model.DashboardData
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.usecase.transaction.GetDashboardDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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

    private val now = Calendar.getInstance()
    private val _selectedYearMonth = MutableStateFlow(
        Pair(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    )
    val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

    private var userId: String? = null

    init {
        loadDashboard()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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
                _selectedYearMonth
                    .flatMapLatest { (year, month) ->
                        getDashboardDataUseCase(userId!!, year, month)
                    }
                    .collect { dashboardData ->
                        val currentState = _uiState.value
                        val isRefreshing = (currentState as? DashboardUiState.Success)?.isRefreshing ?: false
                        _uiState.value = DashboardUiState.Success(dashboardData, isRefreshing)
                    }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to load dashboard", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load dashboard")
            }
        }
    }

    fun previousMonth() {
        val (year, month) = _selectedYearMonth.value
        _selectedYearMonth.value = if (month == 0) Pair(year - 1, 11) else Pair(year, month - 1)
    }

    fun nextMonth() {
        val (year, month) = _selectedYearMonth.value
        _selectedYearMonth.value = if (month == 11) Pair(year + 1, 0) else Pair(year, month + 1)
    }

    fun selectedMonthLabel(): String {
        val (year, month) = _selectedYearMonth.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
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
