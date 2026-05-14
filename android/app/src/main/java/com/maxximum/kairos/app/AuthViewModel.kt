package com.maxximum.kairos.app

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxximum.kairos.data.auth.ApiServerStore
import com.maxximum.kairos.data.auth.AuthSessionStore
import com.maxximum.kairos.data.remote.AuthApi
import com.maxximum.kairos.data.sync.TodoSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isCheckingSession: Boolean = true,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val email: String? = null,
    val apiBaseUrl: String = "",
    val message: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val apiServerStore = ApiServerStore(application)
    private val api = AuthApi { apiServerStore.getBaseUrl() }
    private val sessionStore = AuthSessionStore(application)
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        val user = sessionStore.user()
        _uiState.value = AuthUiState(
            isCheckingSession = false,
            isAuthenticated = user != null,
            email = user?.email,
            apiBaseUrl = apiServerStore.getBaseUrl()
        )
    }

    fun register(email: String, password: String) {
        authenticate(email, password, isRegister = true)
    }

    fun login(email: String, password: String) {
        authenticate(email, password, isRegister = false)
    }

    fun logout() {
        sessionStore.clear()
        _uiState.value = AuthUiState(
            isCheckingSession = false,
            apiBaseUrl = apiServerStore.getBaseUrl()
        )
    }

    fun setApiBaseUrl(baseUrl: String) {
        apiServerStore.setBaseUrl(baseUrl)
        sessionStore.clear()
        _uiState.value = AuthUiState(
            isCheckingSession = false,
            apiBaseUrl = apiServerStore.getBaseUrl(),
            message = "Server changed. Sign in again."
        )
    }

    private fun authenticate(email: String, password: String, isRegister: Boolean) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Email and password are required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val tokens = if (isRegister) {
                    api.register(normalizedEmail, password, deviceName())
                } else {
                    api.login(normalizedEmail, password, deviceName())
                }
                sessionStore.save(tokens)
                _uiState.value = AuthUiState(
                    isCheckingSession = false,
                    isAuthenticated = true,
                    email = tokens.user.email,
                    apiBaseUrl = apiServerStore.getBaseUrl(),
                    message = if (isRegister) "Account created." else "Signed in."
                )
                TodoSyncWorker.enqueueNow(getApplication())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = e.message ?: "Could not connect to Kairos API."
                )
            }
        }
    }

    private fun deviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }
    }
}
