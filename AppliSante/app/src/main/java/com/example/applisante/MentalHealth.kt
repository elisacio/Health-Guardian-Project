package com.example.applisante

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Preview(showBackground = true)
@Composable
fun MentalHealthPreview() {
    val navController = rememberNavController()
    val context = LocalContext.current

    SessionManager.currentUser = UserSession(1, "preview@example.com", "Preview", "User")
    SessionManager.day = "2024-01-01T12:00"

    val db = remember {
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    
    MentalHealth(onNavigateBack={}, navController, db)
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealth(onNavigateBack: () -> Unit, navController: NavController, db: AppDatabase) {
    var pnn50Points by remember { mutableStateOf<List<Float>>(emptyList()) }
    var chartEndDate by remember { mutableStateOf<LocalDateTime?>(null) }
    var phq9Score by remember { mutableStateOf(-1) }
    var gad7Score by remember { mutableStateOf(-1) }

    // Couleurs
    val isDark = isSystemInDarkTheme()
    val cardColorAnxiety = if (isDark) Color(0xFF76647a) else Color(0xFFebd5f0)
    val cardContentColorAnxiety = if (isDark) Color.White else Color(0xFF340140)
    val buttonColorAnxiety =  Color(0xFF340140)
    val buttonTextColorAnxiety =  Color.White
    val cardColorDepression = if (isDark) Color(0xFF1A334A) else Color(0xFFc5d5e8)
    val cardContentColorDepression = if (isDark) Color.White else Color(0xFF011833)
    val buttonColorDepression =  Color(0xFF011833)
    val buttonTextColorDepression =  Color.White

    //Depression
    val interpretationDepression = when (phq9Score) {
        in 0..4 -> "Your score suggests minimal or no symptoms of depression."
        in 5..9 -> "Your score indicates mild symptoms of depression."
        in 10..14 -> "Your score suggests moderate symptoms of depression."
        in 15..19 -> "Your score indicates moderately severe symptoms of depression."
        in 20..27 -> "Your score suggests severe symptoms of depression."
        else -> "Unknown"
    }

    //Anxiety
    val interpretationAnxiety = when (gad7Score) {
        in 0..4 -> "Your score suggests minimal anxiety."
        in 5..9 -> "Your score indicates mild anxiety."
        in 10..14 -> "Your score suggests moderate anxiety."
        in 15..21 -> "Your score indicates severe anxiety."
        else -> "Unknown"
    }

    LaunchedEffect(SessionManager.currentUser, SessionManager.day) {
        val currentUser = SessionManager.currentUser
        val day = SessionManager.day
        if (currentUser != null && day != null) {
            withContext(Dispatchers.IO) {
                val user = db.userDao().getByEmail(currentUser.email)
                if (user != null) {
                    phq9Score = user.phq9Score
                    gad7Score = user.gad7Score

                    if (phq9Score == -1 || gad7Score == -1) {
                        val latestData = db.userDao().getLatestUserData(user.id)
                        if (latestData != null) {
                            if (phq9Score == -1) phq9Score = latestData.DepressionScore.toInt()
                            if (gad7Score == -1) gad7Score = latestData.AnxietyScore.toInt()
                        }
                    }


                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                    
                    val endDateTime = try {
                        LocalDateTime.parse(day.substring(0, 16), formatter)
                    } catch (e: DateTimeParseException) {
                        try {
                            LocalDateTime.parse(day + "T00:00", formatter)
                        } catch (e2: DateTimeParseException) {
                            null
                        }
                    }

                    if (endDateTime != null) {
                        chartEndDate = endDateTime
                        val startDateTime = endDateTime.minusHours(24)
                        val startDateStr = startDateTime.format(formatter)
                        
                        val userDataList = db.userDao().getUserDataInRange(user.id, startDateStr, day)
                        val dataMap = userDataList.associateBy { it.isoDate }
                        
                        val points = mutableListOf<Float>()
                        var currentMinute = startDateTime
                        while (!currentMinute.isAfter(endDateTime)) {
                            val key = currentMinute.format(formatter)
                            val pnn50Raw = dataMap[key]?.pNN50 ?: -1f
                            val pnn50 = if (pnn50Raw == -1f) 100f else pnn50Raw
                            points.add(pnn50)
                            currentMinute = currentMinute.plusMinutes(1)
                        }
                        pnn50Points = points
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mental Health") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Past 24 Hours Stress Level",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)
            ) {
                StressGraphe(data = pnn50Points, endDateTime = chartEndDate)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColorDepression)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Level of Depression",
                        style = MaterialTheme.typography.titleMedium,
                        color = cardContentColorDepression,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = interpretationDepression,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cardContentColorDepression,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally)
                    )
                    Button(
                        onClick = { navController.navigate("MentalHealthPHQ9") },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColorDepression)
                    ) {
                        Text("Take Depression Questionnaire", color = buttonTextColorDepression)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColorAnxiety)
            ) {
                Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
                ) {
                    Text(text="Level of Anxiety",
                        style = MaterialTheme.typography.titleMedium,
                        color = cardContentColorAnxiety,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = interpretationAnxiety,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cardContentColorAnxiety,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally)
                    )
                    Button(onClick = { navController.navigate("MentalHealthGAD7") },
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColorAnxiety),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Take Anxiety Questionnaire", color = buttonTextColorAnxiety)
                    }
                }
            }


        }
    }
}

@Composable
fun StressGraphe(data: List<Float>, endDateTime: LocalDateTime?) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = TextStyle(color = onSurfaceVariant, fontSize = 12.sp)

    if (data.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading data...", color = onSurfaceVariant)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        val leftMargin = 60.dp.toPx()
        val bottomMargin = 40.dp.toPx()
        val topMargin = 10.dp.toPx()
        val rightMargin = 40.dp.toPx()
        
        val chartWidth = width - leftMargin - rightMargin
        val chartHeight = height - bottomMargin - topMargin

        val maxVal = 100f
        val minVal = 0f
        val range = maxVal - minVal


        drawLine(color = onSurfaceVariant.copy(alpha = 0.5f), start = Offset(leftMargin, topMargin), end = Offset(leftMargin, height - bottomMargin), strokeWidth = 2f)
        drawLine(color = onSurfaceVariant.copy(alpha = 0.5f), start = Offset(leftMargin, height - bottomMargin), end = Offset(width - rightMargin, height - bottomMargin), strokeWidth = 2f)

        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val labelVal = minVal + (range / yLabelCount * i)
            val yPos = height - bottomMargin - ((labelVal - minVal) / range * chartHeight)
            
            drawText(
                textMeasurer = textMeasurer,
                text = labelVal.toInt().toString(),
                style = textStyle,
                topLeft = Offset(leftMargin - 40.dp.toPx(), yPos - 8.sp.toPx())
            )
        }

        if (endDateTime != null) {
            val totalHours = 24
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            
            for (i in 0..totalHours) {
                val index = i * 60
                if (index < data.size) {
                    val xPos = leftMargin + (index.toFloat() / (data.size - 1).coerceAtLeast(1) * chartWidth)
                    
                    drawLine(
                        color = onSurfaceVariant.copy(alpha = 0.3f),
                        start = Offset(xPos, height - bottomMargin),
                        end = Offset(xPos, height - bottomMargin + 5.dp.toPx()),
                        strokeWidth = 1f
                    )
                    
                    if (i % 6 == 0) {
                        val time = endDateTime.minusHours((totalHours - i).toLong())
                        drawText(
                            textMeasurer = textMeasurer,
                            text = time.format(formatter),
                            style = textStyle,
                            topLeft = Offset(xPos - 15.dp.toPx(), height - bottomMargin + 8.dp.toPx())
                        )
                    }
                }
            }
        }

        // Draw Line Chart - Normal Y-axis with inverted data
        if (range > 0f) {
            val path = Path()
            data.forEachIndexed { index, pnn50 ->
                // Use 100-pnn50 to represent stress level
                val stressValue = (100f - pnn50).coerceIn(minVal, maxVal)
                val x = leftMargin + (index.toFloat() / (data.size - 1).coerceAtLeast(1) * chartWidth)
                // Normal Y position calculation
                val y = height - bottomMargin - ((stressValue - minVal) / range * chartHeight)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF2196F3), // Vibrant Blue
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
