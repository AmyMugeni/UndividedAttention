package com.dreammap.attentionshieldlogger

import kotlin.math.*
import kotlin.math.roundToInt

data class ASIResult(
    val score: Int,                     // 0-100
    val level: ASILevel,
    val label: String,
    val description: String,

    // Component scores (0-100 each)
    val intensityScore: Int,
    val fragmentationScore: Int,
    val compulsionScore: Int,
    val disruptionScore: Int,

    // Sub-component breakdowns
    val totalScreenTimeNorm: Float,
    val avgSessionNorm: Float,
    val longestSessionNorm: Float,
    val sessionFrequencyNorm: Float,
    val burstinessScore: Float,
    val variabilityScore: Float,
    val inverseGapScore: Float,
    val rapidReopenRate: Float,
    val nightUsageScore: Float,
    val longBlockScore: Float,

    val dominantDimension: String
)

enum class ASILevel {
    HEALTHY, MODERATE, HIGH, CRITICAL
}

object ASICalculator {

    // ─── Top-level dimension weights (α, β, γ, δ) ───────────────────────
    private const val ALPHA = 0.30f   // Intensity
    private const val BETA  = 0.25f   // Fragmentation
    private const val GAMMA = 0.25f   // Compulsion
    private const val DELTA = 0.20f   // Disruption

    // ─── Intensity sub-weights ───────────────────────────────────────────
    private const val W1 = 0.50f   // Total screen time
    private const val W2 = 0.30f   // Avg session duration
    private const val W3 = 0.20f   // Longest session

    // ─── Fragmentation sub-weights ──────────────────────────────────────
    private const val W4 = 0.40f   // Session frequency
    private const val W5 = 0.35f   // Burstiness
    private const val W6 = 0.25f   // Session variability

    // ─── Compulsion sub-weights ─────────────────────────────────────────
    private const val W7 = 0.40f   // Inverse gap
    private const val W8 = 0.35f   // Pickups per hour
    private const val W9 = 0.25f   // Rapid reopen rate

    // ─── Disruption sub-weights ─────────────────────────────────────────
    private const val W10 = 0.60f  // Night usage
    private const val W11 = 0.40f  // Uninterrupted long blocks

    // ─── Normalisation thresholds (k values for exponential smoothing) ──
    private const val K_TOTAL_TIME      = 120f  // 2 hours feels significant
    private const val K_AVG_SESSION     = 20f   // 20 min avg session
    private const val K_LONGEST_SESSION = 45f   // 45 min longest session
    private const val K_FREQUENCY       = 12f   // 12 sessions/day
    private const val K_GAP             = 60f   // 60 min avg gap
    private const val K_PICKUPS         = 6f    // 6 pickups/hour
    private const val K_LONG_BLOCKS     = 3f    // 3 long blocks/day

    // ─── Exponential normalisation: smooth, no hard cutoff ──────────────
    private fun norm(x: Float, k: Float): Float {
        return 1f - exp(-x / k)
    }

    fun calculate(sessions: List<SessionEvent>): ASIResult {
        if (sessions.isEmpty()) {
            return emptyResult()
        }

        val now = System.currentTimeMillis()

        // ── Raw metrics ─────────────────────────────────────────────────

        val totalMinutes = sessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
        val avgSession = totalMinutes / sessions.size
        val longestSession = sessions.maxOf { it.durationMinutes }

        val sessionCount = sessions.size.toFloat()

        // Session start times sorted
        val startTimes = sessions.map { it.sessionStart }.sorted()

        // Inter-session gaps in minutes
        val gaps = if (startTimes.size > 1) {
            startTimes.zipWithNext { a, b -> (b - a) / 60000f }
        } else emptyList()

        val avgGap = if (gaps.isNotEmpty()) gaps.average().toFloat() else 999f

        // Burstiness — sessions within 15 min windows
        val burstySessions = if (startTimes.size > 1) {
            startTimes.zipWithNext { a, b ->
                (b - a) < 15 * 60 * 1000L  // within 15 minutes
            }.count { it }.toFloat()
        } else 0f

        val burstiness = if (sessionCount > 1)
            burstySessions / (sessionCount - 1f)
        else 0f

        // Session variability — normalised std dev of durations
        val durations = sessions.map { it.durationMinutes }
        val mean = durations.average().toFloat()
        val stdDev = if (durations.size > 1) {
            sqrt(durations.map { (it - mean).pow(2) }.average()).toFloat()
        } else 0f
        val variability = norm(stdDev, 10f)

        // Rapid reopens — sessions starting within 2 min of previous end
        val rapidReopens = if (sessions.size > 1) {
            sessions.sortedBy { it.sessionStart }
                .zipWithNext { a, b ->
                    (b.sessionStart - a.sessionEnd) < 2 * 60 * 1000L
                }.count { it }.toFloat()
        } else 0f

        val rapidReopenRate = if (sessionCount > 1)
            rapidReopens / (sessionCount - 1f)
        else 0f

        // Pickups per hour — based on total observation window
        val observationHours = if (startTimes.isNotEmpty()) {
            val windowMs = (startTimes.last() - startTimes.first())
                .coerceAtLeast(3600000L)  // min 1 hour window
            windowMs / 3600000f
        } else 1f

        val pickupsPerHour = sessionCount / observationHours

        // Night usage — sessions after 23:00, weighted higher
        val nightSessions = sessions.count { it.timeOfDay == "night" }
        val nightFraction = nightSessions.toFloat() / sessionCount

        // Long uninterrupted blocks — sessions > 30 min
        val longBlocks = sessions.count { it.durationMinutes >= 30f }.toFloat()

        // ── Normalise all metrics ────────────────────────────────────────

        val T    = norm(totalMinutes, K_TOTAL_TIME)
        val Savg = norm(avgSession, K_AVG_SESSION)
        val L    = norm(longestSession, K_LONGEST_SESSION)
        val Fq   = norm(sessionCount, K_FREQUENCY)
        val B    = burstiness.coerceIn(0f, 1f)
        val V    = variability.coerceIn(0f, 1f)
        val Ginv = 1f - norm(avgGap, K_GAP)
        val P    = norm(pickupsPerHour, K_PICKUPS)
        val R    = rapidReopenRate.coerceIn(0f, 1f)
        val N    = nightFraction.coerceIn(0f, 1f)
        val U    = norm(longBlocks, K_LONG_BLOCKS)

        // ── Compute dimensions ───────────────────────────────────────────

        val intensity     = W1 * T + W2 * Savg + W3 * L
        val fragmentation = W4 * Fq + W5 * B + W6 * V
        val compulsion    = W7 * Ginv + W8 * P + W9 * R
        val disruption    = W10 * N + W11 * U

        // ── Final ASI ────────────────────────────────────────────────────

        val asi = ALPHA * intensity +
                BETA  * fragmentation +
                GAMMA * compulsion +
                DELTA * disruption

        val finalScore = (asi * 100).roundToInt().coerceIn(0, 100)

        // ── Level ────────────────────────────────────────────────────────

        val level = when {
            finalScore <= 30 -> ASILevel.HEALTHY
            finalScore <= 60 -> ASILevel.MODERATE
            finalScore <= 80 -> ASILevel.HIGH
            else             -> ASILevel.CRITICAL
        }

        // ── Dominant dimension ───────────────────────────────────────────

        val dimensions = mapOf(
            "Intensity"     to (ALPHA * intensity),
            "Fragmentation" to (BETA  * fragmentation),
            "Compulsion"    to (GAMMA * compulsion),
            "Disruption"    to (DELTA * disruption)
        )
        val dominantDimension = dimensions.maxByOrNull { it.value }?.key ?: "None"

        val label = when (level) {
            ASILevel.HEALTHY  -> "Healthy"
            ASILevel.MODERATE -> "Moderate"
            ASILevel.HIGH     -> "High"
            ASILevel.CRITICAL -> "Critical"
        }

        val description = when (level) {
            ASILevel.HEALTHY ->
                "Your attention is in good shape. " +
                        "Usage patterns show intentional engagement."
            ASILevel.MODERATE ->
                "Some saturation detected. " +
                        "$dominantDimension is the main driver — worth monitoring."
            ASILevel.HIGH ->
                "Attention saturation is elevated. " +
                        "$dominantDimension is significantly impacting your focus."
            ASILevel.CRITICAL ->
                "High attention saturation detected. " +
                        "$dominantDimension is the dominant pattern. " +
                        "A digital detox is recommended."
        }

        return ASIResult(
            score = finalScore,
            level = level,
            label = label,
            description = description,
            intensityScore = (intensity * 100).roundToInt().coerceIn(0, 100),
            fragmentationScore = (fragmentation * 100).roundToInt().coerceIn(0, 100),
            compulsionScore = (compulsion * 100).roundToInt().coerceIn(0, 100),
            disruptionScore = (disruption * 100).roundToInt().coerceIn(0, 100),
            totalScreenTimeNorm = T,
            avgSessionNorm = Savg,
            longestSessionNorm = L,
            sessionFrequencyNorm = Fq,
            burstinessScore = B,
            variabilityScore = V,
            inverseGapScore = Ginv,
            rapidReopenRate = R,
            nightUsageScore = N,
            longBlockScore = U,
            dominantDimension = dominantDimension
        )
    }

    private fun emptyResult() = ASIResult(
        score = 0,
        level = ASILevel.HEALTHY,
        label = "No data",
        description = "No sessions recorded today yet.",
        intensityScore = 0,
        fragmentationScore = 0,
        compulsionScore = 0,
        disruptionScore = 0,
        totalScreenTimeNorm = 0f,
        avgSessionNorm = 0f,
        longestSessionNorm = 0f,
        sessionFrequencyNorm = 0f,
        burstinessScore = 0f,
        variabilityScore = 0f,
        inverseGapScore = 0f,
        rapidReopenRate = 0f,
        nightUsageScore = 0f,
        longBlockScore = 0f,
        dominantDimension = "None"
    )
}

private fun Float.pow(n: Int): Float = this.toDouble().pow(n).toFloat()
