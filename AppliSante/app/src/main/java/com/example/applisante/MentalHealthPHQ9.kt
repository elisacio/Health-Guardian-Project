package com.example.applisante

import android.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Preview(showBackground = true)
@Composable
fun MentalHealthPHQ9Preview() {
    val context = LocalContext.current
    val db = remember {
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    MentalHealthPHQ9(onNavigateBack={}, onSubmitClicked = {score, requiresImmediateHelp ->}, db)
}


data class Question(val text: String, val options: List<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealthPHQ9(
    onNavigateBack: () -> Unit,
    onSubmitClicked: (score: Int, requiresImmediateHelp: Boolean) -> Unit,
    db: AppDatabase
){
    val scope = rememberCoroutineScope()

    val questions = listOf(
        Question(
            text = "1. Little interest or pleasure in doing things",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "2. Feeling down, depressed, or hopeless",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "3. Trouble falling asleep, staying asleep, or sleeping too much",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "4. Feeling tired or having little energy",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "5. Poor appetite or overeating",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "6. Feeling bad about yourself - or that you’re a failure or have let yourself or your family down",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "7. Trouble concentrating on things, such as reading the newspaper or watching television",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "8. Moving or speaking so slowly that other people could have noticed. Or, the opposite - being so fidgety or restless that you have been moving around a lot more than usual",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "9. Thoughts that you would be better off dead or of hurting yourself in some way",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        )
    )

    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }

    // Gestion des couleurs
    val isDark = isSystemInDarkTheme()
    val pastelBlue = if (isDark) Color(0xFF1A334A) else Color(0xFFc5d5e8)
    val cardContentColor = if (isDark) Color.White else Color(0xFF011833)
    val buttonColor = if (isDark) Color(0xFFE3F2FD) else Color(0xFF011833)
    val buttonTextColor = if (isDark) Color(0xFF011833) else Color.White
    val descriptionTextColor = if (isDark) Color.White else Color.DarkGray



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mental Health") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(paddingValues = padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Permet de faire défiler l'écran
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "Depression Questionnaire", style = MaterialTheme.typography.headlineMedium, color = cardContentColor)
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "The proposed questionnaire, named PHQ-9, is a multipurpose instrument for screening, diagnosing, monitoring and measuring the severity of depression.",
                style = MaterialTheme.typography.bodyMedium, color = descriptionTextColor, modifier = Modifier.padding(horizontal = 40.dp), textAlign = TextAlign.Justify)

            Spacer(modifier = Modifier.height(16.dp))

            Text(text="Over the last 2 weeks, how often have you been bothered by the following problems?", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = cardContentColor, modifier = Modifier.padding(horizontal = 40.dp), textAlign = TextAlign.Justify)

            Spacer(modifier = Modifier.height(16.dp))

            // Affichage des 9 questions
            questions.forEachIndexed { questionIndex, question ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = pastelBlue
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = question.text, style = MaterialTheme.typography.titleMedium, color=cardContentColor)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Affichage des 4 options pour cette question
                        question.options.forEachIndexed { optionIndex, optionText ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (selectedAnswers[questionIndex] == optionIndex),
                                        onClick = { selectedAnswers[questionIndex] = optionIndex }
                                    )
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedAnswers[questionIndex] == optionIndex),
                                    onClick = { selectedAnswers[questionIndex] = optionIndex },

                                    colors = RadioButtonColors(
                                        selectedColor = cardContentColor,
                                        unselectedColor = cardContentColor,
                                        disabledSelectedColor = Color.Gray,
                                        disabledUnselectedColor = Color.Gray
                                    )
                                )
                                Text(text = optionText, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bouton Soumettre
            Button(
                enabled = selectedAnswers.size == questions.size,
                onClick = {
                    val totalScore = selectedAnswers.values.sum()
                    val q9Answer = selectedAnswers[8] ?: 0
                    val requiresImmediateHelp = q9Answer > 0

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val currentUser = SessionManager.currentUser
                            if (currentUser != null) {
                                val user = db.userDao().getByEmail(currentUser.email)
                                if (user != null) {
                                    db.userDao().update(user.copy(phq9Score = totalScore))
                                }
                            }
                        }
                        onSubmitClicked(totalScore, requiresImmediateHelp)
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("Submit", color = buttonTextColor)
            }
        }
    }
}