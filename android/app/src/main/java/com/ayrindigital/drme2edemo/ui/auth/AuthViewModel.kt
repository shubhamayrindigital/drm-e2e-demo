package com.ayrindigital.drme2edemo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.ayrindigital.drme2edemo.data.api.LoginRequest
import com.ayrindigital.drme2edemo.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AuthViewModel"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val userEmail = authRepository.userEmail.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = null,
    )

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Signup attempt: $email")
                authRepository.signup(email, password)
                Log.d(TAG, "Signup success")
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Signup failed: ${e.message}", e)
                _error.value = e.message ?: "Signup failed"
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Login attempt: $email")
                authRepository.login(email, password)
                Log.d(TAG, "Login success")
                _error.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Login failed: ${e.message}", e)
                _error.value = e.message ?: "Login failed"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
