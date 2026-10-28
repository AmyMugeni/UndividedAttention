package com.dreammap.attentionshieldlogger

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_events",
    indices = [
        Index(value = ["packageName", "timestamp"]),
        Index(value = ["packageName", "sessionId"])
    ]
)
data class NotificationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val sessionId: Long? = null,        // Linked session if user opened app from this notification
    val notificationTitle: String? = null,
    val isOngoing: Boolean = false,
    val isForeground: Boolean = false,
    val isHeadsUp: Boolean = false      // High-priority notification
)

@Entity(tableName = "session_events")
data class SessionEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val userId: String = "",
    val packageName: String,
    val appLabel: String,

    val sessionStart: Long,
    val sessionEnd: Long,
    val durationMinutes: Float,

    val timeOfDay: String,
    val sessionHour: Int = -1,
    val dayOfWeek: String,
    val date: String,

    // ── Behavioral signals (core ML features) ──
    val scrollCount: Int = 0,
    val tapCount: Int = 0,
    val scrollSpeedEstimate: Float = 0f,

    // ── Legacy heuristic flags (NOT for ML) ──
    val isPassiveSession: Boolean = false,
    val isAutoplaySession: Boolean = false,

    // ── Content/media metadata ──
    val actualVideoCount: Int = 0,
    val autoplayVideoCount: Int = 0,
    val totalPlaybackMinutes: Float = 0f,
    val wasMediaDetected: Boolean = false,
    val detectedFormat: String = "UNKNOWN",

    // ── Notification signals (ML-critical) ──
    val notificationCount: Int = 0,              // since last reset (volume)
    val notificationsInWindow: Int = 0,          // within 3 min window (pressure)
    val notificationBeforeSessionMs: Long = -1L, // REQUIRED (recency)
    val triggeredByNotification: Boolean = false // binary label
)