package com.ayrindigital.drme2edemo.data.auth

import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.api.LoginRequest
import com.ayrindigital.drme2edemo.data.api.SignupRequest
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val apiService: ApiService,
    private val tokenStore: TokenStore,
) {
    val token: Flow<String?> = tokenStore.token
    val userEmail: Flow<String?> = tokenStore.userEmail

    suspend fun signup(email: String, password: String) {
        val response = apiService.signup(SignupRequest(email, password))
        tokenStore.saveToken(response.token, response.userId, email)
    }

    suspend fun login(email: String, password: String) {
        val response = apiService.login(LoginRequest(email, password))
        tokenStore.saveToken(response.token, response.userId, email)
    }

    suspend fun logout() {
        tokenStore.clearToken()
    }
}
