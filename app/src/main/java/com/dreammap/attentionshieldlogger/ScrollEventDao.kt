// ScrollEventDao.kt
package com.dreammap.attentionshieldlogger

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollEventDao {

    @Insert
    suspend fun insert(event: ScrollEvent): Long

    @Insert
    suspend fun insertAll(events: List<ScrollEvent>)

    // Get scrolls for session building
    @Query("""
        SELECT * FROM scroll_events 
        WHERE packageName = :pkg 
        AND timestamp BETWEEN :startTime AND :endTime
        AND sessionId IS NULL
        ORDER BY timestamp ASC
    """)
    suspend fun getUnlinkedEvents(
        pkg: String,
        startTime: Long,
        endTime: Long
    ): List<ScrollEvent>

    // Get ALL scroll events (not just unlinked ones)
    @Query("""
    SELECT * FROM scroll_events 
    WHERE packageName = :pkg 
    AND timestamp BETWEEN :startTime AND :endTime
    ORDER BY timestamp ASC
""")
    suspend fun getEventsInRange(
        pkg: String,
        startTime: Long,
        endTime: Long
    ): List<ScrollEvent>

    // Link scrolls to a session
    @Query("""
        UPDATE scroll_events 
        SET sessionId = :sessionId 
        WHERE packageName = :pkg 
        AND timestamp BETWEEN :startTime AND :endTime
        AND sessionId IS NULL
    """)
    suspend fun linkToSession(
        pkg: String,
        sessionId: Long,
        startTime: Long,
        endTime: Long
    )

    // Get scroll counts for session
    @Query("""
        SELECT 
            COUNT(CASE WHEN eventType = 'SCROLL' THEN 1 END) as scrollCount,
            COUNT(CASE WHEN eventType = 'TAP' THEN 1 END) as tapCount
        FROM scroll_events 
        WHERE sessionId = :sessionId
    """)
    suspend fun getSessionCounts(sessionId: Long): SessionScrollCounts

    // Cleanup
    @Query("""
        DELETE FROM scroll_events 
        WHERE timestamp < :cutoffTime
    """)
    suspend fun deleteOlderThan(cutoffTime: Long)

    // Real-time observation for overlay
    @Query("""
        SELECT * FROM scroll_events 
        WHERE packageName = :pkg 
        AND timestamp >= :since
        ORDER BY timestamp DESC
    """)
    fun observeRecentScrolls(pkg: String, since: Long): Flow<List<ScrollEvent>>
}

data class SessionScrollCounts(
    val scrollCount: Int,
    val tapCount: Int
)