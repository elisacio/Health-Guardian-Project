package com.example.applisante

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import androidx.room.Room
import java.time.LocalDate

data class CycleInfo(
    val cycleId: Int,
    val startDate: String,
    val endDate: String,
    val cycleLength: Float,
    val periodLength: Float,
    val ovulationDate: String,
    val note: String,
    val isExcluded: Boolean
)
@Preview(showBackground = true)
@Composable
fun MenstrualCycleHistoryPreview() {

    val context = LocalContext.current

    SessionManager.currentUser = UserSession(1, "preview@example.com", "Preview", "User")
    SessionManager.day = "2024-01-01T12:00"

    val db = remember {
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    MenstrualCycleHistory(
        onNavigateBack = {},
        db = db)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenstrualCycleHistory(onNavigateBack: () -> Unit, db: AppDatabase) {
    val coroutineScope = rememberCoroutineScope()
    var userCycles by remember { mutableStateOf<List<CycleInfo>>(emptyList()) }
    var currentUserId by remember { mutableStateOf(-1) }

    // Dialog State
    var showDialog by remember { mutableStateOf(false) }
    var selectedCycleId by remember { mutableStateOf(-1) }
    var textNote by remember { mutableStateOf("") }

    val isDarkTheme = isSystemInDarkTheme()
    val pastelPink = if(isDarkTheme) Color(0xFF4A151C) else Color(0xFFFFe3e6)
    val darkPink = if(isDarkTheme) Color(0xFFFFB3C6) else Color(0xFF360106)
    val excludedBg = if(isDarkTheme) Color(0xFF2C2C2C) else Color(0xFFE0E0E0)
    val excludedText = if(isDarkTheme) Color(0xFF888888) else Color(0xFF757575)

    fun loadData() {
        coroutineScope.launch {
            val sessionUser = SessionManager.currentUser
            if (sessionUser != null) {
                val userFromDb = db.userDao().getByEmail(sessionUser.email)
                if (userFromDb != null) {
                    currentUserId = userFromDb.id
                    val allData = db.userDao().getUserData(userFromDb.id)
                    // Grouper les données par cycle ID
                    val grouped = allData.groupBy { it.CycleID }.filter { it.key != -1 }

                    val maxCycleId = grouped.keys.maxOrNull() ?: -1

                    val cycles = grouped.map { (id, dataList) ->
                        val firstRecord = dataList.minByOrNull { it.isoDate } ?: dataList.first()

                        val startLocalDate = try {
                            val recordDate = LocalDate.parse(firstRecord.isoDate.substring(0, 10))
                            recordDate.minusDays(firstRecord.DayInCycle.toLong() - 1)
                        } catch (e: Exception) {
                            LocalDate.now()
                        }

                        val cycleLength = if (id == maxCycleId) {
                            SharedMenstrualData.prediction?.cycleLength ?: dataList.maxOf { it.DayInCycle }.toInt()
                        } else {
                            dataList.maxOf { it.DayInCycle }.toInt()
                        }

                        val endLocalDate = startLocalDate.plusDays((cycleLength - 1).toLong())

                        val ovulationDay = if (id == maxCycleId) {
                            SharedMenstrualData.prediction?.ovulationDay ?: (cycleLength - 14).coerceAtLeast(1)
                        } else {
                            (cycleLength - 14).coerceAtLeast(1)
                        }
                        val ovulationLocalDate = startLocalDate.plusDays((ovulationDay - 1).toLong())

                        CycleInfo(
                            cycleId = id,
                            startDate = startLocalDate.toString(),
                            endDate = endLocalDate.toString(),
                            cycleLength = cycleLength.toFloat(),
                            periodLength = dataList.first().AvgPeriodLength,
                            ovulationDate = ovulationLocalDate.toString(),
                            note = dataList.firstOrNull { it.note.isNotEmpty() }?.note ?: "",
                            isExcluded = dataList.first().isExcluded
                        )
                    }.sortedByDescending { it.startDate }

                    userCycles = cycles
                }
            }
        }
    }

    LaunchedEffect(SessionManager.currentUser?.email, SessionManager.day) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menstrual Cycle Tracking") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            Text("History", fontWeight = FontWeight.Bold, fontSize = 30.sp)

            Spacer(modifier = Modifier.height(50.dp))

            userCycles.forEach { cycle ->
                val isExcluded = cycle.isExcluded
                val bgColor = if (isExcluded) excludedBg else pastelPink
                val txtColor = if (isExcluded) excludedText else darkPink

                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = if (isExcluded) "Cycle ${cycle.cycleId} (Excluded)" else "Cycle ${cycle.cycleId}",
                            fontWeight = FontWeight.SemiBold,
                            color = txtColor,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(text = "· Start : ${cycle.startDate}", color = txtColor, fontSize = 16.sp)
                        Text(text = "· Ovulation Date : ${cycle.ovulationDate}", color = txtColor, fontSize = 16.sp)
                        Text(text = "· End : ${cycle.endDate}", color = txtColor, fontSize = 16.sp)
                        Text(text = "· Cycle Length : ${cycle.cycleLength.toInt()} days", color = txtColor, fontSize = 16.sp)
                        Text(text = "· Period Length : ${cycle.periodLength.toInt()} days", color = txtColor, fontSize = 16.sp)

                        if (cycle.note.isNotEmpty()) {
                            Text(text = "· Note : ${cycle.note}", color = txtColor, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                selectedCycleId = cycle.cycleId
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(0.6f).height(40.dp).align(Alignment.CenterHorizontally),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = txtColor)
                        ) {
                            Text("add a note", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    db.userDao().updateCycleExclusion(currentUserId, cycle.cycleId, !isExcluded)
                                    loadData()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.6f).height(40.dp).align(Alignment.CenterHorizontally),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = txtColor)
                        ) {
                            Text(if (isExcluded) "include cycle" else "exclude cycle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }


        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = "Add a note") },
                text = {
                    OutlinedTextField(
                        value = textNote,
                        onValueChange = { textNote = it },
                        label = { Text("Your note") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                db.userDao().updateCycleNote(currentUserId, selectedCycleId, textNote)
                                showDialog = false
                                textNote = ""
                                loadData()
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}