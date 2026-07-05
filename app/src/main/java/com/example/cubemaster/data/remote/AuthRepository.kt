package com.example.cubemaster.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun ensureSignedIn(): FirebaseUser {
        auth.currentUser?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user ?: error("Не вдалося ініціалізувати локальний обліковий запис")
    }
}
