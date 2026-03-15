package com.example.applisante

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import androidx.room.Room
import com.example.applisante.ui.theme.ApplisanteTheme
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "app_db"
        ).fallbackToDestructiveMigration(true).build()

        enableEdgeToEdge()

        copyAssetToInternalStorage(this, "LSTM_model.tflite")

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            ApplisanteTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "Login"
                ) {
                    composable("Login") {
                        Login(navController, db) }

                    composable("Register") {
                        Register(navController, db) }

                    composable("Home") {
                        Home(navController, db)
                    }
                    composable("PhysicalActivity") {
                        PhysicalActivity(
                            onNavigateBack = { navController.popBackStack() },
                            db
                        )
                    }
                    composable("HeartRate") {
                        HeartRate(onNavigateBack = { navController.popBackStack() },navController, db)
                    }
                    composable("Sleep") {
                        Sleep(onNavigateBack = { navController.popBackStack() }, db)
                    }
                    composable("MentalHealth") {
                        MentalHealth(onNavigateBack = { navController.popBackStack() }, navController, db)
                    }
                    composable("MentalHealthPHQ9") {
                        MentalHealthPHQ9(
                            onNavigateBack = { navController.popBackStack() },
                            onSubmitClicked = { score, requiresImmediateHelp ->
                                navController.navigate("MentalHealthPHQ9Result/$score/$requiresImmediateHelp")
                            },
                            db
                        )
                    }
                    composable(
                        route = "MentalHealthPHQ9Result/{score}/{requiresImmediateHelp}",
                        arguments = listOf(
                            navArgument("score") { type = NavType.IntType },
                            navArgument("requiresImmediateHelp") { type = NavType.BoolType }
                        )
                    ) { backStackEntry ->
                        val score = backStackEntry.arguments?.getInt("score") ?: 0
                        val requiresHelp = backStackEntry.arguments?.getBoolean("requiresImmediateHelp") ?: false
                        MentalHealthPHQ9Result(
                            onNavigateHome = {
                                navController.popBackStack("MentalHealth", inclusive = false)
                            },
                            score = score,
                            requiresImmediateHelp = requiresHelp
                        )
                    }
                    composable("MentalHealthGAD7") {
                        MentalHealthGAD7(
                            onNavigateBack = { navController.popBackStack() },
                            onSubmitClicked = { score ->
                                navController.navigate("MentalHealthGAD7Result/$score")
                            },
                            db
                        )
                    }
                    composable(
                        route = "MentalHealthGAD7Result/{score}",
                        arguments = listOf(
                            navArgument("score") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val score = backStackEntry.arguments?.getInt("score") ?: 0
                        MentalHealthGAD7Result(
                            onNavigateHome = {
                                navController.popBackStack("MentalHealth", inclusive = false)
                            },
                            score = score
                        )
                    }
                    composable("MenstrualCycle") {
                        MenstrualCycle(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToCalendar = { navController.navigate("MenstrualCycleCalendar") },
                            onNavigateToHistory = { navController.navigate("MenstrualCycleHistory") },
                            db = db
                        )
                    }
                    composable("MenstrualCycleCalendar") {
                        MenstrualCycleCalendar(
                            onNavigateBack = { navController.popBackStack() },
                            db = db
                        )
                    }
                    composable("MenstrualCycleHistory") {
                        MenstrualCycleHistory(
                            onNavigateBack = { navController.popBackStack() },
                            db = db
                        )
                    }
                    composable("Dev"){
                        Dev(onNavigateBack = { navController.popBackStack() },navController, db)
                    }
                }
            }
        }
    }
}