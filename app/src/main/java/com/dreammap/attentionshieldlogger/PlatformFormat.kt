// PlatformFormat.kt - Make sure this is your ONLY format detection file
package com.dreammap.attentionshieldlogger

enum class PlatformFormat {
    SHORTS,
    REELS,
    STORIES,
    STANDARD,
    LONG_FORM,
    TEXT_ONLY,
    UNKNOWN
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    NA
}

data class FormatConfig(
    val format: PlatformFormat,
    val avgVideoLengthMinutes: Float,
    val confidenceLevel: ConfidenceLevel,
    val confidenceReason: String
)

// YouTube format configs
val youtubeFormatConfigs = mapOf(
    PlatformFormat.SHORTS to FormatConfig(
        format = PlatformFormat.SHORTS,
        avgVideoLengthMinutes = 0.75f,
        confidenceLevel = ConfidenceLevel.HIGH,
        confidenceReason = "YouTube Shorts are consistently under 60 seconds."
    ),
    PlatformFormat.STANDARD to FormatConfig(
        format = PlatformFormat.STANDARD,
        avgVideoLengthMinutes = 8f,
        confidenceLevel = ConfidenceLevel.MEDIUM,
        confidenceReason = "Standard YouTube videos average 8-10 minutes."
    ),
    PlatformFormat.LONG_FORM to FormatConfig(
        format = PlatformFormat.LONG_FORM,
        avgVideoLengthMinutes = 20f,
        confidenceLevel = ConfidenceLevel.LOW,
        confidenceReason = "Long-form content varies widely in length."
    )
)

// Instagram format configs
val instagramFormatConfigs = mapOf(
    PlatformFormat.STORIES to FormatConfig(
        format = PlatformFormat.STORIES,
        avgVideoLengthMinutes = 0.25f,
        confidenceLevel = ConfidenceLevel.HIGH,
        confidenceReason = "Instagram Stories are consistently around 15 seconds."
    ),
    PlatformFormat.REELS to FormatConfig(
        format = PlatformFormat.REELS,
        avgVideoLengthMinutes = 0.5f,
        confidenceLevel = ConfidenceLevel.HIGH,
        confidenceReason = "Instagram Reels average 15-30 seconds."
    ),
    PlatformFormat.LONG_FORM to FormatConfig(
        format = PlatformFormat.LONG_FORM,
        avgVideoLengthMinutes = 2f,
        confidenceLevel = ConfidenceLevel.LOW,
        confidenceReason = "Instagram video posts vary widely in length."
    )
)

// Generic fallback for other apps
val genericPlatformConfigs = mapOf(
    "TikTok" to FormatConfig(
        format = PlatformFormat.SHORTS,
        avgVideoLengthMinutes = 0.5f,
        confidenceLevel = ConfidenceLevel.MEDIUM,
        confidenceReason = "TikTok content averages 15-60 seconds."
    ),
    "Snapchat" to FormatConfig(
        format = PlatformFormat.STORIES,
        avgVideoLengthMinutes = 0.17f,
        confidenceLevel = ConfidenceLevel.HIGH,
        confidenceReason = "Snapchat Stories are typically 10 seconds."
    ),
    "Facebook" to FormatConfig(
        format = PlatformFormat.STANDARD,
        avgVideoLengthMinutes = 3f,
        confidenceLevel = ConfidenceLevel.LOW,
        confidenceReason = "Facebook has mixed content types."
    ),
    "Twitter/X" to FormatConfig(
        format = PlatformFormat.TEXT_ONLY,
        avgVideoLengthMinutes = 0f,
        confidenceLevel = ConfidenceLevel.HIGH,
        confidenceReason = "Twitter is primarily text-based."
    )
)

/**
 * UNIFIED format detection - use ONLY this function everywhere
 */
fun detectFormat(packageName: String, appLabel: String, session: SessionEvent): PlatformFormat {
    return when (appLabel) {
        "YouTube" -> detectYouTubeFormat(session)
        "Instagram" -> detectInstagramFormat(session)
        else -> detectGenericFormat(appLabel, session)
    }
}

/**
 * Get format config for any app
 */
fun getFormatConfig(appLabel: String, format: PlatformFormat): FormatConfig? {
    return when (appLabel) {
        "YouTube" -> youtubeFormatConfigs[format]
        "Instagram" -> instagramFormatConfigs[format]
        else -> genericPlatformConfigs[appLabel]
    }
}

/**
 * Estimate video count based on session
 */
fun estimateVideoCount(session: SessionEvent): Pair<Int, ConfidenceLevel> {
    val format = detectFormat(session.packageName, session.appLabel, session)
    val config = getFormatConfig(session.appLabel, format)
        ?: return Pair(0, ConfidenceLevel.NA)

    if (config.avgVideoLengthMinutes <= 0f) {
        return Pair(0, ConfidenceLevel.NA)
    }

    val count = maxOf(1, (session.durationMinutes / config.avgVideoLengthMinutes).toInt())
    return Pair(count, config.confidenceLevel)
}

// Private detection functions
private fun detectYouTubeFormat(session: SessionEvent): PlatformFormat {
    return when {
        // Strong Shorts indicators
        session.scrollSpeedEstimate > 20f && session.durationMinutes < 10f -> PlatformFormat.SHORTS
        // Strong Long-form indicators
        session.durationMinutes > 15f && session.scrollCount < 5 -> PlatformFormat.LONG_FORM
        // Short duration + lots of scrolls = Shorts
        session.durationMinutes < 5f && session.scrollCount > 10 -> PlatformFormat.SHORTS
        // Very long = Long-form
        session.durationMinutes > 20f -> PlatformFormat.LONG_FORM
        // Default
        else -> PlatformFormat.STANDARD
    }
}

private fun detectInstagramFormat(session: SessionEvent): PlatformFormat {
    return when {
        // Tap-heavy + short = Stories
        session.tapCount > session.scrollCount * 2 && session.tapCount > 3 && session.durationMinutes < 5f -> PlatformFormat.STORIES
        // Fast scrolling + short = Reels
        session.scrollSpeedEstimate > 15f && session.durationMinutes < 10f -> PlatformFormat.REELS
        // Long + low interaction = Long-form video
        session.durationMinutes > 10f && session.scrollCount < 5 -> PlatformFormat.LONG_FORM
        // More scrolling than tapping = Reels
        session.scrollSpeedEstimate > 10f && session.tapCount <= session.scrollCount -> PlatformFormat.REELS
        // More tapping than scrolling = Stories
        session.tapCount > session.scrollCount -> PlatformFormat.STORIES
        // Default
        else -> PlatformFormat.STANDARD
    }
}

private fun detectGenericFormat(appLabel: String, session: SessionEvent): PlatformFormat {
    return when {
        session.scrollSpeedEstimate > 20f -> PlatformFormat.SHORTS
        session.tapCount > session.scrollCount * 2 -> PlatformFormat.STORIES
        session.durationMinutes > 15f && session.scrollCount < 10 -> PlatformFormat.LONG_FORM
        session.scrollSpeedEstimate < 2f && session.tapCount < 3 -> PlatformFormat.TEXT_ONLY
        else -> PlatformFormat.STANDARD
    }
}