// NotificationTracker.kt
package com.dreammap.attentionshieldlogger

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationTracker : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase

    private val targetPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android"
    )

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in targetPackages) return

        val flags = sbn.notification.flags
        val isOngoing = flags and Notification.FLAG_ONGOING_EVENT != 0
        val isFgService = flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        if (isOngoing || isFgService) return

        // Extract notification details safely
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val isHeadsUp = sbn.isClearable && !isOngoing

        val event = NotificationEvent(
            packageName = pkg,
            timestamp = System.currentTimeMillis(),
            notificationTitle = title,
            isOngoing = isOngoing,
            isForeground = isFgService,
            isHeadsUp = isHeadsUp
        )

        scope.launch {
            try {
                database.notificationDao().insert(event)

                // Keep only last 30 days of notifications
                val cutoff = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
                database.notificationDao().deleteOlderThan(cutoff)
            } catch (e: Exception) {
                // Log but don't crash
                android.util.Log.e("NotificationTracker", "Failed to save notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: Track notification dismissals
    }

    companion object {

        /**
         * Compute notification context for a session at END time
         * This should be called when finalizing a session, not at creation
         */
        suspend fun computeNotificationContext(
            pkg: String,
            sessionStart: Long,
            database: AppDatabase
        ): NotificationContext {
            val dao = database.notificationDao()
            val WINDOW_MS = 3 * 60 * 1000L  // 3 minutes

            // Get notifications in the 3-minute window before session
            val windowStart = sessionStart - WINDOW_MS
            val notificationsInWindow = dao.getCountInRange(pkg, windowStart, sessionStart)

            // Get total notifications before this session
            val totalBefore = dao.getCountInRange(pkg, 0, sessionStart)

            // Get last notification timestamp
            val lastTimestamp = dao.getLastTimestampBefore(pkg, sessionStart)

            // Calculate time since last notification
            val timeSinceLast = if (lastTimestamp != null) {
                sessionStart - lastTimestamp
            } else {
                -1L
            }

            // Determine if triggered by notification
            val triggeredByNotification = lastTimestamp != null &&
                    timeSinceLast in 0..WINDOW_MS

            return NotificationContext(
                notificationCount = totalBefore,
                notificationsInWindow = notificationsInWindow,
                notificationBeforeSessionMs = timeSinceLast,
                triggeredByNotification = triggeredByNotification
            )
        }

        fun hasPermission(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(context.packageName, ignoreCase = true)
        }
    }
}

data class NotificationContext(
    val notificationCount: Int,
    val notificationsInWindow: Int,
    val notificationBeforeSessionMs: Long,
    val triggeredByNotification: Boolean
)