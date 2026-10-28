// ScrollEvent.kt
package com.dreammap.attentionshieldlogger

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scroll_events",
    indices = [
        Index(value = ["packageName", "timestamp"]),
        Index(value = ["sessionId"])
    ]
)
data class ScrollEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val sessionId: Long? = null,  // Linked when session is created
    val eventType: String,        // "SCROLL" or "TAP"
    val scrollDelta: Float = 0f,
    val confidence: Float = 0f,   // How confident we are this is real scroll
    val contentHash: String? = null // For content-based dedup
)