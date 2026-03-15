package com.example.applisante

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true)
@Composable
fun MentalHealthGAD7ResultPreview() {
    MentalHealthGAD7Result(onNavigateHome={},score = 10)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealthGAD7Result(
    onNavigateHome: () -> Unit,
    score: Int
) {
    val interpretation = when (score) {
        in 0..4 -> "Your score suggests minimal anxiety. It is completely normal to feel a little worry from time to time. Keep up with your current healthy habits and continue finding time to relax."
        in 5..9 -> "Your score indicates mild anxiety. You might be dealing with some extra stress lately. Remember to take breaks, practice self-care, and consider talking to a trusted friend if you feel overwhelmed."
        in 10..14 -> "Your score suggests moderate anxiety. These persistent feelings of worry and tension can be difficult to manage on your own. It might be helpful to reach out to a healthcare professional or counselor for guidance and coping strategies."
        in 15..21 -> "Your score indicates severe anxiety. These persistent feelings of worry and tension can be difficult to manage on your own. We strongly encourage you to reach out to a doctor or a mental health professional who can provide the right support to help you feel better."
        else -> "Score calculation error."
    }

    // Gestion des couleurs
    val isDark = isSystemInDarkTheme()
    val cardColor = if (isDark) Color(0xFF76647a) else Color(0xFFebd5f0)
    val cardContentColor = if (isDark) Color.White else Color(0xFF340140)
    val buttonColor = if (isDark) Color(0xFFebd5f0) else Color(0xFF340140)
    val buttonTextColor = if (isDark) Color(0xFF340140) else Color.White

    val spacerHeight = 150.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mental Health") },
                navigationIcon = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.height(spacerHeight))
            Text(
                text = "Your Score:",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(15.dp))
            Text(
                text = "$score / 21",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(spacerHeight))


            // Carte d'interprétation
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = interpretation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cardContentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNavigateHome,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("Back to Mental Health Home", fontWeight = FontWeight.Bold, color=buttonTextColor, fontSize = 16.sp)
            }
        }
    }
}