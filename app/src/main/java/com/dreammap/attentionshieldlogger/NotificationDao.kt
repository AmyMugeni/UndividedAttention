// NotificationDao.kt
package com.dreammap.attentionshieldlogger

import androidx.room.*
import androidx.room.OnConflictStrategy.Companion.REPLACE

@Dao
interface NotificationDao {

    @Insert(onConflict = REPLACE)
    suspend fun insert(event: NotificationEvent): Long

    @Query("""
        SELECT * FROM notification_events 
        WHERE packageName = :pkg 
        ORDER BY timestamp DESC 
        LIMIT 100
    """)
    suspend fun getRecentForApp(pkg: String): List<NotificationEvent>

    @Query("""
        SELECT COUNT(*) FROM notification_events 
        WHERE packageName = :pkg
    """)
    suspend fun getTotalCount(pkg: String): Int

    @Query("""
        SELECT COUNT(*) FROM notification_events 
        WHERE packageName = :pkg 
        AND timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getCountInRange(pkg: String, startTime: Long, endTime: Long): Int

    @Query("""
        SELECT * FROM notification_events 
        WHERE packageName = :pkg 
        AND timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getInRange(pkg: String, startTime: Long, endTime: Long): List<NotificationEvent>

    @Query("""
        SELECT MAX(timestamp) FROM notification_events 
        WHERE packageName = :pkg 
        AND timestamp <= :beforeTime
    """)
    suspend fun getLastTimestampBefore(pkg: String, beforeTime: Long): Long?

    @Query("""
        UPDATE notification_events 
        SET sessionId = :sessionId 
        WHERE packageName = :pkg 
        AND timestamp BETWEEN :sessionStart - 180000 AND :sessionStart
        AND sessionId IS NULL
    """)
    suspend fun linkNotificationsToSession(
        pkg: String,
        sessionId: Long,
        sessionStart: Long
    )

    @Query("""
        DELETE FROM notification_events 
        WHERE timestamp < :cutoffTime
    """)
    suspend fun deleteOlderThan(cutoffTime: Long)

    // For ML - get notification pressure patterns
    @Query("""
        SELECT 
            strftime('%H', datetime(timestamp/1000, 'unixepoch')) as hour,
            COUNT(*) as count
        FROM notification_events 
        WHERE packageName = :pkg 
        AND timestamp >= :since
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun getHourlyDistribution(pkg: String, since: Long): List<HourlyNotificationCount>
}

data class HourlyNotificationCount(
    val hour: String,
    val count: Int
)