package com.example.applisante

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Preview(showBackground = true)
@Composable
fun HeartRatePreview() {
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
    
    HeartRate(onNavigateBack={}, navController, db)
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRate(onNavigateBack: () -> Unit,navController: NavController, db: AppDatabase) {
    var rhrValue by remember { mutableStateOf<Int?>(null) }
    var heartRatePoints by remember { mutableStateOf<List<Int>>(emptyList()) }
    var coherenceList by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var chartEndDate by remember { mutableStateOf<LocalDateTime?>(null) }
    var isDataLoaded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val anomalyDisplayValue = remember(coherenceList, heartRatePoints, isDataLoaded) {
        if (!isDataLoaded) {
            "Loading..."
        } else if (heartRatePoints.isEmpty()) {
            "0"
        } else if (coherenceList.isEmpty()) {
            "N/A"
        } else {
            val count = if (coherenceList.size >= heartRatePoints.size) {
                coherenceList.takeLast(heartRatePoints.size).count { !it }
            } else {
                coherenceList.count { !it }
            }
            count.toString()
        }
    }

    LaunchedEffect(SessionManager.currentUser, SessionManager.day) {
        val currentUser = SessionManager.currentUser
        val day = SessionManager.day
        if (currentUser != null && day != null) {
            isDataLoaded = false
            withContext(Dispatchers.IO) {
                val user = db.userDao().getByEmail(currentUser.email)
                if (user != null) {
                    val currentData = db.userDao().getUserDataByDate(user.id, day)
                    rhrValue = currentData?.RestingHeartRate

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
                        val start48h = endDateTime.minusHours(48)
                        val start48hStr = start48h.format(formatter)
                        
                        val userDataList = db.userDao().getUserDataInRange(user.id, start48hStr, day)
                        val dataMap = userDataList.associateBy { it.isoDate }
                        
                        val points48h = mutableListOf<Int>()
                        var currentMinute = start48h
                        var hasStarted = false
                        while (!currentMinute.isAfter(endDateTime)) {
                            val key = currentMinute.format(formatter)
                            val heartRate = dataMap[key]?.heart_rate ?: 0
                            if(heartRate != 0){
                                hasStarted = true
                            }
                            if(hasStarted){
                                points48h.add(heartRate)
                            }
                            currentMinute = currentMinute.plusMinutes(1)
                        }
                        
                        heartRatePoints = if (points48h.size >= 1440) {
                            points48h.takeLast(1441)
                        } else {
                            points48h
                        }

                        if (points48h.size > 1440) { 
                            if (!Python.isStarted()) {
                                Python.start(AndroidPlatform(context))
                            }
                            val py = Python.getInstance()
                            val pyModule = py.getModule("predictionScript")
                            val result = pyModule.callAttr("verifier_rythme_cardiaque", points48h.toIntArray())
                            val pyResults = result.toJava(DoubleArray::class.java)
                            
                            coherenceList = List(points48h.size) { i ->
                                if (i < 1440) {
                                    true
                                } else {
                                    val resultIndex = if (pyResults.size == points48h.size) i else i - 1440
                                    if (resultIndex in pyResults.indices) pyResults[resultIndex] == 0.0 else true
                                }
                            }
                        } else {
                            coherenceList = emptyList()
                        }
                    }
                }
                isDataLoaded = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Heart Rate") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Past 24 Hours Heart Rate",
                style = MaterialTheme.typography.titleMedium
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)
            ) {
                Graphe(data = heartRatePoints, coherence = coherenceList, endDateTime = chartEndDate)
            }

            HorizontalDivider()

            Text(
                text = "Daily stats",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            StatRow(
                label = "Resting Heart Rate",
                value = if (!isDataLoaded) "Loading..." else (rhrValue?.let { "$it bpm" } ?: "-- bpm"),
                color = Color(0xFFF44336)
            )

            StatRow(
                label = "Anomalies detected",
                value = anomalyDisplayValue,
                color = Color(0xFFD32F2F),
                note = "Note: This is an indicator that shows when your heart rate has varied significantly due to external factors (stress, sudden effort, etc.). It also counts when the watch becomes active or inactive."
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color, note: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            }
            if (note != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun Graphe(data: List<Int>, coherence: List<Boolean>, endDateTime: LocalDateTime?) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
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

        val maxHR = 200f
        val minHR = 0f
        val range = maxHR - minHR

        if (coherence.isNotEmpty() && data.isNotEmpty()) {
            val relevantCoherence = if (coherence.size >= data.size) {
                coherence.takeLast(data.size)
            } else {
                coherence
            }

            val colWidth = (chartWidth / (data.size - 1).coerceAtLeast(1)).coerceAtLeast(1f)
            for (i in data.indices) {
                if (i < relevantCoherence.size && !relevantCoherence[i]) {
                    val xPos = leftMargin + (i.toFloat() / (data.size - 1).coerceAtLeast(1) * chartWidth)
                    drawRect(
                        color = Color.Red.copy(alpha = 0.2f),
                        topLeft = Offset(xPos - colWidth / 2, topMargin),
                        size = androidx.compose.ui.geometry.Size(colWidth, chartHeight)
                    )
                }
            }
        }

        drawLine(color = onSurfaceVariant.copy(alpha = 0.5f), start = Offset(leftMargin, topMargin), end = Offset(leftMargin, height - bottomMargin), strokeWidth = 2f)
        drawLine(color = onSurfaceVariant.copy(alpha = 0.5f), start = Offset(leftMargin, height - bottomMargin), end = Offset(width - rightMargin, height - bottomMargin), strokeWidth = 2f)

        val yLabelCount = 4
        for (i in 0..yLabelCount) {
            val hrValue = minHR + (range / yLabelCount * i)
            val yPos = height - bottomMargin - ((hrValue - minHR) / range * chartHeight)
            
            drawText(
                textMeasurer = textMeasurer,
                text = hrValue.toInt().toString(),
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

        if (range > 0f && data.isNotEmpty()) {
            val path = Path()
            for (i in data.indices) {
                val x = leftMargin + (i.toFloat() / (data.size - 1).coerceAtLeast(1) * chartWidth)
                val y = height - bottomMargin - ((data[i].toFloat().coerceIn(minHR, maxHR) - minHR) / range * chartHeight)
                
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(0xFFF44336), // Vibrant Red
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
