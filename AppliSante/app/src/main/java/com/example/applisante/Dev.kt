package com.example.applisante

import android.widget.LinearLayout
import android.widget.Space
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.constraintlayout.widget.ConstraintSet
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Dev(onNavigateBack: () -> Unit, navController: NavController, db : AppDatabase) {
    DevDrawer(onNavigateBack, navController, db)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevDrawer(onNavigateBack: () -> Unit, navController: NavController, db : AppDatabase) {
    val user = SessionManager.currentUser
    val scope = rememberCoroutineScope()

    var db_user by remember { mutableStateOf(User())}
    LaunchedEffect(user?.email, SessionManager.day) {
        withContext(Dispatchers.IO) {
            db_user = db.userDao().getByEmail(user!!.email)!!
        }
    }
    val context = LocalContext.current

    // Contenu principal
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Debug area", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Day: ${SessionManager.day}", 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        DevContent(
            modifier = Modifier.padding(padding),
            on1WeekClick = { scope.launch { forward_week(context = context, user = db_user, isoDateBase = SessionManager.day, db = db)}},
            on24HoursClick = { scope.launch { forward_day(context = context, user = db_user, isoDateBase = SessionManager.day, db = db) } },
            on1HourClick = { scope.launch { forward_hour(context = context, user = db_user, isoDateBase = SessionManager.day, db = db) } },
            on1minuteClick = { scope.launch { forward_minute(context = context, user = db_user, isoDateBase = SessionManager.day, db = db) } },
            db = db,
            user = user!!
        )
    }
}


@Preview(showBackground = true)
@Composable
fun DevPreview() {
    val context = LocalContext.current
    val db = Room.inMemoryDatabaseBuilder(
        context,
        AppDatabase::class.java
    ).build()

    DevContent(
        modifier = Modifier,
        on1WeekClick = {},
        on24HoursClick = {},
        on1HourClick = {},
        on1minuteClick = {},
        db = db,
        user = UserSession(0, "test@test", "testfirstname", "testlastname")
    )
}

@Composable
fun DevContent(
    modifier: Modifier = Modifier,
    on1WeekClick: () -> Unit,
    on24HoursClick: () -> Unit,
    on1HourClick: () -> Unit,
    on1minuteClick: () -> Unit,
    db : AppDatabase,
    user : UserSession
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(29.dp)
            ){
                Functionality(
                    "1 week forward",
                    Icons.Default.DoubleArrow,
                    Modifier.fillMaxWidth(),
                    onClick = on1WeekClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(29.dp)
            ){
                Functionality(
                    "24h forward",
                    Icons.Default.DoubleArrow,
                    Modifier.fillMaxWidth(),
                    onClick = on24HoursClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(29.dp)
            ){
                Functionality(
                    "1h forward",
                    Icons.Default.DoubleArrow,
                    Modifier.fillMaxWidth(),
                    onClick = on1HourClick
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(29.dp)
            ){
                Functionality(
                    "1m forward",
                    Icons.Default.DoubleArrow,
                    Modifier.fillMaxWidth(),
                    onClick = on1minuteClick
                )
            }


        }
    }