package com.example.applisante

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class UserSession(
    val id: Int,
    val email: String,
    val firstName: String,
    val lastName: String
)

object SessionManager {
    var currentUser: UserSession? = null
    var day by mutableStateOf("")
}