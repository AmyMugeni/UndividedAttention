package com.dreammap.attentionshieldlogger

data class TodayStats(
    val totalScreenTimeMinutes: Float,
    val estimatedVideosWatched: Map<String, Int>,
    val mostUsedApp: String,
    val notificationCount: Int,
    val sessionCount: Int,
    val asiResult: ASIResult? = null
)