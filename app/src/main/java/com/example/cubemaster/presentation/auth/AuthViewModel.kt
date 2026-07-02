package com.example.cubemaster.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cubemaster.data.remote.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        if (auth.currentUser != null) {
            _state.update { it.copy(isAuthenticated = true) }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signInWithEmail(email, password)
                _state.update { it.copy(isLoading = false, isAuthenticated = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                auth.registerWithEmail(email, password)
                _state.update { it.copy(isLoading = false, isAuthenticated = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                auth.resetPassword(email)
                _state.update { it.copy(isLoading = false, error = "Лист для скидання надіслано на $email") }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun parseError(e: Exception): String = when {
        e.message?.contains("password") == true -> "Неправильний пароль"
        e.message?.contains("no user record") == true -> "Обліковий запис не знайдено"
        e.message?.contains("email address is already in use") == true -> "Email вже використовується"
        else -> "Помилка: ${e.message}"
    }
}
