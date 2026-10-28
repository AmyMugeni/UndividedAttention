package com.dreammap.attentionshieldlogger

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class LoggerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            UsageLogger(applicationContext).logRecentSessions()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

fun scheduleLogging(context: Context) {
    // Immediate one-time sync so data appears right away
    val immediateRequest = OneTimeWorkRequestBuilder<LoggerWorker>().build()
    WorkManager.getInstance(context).enqueue(immediateRequest)

    // Regular periodic job every 6 hours
    val request = PeriodicWorkRequestBuilder<LoggerWorker>(6, TimeUnit.HOURS)
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "usage_logging",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}