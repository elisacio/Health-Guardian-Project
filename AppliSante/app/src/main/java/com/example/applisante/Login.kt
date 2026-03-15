package com.example.applisante

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.*
import android.util.Patterns
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun Login(navController: NavController, db: AppDatabase) {
    var lastCharIndex by remember { mutableStateOf(-1) }
    val passwordTransformation = remember(lastCharIndex) {
        LastCharVisiblePasswordTransformation(lastCharIndex)
    }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    // Vérification élémentaire pour activer le bouton
    val isInputValid by derivedStateOf {
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                password.isNotBlank()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // HEADER
            Text("Health", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            // PROFILE ICON
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("Sign in to your account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { new ->
                    password = new
                    lastCharIndex = new.length - 1
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                visualTransformation = passwordTransformation,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password
                )
            )

            Spacer(Modifier.height(16.dp))

            // Bouton désactivé si les champs sont invalides
            Button(
                onClick = {
                    error = ""
                    CoroutineScope(Dispatchers.IO).launch {

                        val user = db.userDao().getByEmail(email.lowercase())
                        if (user != null && PasswordUtils.verify(password, user.passwordHash)) {
                            val latestData = db.userDao().getLatestUserData(user.id)
                            withContext(Dispatchers.Main) {
                                SessionManager.currentUser = UserSession(
                                    id = user.id,
                                    email = user.email,
                                    firstName = user.firstName,
                                    lastName = user.lastName
                                )
                                SessionManager.day = "2026-02-11T08:30"
                                navController.navigate("Home")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                error = "Incorrect email or password"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isInputValid
            ) {
                Text("Sign in")
            }

            Spacer(Modifier.height(8.dp))
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = { navController.navigate("Register") }) {
                Text("You don't have an account? Create one here!")
            }
        }
    }
}