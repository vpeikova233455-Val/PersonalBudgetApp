package com.budgetapp.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.constants.Constants
import com.budgetapp.core.security.EncryptionManager
import com.budgetapp.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed class SplashState {
    object Restoring : SplashState()
    object Ready : SplashState()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Restoring)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    init {
        restoreAndProceed()
    }

    private fun restoreAndProceed() {
        viewModelScope.launch {
            val userId = EncryptionManager.getString(context, Constants.KEY_USER_ID) ?: "local_user"
            val restoreKey = Constants.KEY_RESTORE_DONE_PREFIX + userId
            val alreadyRestored = EncryptionManager.getBoolean(context, restoreKey)

            if (!alreadyRestored) {
                // First launch for this user — pull everything from Firestore before showing data.
                // Cap at 15 seconds so the app never hangs on a slow connection.
                withTimeoutOrNull(15_000L) {
                    syncRepository.restoreAll()
                }
                EncryptionManager.saveBoolean(context, restoreKey, true)
            } else {
                // Subsequent launch — kick off a background sync without blocking the UI.
                launch { syncRepository.syncAll() }
            }

            _state.value = SplashState.Ready
        }
    }
}
