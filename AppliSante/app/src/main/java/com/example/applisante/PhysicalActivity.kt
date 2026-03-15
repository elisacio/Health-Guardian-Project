package com.example.applisante

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.json.JSONObject

data class ActivityPrediction(
    val isoDate:    String,
    val hourMinute: Float,
    val label:      String,
    val intensity:  Int,
    val probs:      FloatArray,
    val steps:      Int,
    val calories:   Float,
)

//    Classifieur TFLite

class ActivityClassifier(context: Context) {

    companion object {
        val LABELS = listOf("rest", "light", "moderate", "intense")
        val COLORS = listOf(
            Color(0xFF4CAF50), Color(0xFF2196F3),
            Color(0xFFFF9800), Color(0xFFF44336),
        )
        const val ASSET_MODEL_FILE_PUBLIC = "model_fl_final.tflite"
        private const val ASSET_MODEL_FILE = ASSET_MODEL_FILE_PUBLIC
        private const val FL_MODEL_FILE = "model_fl_final.tflite"
    }

    private val interpreter: Interpreter
    private val scalerMean:  FloatArray
    private val scalerScale: FloatArray

    init {
        val options = Interpreter.Options().apply { useNNAPI = false }

        // Charger le modèle FL téléchargé s'il existe, sinon le modèle de base
        val flFile = java.io.File(context.filesDir, FL_MODEL_FILE)
        val buffer = if (flFile.exists()) {
            android.util.Log.i("ActivityClassifier", "Modele FL charge : ${flFile.path}")
            java.io.FileInputStream(flFile).channel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, flFile.length()
            )
        } else {
            android.util.Log.i("ActivityClassifier", "Modele de base charge depuis assets")
            loadModelBuffer(context, ASSET_MODEL_FILE)
        }

        // Pas de ResourceVariable — modèle frozen, allocation directe
        interpreter = Interpreter(buffer, options)

        val json = org.json.JSONObject(
            context.assets.open("scaler_params.json").bufferedReader().readText()
        )
        scalerMean  = FloatArray(4) { json.getJSONArray("mean").getDouble(it).toFloat() }
        scalerScale = FloatArray(4) { json.getJSONArray("scale").getDouble(it).toFloat() }
    }

    fun predict(row: UserData): ActivityPrediction {
        val input  = Array(1) { floatArrayOf(
            (row.ZCC.toFloat()        - scalerMean[0]) / scalerScale[0],
            (row.Energy               - scalerMean[1]) / scalerScale[1],
            (row.TAT                  - scalerMean[2]) / scalerScale[2],
            (row.heart_rate.toFloat() - scalerMean[3]) / scalerScale[3],
        )}
        val output = Array(1) { FloatArray(4) }
        interpreter.runSignature(mapOf("x" to input), mapOf("logits" to output), "infer")
        val probs = output[0]
        val idx   = probs.indices.maxByOrNull { probs[it] } ?: 0
        return ActivityPrediction(
            isoDate = row.isoDate, hourMinute = parseHourMinute(row.isoDate),
            label = LABELS[idx], intensity = idx, probs = probs,
            steps = row.Steps, calories = row.Calories,
        )
    }

    fun close() = interpreter.close()

    private fun loadModelBuffer(context: Context, filename: String): MappedByteBuffer {
        val fd  = context.assets.openFd(filename)
        val fis = FileInputStream(fd.fileDescriptor)
        return fis.channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        ).also { fis.close(); fd.close() }
    }

    private fun parseHourMinute(isoDate: String): Float = try {
        val t = isoDate.substringAfter("T")
        t.substringBefore(":").toFloat() + t.substringAfter(":").take(2).toFloat() / 60f
    } catch (e: Exception) { 0f }
}



@Composable
fun ActivityTimeline(
    predictions: List<ActivityPrediction>,
    endDateTime: LocalDateTime?,
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle    = TextStyle(color = Color.Gray, fontSize = 12.sp)

    if (predictions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data", color = Color.Gray)
        }
        return
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val leftMargin   = 8.dp.toPx()
        val rightMargin  = 8.dp.toPx()
        val bottomMargin = 40.dp.toPx()
        val topMargin    = 8.dp.toPx()
        val barTop       = topMargin
        val barHeight    = size.height - bottomMargin - topMargin
        val chartWidth   = size.width - leftMargin - rightMargin

        val segWidth = chartWidth / predictions.size.coerceAtLeast(1)
        predictions.forEachIndexed { i, pred ->
            drawRect(
                color   = ActivityClassifier.COLORS[pred.intensity],
                topLeft = Offset(leftMargin + i * segWidth, barTop),
                size    = Size(segWidth + 1f, barHeight),
            )
        }

        if (endDateTime != null) {
            val startDT      = endDateTime.minusHours(24)
            val totalMinutes = 24 * 60f
            val formatter    = DateTimeFormatter.ofPattern("HH:mm")

            for (h in 0..24 step 3) {
                val tickTime = startDT.plusHours(h.toLong())
                val xPos     = leftMargin + (h * 60f / totalMinutes) * chartWidth

                drawLine(
                    color       = Color.White.copy(alpha = 0.7f),
                    start       = Offset(xPos, barTop),
                    end         = Offset(xPos, barTop + barHeight),
                    strokeWidth = 1.5f,
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text         = tickTime.format(formatter),
                    style        = textStyle,
                    topLeft      = Offset(xPos - 14.dp.toPx(), barTop + barHeight + 4.dp.toPx()),
                )
            }
        }
    }
}

//    Légende

@Composable
fun ActivityLegend() {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        ActivityClassifier.LABELS.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ActivityClassifier.COLORS[i], RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 11.sp)
            }
        }
    }
}

//    Cartes de répartition                                                      

@Composable
fun ActivitySummaryCards(predictions: List<ActivityPrediction>) {
    val total  = predictions.size.coerceAtLeast(1)
    val counts = IntArray(4).also { c -> predictions.forEach { c[it.intensity]++ } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Breakdown over 24h",
            fontWeight = FontWeight.SemiBold,
            fontSize   = 15.sp,
            modifier   = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityClassifier.LABELS.forEachIndexed { i, label ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = ActivityClassifier.COLORS[i].copy(alpha = 0.15f)
                    ),
                ) {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${"%.0f".format(counts[i] * 100f / total)}%",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = ActivityClassifier.COLORS[i],
                        )
                        Text(label, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun slotKey(isoDate: String): String {
    val min  = isoDate.substring(14, 16).toIntOrNull() ?: 0
    val slot = (min / 15) * 15
    return isoDate.substring(0, 14) + "%02d".format(slot)
}

@Composable
fun StepsSection(predictions: List<ActivityPrediction>) {
    val totalSteps = predictions.distinctBy { slotKey(it.isoDate) }.sumOf { it.steps }
    StatRow("Steps (24h)", "%,d".format(totalSteps), Color(0xFF9C27B0))
}

@Composable
fun CaloriesSection(predictions: List<ActivityPrediction>) {
    val totalCalories = predictions.distinctBy { slotKey(it.isoDate) }.sumOf { it.calories.toDouble() }
    StatRow("Active calories (24h)", "%.0f kcal".format(totalCalories), Color(0xFFE91E63))
}

@Composable
fun ActiveTimeSection(predictions: List<ActivityPrediction>) {
    val activeMinutes = predictions.count { it.intensity >= 2 }
    val display = if (activeMinutes >= 60)
        "${activeMinutes / 60}h ${activeMinutes % 60}min"
    else
        "${activeMinutes}min"
    StatRow("Moderate/intense time (24h)", display, Color(0xFFFF9800))
}

@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

//    Indicateur modèle FL                                                       

@Composable
private fun FlModelBadge(isFlModel: Boolean) {
    val (text, bgColor) = if (isFlModel)
        Pair("Federated model", Color(0xFF6200EE).copy(alpha = 0.12f))
    else
        Pair("Base model", Color.Gray.copy(alpha = 0.10f))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Text(
            text     = text,
            fontSize = 12.sp,
            color    = if (isFlModel) Color(0xFF6200EE) else Color.Gray,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

//    Écran principal                                                            

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalActivity(
    onNavigateBack: () -> Unit,
    db: AppDatabase,
) {
    val context = LocalContext.current

    var predictions  by remember { mutableStateOf<List<ActivityPrediction>>(emptyList()) }
    var endDateTime  by remember { mutableStateOf<LocalDateTime?>(null) }
    var isLoading    by remember { mutableStateOf(true) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }
    var isFlModel    by remember { mutableStateOf(false) }
    var modelMissing by remember { mutableStateOf(false) }

    LaunchedEffect(SessionManager.currentUser, SessionManager.day) {
        val currentUser = SessionManager.currentUser
        val day         = SessionManager.day
        if (currentUser == null || day.isNullOrEmpty()) {
            isLoading = false
            errorMsg  = "User or date not available."
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                val endDT = try {
                    LocalDateTime.parse(day.substring(0, 16), formatter)
                } catch (e: DateTimeParseException) {
                    LocalDateTime.parse("${day}T00:00", formatter)
                }
                endDateTime = endDT

                val startDateStr = endDT.minusHours(24).format(formatter)
                val user = db.userDao().getByEmail(currentUser.email)
                    ?: run { errorMsg = "User not found."; return@withContext }

                val userDataList = db.userDao().getUserDataInRange(user.id, startDateStr, day)
                if (userDataList.isEmpty()) {
                    errorMsg = "No data available for the past 24 hours."
                    return@withContext
                }

                isFlModel = java.io.File(context.filesDir, "fl_weights.json").exists()

                val classifier = ActivityClassifier(context)
                predictions    = userDataList.map { classifier.predict(it) }
                classifier.close()

            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("intensity_model_inference.tflite") ||
                    msg.contains("intensity_model_train.tflite") ||
                    msg.contains(ActivityClassifier.ASSET_MODEL_FILE_PUBLIC)) {
                    modelMissing = true
                } else {
                    errorMsg = "Error: $msg"
                }
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physical Activity") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Analyzing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            modelMissing -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(32.dp),
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Model not yet available.",
                        style    = MaterialTheme.typography.titleSmall,
                        color    = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Please wait for the first synchronization to complete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            errorMsg != null -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp))
            }

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Activity over the past 24 hours",
                    style = MaterialTheme.typography.titleMedium)

                FlModelBadge(isFlModel)

                Text("${predictions.size} measurements analyzed",
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.shapes.medium,
                        )
                        .padding(8.dp),
                ) {
                    ActivityTimeline(predictions, endDateTime)
                }

                ActivityLegend()
                HorizontalDivider()
                ActivitySummaryCards(predictions)
                HorizontalDivider()

                Text("Daily stats",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp)
                StepsSection(predictions)
                CaloriesSection(predictions)
                ActiveTimeSection(predictions)
            }
        }
    }
}

//    Preview                                                                    

@Preview(showBackground = true)
@Composable
fun PhysicalActivityPreview() {
    val context = LocalContext.current
    SessionManager.currentUser = UserSession(1, "preview@example.com", "Preview", "User")
    SessionManager.day         = "2026-02-07T09:46"
    val db = remember {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    PhysicalActivity(onNavigateBack = {}, db = db)
}

private class FakeNonEmptyMap : HashMap<String, Any>() {
    override fun isEmpty() = false
}