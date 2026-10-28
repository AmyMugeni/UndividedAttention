// PlatformConfig.kt - Keep only these helpers
package com.dreammap.attentionshieldlogger

fun getConfidenceLabel(level: ConfidenceLevel): String {
    return when (level) {
        ConfidenceLevel.HIGH -> "High confidence"
        ConfidenceLevel.MEDIUM -> "Medium confidence"
        ConfidenceLevel.LOW -> "Low confidence"
        ConfidenceLevel.NA -> "N/A"
    }
}

fun getConfidenceDescription(level: ConfidenceLevel): String {
    return when (level) {
        ConfidenceLevel.HIGH -> "This estimate is reliable for this platform."
        ConfidenceLevel.MEDIUM -> "This estimate is approximate — actual count may vary."
        ConfidenceLevel.LOW -> "This estimate has high variance — treat it as a rough guide only."
        ConfidenceLevel.NA -> "No video estimate available for this platform."
    }
}