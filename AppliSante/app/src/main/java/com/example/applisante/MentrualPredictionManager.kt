package com.example.applisante

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// La structure de données
data class CyclePrediction(
    val cycleLength: Int,
    val ovulationDay: Int,
    val status: String,
    val fertilityProb: String
)

object MenstrualPredictionManager {
    suspend fun getPredictions(context: Context, userData: List<UserData>): CyclePrediction? = withContext(Dispatchers.IO) {
        try {

            val jsonString = Gson().toJson(userData)

            val filesDir = context.filesDir.absolutePath

            val py = Python.getInstance()
            val module = py.getModule("predictionMenstrualCycles")

            val resultJsonString = module.callAttr(
                "predict_from_kotlin",
                filesDir,
                jsonString
            ).toString()

            val jsonObj = JSONObject(resultJsonString)

            return@withContext CyclePrediction(
                cycleLength = jsonObj.getInt("predicted_cycle_length"),
                ovulationDay = jsonObj.getInt("predicted_ovulation"),
                status = jsonObj.getString("status"),
                fertilityProb = jsonObj.getString("fertility_prob")
            )

        } catch (e: Exception) {
            Log.e("PredictionManager", "Erreur lors de la prédiction IA", e)
            return@withContext null
        }
    }
}