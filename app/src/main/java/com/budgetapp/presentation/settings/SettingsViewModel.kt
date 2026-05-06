package com.budgetapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.Result
import com.budgetapp.core.util.toDateString
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userEmail: String = "",
    val syncStatus: SyncStatus = SyncStatus(),
    val lastSyncFormatted: String = "Never",
    val isLoggingOut: Boolean = false,
    val logoutError: String? = null,
    val logoutSuccess: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        observeSyncStatus()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            // Get user email from Firebase Auth
            val email = "user@example.com" // TODO: Get from AuthRepository
            _uiState.update { it.copy(userEmail = email) }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncRepository.getSyncStatus().collect { syncStatus ->
                val lastSyncFormatted = syncStatus.lastSyncTime?.let {
                    it.toDateString("MMM dd, yyyy HH:mm")
                } ?: "Never"

                _uiState.update {
                    it.copy(
                        syncStatus = syncStatus,
                        lastSyncFormatted = lastSyncFormatted
                    )
                }
            }
        }
    }

    fun manualSync() {
        viewModelScope.launch {
            syncRepository.syncAll()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, logoutError = null) }

            when (val result = authRepository.logout()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoggingOut = false,
                            logoutSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoggingOut = false,
                            logoutError = result.exception.message ?: "Logout failed"
                        )
                    }
                }
                is Result.Loading -> {}
            }
        }
    }
}
