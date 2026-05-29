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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private val userId: String = runBlocking { authRepository.getCurrentUserId() } ?: ""

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val now = Calendar.getInstance()
    private val _selectedYearMonth = MutableStateFlow(
        Pair(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    )
    val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

    init {
        observeDashboard()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard() {
        if (userId.isEmpty()) {
            _uiState.value = DashboardUiState.Error("User not logged in")
            return
        }

        _selectedYearMonth
            .flatMapLatest { (year, month) ->
                getDashboardDataUseCase(userId, year, month)
                    .map<DashboardData, DashboardUiState> { data ->
                        val isRefreshing =
                            (_uiState.value as? DashboardUiState.Success)?.isRefreshing ?: false
                        DashboardUiState.Success(data, isRefreshing)
                    }
                    .catch { e ->
                        AppLogger.e(TAG, "Dashboard data error", e)
                        emit(DashboardUiState.Error(e.message ?: "Failed to load dashboard"))
                    }
            }
            .onEach { state -> _uiState.value = state }
            .catch { e ->
                AppLogger.e(TAG, "Dashboard observer error", e)
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load dashboard")
            }
            .launchIn(viewModelScope)
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
                val current = _uiState.value
                if (current is DashboardUiState.Success) {
                    _uiState.value = current.copy(isRefreshing = true)
                }
                syncRepository.syncAll()
                delay(1000)
                val updated = _uiState.value
                if (updated is DashboardUiState.Success) {
                    _uiState.value = updated.copy(isRefreshing = false)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Dashboard refresh failed", e)
                val updated = _uiState.value
                if (updated is DashboardUiState.Success) {
                    _uiState.value = updated.copy(isRefreshing = false)
                }
            }
        }
    }

    // Called from the Error state Retry button — restarts the data observer.
    fun retry() {
        _uiState.value = DashboardUiState.Loading
        observeDashboard()
    }
}
