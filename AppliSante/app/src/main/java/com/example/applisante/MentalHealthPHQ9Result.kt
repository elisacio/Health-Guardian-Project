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
fun MentalHealthPHQ9ResultPreview() {
    MentalHealthPHQ9Result(onNavigateHome={},score = 15, requiresImmediateHelp = true)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealthPHQ9Result(
    onNavigateHome: () -> Unit,
    score: Int,
    requiresImmediateHelp: Boolean
) {
    val interpretation = when (score) {
        in 0..4 -> "Your score suggests minimal or no symptoms of depression. Keep focusing on your well-being and maintaining healthy habits."
        in 5..9 -> "Your score indicates mild symptoms of depression. You might be going through a stressful period. Consider incorporating more self-care into your routine or talking to a trusted friend."
        in 10..14 -> "Your score suggests moderate symptoms of depression. It might be helpful to monitor how you're feeling and consider reaching out to a healthcare professional or counselor for guidance."
        in 15..19 -> "Your score indicates moderately severe symptoms of depression. These feelings can be heavy to carry alone. We strongly encourage you to consult a healthcare provider or a mental health professional for support."
        in 20..27 -> "Your score suggests severe symptoms of depression. Please know that help is available and you don't have to go through this alone. It is highly recommended that you reach out to a doctor or a mental health specialist as soon as possible."
        else -> "Score calculation error."
    }

    // Gestion des couleurs
    val isDark = isSystemInDarkTheme()
    val pastelBlue = if (isDark) Color(0xFF1A334A) else Color(0xFFE3F2FD)
    val cardContentColor = if (isDark) Color.White else Color(0xFF011833)
    val buttonColor = if (isDark) Color(0xFFE3F2FD) else Color(0xFF011833)
    val buttonTextColor = if (isDark) Color(0xFF011833) else Color.White


    val spacerHeight = if (requiresImmediateHelp) 50.dp else 120.dp

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
                text = "$score / 27",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(spacerHeight))


            // Carte d'interprétation principale
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = pastelBlue),
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

            Spacer(modifier = Modifier.height(24.dp))

            // Message concernant le risque de suicide si la question 9 a été cochée (> 0)
            if (requiresImmediateHelp) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF4A2020) else Color(0xFFFFEBEE) // Rouge pastel
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Important Support",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFCDD2) else Color(0xFFB71C1C)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Because of your response to the last question, we want to remind you that you are not alone.\n\nIf you're in France, do not hesitate to reach out to the Suicide Prevention Hotline 3114.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (isDark) Color.White else Color.Black
                        )
                    }
                }
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