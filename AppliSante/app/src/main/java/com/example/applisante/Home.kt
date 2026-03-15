package com.example.applisante

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.room.Room
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

// Adresse du serveur FL
private const val FL_HOST = "192.168.1.34"
private const val FL_PORT = 8080

@Composable
fun Home(navController: NavController, db: AppDatabase) {
    HomeDrawer(navController, db)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDrawer(navController: NavController, db: AppDatabase) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    var user        by remember { mutableStateOf(SessionManager.currentUser) }
    val context     = LocalContext.current

    // Observer WorkManager
    // On observe les tâches FL pour afficher l'état en temps réel dans le menu déroulant
    val workInfos by remember {
        WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(FL_PhysicalActivity.WORK_TAG)
    }.let { liveData ->
        produceState<List<WorkInfo>>(initialValue = emptyList()) {
            val observer = androidx.lifecycle.Observer<List<WorkInfo>> { value = it ?: emptyList() }
            liveData.observeForever(observer)
            awaitDispose { liveData.removeObserver(observer) }
        }
    }

    // Prioritiser RUNNING > ENQUEUED > SUCCEEDED > FAILED > CANCELLED
    val latestWorkInfo = workInfos
        .filter { it.state != WorkInfo.State.CANCELLED }
        .minByOrNull { it.state.ordinal }
        ?: workInfos.maxByOrNull { it.state.ordinal }
    val isFLRunning    = latestWorkInfo?.state == WorkInfo.State.RUNNING ||
            latestWorkInfo?.state == WorkInfo.State.ENQUEUED

    val flStatusLabel = when (latestWorkInfo?.state) {
        WorkInfo.State.RUNNING   -> "Synchronization in progress…"
        WorkInfo.State.ENQUEUED  -> "Waiting for connection…"
        WorkInfo.State.SUCCEEDED -> "Model synchronized"
        WorkInfo.State.FAILED    -> "Synchronization failed"
        WorkInfo.State.CANCELLED -> "Synchronization cancelled"
        else -> {
            val ts = FLScheduler.lastTrainedTimestamp(context, FL_PhysicalActivity::class.java)
            if (ts == 0L) "Never synchronized"
            else "Last sync: ${
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
            }"
        }
    }

    //  Connexion + lancement FL automatique
    LaunchedEffect(Unit) {
        if (user == null) {
            withContext(Dispatchers.IO) {
                val devEmail = "dev@example.com"
                var devUser  = db.userDao().getByEmail(devEmail)

                if (devUser == null) {
                    val newUserId = db.userDao().insert(
                        User(
                            email        = devEmail,
                            passwordHash = PasswordUtils.hash("password"),
                            firstName    = "Dev",
                            lastName     = "User",
                            age          = 30,
                            height       = 175f,
                            weight       = 70f,
                            menstruated  = true
                        )
                    ).toInt()

                    val isoDateMax        = "2026-02-11T08:30"
                    val (csvDataList, _)  = readCsvFromAssets(context, isoDateMax, "data_final_with_cycles.csv")
                    val entities = csvDataList.map {
                        UserData(
                            userId            = newUserId,
                            isoDate           = it.isoDate,
                            heart_rate        = it.heart_rate,
                            GarminStressLevel = it.GarminStressLevel,
                            ZCC               = it.ZCC,
                            SleepingStage     = it.SleepingStage,
                            StageDuration     = it.StageDuration,
                            SleepScore        = it.SleepScore,
                            RestingHeartRate  = it.RestingHeartRate,
                            pNN50             = it.pNN50,
                            SD2               = it.SD2,
                            CycleID           = it.CycleID,
                            DayInCycle        = it.DayInCycle,
                            BasaleTemperature = it.BasaleTemperature,
                            IsOnPeriod        = it.IsOnPeriod,
                            AvgCycleLength    = it.AvgCycleLength,
                            AvgPeriodLength   = it.AvgPeriodLength,
                            TAT               = it.TAT,
                            Energy            = it.Energy,
                            Steps             = it.Steps,
                            Calories          = it.Calories,
                            ActivityScore     = it.ActivityScore,
                            RealStress        = it.RealStress,
                            DepressionScore   = it.DepressionScore,
                            AnxietyScore      = it.AnxietyScore
                        )
                    }
                    db.userDao().insertUserData(entities)
                    devUser = User(id = newUserId, email = devEmail, firstName = "Dev", lastName = "User")
                    SessionManager.day = isoDateMax
                }

                SessionManager.currentUser = UserSession(
                    devUser!!.id, devUser!!.email,
                    devUser!!.firstName, devUser!!.lastName
                )
                if (SessionManager.day.isEmpty()) SessionManager.day = "2026-02-11T08:30"
                user = SessionManager.currentUser
            }

            // Lancement FL : hebdomadaire
            FLScheduler.onLogin(context, FL_PhysicalActivity::class.java, FL_HOST, FL_PORT)
        }
    }

    val currentUser = user
    if (currentUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 300.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier            = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // ── Partie haute du drawer ─────────────────────────
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector        = Icons.Default.Person,
                                contentDescription = "User",
                                modifier           = Modifier.size(80.dp).padding(top = 16.dp),
                                tint               = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text       = "${currentUser.firstName} ${currentUser.lastName}",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                            Spacer(Modifier.height(24.dp))

                            // Section FL
                            Column(
                                modifier            = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text  = "Personalized model",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // État de la dernière synchronisation
                                Text(
                                    text  = flStatusLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        flStatusLabel.startsWith("") ->
                                            MaterialTheme.colorScheme.primary
                                        flStatusLabel.startsWith("") ->
                                            MaterialTheme.colorScheme.error
                                        else ->
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )

                                // Bouton "Synchronize models"
                                OutlinedButton(
                                    onClick  = {
                                        scope.launch {
                                            drawerState.close()
                                            FLScheduler.launchNow(context, FL_PhysicalActivity::class.java, FL_HOST, FL_PORT)
                                        }
                                    },
                                    enabled  = !isFLRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (isFLRunning) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Synchronizing…")
                                    } else {
                                        Icon(
                                            imageVector        = Icons.Default.Sync,
                                            contentDescription = null,
                                            modifier           = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Synchronize models")
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                        }

                        // Partie basse : Sign out
                        Button(
                            onClick  = {
                                FLScheduler.cancelAll(context, FL_PhysicalActivity::class.java)
                                SessionManager.currentUser = null
                                navController.navigate("Login") {
                                    popUpTo("Home") { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            Text("Sign out")
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title          = { Text("Health", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Person, contentDescription = "Menu")
                            }
                        },
                        actions        = {
                            IconButton(onClick = { navController.navigate("Dev") }) {
                                Icon(Icons.Default.Build, contentDescription = "DevMenu")
                            }
                        }
                    )
                }
            ) { padding ->
                HomeContent(
                    modifier              = Modifier.padding(padding),
                    onHeartRateClick      = { navController.navigate("HeartRate") },
                    onActivityClick       = { navController.navigate("PhysicalActivity") },
                    onSleepClick          = { navController.navigate("Sleep") },
                    onMentalHealthClick   = { navController.navigate("MentalHealth") },
                    onMenstrualCycleClick = { navController.navigate("MenstrualCycle") },
                    db                    = db,
                    user                  = currentUser
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    val context = LocalContext.current
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    HomeContent(
        modifier              = Modifier,
        onHeartRateClick      = {},
        onActivityClick       = {},
        onSleepClick          = {},
        onMentalHealthClick   = {},
        onMenstrualCycleClick = {},
        db                    = db,
        user                  = UserSession(0, "test@test", "testfirstname", "testlastname")
    )
}

@Composable
fun HomeContent(
    modifier              : Modifier = Modifier,
    onHeartRateClick      : () -> Unit,
    onActivityClick       : () -> Unit,
    onSleepClick          : () -> Unit,
    onMentalHealthClick   : () -> Unit,
    onMenstrualCycleClick : () -> Unit,
    db                    : AppDatabase,
    user                  : UserSession
) {
    var BPM         by remember { mutableStateOf("") }
    var STRESS      by remember { mutableStateOf("") }
    var SLEEP_SCORE by remember { mutableStateOf("") }
    var ACTIVE_TIME by remember { mutableStateOf("") }
    var MENSTRUAL_SCORE by remember { mutableStateOf("") }
    var isFertilePhase  by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    // Observe FL worker — rafraîchit ACTIVE_TIME dès que la synchro se termine
    val workInfos by remember {
        WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData(FL_PhysicalActivity.WORK_TAG)
    }.let { liveData ->
        produceState<List<WorkInfo>>(initialValue = emptyList()) {
            val observer = androidx.lifecycle.Observer<List<WorkInfo>> { value = it ?: emptyList() }
            liveData.observeForever(observer)
            awaitDispose { liveData.removeObserver(observer) }
        }
    }
    val flJustSucceeded = workInfos.any { it.state == WorkInfo.State.SUCCEEDED }

    LaunchedEffect(user.email, SessionManager.day) {
        withContext(Dispatchers.IO) {
            val userFromDb = db.userDao().getByEmail(user.email)
            if (userFromDb != null) {
                val userData = db.userDao().getUserDataByDate(userFromDb.id, SessionManager.day ?: "")
                BPM = userData?.heart_rate?.let { "$it bpm" } ?: "N/A"
                var pnn50 = userData?.pNN50 ?: 0f
                if (pnn50 == -1f) pnn50 = 100f
                STRESS      = "${100f - pnn50} %"
                SLEEP_SCORE = userData?.SleepScore?.let { "$it / 100" } ?: "N/A"
            } else {
                BPM         = "N/A"
                STRESS      = "N/A"
                SLEEP_SCORE = "N/A"
            }
        }
    }

    // Temps d'activité modérée/intense sur les 24 dernières heures
    LaunchedEffect(user.email, SessionManager.day, flJustSucceeded) {
        withContext(Dispatchers.IO) {
            val day = SessionManager.day ?: ""
            if (day.length < 16) return@withContext
            val userFromDb = db.userDao().getByEmail(user.email) ?: return@withContext
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                val endDT     = LocalDateTime.parse(day.substring(0, 16), formatter)
                val startStr  = endDT.minusHours(24).format(formatter)

                val rows          = db.userDao().getUserDataInRange(userFromDb.id, startStr, day)
                if (rows.isEmpty()) { ACTIVE_TIME = "0min"; return@withContext }

                val classifier    = ActivityClassifier(context)
                val activeMinutes = rows.count { classifier.predict(it).intensity >= 2 }
                classifier.close()

                ACTIVE_TIME = if (activeMinutes >= 60)
                    "${activeMinutes / 60}h ${activeMinutes % 60}min"
                else
                    "${activeMinutes}min"
            } catch (e: Exception) {
                ACTIVE_TIME = ""
            }
        }
    }

    LaunchedEffect(user.email, SessionManager.day) {
        withContext(Dispatchers.IO) {
            val userFromDb = db.userDao().getByEmail(user.email) ?: return@withContext
            val allData = db.userDao().getUserData(userFromDb.id)
            val latestData = db.userDao().getLatestUserData(user.id)
            val currentDbDay = latestData?.isoDate?.substring(0, 10)

            if (allData.isNotEmpty()) {

                if (SharedMenstrualData.prediction == null || SharedMenstrualData.lastPredictionDate != currentDbDay) {
                    SharedMenstrualData.prediction = MenstrualPredictionManager.getPredictions(context, allData)
                }

                val pred = SharedMenstrualData.prediction
                val latest = allData.maxByOrNull { it.isoDate }

                if (pred != null && latest != null) {
                    val daysLeft = pred.cycleLength - latest.DayInCycle + 1
                    MENSTRUAL_SCORE = "${daysLeft} days left"

                    val ovDay = pred.ovulationDay
                    isFertilePhase = latest.DayInCycle in (ovDay - 4)..(ovDay + 1)
                }
            } else {
                MENSTRUAL_SCORE = "N/A"
            }
        }
    }

    Column(
        modifier            = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Functionality("Heart Rate", Icons.Default.Favorite, Modifier.weight(1f),
                onClick = onHeartRateClick, score = BPM)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Functionality("Physical Activity", Icons.AutoMirrored.Filled.DirectionsRun, Modifier.weight(1f),
                onClick = onActivityClick, score = ACTIVE_TIME)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Functionality("Sleep", Icons.Default.Bedtime, Modifier.weight(1f),
                onClick = onSleepClick, score = SLEEP_SCORE)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Functionality("Mental Health", Icons.Default.Psychology, Modifier.weight(1f),
                onClick = onMentalHealthClick, score = STRESS)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            val fertileColor = if(isDarkTheme) Color(0xFF81D4FA) else Color(0xFF005675)
            Functionality("Menstrual Cycle", Icons.Default.AccessTime, Modifier.fillMaxWidth(),
                onClick = onMenstrualCycleClick,
                score = MENSTRUAL_SCORE,
                customScoreColor = if (isFertilePhase) fertileColor else null
            )
        }
    }
}

@Composable
fun Functionality(
    title    : String,
    icon     : ImageVector,
    modifier : Modifier,
    onClick  : (() -> Unit)? = null,
    score    : String = "",
    customScoreColor: Color? = null
) {
    Card(
        modifier  = modifier.height(120.dp).clickable { onClick?.invoke() },
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (score.isNotEmpty() && score != "N/A") {
                Text(
                    text       = score,
                    style      = MaterialTheme.typography.titleLarge,
                    color      = customScoreColor ?: MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
