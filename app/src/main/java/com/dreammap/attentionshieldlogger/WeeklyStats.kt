package com.dreammap.attentionshieldlogger

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class DayStats(
    val date: String,
    val dayLabel: String,
    val totalMinutes: Float,
    val sessionCount: Int,
    val avgSessionMinutes: Float,
    val passiveSessions: Int,
    val autoplaySessions: Int,
    val avgScrollSpeed: Float,
    val lateNightSessions: Int,
    val wellnessScore: Int
)

data class WeeklyStats(
    val days: List<DayStats>,
    val totalScreenTimePerApp: Map<String, Float>,
    val weeklyEstimatedVideos: Map<String, Int>,
    val sessionsThisWeek: List<SessionEvent>,
    val bestDay: DayStats?,
    val worstDay: DayStats?
)

fun computeWeeklyStats(sessions: List<SessionEvent>): WeeklyStats {
    Log.d("WeeklyStats", "Starting computeWeeklyStats with ${sessions.size} total sessions")

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    // --- 1. Compute start of week (Monday) safely ---
    val now = Calendar.getInstance()

    val startOfWeek = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = now.timeInMillis

        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
        add(Calendar.DAY_OF_YEAR, diff)

        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val endOfWeek = (startOfWeek.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 7)
    }

    Log.d("WeeklyStats", "Filtering sessions from ${dateFormat.format(startOfWeek.time)} to ${dateFormat.format(endOfWeek.time)}")

    // --- 2. Filter sessions to THIS WEEK ONLY ---
    val sessionsThisWeek = sessions.filter { session ->
        val parsed = try {
            dateFormat.parse(session.date)
        } catch (e: Exception) {
            null
        } ?: return@filter false

        val time = parsed.time
        time >= startOfWeek.timeInMillis && time < endOfWeek.timeInMillis
    }

    Log.d("WeeklyStats", "Filtered to ${sessionsThisWeek.size} sessions for this week")

    // --- 3. Generate week days (Mon → Sun) ---
    val weekDays = (0..6).map { offset ->
        val cal = startOfWeek.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, offset)

        val dateStr = dateFormat.format(cal.time)
        val label = dayFormat.format(cal.time)

        dateStr to label
    }

    // --- 4. Group filtered sessions ---
    val sessionsByDate = sessionsThisWeek.groupBy { it.date }

    // --- 5. Compute per-day stats ---
    val dayStatsList = weekDays.map { (date, label) ->
        val daySessions = sessionsByDate[date] ?: emptyList()

        val totalMins = daySessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
        val sessionCount = daySessions.size

        val avgSession = if (sessionCount > 0) {
            totalMins / sessionCount
        } else 0f

        val passiveCount = daySessions.count { it.isPassiveSession }
        val autoplayCount = daySessions.count { it.isAutoplaySession }

        val scrollValues = daySessions
            .filter { it.scrollSpeedEstimate > 0 }
            .map { it.scrollSpeedEstimate }

        val avgScroll = if (scrollValues.isNotEmpty()) {
            scrollValues.average().toFloat()
        } else 0f

        val lateNight = daySessions.count { it.timeOfDay == "night" }

        val wellnessScore = computeWellnessScore(
            totalMinutes = totalMins,
            passiveSessions = passiveCount,
            autoplaySessions = autoplayCount,
            avgScrollSpeed = avgScroll,
            lateNightSessions = lateNight,
            sessionCount = sessionCount
        )

        Log.d("WeeklyStats", "Computed stats for $date ($label): ${daySessions.size} sessions, $totalMins mins")

        DayStats(
            date = date,
            dayLabel = label,
            totalMinutes = totalMins,
            sessionCount = sessionCount,
            avgSessionMinutes = avgSession,
            passiveSessions = passiveCount,
            autoplaySessions = autoplayCount,
            avgScrollSpeed = avgScroll,
            lateNightSessions = lateNight,
            wellnessScore = wellnessScore
        )
    }

    // --- 6. Active days only ---
    val activeDays = dayStatsList.filter { it.sessionCount > 0 }

    // --- 7. Weekly totals PER APP ---
    val timePerApp = sessionsThisWeek
        .groupBy { it.appLabel }
        .mapValues { (_, appSessions) ->
            appSessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
        }

    // --- 8. Weekly video estimates (FIXED: using unified estimateVideoCount) ---
    val weeklyVideos = sessionsThisWeek.groupBy { it.appLabel }.mapValues { (_, appSessions) ->
        appSessions.sumOf { session ->
            estimateVideoCount(session).first.toInt()
        }
    }

    // Add logging for video estimates
    Log.d("WeeklyStats", "Weekly video estimates: $weeklyVideos")
    Log.d("WeeklyStats", "Weekly Stats result: apps=${timePerApp.keys}, totalMins=${timePerApp.values.sum()}")

    return WeeklyStats(
        days = dayStatsList,
        totalScreenTimePerApp = timePerApp,
        weeklyEstimatedVideos = weeklyVideos,
        sessionsThisWeek = sessionsThisWeek,
        bestDay = activeDays.maxByOrNull { it.wellnessScore },
        worstDay = activeDays.minByOrNull { it.wellnessScore }
    )
}

fun computeWellnessScore(
    totalMinutes: Float,
    passiveSessions: Int,
    autoplaySessions: Int,
    avgScrollSpeed: Float,
    lateNightSessions: Int,
    sessionCount: Int
): Int {
    if (sessionCount == 0) return 100

    var score = 100

    score -= when {
        totalMinutes > 180 -> 35
        totalMinutes > 120 -> 25
        totalMinutes > 60  -> 15
        totalMinutes > 30  -> 5
        else -> 0
    }

    score -= (autoplaySessions * 8).coerceAtMost(24)
    score -= (passiveSessions * 5).coerceAtMost(15)

    score -= when {
        avgScrollSpeed > 50 -> 20
        avgScrollSpeed > 30 -> 12
        avgScrollSpeed > 15 -> 6
        else -> 0
    }

    score -= (lateNightSessions * 7).coerceAtMost(21)

    return score.coerceIn(0, 100)
}