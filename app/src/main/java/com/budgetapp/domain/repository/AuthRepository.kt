package com.budgetapp.domain.repository

import com.budgetapp.core.util.Result

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<String>
    suspend fun login(email: String, password: String): Result<String>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUserId(): String?
    suspend fun isUserLoggedIn(): Boolean
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
