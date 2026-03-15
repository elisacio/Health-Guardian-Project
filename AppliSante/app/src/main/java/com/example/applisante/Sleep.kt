package com.example.applisante

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private enum class SleepViewMode { NIGHT, OVERALL }

data class SleepSample(
    val dt: LocalDateTime,
    val stage: String,
    val sleepScore: Int?
)

private data class DailyScore(
    val date: LocalDate,
    val score: Int?
)

private val STAGE_ORDER = listOf("deep", "light", "rem", "awake")
private val STAGE_LABEL = mapOf(
    "deep" to "Deep",
    "light" to "Light",
    "rem" to "REM",
    "awake" to "Awake"
)

private val STAGE_COLOR = mapOf(
    "deep" to Color(0xFF1E3A8A),
    "light" to Color(0xFF3B82F6),
    "rem" to Color(0xFFEC4899),
    "awake" to Color(0xFFF59E0B)
)

private fun parseIsoToLocalDateTime(iso: String): LocalDateTime {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    return LocalDateTime.parse(iso.substring(0, 16), fmt)
}

private fun stageToUi(stage: EnumSleepingStage): String? = when (stage) {
    EnumSleepingStage.DEEP -> "deep"
    EnumSleepingStage.LIGHT -> "light"
    EnumSleepingStage.REM -> "rem"
    EnumSleepingStage.AWAKE -> "awake"
    EnumSleepingStage.NOSLEEP -> null
}

private fun normalizeScore(score: Int?): Int? {
    if (score == null || score <= 0) return null
    return score
}

private fun computeStageMinutes(night: List<SleepSample>): Map<String, Int> {
    val counts = night.groupingBy { it.stage }.eachCount()
    return STAGE_ORDER.associateWith { counts[it] ?: 0 }
}

private fun getNightScore(night: List<SleepSample>): Int? =
    normalizeScore(night.firstNotNullOfOrNull { it.sleepScore })

private fun buildOverallScores(nights: List<List<SleepSample>>): List<DailyScore> {
    return nights.mapNotNull { night ->
        if (night.isEmpty()) return@mapNotNull null
        DailyScore(
            date = night.last().dt.toLocalDate(),
            score = normalizeScore(getNightScore(night))
        )
    }
}

private data class StageSegment(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val stage: String
)

private fun compressSegments(night: List<SleepSample>): List<StageSegment> {
    if (night.isEmpty()) return emptyList()
    val segs = ArrayList<StageSegment>()
    var start = 0
    var curStage = night[0].stage

    for (i in 1 until night.size) {
        val s = night[i].stage
        if (s != curStage) {
            segs.add(StageSegment(start, i, curStage))
            start = i
            curStage = s
        }
    }
    segs.add(StageSegment(start, night.size, curStage))
    return segs
}

private fun extractAllNights(samples: List<SleepSample>, gapMinutes: Long = 10): List<List<SleepSample>> {
    if (samples.isEmpty()) return emptyList()
    val sorted = samples.sortedBy { it.dt }

    val blocks = ArrayList<MutableList<SleepSample>>()
    var current = mutableListOf(sorted.first())

    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val cur = sorted[i]
        val gap = Duration.between(prev.dt, cur.dt).toMinutes()
        if (gap > gapMinutes) {
            blocks.add(current)
            current = mutableListOf()
        }
        current.add(cur)
    }
    blocks.add(current)

    return blocks.filter { it.size >= 30 }
}

private fun formatDurationMinutes(mins: Int): String {
    if (mins < 60) return "${mins} min"
    val h = mins / 60
    val m = mins % 60
    return if (m == 0) "${h}h" else "${h}h ${m.toString().padStart(2, '0')}"
}

private suspend fun loadSleepNightsFromDbUntil(
    db: AppDatabase,
    userEmail: String,
    endIso: String,
    windowDays: Long = 30
): List<List<SleepSample>> = withContext(Dispatchers.IO) {
    val user = db.userDao().getByEmail(userEmail) ?: return@withContext emptyList()

    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val endDT = LocalDateTime.parse(endIso.substring(0, 16), fmt)
    val startIso = endDT.minusDays(windowDays).format(fmt)

    val rows = db.userDao().getUserDataInRange(user.id, startIso, endIso)

    val samples = rows.mapNotNull { r ->
        val uiStage = stageToUi(r.SleepingStage) ?: return@mapNotNull null
        val dt = runCatching { parseIsoToLocalDateTime(r.isoDate) }.getOrNull() ?: return@mapNotNull null

        SleepSample(
            dt = dt,
            stage = uiStage,
            sleepScore = r.SleepScore
        )
    }

    extractAllNights(samples, gapMinutes = 10)
}

@Preview(showBackground = true)
@Composable
fun SleepPreview() {
    Sleep(
        onNavigateBack = {},
        db = Room.inMemoryDatabaseBuilder(
            LocalContext.current,
            AppDatabase::class.java
        ).build(),
        previewMode = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sleep(
    onNavigateBack: () -> Unit,
    db: AppDatabase,
    previewMode: Boolean = false
) {
    val dark = isSystemInDarkTheme()
    val chartBg = if (dark) Color(0xFF111827) else Color(0xFFF6F8FF)

    var nights by remember { mutableStateOf<List<List<SleepSample>>>(emptyList()) }
    var nightIndex by remember { mutableStateOf(0) }
    var stageMinutes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(SleepViewMode.NIGHT) }

    val night: List<SleepSample> = nights.getOrNull(nightIndex).orEmpty()
    val nightScore: Int? = remember(night) { getNightScore(night) }
    val overallScores = remember(nights) { buildOverallScores(nights) }

    val cutoffIso = SessionManager.day ?: ""
    val userEmail = SessionManager.currentUser?.email ?: ""

    LaunchedEffect(previewMode, cutoffIso, userEmail) {
        if (previewMode) {
            val base = LocalDateTime.of(2026, 2, 26, 23, 0)
            val mockNight1 = (0 until 420).map { i ->
                val stage = when {
                    i < 15 -> "awake"
                    i < 90 -> "light"
                    i < 140 -> "deep"
                    i < 210 -> "light"
                    i < 260 -> "rem"
                    i < 330 -> "light"
                    i < 380 -> "rem"
                    else -> "awake"
                }
                SleepSample(dt = base.plusMinutes(i.toLong()), stage = stage, sleepScore = 82)
            }
            val mockNight2 = (0 until 395).map { i ->
                val stage = when {
                    i < 20 -> "awake"
                    i < 70 -> "light"
                    i < 130 -> "deep"
                    i < 210 -> "light"
                    i < 260 -> "rem"
                    i < 340 -> "light"
                    else -> "awake"
                }
                SleepSample(dt = base.plusDays(1).plusMinutes(i.toLong()), stage = stage, sleepScore = 76)
            }
            nights = listOf(mockNight1, mockNight2)
            nightIndex = 1
            stageMinutes = computeStageMinutes(mockNight2)
            error = null
            return@LaunchedEffect
        }

        if (userEmail.isBlank() || cutoffIso.length < 16) {
            nights = emptyList()
            nightIndex = 0
            stageMinutes = emptyMap()
            error = "Missing user or SessionManager.day."
            return@LaunchedEffect
        }

        runCatching {
            val allNights = loadSleepNightsFromDbUntil(
                db = db,
                userEmail = userEmail,
                endIso = cutoffIso,
                windowDays = 30
            )
            nights = allNights
            nightIndex = (allNights.size - 1).coerceAtLeast(0)
            stageMinutes = computeStageMinutes(allNights.getOrNull(nightIndex).orEmpty())
            error = null
        }.onFailure {
            error = it.message ?: "Unknown error"
            nights = emptyList()
            nightIndex = 0
            stageMinutes = emptyMap()
        }
    }

    LaunchedEffect(nightIndex, nights) {
        stageMinutes = computeStageMinutes(nights.getOrNull(nightIndex).orEmpty())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5E5)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "Failed to load sleep data: $error",
                        color = Color(0xFF8A1E1E)
                    )
                }
                return@Column
            }

            if (nights.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = "No sleep night found before cutoff (${cutoffIso.take(16)}).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }

            Card(shape = RoundedCornerShape(18.dp)) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        selected = viewMode == SleepViewMode.NIGHT,
                        onClick = { viewMode = SleepViewMode.NIGHT },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        label = { Text("Night") }
                    )

                    SegmentedButton(
                        modifier = Modifier.weight(1f),
                        selected = viewMode == SleepViewMode.OVERALL,
                        onClick = { viewMode = SleepViewMode.OVERALL },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        label = { Text("Overall") }
                    )
                }
            }

            if (viewMode == SleepViewMode.NIGHT) {
                val hasPrev = nightIndex > 0
                val hasNext = nightIndex < nights.size - 1

                val locale = Locale.ENGLISH
                val dateFmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", locale)
                val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

                val start = night.firstOrNull()?.dt
                val end = night.lastOrNull()?.dt

                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { if (hasPrev) nightIndex -= 1 }, enabled = hasPrev) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous night")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = start?.format(dateFmt) ?: "Night",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (start != null && end != null) {
                                Text(
                                    text = "${start.format(timeFmt)} → ${end.format(timeFmt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { if (hasNext) nightIndex += 1 }, enabled = hasNext) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = "Next night")
                        }
                    }
                }

                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Sleep score",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Overall score for this night",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val scoreText = nightScore?.let { "${it}/100" } ?: "N/A"
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = chartBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .padding(12.dp)
                    ) {
                        SleepHypnogramBlocks(night = night, isDarkTheme = dark)
                    }
                }

                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SleepDonutChart(stageMinutes = stageMinutes, isDarkTheme = dark)
                                val total = STAGE_ORDER.sumOf { stageMinutes[it] ?: 0 }
                                Text(
                                    text = formatDurationMinutes(total),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (dark) Color.White else Color(0xFF111827)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                STAGE_ORDER.forEach { stage ->
                                    val mins = stageMinutes[stage] ?: 0
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    color = (STAGE_COLOR[stage] ?: Color.Gray),
                                                    shape = RoundedCornerShape(3.dp)
                                                )
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = STAGE_LABEL[stage] ?: stage,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatDurationMinutes(mins),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OverallSleepScoreSection(
                    scores = overallScores,
                    isDarkTheme = dark
                )
            }
        }
    }
}

@Composable
private fun OverallSleepScoreSection(
    scores: List<DailyScore>,
    isDarkTheme: Boolean,
    maxDays: Int = 14
) {
    if (scores.isEmpty()) {
        Card(shape = RoundedCornerShape(18.dp)) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "No sleep scores available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val lastScores = scores.takeLast(maxDays)

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Overall sleep score",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "Last ${lastScores.size} nights",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SleepScoreLineChart(
                scores = lastScores,
                isDarkTheme = isDarkTheme
            )
        }
    }
}

@Composable
private fun SleepScoreLineChart(
    scores: List<DailyScore>,
    isDarkTheme: Boolean
) {
    val textMeasurer = rememberTextMeasurer()

    val textColor = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else Color(0xFF111827)
    val gridColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val axisColor = if (isDarkTheme) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)
    val lineColor = if (isDarkTheme) Color(0xFF93C5FD) else Color(0xFF2563EB)

    val textStyle = TextStyle(color = textColor, fontSize = 11.sp)

    val validPoints = scores.mapIndexedNotNull { index, item ->
        val score = item.score ?: return@mapIndexedNotNull null
        index to score
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val left = 40.dp.toPx()
        val right = 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 28.dp.toPx()

        val w = (size.width - left - right).coerceAtLeast(1f)
        val h = (size.height - top - bottom).coerceAtLeast(1f)

        fun xForIndex(i: Int): Float {
            if (scores.size <= 1) return left
            return left + (i.toFloat() / (scores.size - 1).toFloat()) * w
        }

        fun yForScore(v: Int): Float {
            val clamped = v.coerceIn(0, 100).toFloat()
            return top + h - (clamped / 100f) * h
        }

        listOf(0, 50, 100).forEach { value ->
            val y = yForScore(value)
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(left + w, y),
                strokeWidth = 1.2f
            )
            drawText(
                textMeasurer = textMeasurer,
                text = value.toString(),
                style = textStyle,
                topLeft = Offset(4.dp.toPx(), y - 7.dp.toPx())
            )
        }

        drawLine(
            color = axisColor,
            start = Offset(left, top),
            end = Offset(left, top + h),
            strokeWidth = 1.2f
        )
        drawLine(
            color = axisColor,
            start = Offset(left, top + h),
            end = Offset(left + w, top + h),
            strokeWidth = 1.2f
        )

        val dateFmt = DateTimeFormatter.ofPattern("dd/MM", Locale.ENGLISH)
        val startLabel = scores.first().date.format(dateFmt)
        val endLabel = scores.last().date.format(dateFmt)

        drawText(
            textMeasurer = textMeasurer,
            text = startLabel,
            style = textStyle,
            topLeft = Offset(left - 4.dp.toPx(), top + h + 6.dp.toPx())
        )

        val endWidth = textMeasurer.measure(endLabel, style = textStyle).size.width.toFloat()
        drawText(
            textMeasurer = textMeasurer,
            text = endLabel,
            style = textStyle,
            topLeft = Offset(left + w - endWidth, top + h + 6.dp.toPx())
        )

        if (validPoints.isEmpty()) {
            val msg = "No valid score values"
            val msgWidth = textMeasurer.measure(msg, style = textStyle).size.width.toFloat()
            drawText(
                textMeasurer = textMeasurer,
                text = msg,
                style = textStyle,
                topLeft = Offset(left + (w - msgWidth) / 2f, top + h / 2f)
            )
            return@Canvas
        }

        for (i in 0 until validPoints.size - 1) {
            val (x0Index, y0Score) = validPoints[i]
            val (x1Index, y1Score) = validPoints[i + 1]

            drawLine(
                color = lineColor,
                start = Offset(xForIndex(x0Index), yForScore(y0Score)),
                end = Offset(xForIndex(x1Index), yForScore(y1Score)),
                strokeWidth = 4f
            )
        }

        validPoints.forEach { (index, score) ->
            drawCircle(
                color = lineColor,
                radius = 5f,
                center = Offset(xForIndex(index), yForScore(score))
            )
        }
    }
}

@Composable
private fun SleepHypnogramBlocks(
    night: List<SleepSample>,
    isDarkTheme: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val locale = Locale.ENGLISH

    val textColor = if (isDarkTheme) Color.White.copy(alpha = 0.92f) else Color(0xFF111827)
    val gridColor = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val axisColor = if (isDarkTheme) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)

    val textStyle = TextStyle(color = textColor, fontSize = 12.sp)

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (night.isEmpty()) return@Canvas

        val leftMargin = 58.dp.toPx()
        val rightMargin = 12.dp.toPx()
        val topMargin = 10.dp.toPx()
        val bottomMargin = 30.dp.toPx()

        val w = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
        val h = (size.height - topMargin - bottomMargin).coerceAtLeast(1f)

        fun levelOf(stage: String): Int = when (stage) {
            "deep" -> 1
            "light" -> 2
            "rem" -> 3
            "awake" -> 4
            else -> 1
        }

        val maxLevel = 4

        fun yForLevel(level: Int): Float {
            val frac = level / maxLevel.toFloat()
            return topMargin + h - frac * h
        }

        val yBaseline = yForLevel(0)

        STAGE_ORDER.forEach { s ->
            val y = yForLevel(levelOf(s))
            drawLine(
                color = gridColor,
                start = Offset(leftMargin, y),
                end = Offset(leftMargin + w, y),
                strokeWidth = 1.2f
            )
        }

        val segs = compressSegments(night)
        val n = night.size.toFloat().coerceAtLeast(1f)

        for (seg in segs) {
            val x0 = leftMargin + (seg.startIndex / n) * w
            val x1 = leftMargin + (seg.endIndexExclusive / n) * w
            val yTop = yForLevel(levelOf(seg.stage))

            drawRect(
                color = STAGE_COLOR[seg.stage] ?: axisColor,
                topLeft = Offset(x0, yTop),
                size = Size(
                    width = (x1 - x0).coerceAtLeast(1f),
                    height = (yBaseline - yTop).coerceAtLeast(1f)
                )
            )
        }

        STAGE_ORDER.forEach { s ->
            val y = yForLevel(levelOf(s))
            drawText(
                textMeasurer = textMeasurer,
                text = STAGE_LABEL[s] ?: s,
                style = textStyle,
                topLeft = Offset(6.dp.toPx(), y - 8.dp.toPx())
            )
        }

        val start = night.first().dt
        val end = night.last().dt
        val totalMinutes = Duration.between(start, end).toMinutes().coerceAtLeast(1)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

        fun labelWidthPx(label: String): Float {
            val layout = textMeasurer.measure(label, style = textStyle.copy(fontSize = 11.sp))
            return layout.size.width.toFloat()
        }

        val startLabel = start.format(timeFmt)
        val endLabel = end.format(timeFmt)
        val startW = labelWidthPx(startLabel)
        val endW = labelWidthPx(endLabel)

        val xStart = leftMargin
        val xEnd = leftMargin + w

        drawLine(color = axisColor, start = Offset(xStart, topMargin), end = Offset(xStart, topMargin + h), strokeWidth = 1.2f)
        drawLine(color = axisColor, start = Offset(xEnd, topMargin), end = Offset(xEnd, topMargin + h), strokeWidth = 1.2f)

        drawText(
            textMeasurer = textMeasurer,
            text = startLabel,
            style = textStyle.copy(fontSize = 11.sp),
            topLeft = Offset(xStart - 2.dp.toPx(), topMargin + h + 6.dp.toPx())
        )

        drawText(
            textMeasurer = textMeasurer,
            text = endLabel,
            style = textStyle.copy(fontSize = 11.sp),
            topLeft = Offset(xEnd - endW, topMargin + h + 6.dp.toPx())
        )

        val tickEveryMin = 120L
        val minGapFromRight = endW + 10.dp.toPx()
        val minGapFromLeft = startW + 10.dp.toPx()

        var t = tickEveryMin
        while (t < totalMinutes) {
            val frac = t.toFloat() / totalMinutes.toFloat()
            val x = xStart + frac * w

            val label = start.plusMinutes(t).format(timeFmt)
            val lw = labelWidthPx(label)
            val labelLeft = x - lw / 2f
            val labelRight = x + lw / 2f

            val tooCloseLeft = labelLeft < xStart + minGapFromLeft
            val tooCloseRight = labelRight > xEnd - minGapFromRight

            if (!tooCloseLeft && !tooCloseRight) {
                drawLine(color = axisColor, start = Offset(x, topMargin), end = Offset(x, topMargin + h), strokeWidth = 1.2f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = textStyle.copy(fontSize = 11.sp),
                    topLeft = Offset(x - lw / 2f, topMargin + h + 6.dp.toPx())
                )
            }
            t += tickEveryMin
        }
    }
}

@Composable
private fun SleepDonutChart(
    stageMinutes: Map<String, Int>,
    isDarkTheme: Boolean
) {
    val total = STAGE_ORDER.sumOf { stageMinutes[it] ?: 0 }.coerceAtLeast(1)
    val dividerColor = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt)
        val pad = 10.dp.toPx()
        val diameter = min(size.width, size.height) - pad * 2f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

        drawArc(
            color = dividerColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = stroke
        )

        var startAngle = -90f
        STAGE_ORDER.forEach { stage ->
            val mins = stageMinutes[stage] ?: 0
            if (mins <= 0) return@forEach
            val sweep = 360f * (mins.toFloat() / total.toFloat())
            drawArc(
                color = STAGE_COLOR[stage] ?: Color.Gray,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = stroke
            )
            startAngle += sweep
        }
    }
}