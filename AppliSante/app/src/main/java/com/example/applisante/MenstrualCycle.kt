package com.example.applisante

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room

object SharedMenstrualData {
    var prediction: CyclePrediction? = null
    var lastPredictionDate: String? = null // Format "YYYY-MM-DD"
}

@Preview(showBackground = true)
@Composable
fun MenstrualCyclePreview() {
    val context = LocalContext.current

    SessionManager.currentUser = UserSession(1, "preview@example.com", "Preview", "User")
    SessionManager.day = "2024-01-01T12:00"

    val db = remember {
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    MenstrualCycle(
        onNavigateBack = {  },
        onNavigateToCalendar = { },
        onNavigateToHistory = {  },
        db = db)
}

@Composable
fun CycleVisualizer(
    currentDay: Int,
    currentPhase: String,
    fertilityProb: String,
    ovulationDay: Int,
    periodLength: Int = 5,
    totalDays: Int = 28,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(280.dp)) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val smallCircleRadius = 12.dp.toPx()
            val smallCircleStrokeWidth = 8.dp.toPx()
            val radius = size.minDimension / 2 - smallCircleRadius
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val degreesPerDay = 360f / totalDays

            drawCircle(
                color = if (isDarkTheme) Color.DarkGray else Color.LightGray,
                radius = radius,
                style = Stroke(width = strokeWidth)
            )

            val periodColor = if(isDarkTheme) Color(0xFFE57373) else Color(0xFFC22300)
            drawArc(
                color = periodColor,
                startAngle = 270f,
                sweepAngle = periodLength * degreesPerDay,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val fertileStartDay = ovulationDay - 4
            val fertileDaysCount = 6f
            val fertileStartAngle = 270f + (fertileStartDay - 1) * degreesPerDay
            val lightBlueColor = if(isDarkTheme) Color(0xFF81D4FA) else Color(0xFF55BFE0)

            drawArc(
                color = lightBlueColor,
                startAngle = fertileStartAngle,
                sweepAngle = fertileDaysCount * degreesPerDay,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val dayToCalculate = currentDay.coerceAtMost(totalDays)
            val angleDegrees = 270f + ((dayToCalculate - 1).toFloat() / totalDays) * 360f
            val angleRad = (angleDegrees * PI / 180f).toFloat()

            val indicatorX = center.x + radius * cos(angleRad)
            val indicatorY = center.y + radius * sin(angleRad)

            drawCircle(
                style = Stroke(width = smallCircleStrokeWidth),
                color = if (isDarkTheme) Color.White else Color.Black,
                radius = smallCircleRadius,
                center = Offset(indicatorX, indicatorY)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val daysUntilPeriod = totalDays - currentDay + 1
            Text(
                text = "$currentPhase phase",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = if(isDarkTheme) Color(0xFFFFB3C6) else Color(0xFF800020)
            )
            Spacer(modifier = Modifier.height(15.dp))
            Text(
                text = "$daysUntilPeriod days until next period",
                fontSize = 18.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text= "Fertility : $fertilityProb",
                fontSize = 18.sp,
                color = if(isDarkTheme) Color(0xFF81D4FA) else Color(0xFF005675)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenstrualCycle(
    onNavigateBack: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToHistory: () -> Unit,
    db: AppDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()



    var latestData by remember { mutableStateOf<UserData?>(null) }
    var hasAnsweredPeriod by remember { mutableStateOf(false) }



    var prediction by remember { mutableStateOf<CyclePrediction?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Gestion des couleurs
    val isDarkTheme = isSystemInDarkTheme()
    val bordeauxColor = if (isDarkTheme) Color(0xFFFFB3C6) else Color(0xFF800020)
    val orangeColor = if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE35300)
    val orangeClairColor = if (isDarkTheme) Color(0xFF4E342E) else Color(0xFFFFF4EB)


    LaunchedEffect(SessionManager.currentUser?.email, SessionManager.day) {
        val sessionUser = SessionManager.currentUser
        if (sessionUser != null) {
            val userFromDb = db.userDao().getByEmail(sessionUser.email)
            if (userFromDb != null) {
                latestData = db.userDao().getLatestUserData(userFromDb.id)
                if (latestData?.HasAnsweredPeriodQ == true) {
                    hasAnsweredPeriod = true
                }

                val allData = db.userDao().getUserData(userFromDb.id)
                if (allData.isNotEmpty()) {
                    val currentDbDay = latestData?.isoDate?.substring(0, 10)

                    if (SharedMenstrualData.prediction == null || SharedMenstrualData.lastPredictionDate != currentDbDay) {
                        SharedMenstrualData.prediction = MenstrualPredictionManager.getPredictions(context, allData)
                        SharedMenstrualData.lastPredictionDate = currentDbDay
                    }
                    prediction = SharedMenstrualData.prediction
                }
            }
        }
        isLoading = false
    }


    val jourActuel = latestData?.DayInCycle ?: 1
    val joursTotaux = prediction?.cycleLength ?: latestData?.AvgCycleLength?.toInt() ?: 28

    val periodLength = latestData?.AvgPeriodLength?.toInt() ?: 5
    val ovulation = prediction?.ovulationDay ?: (joursTotaux - 14).coerceAtLeast(1)

    val phase = prediction?.status ?: "Calculating"
    val fertility = prediction?.fertilityProb ?: "..."

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
        if (isLoading || prediction == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = bordeauxColor,
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Analyzing your cycle data...",
                        color = bordeauxColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(paddingValues = padding).fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    "Day $jourActuel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                CycleVisualizer(
                    currentDay = jourActuel,
                    totalDays = joursTotaux,
                    currentPhase = phase,
                    fertilityProb = fertility,
                    ovulationDay = ovulation,
                    periodLength = periodLength,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(30.dp))


                // Carte de question sur les règles
                if (!hasAnsweredPeriod && (jourActuel > joursTotaux - 2 || jourActuel < periodLength + 2)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(bottom = 20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = orangeClairColor
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Are you on your period today?",
                                fontWeight = FontWeight.SemiBold,
                                color = orangeColor,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            latestData?.let {
                                                db.userDao().updatePeriodStatus(it.dataId, true)
                                                db.userDao().updatePeriodQStatus(it.dataId, true)
                                                hasAnsweredPeriod = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = orangeColor,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Yes")
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            latestData?.let {
                                                db.userDao().updatePeriodStatus(it.dataId, false)
                                                hasAnsweredPeriod = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = orangeColor,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("No")
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(60.dp))
                }

                Button(
                    onClick = onNavigateToCalendar,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(55.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = bordeauxColor)
                ) {
                    Text(
                        "View Calendar",
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color.Black else Color.White,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(55.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = bordeauxColor)
                ) {
                    Text(
                        "View History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}