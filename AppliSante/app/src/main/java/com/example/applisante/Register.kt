package com.example.applisante

import android.app.NotificationChannel
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import kotlinx.coroutines.*
import android.util.Patterns
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign


@Composable
@Preview(showBackground = true)
fun RegisterPreview() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val db = Room.inMemoryDatabaseBuilder(
        context,
        AppDatabase::class.java
    ).build()

    Register(navController = navController, db = db)
}



@Composable
fun Register(navController: NavController, db: AppDatabase) {
    var lastCharIndex by remember { mutableStateOf(-1) }
    var page by remember { mutableStateOf(1) }
    val passwordTransformation = remember(lastCharIndex) {
        LastCharVisiblePasswordTransformation(lastCharIndex)
    }

    // Page 1
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }

    // Page 2
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var menstruated by remember { mutableStateOf(true) }
    var sex by remember { mutableStateOf(EnumSex.OTHER) }

    // Page 3
    var lastPeriodDate by remember { mutableStateOf("") }
    var avgCycleLength by remember { mutableStateOf("28") }
    var avgPeriodLength by remember { mutableStateOf("5") }
    var isRegular by remember { mutableStateOf(true) }

    // Page 4: File Selection
    val context = LocalContext.current
    val dataFiles = remember { 
        context.assets.list("data")?.toList() ?: listOf("data_final_with_cycles.csv")
    }
    var selectedFile by remember { mutableStateOf(dataFiles.firstOrNull() ?: "data_final_with_cycles.csv") }
    var expanded by remember { mutableStateOf(false) }

    var error by remember { mutableStateOf("") }

    fun isPage1Valid(): Boolean {
        return email.isNotBlank() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                password.length >= 6 &&
                firstName.isNotBlank() &&
                lastName.isNotBlank() &&
                age.toIntOrNull()?.let { it > 0 } == true
    }

    fun isPage2Valid(): Boolean {
        return height.toFloatOrNull()?.let { it > 0 } == true &&
                weight.toFloatOrNull()?.let { it > 0 } == true
    }

    fun isPage3Valid(): Boolean {
        return lastPeriodDate.isNotBlank() &&
                avgCycleLength.toIntOrNull()?.let { it > 0 } == true &&
                avgPeriodLength.toIntOrNull()?.let { it > 0 } == true
    }

    fun createAccount() {
        error = ""
        CoroutineScope(Dispatchers.IO).launch {
            val existing = db.userDao().getByEmail(email.lowercase())
            withContext(Dispatchers.Main) {
                if (existing != null) {
                    error = "Account already exists"
                } else {
                    val hash = PasswordUtils.hash(password)
                    val isoDateMax = "2026-02-11T08:30"

                    val userId = db.userDao().insert(
                        User(
                            email = email.lowercase(),
                            passwordHash = hash,
                            firstName = firstName,
                            lastName = lastName,
                            age = age.toInt(),
                            height = height.toFloat(),
                            weight = weight.toFloat(),
                            menstruated = menstruated,
                            sex = sex,
                            lastPeriodDate = if (menstruated) lastPeriodDate else "",
                            initialAvgCycleLength = if (menstruated) avgCycleLength.toInt() else 28,
                            initialAvgPeriodLength = if (menstruated) avgPeriodLength.toInt() else 5,
                            isCycleRegular = if (menstruated) isRegular else true,
                            dataFileName = selectedFile
                        )
                    ).toInt()

                    val csvDataList = readCsvFromAssets_list(context, isoDateMax, selectedFile)
                    val entities = csvDataList.map {
                        UserData(
                            userId = userId,
                            isoDate = it.isoDate,
                            heart_rate = it.heart_rate,
                            GarminStressLevel = it.GarminStressLevel,
                            ZCC = it.ZCC,
                            SleepingStage = it.SleepingStage,
                            StageDuration = it.StageDuration,
                            SleepScore = it.SleepScore,
                            RestingHeartRate = it.RestingHeartRate,
                            pNN50 = it.pNN50,
                            SD2 = it.SD2,
                            CycleID = it.CycleID,
                            DayInCycle = it.DayInCycle,
                            BasaleTemperature = it.BasaleTemperature,
                            IsOnPeriod = it.IsOnPeriod,
                            AvgCycleLength = it.AvgCycleLength,
                            AvgPeriodLength = it.AvgPeriodLength,
                            TAT = it.TAT,
                            Energy = it.Energy,
                            Steps = it.Steps,
                            Calories = it.Calories,
                            ActivityScore = it.ActivityScore,
                            RealStress = it.RealStress,
                            DepressionScore = it.DepressionScore,
                            AnxietyScore = it.AnxietyScore
                        )
                    }
                    db.userDao().insertUserData(entities)

                    SessionManager.day = isoDateMax
                    navController.navigate("Login")
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Health", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp))
            }

            Spacer(Modifier.height(16.dp))

            when (page) {
                1 -> {

                    Text("Create your account", style = MaterialTheme.typography.titleMedium)

                    TextButton(onClick = { navController.navigate("Login") }) {
                        Text("Or sign in to your account if you already have one", textAlign = TextAlign.Center)
                    }

                    OutlinedTextField(email, onValueChange = { email = it.lowercase() }, label = { Text("Email")  })
                    OutlinedTextField(
                        value = password,
                        onValueChange = { new ->
                            password = new
                            lastCharIndex = new.length - 1
                        },
                        label = { Text("Password") },
                        visualTransformation = passwordTransformation,
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password
                        )
                    )

                    Row {
                        OutlinedTextField(firstName, { firstName = it },
                            label = { Text("First Name") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(lastName, { lastName = it },
                            label = { Text("Last Name") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(age, { age = it }, label = { Text("Age") })

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { page = 2 },
                        enabled = isPage1Valid()
                    ) {
                        Text("Next")
                    }
                }
                2 -> {

                    Text("Create your account", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(height, { height = it }, label = { Text("Height (cm)") })
                    OutlinedTextField(weight, { weight = it }, label = { Text("Weight (kg)") })

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Biological sex")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sex == EnumSex.MALE,   onClick = { sex = EnumSex.MALE })
                        Text("Male")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = sex == EnumSex.FEMALE, onClick = { sex = EnumSex.FEMALE })
                        Text("Female")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = sex == EnumSex.OTHER,  onClick = { sex = EnumSex.OTHER })
                        Text("Other")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are you menstruated ?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = menstruated, onClick = { menstruated = true })
                        Text("Yes")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = !menstruated, onClick = { menstruated = false })
                        Text("No")
                    }

                    Button(
                        onClick = {
                            if (menstruated) page = 3 else page = 4
                        },
                        enabled = isPage1Valid() && isPage2Valid()
                    ) {
                        Text("Next")
                    }
                }
                3 -> {

                    Text("Menstrual Cycle Details", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = lastPeriodDate,
                        onValueChange = { lastPeriodDate = it },
                        label = { Text("First day of last period (YYYY-MM-DD)") }
                    )
                    OutlinedTextField(
                        value = avgCycleLength,
                        onValueChange = { avgCycleLength = it },
                        label = { Text("Average cycle length (days)") }
                    )
                    OutlinedTextField(
                        value = avgPeriodLength,
                        onValueChange = { avgPeriodLength = it },
                        label = { Text("Average period length (days)") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are your periods regular?")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isRegular, onClick = { isRegular = true })
                        Text("Yes")
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = !isRegular, onClick = { isRegular = false })
                        Text("No")
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { page = 4 },
                        enabled = isPage3Valid()
                    ) {
                        Text("Next")
                    }
                }
                4 -> {
                    Text("Select Data File", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedFile)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            dataFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file) },
                                    onClick = {
                                        selectedFile = file
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { createAccount() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Account")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}