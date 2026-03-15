package com.example.applisante

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * FLScheduler — gère le déclenchement du FL pour n'importe quel worker :
 *   1. Hebdomadaire        → PeriodicWorkRequest toutes les 7 jours
 *   2. Manuel              → bouton "Synchronize models" dans le drawer
 *
 * Chaque worker dispose de ses propres clés SharedPreferences et noms de tâche,
 * dérivés du nom de la classe — pas de collision entre workers distincts.
 */
object FLScheduler {

    private const val TAG           = "FLScheduler"
    private const val INTERVAL_DAYS = 7L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("fl_scheduler_prefs", Context.MODE_PRIVATE)

    private fun keyLastTrained(workerClass: Class<out ListenableWorker>) =
        "last_trained_${workerClass.simpleName}"

    private fun keyEverTrained(workerClass: Class<out ListenableWorker>) =
        "ever_trained_${workerClass.simpleName}"

    private fun workNameOneTime(workerClass: Class<out ListenableWorker>) =
        "fl_onetime_${workerClass.simpleName}"

    private fun workNameWeekly(workerClass: Class<out ListenableWorker>) =
        "fl_weekly_${workerClass.simpleName}"

    private fun workTagWeekly(workerClass: Class<out ListenableWorker>) =
        "fl_weekly_tag_${workerClass.simpleName}"

    // API publique

    /**
     * À appeler juste après la connexion.
     * Lance immédiatement si jamais entraîné, planifie ensuite
     * (ou replanifie) le rappel hebdomadaire.
     */
    fun onLogin(
        context: Context,
        workerClass: Class<out ListenableWorker>,
        flHost: String,
        flPort: Int,
    ) {
        val everTrained = prefs(context).getBoolean(keyEverTrained(workerClass), false)
        if (!everTrained) {
            Log.i(TAG, "${workerClass.simpleName} — première connexion → lancement FL immédiat")
            launchNow(context, workerClass, flHost, flPort)
        }
        scheduleWeekly(context, workerClass, flHost, flPort)
    }

    /** Lancement immédiat. Utilisé pour le manuel et la première connexion. */
    fun launchNow(
        context: Context,
        workerClass: Class<out ListenableWorker>,
        flHost: String,
        flPort: Int,
    ) {
        val data = workDataOf(
            FL_PhysicalActivity.KEY_HOST to flHost,
            FL_PhysicalActivity.KEY_PORT to flPort,
        )
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(FL_PhysicalActivity.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workNameOneTime(workerClass),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "${workerClass.simpleName} — one-time enqueued (REPLACE)")
    }

    /** Planifie (ou maintient) le rappel hebdomadaire. */
    fun scheduleWeekly(
        context: Context,
        workerClass: Class<out ListenableWorker>,
        flHost: String,
        flPort: Int,
    ) {
        val data = workDataOf(
            FL_PhysicalActivity.KEY_HOST to flHost,
            FL_PhysicalActivity.KEY_PORT to flPort,
        )
        val request = PeriodicWorkRequest.Builder(workerClass, INTERVAL_DAYS, TimeUnit.DAYS)
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(workTagWeekly(workerClass))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workNameWeekly(workerClass),
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        Log.i(TAG, "${workerClass.simpleName} — weekly scheduled")
    }

    /** Annule tout (utile à la déconnexion). */
    fun cancelAll(
        context: Context,
        workerClass: Class<out ListenableWorker>,
    ) {
        WorkManager.getInstance(context).cancelUniqueWork(workNameOneTime(workerClass))
        WorkManager.getInstance(context).cancelUniqueWork(workNameWeekly(workerClass))
        Log.i(TAG, "${workerClass.simpleName} — FL tasks cancelled")
    }

    //  Persistance

    fun markTrained(context: Context, workerClass: Class<out ListenableWorker>) {
        prefs(context).edit()
            .putBoolean(keyEverTrained(workerClass), true)
            .putLong(keyLastTrained(workerClass), System.currentTimeMillis())
            .apply()
        Log.i(TAG, "${workerClass.simpleName} — markTrained enregistré")
    }

    fun lastTrainedTimestamp(context: Context, workerClass: Class<out ListenableWorker>): Long =
        prefs(context).getLong(keyLastTrained(workerClass), 0L)

    fun hasEverTrained(context: Context, workerClass: Class<out ListenableWorker>): Boolean =
        prefs(context).getBoolean(keyEverTrained(workerClass), false)
}