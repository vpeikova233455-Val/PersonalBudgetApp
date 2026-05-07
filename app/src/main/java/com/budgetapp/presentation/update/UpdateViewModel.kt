package com.budgetapp.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.update.UpdateChecker
import com.budgetapp.core.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo) : UpdateState()
    data class Failed(val info: UpdateInfo, val reason: String) : UpdateState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = updateChecker.checkForUpdate() ?: return@launch
            _state.value = UpdateState.Available(info)
        }
    }

    fun dismissUpdate() {
        _state.value = UpdateState.Idle
    }

    fun startDownload() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(info, 0)
            val file = updateChecker.downloadApk(info.downloadUrl) { progress ->
                _state.value = UpdateState.Downloading(info, progress)
            }
            if (file != null) {
                _state.value = UpdateState.ReadyToInstall(info)
                updateChecker.installApk(file)
            } else {
                _state.value = UpdateState.Failed(info, "Download failed — check your connection and try again.")
            }
        }
    }

    fun retryDownload() {
        val info = (_state.value as? UpdateState.Failed)?.info ?: return
        _state.value = UpdateState.Available(info)
        startDownload()
    }
}
