package com.example.applisante

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Preview(showBackground = true)
@Composable
fun MenstrualCycleCalendarPreview() {

    val context = LocalContext.current

    SessionManager.currentUser = UserSession(1, "preview@example.com", "Preview", "User")
    SessionManager.day = "2024-01-01T12:00"

    val db = remember {
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    MenstrualCycleCalendar(onNavigateBack = {}, db = db)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenstrualCycleCalendar(onNavigateBack: () -> Unit, db: AppDatabase) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var isInitialized by remember { mutableStateOf(false) }

    var periodDays by remember { mutableStateOf<List<LocalDate>>(emptyList()) }
    var fertileDays by remember { mutableStateOf<List<LocalDate>>(emptyList()) }

    val isDarkTheme = isSystemInDarkTheme()
    val periodColor = if(isDarkTheme) Color(0xFFE57373) else Color(0xFFC22300)
    val fertileColor = if(isDarkTheme) Color(0xFF81D4FA) else Color(0xFF55BFE0)
    val bordeauxColor = if(isDarkTheme) Color(0xFFFFB3C6) else Color(0xFF800020)

    LaunchedEffect(currentMonth, SessionManager.currentUser?.email, SessionManager.day) {
        val sessionUser = SessionManager.currentUser
        if (sessionUser != null) {
            val userFromDb = db.userDao().getByEmail(sessionUser.email)
            if (userFromDb != null) {
                val userData = db.userDao().getUserData(userFromDb.id)
                val latestData = userData.maxByOrNull { it.isoDate }
                if (latestData != null) {
                    try {
                        val dbToday = LocalDate.parse(latestData.isoDate.substring(0, 10))
                        today = dbToday // Met à jour le jour en cours d'après la BDD

                        if (!isInitialized) {
                            currentMonth = YearMonth.from(dbToday)
                            isInitialized = true
                        }
                    } catch (e: Exception) { /* Ignorer si la date est mal formatée */ }
                }


                val pDays = mutableSetOf<LocalDate>()
                val fDays = mutableSetOf<LocalDate>()


                userData.forEach { data ->
                    try {
                        val date = LocalDate.parse(data.isoDate.substring(0, 10))
                        if (data.IsOnPeriod) pDays.add(date)

                        val ovulation = (data.AvgCycleLength - 14).toInt().coerceAtLeast(1)
                        if (data.DayInCycle in (ovulation - 4)..(ovulation + 1)) {
                            fDays.add(date)
                        }
                    } catch (e: Exception) {  }
                }


                val groupedByCycle = userData.groupBy { it.CycleID }.filter { it.key != -1 }
                groupedByCycle.forEach { (cycleId, cycleData) ->
                    val firstRecord = cycleData.minByOrNull { it.isoDate }
                    if (firstRecord != null) {
                        try {
                            val recordDate = LocalDate.parse(firstRecord.isoDate.substring(0, 10))

                            val cycleStart = recordDate.minusDays(firstRecord.DayInCycle.toLong() - 1)

                            val avgPeriodLength = firstRecord.AvgPeriodLength.toInt()
                            val avgCycleLength = firstRecord.AvgCycleLength.toInt()

                            val cycleLength = if (cycleId == 0) (SharedMenstrualData.prediction?.cycleLength ?: avgCycleLength) else avgCycleLength
                            val ovulationDay = if (cycleId == 0) (SharedMenstrualData.prediction?.ovulationDay ?: (cycleLength - 14)) else (cycleLength - 14).coerceAtLeast(1)

                            for (p in 0 until avgPeriodLength) {
                                pDays.add(cycleStart.plusDays(p.toLong()))
                            }

                            val fertileStart = cycleStart.plusDays((ovulationDay - 4 - 1).toLong())
                            for (f in 0..5) {
                                fDays.add(fertileStart.plusDays(f.toLong()))
                            }
                        } catch (e: Exception) {}
                    }
                }


                if (latestData != null) {
                    try {
                        val lastDate = LocalDate.parse(latestData.isoDate.substring(0, 10))
                        val currentDayInCycle = latestData.DayInCycle
                        val cycleStart = lastDate.minusDays(currentDayInCycle.toLong() - 1)

                        val avgCycleLength = latestData.AvgCycleLength.toInt()
                        val avgPeriodLength = latestData.AvgPeriodLength.toInt()

                        val currentPredictedCycleLength = SharedMenstrualData.prediction?.cycleLength ?: avgCycleLength


                        for (i in 1..10) {

                            val projectedStart = cycleStart
                                .plusDays(currentPredictedCycleLength.toLong())
                                .plusDays((avgCycleLength * (i - 1)).toLong())

                            val cycleLength = avgCycleLength
                            val ovulationDay = (cycleLength - 14).coerceAtLeast(1)


                            for (p in 0 until avgPeriodLength) {
                                pDays.add(projectedStart.plusDays(p.toLong()))
                            }

                            val fertileStart = projectedStart.plusDays((ovulationDay - 4 - 1).toLong())
                            for (f in 0..5) {
                                fDays.add(fertileStart.plusDays(f.toLong()))
                            }
                        }
                    } catch (e: Exception) {}
                }

                periodDays = pDays.toList()
                fertileDays = fDays.toList()
            }
        }
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
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            Text("Calendar", fontWeight = FontWeight.Bold, fontSize = 30.sp)

            Spacer(modifier = Modifier.height(50.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Month")
                }

                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${currentMonth.year}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = bordeauxColor
                )

                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CalendarGrid(
                yearMonth = currentMonth,
                today = today,
                periodDays = periodDays,
                fertileDays = fertileDays,
                periodColor = periodColor,
                fertileColor = fertileColor,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.weight(1f))


            CalendarLegend(
                periodColor = periodColor,
                fertileColor = fertileColor,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(60.dp)
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = bordeauxColor,
                    contentColor = if(isDarkTheme) Color.Black else Color.White
                )
            ) {
                Text("Back to Cycle Tracking", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    today: LocalDate,
    periodDays: List<LocalDate>,
    fertileDays: List<LocalDate>,
    periodColor: Color,
    fertileColor: Color,
    isDarkTheme: Boolean
) {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val emptyDaysBefore = firstDayOfMonth.dayOfWeek.value - 1

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (day in daysOfWeek) {
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val totalCells = emptyDaysBefore + daysInMonth
        val rows = Math.ceil(totalCells / 7.0).toInt()
        var currentDay = 1

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    if (cellIndex < emptyDaysBefore || currentDay > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = yearMonth.atDay(currentDay)
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isPeriod = periodDays.contains(date),
                            isFertile = fertileDays.contains(date),
                            periodColor = periodColor,
                            fertileColor = fertileColor,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.weight(1f)
                        )
                        currentDay++
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isPeriod: Boolean,
    isFertile: Boolean,
    periodColor: Color,
    fertileColor: Color,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isPeriod -> periodColor
        isFertile -> fertileColor
        isToday -> if (isDarkTheme) Color.DarkGray else Color.LightGray
        else -> Color.Transparent
    }

    val textColor = if (isPeriod || isFertile) Color.White
    else if (isDarkTheme) Color.White else Color.Black

    Box(
        modifier = modifier.aspectRatio(1f).padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (isToday) Modifier.border(3.dp, if(isDarkTheme) Color.LightGray else Color.Black, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun CalendarLegend(
    periodColor: Color,
    fertileColor: Color,
    isDarkTheme: Boolean
) {
    val todayBgColor = if (isDarkTheme) Color.DarkGray else Color.LightGray
    val todayBorderColor = if (isDarkTheme) Color.LightGray else Color.Black
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = periodColor, label = "Period", textColor = textColor)
            LegendItem(color = fertileColor, label = "Fertile Window", textColor = textColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                color = todayBgColor,
                label = "Today",
                textColor = textColor,
                borderColor = todayBorderColor
            )
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    textColor: Color,
    borderColor: Color? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (borderColor != null) Modifier.border(2.dp, borderColor, CircleShape)
                    else Modifier
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}