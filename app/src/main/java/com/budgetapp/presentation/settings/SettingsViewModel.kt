package com.budgetapp.presentation.settings

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.BuildConfig
import com.budgetapp.core.constants.Constants.KEY_GITHUB_OWNER
import com.budgetapp.core.constants.Constants.KEY_GITHUB_REPO
import com.budgetapp.core.constants.Constants.KEY_GITHUB_TOKEN
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.core.util.Result
import com.budgetapp.core.util.toDateString
import com.budgetapp.data.remote.github.GitHubService
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BugReportStatus {
    object Idle : BugReportStatus()
    object Loading : BugReportStatus()
    data class Success(val issueUrl: String) : BugReportStatus()
    data class Error(val message: String) : BugReportStatus()
}

data class SettingsUiState(
    val userEmail: String = "",
    val syncStatus: SyncStatus = SyncStatus(),
    val lastSyncFormatted: String = "Never",
    val isLoggingOut: Boolean = false,
    val logoutError: String? = null,
    val logoutSuccess: Boolean = false,
    val currentLanguage: String = "en",
    val githubToken: String = "",
    val githubOwner: String = "",
    val githubRepo: String = "",
    val bugReportStatus: BugReportStatus = BugReportStatus.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val gitHubService: GitHubService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            githubToken = EncryptionManager.getString(context, KEY_GITHUB_TOKEN) ?: "",
            githubOwner = EncryptionManager.getString(context, KEY_GITHUB_OWNER) ?: "",
            githubRepo = EncryptionManager.getString(context, KEY_GITHUB_REPO) ?: ""
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        observeSyncStatus()
        loadCurrentLanguage()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
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

    private fun loadCurrentLanguage() {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (locales.isEmpty) "en" else locales[0]?.language ?: "en"
        _uiState.update { it.copy(currentLanguage = tag) }
    }

    fun setLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (languageTag == "en") LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(languageTag)
        )
        _uiState.update { it.copy(currentLanguage = languageTag) }
    }

    fun manualSync() {
        viewModelScope.launch {
            syncRepository.syncAll()
        }
    }

    fun saveGitHubSettings(token: String, owner: String, repo: String) {
        EncryptionManager.saveString(context, KEY_GITHUB_TOKEN, token)
        EncryptionManager.saveString(context, KEY_GITHUB_OWNER, owner)
        EncryptionManager.saveString(context, KEY_GITHUB_REPO, repo)
        _uiState.update { it.copy(githubToken = token, githubOwner = owner, githubRepo = repo) }
    }

    fun submitBugReport(title: String, description: String) {
        val state = _uiState.value
        if (state.githubToken.isBlank() || state.githubOwner.isBlank() || state.githubRepo.isBlank()) {
            _uiState.update {
                it.copy(bugReportStatus = BugReportStatus.Error("Configure GitHub settings first."))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(bugReportStatus = BugReportStatus.Loading) }

            val body = buildString {
                append(description.trim())
                if (description.isNotBlank()) append("\n\n---\n")
                append("**App Version:** ${BuildConfig.VERSION_NAME}\n")
                append("**Android:** ${Build.VERSION.RELEASE}\n")
                append("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}")
            }

            gitHubService.createIssue(
                token = state.githubToken,
                owner = state.githubOwner,
                repo = state.githubRepo,
                title = title,
                body = body
            ).fold(
                onSuccess = { url ->
                    _uiState.update { it.copy(bugReportStatus = BugReportStatus.Success(url)) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(bugReportStatus = BugReportStatus.Error(e.message ?: "Unknown error"))
                    }
                }
            )
        }
    }

    fun clearBugReportStatus() {
        _uiState.update { it.copy(bugReportStatus = BugReportStatus.Idle) }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, logoutError = null) }

            when (val result = authRepository.logout()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(isLoggingOut = false, logoutSuccess = true)
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
