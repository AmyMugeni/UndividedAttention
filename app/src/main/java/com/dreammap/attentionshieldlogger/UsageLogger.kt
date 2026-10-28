// UsageLogger.kt
package com.dreammap.attentionshieldlogger

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class UsageLogger(private val context: Context) {

    private val targetApps = mapOf(
        "com.zhiliaoapp.musically"   to "TikTok",
        "com.instagram.android"      to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.twitter.android"        to "Twitter/X",
        "com.facebook.katana"        to "Facebook",
        "com.snapchat.android"       to "Snapchat"
    )

    companion object {
        private val mutex = Mutex()
        private const val SIXTY_DAYS_MS    = 60L * 24 * 60 * 60 * 1000L
        private const val MIN_SESSION_MINS = 0.5f
    }

    suspend fun logRecentSessions() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val db = AppDatabase.getInstance(context)
            val userId = UserPreferences.getUserId(context)

            val end = System.currentTimeMillis()
            val start = end - SIXTY_DAYS_MS

            val events = usm.queryEvents(start, end)
            val event = UsageEvents.Event()
            val sessionStarts = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg !in targetApps) continue

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        sessionStarts[pkg] = event.timeStamp
                    }

                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val sessionStart = sessionStarts[pkg] ?: continue
                        val sessionEnd = event.timeStamp
                        val durationMins = (sessionEnd - sessionStart) / 60000f

                        if (durationMins < MIN_SESSION_MINS) {
                            sessionStarts.remove(pkg)
                            continue
                        }

                        if (db.sessionDao().sessionExists(pkg, sessionStart) > 0) {
                            sessionStarts.remove(pkg)
                            continue
                        }

                        // Build session using Room data
                        val session = buildSession(
                            db, pkg, sessionStart, sessionEnd, durationMins, userId
                        )

                        db.sessionDao().insert(session)

                        // Link scroll events to this session
                        db.scrollEventDao().linkToSession(
                            pkg = pkg,
                            sessionId = session.id.toLong(),
                            startTime = sessionStart - 5000,
                            endTime = sessionEnd + 15000
                        )
                        val counts = db.scrollEventDao().getSessionCounts(session.id.toLong())
                        db.sessionDao().updateScrollCounts(
                            sessionId = session.id,
                            scrollCount = counts.scrollCount,
                            tapCount = counts.tapCount
                        )

                        // Link notifications to this session
                        db.notificationDao().linkNotificationsToSession(
                            pkg = pkg,
                            sessionId = session.id.toLong(),
                            sessionStart = sessionStart
                        )

                        sessionStarts.remove(pkg)
                    }
                }
            }
        }
    }

    // In UsageLogger.kt - Replace the entire file's format-related code

    private suspend fun buildSession(
        db: AppDatabase,
        pkg: String,
        sessionStart: Long,
        sessionEnd: Long,
        durationMins: Float,
        userId: String
    ): SessionEvent {

        val cal = Calendar.getInstance().apply { timeInMillis = sessionStart }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sessionStart))
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(sessionStart))

        val timeOfDay = when (hour) {
            in 5..11  -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else      -> "night"
        }

        // ── Get scroll counts from Room ────────────────────────────
        val scrollEvents = db.scrollEventDao().getUnlinkedEvents(
            pkg = pkg,
            startTime = sessionStart - 5000,
            endTime = sessionEnd + 15000
        )

        val scrollCount = scrollEvents.count { it.eventType == "SCROLL" }
        val tapCount = scrollEvents.count { it.eventType == "TAP" }
        val scrollSpeed = if (durationMins > 0) scrollCount / durationMins else 0f

        Log.d("USAGE_LOGGER", "Session for $pkg: S=$scrollCount, T=$tapCount (${scrollEvents.size} events)")

        // ── Get notification context from Room ─────────────────────
        val notificationContext = NotificationTracker.computeNotificationContext(
            pkg = pkg,
            sessionStart = sessionStart,
            database = db
        )

        // ── Build session FIRST (without format) ───────────────────
        val session = SessionEvent(
            userId = userId,
            packageName = pkg,
            appLabel = targetApps[pkg] ?: pkg,
            sessionStart = sessionStart,
            sessionEnd = sessionEnd,
            durationMinutes = durationMins,
            timeOfDay = timeOfDay,
            sessionHour = hour,
            dayOfWeek = dayOfWeek,
            date = dateStr,

            // Scroll metrics
            scrollCount = scrollCount,
            tapCount = tapCount,
            scrollSpeedEstimate = scrollSpeed,

            // Notification metrics (ALL from Room now)
            notificationCount = notificationContext.notificationCount,
            notificationsInWindow = notificationContext.notificationsInWindow,
            notificationBeforeSessionMs = notificationContext.notificationBeforeSessionMs,
            triggeredByNotification = notificationContext.triggeredByNotification
        )

        // ── NOW detect format using the complete session ───────────
        // This uses the unified function from PlatformFormat.kt
        val detectedFormat = detectFormat(pkg, session.appLabel, session).name

        // Return with detected format
        return session.copy(detectedFormat = detectedFormat)
    }
}