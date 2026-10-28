package com.dreammap.attentionshieldlogger

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CsvExporter {

    suspend fun exportToCsv(context: Context): Intent = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val sessions = db.sessionDao().getAll()

        val header = listOf(
            "user_id",
            "session_start",
            "session_end",
            "date",
            "day_of_week",
            "session_hour",
            "time_of_day",
            "app_label",
            "detected_format",
            "duration_minutes",
            "notification_count",
            "triggered_by_notification",
            "minutes_since_last_session",
            "day_asi_score",
            "day_intensity_score",
            "day_fragmentation_score",
            "day_compulsion_score",
            "day_disruption_score"
        ).joinToString(",")

        // ── ASI per day ──────────────────────────────────────────────
        val asiByDate = sessions
            .groupBy { it.date }
            .mapValues { (_, daySessions) ->
                ASICalculator.calculate(daySessions)
            }

        // ── minutes_since_last_session per app ───────────────────────
        val minutesSinceLastMap = mutableMapOf<Int, Float>()
        sessions
            .groupBy { it.packageName }
            .forEach { (_, appSessions) ->
                val sorted = appSessions.sortedBy { it.sessionStart }
                var prevEnd: Long? = null
                sorted.forEach { session ->
                    minutesSinceLastMap[session.id] = if (prevEnd != null) {
                        ((session.sessionStart - prevEnd!!) / 60000f).coerceAtLeast(0f)
                    } else -1f
                    prevEnd = session.sessionEnd
                }
            }

        // ── Rows ─────────────────────────────────────────────────────
        val rows = sessions.map { session ->
            val asi = asiByDate[session.date] ?: ASICalculator.calculate(listOf(session))
            val minutesSince = minutesSinceLastMap[session.id] ?: -1f

            listOf(
                session.userId,
                session.sessionStart,
                session.sessionEnd,
                session.date,
                session.dayOfWeek,
                session.sessionHour,
                session.timeOfDay,
                session.appLabel,
                session.detectedFormat,
                "%.2f".format(session.durationMinutes),
                session.notificationCount,
                session.triggeredByNotification,
                if (minutesSince < 0) "NA" else "%.2f".format(minutesSince),
                asi.score,
                asi.intensityScore,
                asi.fragmentationScore,
                asi.compulsionScore,
                asi.disruptionScore
            ).joinToString(",") { escapeCsv(it) }
        }

        val csv = (listOf(header) + rows).joinToString("\n")

        val file = File(context.cacheDir, "SocialMediaUse_export.csv")
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Social Media Use Data Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun escapeCsv(value: Any?): String {
        val str = value?.toString() ?: ""
        return if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }
}
