package com.example.applisante

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
fun MentalHealthGAD7Preview() {
    val context = LocalContext.current
    val db = remember {
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    MentalHealthGAD7(onNavigateBack={}, onSubmitClicked = {score ->}, db)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealthGAD7(
    onNavigateBack: () -> Unit,
    onSubmitClicked: (score: Int) -> Unit,
    db: AppDatabase
){
    val scope = rememberCoroutineScope()

    val questions = listOf(
        Question(
            text = "1. Feeling nervous, anxious or on edge",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "2. Not being able to stop or control worrying",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "3. Worrying too much about different things",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "4. Trouble relaxing",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "5. Being so restless that it is hard to sit still",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "6. Becoming easily annoyed or irritable",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        ),
        Question(
            text = "7. Feeling afraid as if something awful might happen",
            options = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")
        )
    )

    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }

    // Gestion des couleurs
    val isDark = isSystemInDarkTheme()
    val cardColor = if (isDark) Color(0xFF76647a) else Color(0xFFebd5f0)
    val cardContentColor = if (isDark) Color.White else Color(0xFF340140)
    val buttonColor = if (isDark) Color(0xFFebd5f0) else Color(0xFF340140)
    val buttonTextColor = if (isDark) Color(0xFF340140) else Color.White
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
            Text(text = "Anxiety Questionnaire", style = MaterialTheme.typography.headlineMedium, color = cardContentColor)
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "The proposed questionnaire, named GAD-7, is a multipurpose instrument for screening, diagnosing, monitoring and measuring the severity of anxiety.",
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
                        containerColor = cardColor
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = question.text, style = MaterialTheme.typography.titleMedium, color=cardContentColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        
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

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val currentUser = SessionManager.currentUser
                            if (currentUser != null) {
                                val user = db.userDao().getByEmail(currentUser.email)
                                if (user != null) {
                                    db.userDao().update(user.copy(gad7Score = totalScore))
                                }
                            }
                        }
                        onSubmitClicked(totalScore)
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