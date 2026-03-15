package com.example.applisante

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.work.WorkerParameters
import dev.flower.flower_tflite.FlowerClient
import dev.flower.flower_tflite.FlowerServiceRunnable
import dev.flower.flower_tflite.SampleSpec
import dev.flower.flower_tflite.helpers.classifierAccuracy
import dev.flower.flower_tflite.helpers.loadMappedAssetFile
import dev.flower.flower_tflite.helpers.negativeLogLikelihoodLoss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * FL_PhysicalActivity — CoroutineWorker exécuté par WorkManager.
 *
 * Cycle complet :
 *   1. Charge les données depuis Room
 *   2. Annote (FCmax + intensité)
 *   3. Normalise (scaler_params.json)
 *   4. Injecte dans FlowerClient et participe aux rounds FL
 *   5. Télécharge model_fl_final.tflite
 *   6. Appelle FLScheduler.markTrained()
 */
class FL_PhysicalActivity(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_HOST          = "fl_host"
        const val KEY_PORT          = "fl_port"
        const val WORK_TAG          = "fl_sync"

        private const val TAG           = "FL_PhysicalActivity"
        private const val NOTIF_CHANNEL = "fl_sync_channel"
        private const val NOTIF_ID      = 1001
        private const val MODEL_ASSET   = "intensity_model_train.tflite"
        private const val FL_MODEL_FILE = "model_fl_final.tflite"
        private const val HTTP_PORT     = 8081
        private const val N_FEATURES    = 4
        private const val N_CLASSES     = 4
        private val LAYER_SIZES_BYTES   = intArrayOf(2048, 512, 32768, 256, 8192, 128, 512, 16)
        private const val GARMIN_WEIGHT = 5   // répétitions pour sur-pondérer les données Garmin
    }


    override suspend fun doWork(): Result {
        val host = inputData.getString(KEY_HOST)
            ?: return Result.failure(workDataOf("error" to "Missing host"))
        val port = inputData.getInt(KEY_PORT, 8080)

        setForeground(buildForegroundInfo("FL synchronization in progress…"))

        return try {
            withContext(Dispatchers.IO) { runFL(host, port) }
            FLScheduler.markTrained(appContext, this::class.java)
            updateNotification("FL model updated")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "FL sync échouée : ${e::class.simpleName} — ${e.message}", e)
            Log.e(TAG, "Cause : ${e.cause?.message}")
            updateNotification("FL sync failed: ${e.message}")
            Result.retry()
        }
    }

    // Pipeline FL

    private suspend fun runFL(host: String, port: Int) {
        // 1. Récupérer l'utilisateur courant
        val db   = AppDatabase.getInstance(appContext)
        val user = withContext(Dispatchers.IO) {
            db.userDao().getFirstUser()
        } ?: throw IllegalStateException("No user found in DB")


        // 2. Charger toutes les mesures
        val rows = withContext(Dispatchers.IO) { db.userDao().getUserData(user.id) }
        if (rows.isEmpty()) throw IllegalStateException("No data available")
        Log.i(TAG, "${rows.size} mesures chargées")

        // 3. Annoter (FCmax + seuil repos + intensité)
        val fcMax          = computeFcMax(user.age, user.sex)
        val reposThreshold = computeReposThreshold(rows, fcMax)
        val (mean, scale)  = loadScalerParams()

        val annotated = rows.mapNotNull { row ->
            val label    = bpmToIntensity(row.heart_rate.toFloat(), fcMax, reposThreshold)
            val features = floatArrayOf(
                (row.ZCC.toFloat()        - mean[0]) / scale[0],
                (row.Energy               - mean[1]) / scale[1],
                (row.TAT                  - mean[2]) / scale[2],
                (row.heart_rate.toFloat() - mean[3]) / scale[3],
            )
            if (features.any { it.isNaN() || it.isInfinite() }) null
            else Pair(features, label)
        }
        if (annotated.isEmpty()) throw IllegalStateException("No valid data after annotation")
        Log.i(TAG, "${annotated.size} échantillons annotés")

        // 4. Initialiser FlowerClient
        val buffer = loadMappedAssetFile(appContext, MODEL_ASSET)
        val spec   = SampleSpec<FloatArray, FloatArray>(
            { it.toTypedArray() },
            { it.toTypedArray() },
            { batchSize -> Array(batchSize) { FloatArray(N_CLASSES) } },
            ::negativeLogLikelihoodLoss,
            ::classifierAccuracy,
        )
        val flowerClient = FlowerClient(buffer, LAYER_SIZES_BYTES, spec)
        withContext(Dispatchers.IO) { flowerClient.initVariables() }

        // 5. Injecter les données (sur-pondération Garmin via répétitions)
        fun oneHot(label: Int) = FloatArray(N_CLASSES) { if (it == label) 1f else 0f }

        // Split stratifié 80/20
        val trainSet = mutableListOf<Pair<FloatArray, Int>>()
        val testSet  = mutableListOf<Pair<FloatArray, Int>>()
        for (cls in 0 until N_CLASSES) {
            val samples  = annotated.filter { it.second == cls }.shuffled()
            val splitIdx = (samples.size * 0.8).toInt().coerceAtLeast(1).coerceAtMost(samples.size)
            trainSet    += samples.take(splitIdx)
            testSet     += samples.drop(splitIdx)
        }
        for ((features, label) in trainSet) {
            repeat(GARMIN_WEIGHT) {
                flowerClient.addSample(features, oneHot(label), isTraining = true)
            }
        }
        for ((features, label) in testSet) {
            flowerClient.addSample(features, oneHot(label), isTraining = false)
        }
        Log.i(TAG, "Données injectées — train×${GARMIN_WEIGHT}: ${trainSet.size * GARMIN_WEIGHT}  test: ${testSet.size}")

        // 6. Connexion gRPC et rounds FL
        val channel = io.grpc.ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .maxInboundMessageSize(100 * 1024 * 1024)
            .build()

        val runnable = FlowerServiceRunnable(
            flowerServerChannel = channel,
            flowerClient        = flowerClient,
            callback            = { msg -> Log.d(TAG, "FL: $msg") },
        )
        runnable.finishLatch.await()
        flowerClient.close()
        Log.i(TAG, "FL rounds terminés")

        // 7. Télécharger le modèle final
        downloadModel(host)
    }

    private fun downloadModel(host: String) {
        val url     = "http://$host:$HTTP_PORT/$FL_MODEL_FILE"
        val outFile = File(appContext.filesDir, FL_MODEL_FILE)
        Log.i(TAG, "Téléchargement modèle : $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout    = 60_000
        conn.connect()
        if (conn.responseCode != HttpURLConnection.HTTP_OK)
            throw Exception("HTTP ${conn.responseCode}")
        conn.inputStream.use { input ->
            outFile.outputStream().use { output ->
                val buf = ByteArray(8192); var n: Int
                while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
            }
        }
        conn.disconnect()
        Log.i(TAG, "Modèle téléchargé : ${outFile.length() / 1024} KB → ${outFile.absolutePath}")
    }

    // Helpers annotation

    private fun computeFcMax(age: Int, sex: EnumSex): Float {
        val base = 208f - 0.7f * age
        return if (sex == EnumSex.FEMALE) base + 5f else base
    }

    private fun computeReposThreshold(rows: List<UserData>, fcMax: Float): Float {
        val nightBpm = rows
            .filter {
                it.isoDate.length >= 13 &&
                        it.isoDate.substring(11, 13).toIntOrNull() in 0..5
            }
            .map { it.heart_rate.toFloat() }
        val floor = fcMax * 0.50f
        if (nightBpm.size < 10) return floor
        val sorted = nightBpm.sorted()
        return maxOf(floor, sorted[(sorted.size * 0.10).toInt()] + 15f)
    }

    private fun bpmToIntensity(bpm: Float, fcMax: Float, reposThreshold: Float): Int {
        if (bpm <= reposThreshold) return 0
        return when {
            bpm / fcMax * 100f < 60f -> 1
            bpm / fcMax * 100f < 70f -> 2
            else                     -> 3
        }
    }

    private fun loadScalerParams(): Pair<FloatArray, FloatArray> {
        val json = appContext.assets.open("scaler_params.json").bufferedReader().readText()
        val obj  = JSONObject(json)
        fun parseArray(key: String): FloatArray {
            val arr = obj.getJSONArray(key)
            return FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }
        return Pair(parseArray("mean"), parseArray("scale"))
    }

    // Notifications

    private fun buildForegroundInfo(text: String): ForegroundInfo {
        createNotificationChannel()
        val notif = NotificationCompat.Builder(appContext, NOTIF_CHANNEL)
            .setContentTitle("Federated Learning")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIF_ID,
            notif,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun updateNotification(text: String) {
        createNotificationChannel()
        val notif = NotificationCompat.Builder(appContext, NOTIF_CHANNEL)
            .setContentTitle("Federated Learning")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setAutoCancel(true)
            .build()
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            "Federated Learning",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "FL model synchronization" }
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}