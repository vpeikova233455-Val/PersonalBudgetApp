package com.budgetapp.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.core.util.Result
import com.budgetapp.core.util.isValidEmail
import com.budgetapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, emailError = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
    }

    fun login() {
        if (!validateInput()) return

        _uiState.update { it.copy(authState = AuthState.Loading) }

        viewModelScope.launch {
            when (val result = authRepository.login(_uiState.value.email, _uiState.value.password)) {
                is Result.Success -> {
                    _uiState.update { it.copy(authState = AuthState.Success(result.data)) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(authState = AuthState.Error(result.exception.message ?: "Login failed"))
                    }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun resetAuthState() {
        _uiState.update { it.copy(authState = AuthState.Idle) }
    }

    private fun validateInput(): Boolean {
        val email = _uiState.value.email
        val password = _uiState.value.password

        var isValid = true

        if (email.isBlank()) {
            _uiState.update { it.copy(emailError = "Email is required") }
            isValid = false
        } else if (!email.isValidEmail()) {
            _uiState.update { it.copy(emailError = "Invalid email format") }
            isValid = false
        }

        if (password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Password is required") }
            isValid = false
        } else if (password.length < 6) {
            _uiState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            isValid = false
        }

        return isValid
    }
}
