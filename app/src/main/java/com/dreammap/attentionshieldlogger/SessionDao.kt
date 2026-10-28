package com.dreammap.attentionshieldlogger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class SessionHourUpdate(val id: Int, val sessionStart: Long)

// For daily notification breakdown charts
data class DailyNotificationCount(
    val date: String,
    val notificationCount: Int
)

// For per-app notification totals
data class AppNotificationTotal(
    val packageName: String,
    val notificationCount: Int
)

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(event: SessionEvent)

    @Query("SELECT * FROM session_events ORDER BY sessionStart DESC")
    suspend fun getAll(): List<SessionEvent>

    @Query("SELECT * FROM session_events WHERE packageName = :pkg ORDER BY sessionStart DESC")
    suspend fun getByApp(pkg: String): List<SessionEvent>

    @Query("""SELECT * FROM session_events WHERE packageName = :pkg
    AND sessionStart BETWEEN :start AND :end ORDER BY sessionStart DESC""")
    suspend fun getSessionsInRange(
        pkg: String,
        start: Long,
        end: Long
    ): List<SessionEvent>

    @Query("SELECT * FROM session_events WHERE date = :date ORDER BY sessionStart DESC")
    suspend fun getByDate(date: String): List<SessionEvent>

    @Query("SELECT COUNT(*) FROM session_events")
    suspend fun getCount(): Int

    @Query("SELECT * FROM session_events WHERE sessionStart >= :startOfWeek ORDER BY sessionStart DESC")
    suspend fun getThisWeek(startOfWeek: Long): List<SessionEvent>

    // Date range query — used for weekly stats
    @Query("""
        SELECT * FROM session_events 
        WHERE date >= :startDate 
        AND date <= :endDate 
        ORDER BY sessionStart DESC
    """)
    suspend fun getWeekSessions(startDate: String, endDate: String): List<SessionEvent>

    // Open-ended date range — used in DashboardScreen
    @Query("SELECT * FROM session_events WHERE date >= :startDate ORDER BY sessionStart DESC")
    suspend fun getFromDate(startDate: String): List<SessionEvent>

    // Fuzzy duplicate check — catches Samsung spurious near-duplicate events
    // within a 1-minute window of the same session start
    @Query("""
        SELECT COUNT(*) FROM session_events 
        WHERE packageName = :pkg 
        AND ABS(sessionStart - :start) < 60000
    """)
    suspend fun sessionExists(pkg: String, start: Long): Int

    // ── Backfill: sessionHour ────────────────────────────────────────────
    @Query("SELECT id, sessionStart FROM session_events WHERE sessionHour = -1")
    suspend fun getSessionsWithUnknownHour(): List<SessionHourUpdate>

    @Query("UPDATE session_events SET sessionHour = :hour WHERE id = :id")
    suspend fun updateSessionHour(id: Int, hour: Int)

    // ── Backfill: userId ─────────────────────────────────────────────────
    @Query("SELECT id FROM session_events WHERE userId = ''")
    suspend fun getSessionsWithEmptyUserId(): List<Int>

    @Query("UPDATE session_events SET userId = :userId WHERE id = :id")
    suspend fun updateUserId(id: Int, userId: String)

    // ── Deduplication ────────────────────────────────────────────────────
    // Keeps only the lowest id (first inserted) per unique session
    @Query("""
        DELETE FROM session_events 
        WHERE id NOT IN (
            SELECT MIN(id) 
            FROM session_events 
            GROUP BY packageName, sessionStart
        )
    """)
    suspend fun deleteDuplicates()

    // ── Notification Counts ──────────────────────────────────────────────

    // Total notifications received for one app across all time
    @Query("""
        SELECT SUM(notificationCount) 
        FROM session_events 
        WHERE packageName = :pkg
    """)
    suspend fun getTotalNotificationsForApp(pkg: String): Int

    // Total notifications for one app within a date range
    @Query("""
        SELECT SUM(notificationCount) 
        FROM session_events 
        WHERE packageName = :pkg 
        AND date BETWEEN :startDate AND :endDate
    """)
    suspend fun getNotificationsForAppInRange(
        pkg: String,
        startDate: String,
        endDate: String
    ): Int

    // Daily notification breakdown for one app — used for charts
    @Query("""
        SELECT date, SUM(notificationCount) AS notificationCount
        FROM session_events
        WHERE packageName = :pkg
        AND date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
    """)
    suspend fun getDailyNotificationsForApp(
        pkg: String,
        startDate: String,
        endDate: String
    ): List<DailyNotificationCount>

    // All apps ranked by notification count in a date range — used for leaderboard/summary
    @Query("""
        SELECT packageName, SUM(notificationCount) AS notificationCount
        FROM session_events
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY packageName
        ORDER BY notificationCount DESC
    """)
    suspend fun getAppNotificationRanking(
        startDate: String,
        endDate: String
    ): List<AppNotificationTotal>

    // Total notifications across ALL apps on a specific date — used for daily digest
    @Query("""
        SELECT SUM(notificationCount) 
        FROM session_events 
        WHERE date = :date
    """)
    suspend fun getTotalNotificationsOnDate(date: String): Int

    // Backfill: find sessions where notificationCount was never set (legacy rows)
    @Query("SELECT id FROM session_events WHERE notificationCount = -1")
    suspend fun getSessionsWithUnknownNotificationCount(): List<Int>

    @Query("UPDATE session_events SET notificationCount = 0 WHERE id = :id")
    suspend fun resetNotificationCount(id: Int)

    // In SessionDao.kt
    @Query("""
    UPDATE session_events 
    SET scrollCount = :scrollCount, 
        tapCount = :tapCount,
        scrollSpeedEstimate = CASE 
            WHEN durationMinutes > 0 THEN CAST(:scrollCount AS REAL) / durationMinutes 
            ELSE 0 
        END
    WHERE id = :sessionId
""")
    suspend fun updateScrollCounts(sessionId: Int, scrollCount: Int, tapCount: Int)
}

