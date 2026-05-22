package com.budgetapp.presentation.settings

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.BuildConfig
import com.budgetapp.core.constants.Constants.KEY_DRIVE_ACCOUNT_EMAIL
import com.budgetapp.core.constants.Constants.KEY_DRIVE_ACCOUNT_TYPE
import com.budgetapp.core.constants.Constants.KEY_DRIVE_FOLDER_ID
import com.budgetapp.core.constants.Constants.KEY_DRIVE_LAST_BACKUP
import com.budgetapp.core.constants.Constants.KEY_GITHUB_OWNER
import com.budgetapp.core.constants.Constants.KEY_GITHUB_REPO
import com.budgetapp.core.constants.Constants.KEY_GITHUB_TOKEN
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.core.util.Result
import com.budgetapp.core.util.toDateString
import com.budgetapp.data.remote.drive.BackupResult
import com.budgetapp.data.remote.drive.DriveBackupOrchestrator
import com.budgetapp.data.remote.github.GitHubService
import com.budgetapp.domain.repository.AuthRepository
import com.budgetapp.domain.repository.SyncRepository
import com.budgetapp.domain.repository.SyncStatus
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DriveBackupStatus {
    object Idle : DriveBackupStatus()
    object Running : DriveBackupStatus()
    data class Success(val timestamp: Long) : DriveBackupStatus()
    data class Error(val message: String) : DriveBackupStatus()
    object NeedsReauth : DriveBackupStatus()
}

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
    val bugReportStatus: BugReportStatus = BugReportStatus.Idle,
    val driveEmail: String = "",
    val driveLastBackup: Long = 0L,
    val driveBackupStatus: DriveBackupStatus = DriveBackupStatus.Idle
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val gitHubService: GitHubService,
    private val driveBackupOrchestrator: DriveBackupOrchestrator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            githubToken    = EncryptionManager.getString(context, KEY_GITHUB_TOKEN) ?: "",
            githubOwner    = EncryptionManager.getString(context, KEY_GITHUB_OWNER) ?: "",
            githubRepo     = EncryptionManager.getString(context, KEY_GITHUB_REPO) ?: "",
            driveEmail     = EncryptionManager.getString(context, KEY_DRIVE_ACCOUNT_EMAIL) ?: "",
            driveLastBackup = EncryptionManager.getString(context, KEY_DRIVE_LAST_BACKUP)?.toLongOrNull() ?: 0L
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
        // Read directly from prefs — source of truth, survives ViewModel recreation
        val token = EncryptionManager.getString(context, KEY_GITHUB_TOKEN).orEmpty()
        val owner = EncryptionManager.getString(context, KEY_GITHUB_OWNER).orEmpty()
        val repo  = EncryptionManager.getString(context, KEY_GITHUB_REPO).orEmpty()

        if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
            _uiState.update {
                it.copy(bugReportStatus = BugReportStatus.Error(
                    "GitHub settings not configured. Fill in your token, owner, and repo in the GitHub Integration section, then tap Save."
                ))
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
                token = token,
                owner = owner,
                repo = repo,
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

    fun onDriveSignInSuccess(account: GoogleSignInAccount) {
        val email = account.email ?: return
        val type = account.account?.type ?: "com.google"
        EncryptionManager.saveString(context, KEY_DRIVE_ACCOUNT_EMAIL, email)
        EncryptionManager.saveString(context, KEY_DRIVE_ACCOUNT_TYPE, type)
        EncryptionManager.saveString(context, KEY_DRIVE_FOLDER_ID, "") // invalidate cached folder
        _uiState.update { it.copy(driveEmail = email) }
    }

    fun disconnectDrive() {
        EncryptionManager.saveString(context, KEY_DRIVE_ACCOUNT_EMAIL, "")
        EncryptionManager.saveString(context, KEY_DRIVE_ACCOUNT_TYPE, "")
        EncryptionManager.saveString(context, KEY_DRIVE_FOLDER_ID, "")
        _uiState.update { it.copy(driveEmail = "", driveLastBackup = 0L, driveBackupStatus = DriveBackupStatus.Idle) }
    }

    fun manualDriveBackup() {
        if (_uiState.value.driveBackupStatus is DriveBackupStatus.Running) return
        viewModelScope.launch {
            _uiState.update { it.copy(driveBackupStatus = DriveBackupStatus.Running) }
            val result = driveBackupOrchestrator.performBackup()
            val newStatus = when (result) {
                is BackupResult.Success       -> {
                    val now = System.currentTimeMillis()
                    _uiState.update { it.copy(driveLastBackup = now) }
                    DriveBackupStatus.Success(now)
                }
                is BackupResult.NotConfigured -> DriveBackupStatus.Idle
                is BackupResult.NeedsReauth   -> DriveBackupStatus.NeedsReauth
                is BackupResult.Error         -> DriveBackupStatus.Error(result.message)
            }
            _uiState.update { it.copy(driveBackupStatus = newStatus) }
        }
    }

    fun clearDriveStatus() {
        _uiState.update { it.copy(driveBackupStatus = DriveBackupStatus.Idle) }
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
