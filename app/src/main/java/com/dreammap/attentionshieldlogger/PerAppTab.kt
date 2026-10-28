package com.dreammap.attentionshieldlogger

import androidx.compose.animation.*
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PerAppTab(sessions: List<SessionEvent>, weeklyStats: WeeklyStats?) {
    var showAllTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load scroll deltas per app
    var scrollDeltasByApp by remember { mutableStateOf<Map<String, ScrollDeltaStats>>(emptyMap()) }

    LaunchedEffect(sessions) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(context)
                val deltas = mutableMapOf<String, ScrollDeltaStats>()

                sessions.groupBy { it.appLabel }.forEach { (app, appSessions) ->
                    var allScrolls = 0
                    var totalDelta = 0f
                    var microScrolls = 0
                    var normalScrolls = 0
                    var macroScrolls = 0

                    appSessions.forEach { session ->
                        val events = db.scrollEventDao().getEventsInRange(
                            pkg = session.packageName,
                            startTime = session.sessionStart - 5000,
                            endTime = session.sessionEnd + 15000
                        )
                        Log.d("DELTA_DEBUG", "App: $app | Session: ${session.sessionStart} → ${session.sessionEnd} | Events found: ${events.size}")
                        val scrolls = events.filter { it.eventType == "SCROLL" && it.scrollDelta > 0 }

                        allScrolls += scrolls.size
                        totalDelta += scrolls.sumOf { it.scrollDelta.toDouble() }.toFloat()
                        microScrolls += scrolls.count { it.scrollDelta in 1f..100f }
                        normalScrolls += scrolls.count { it.scrollDelta in 101f..500f }
                        macroScrolls += scrolls.count { it.scrollDelta > 500f }
                    }

                    if (allScrolls > 0) {
                        deltas[app] = ScrollDeltaStats(
                            avgDelta = totalDelta / allScrolls,
                            microScrolls = microScrolls,
                            normalScrolls = normalScrolls,
                            macroScrolls = macroScrolls,
                            totalScrolls = allScrolls
                        )
                    }
                }

                scrollDeltasByApp = deltas
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text       = "Per App Breakdown",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text     = "This week's statistics per platform",
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        if (sessions.isEmpty()) {
            Text(
                text  = "No data yet. Use your phone normally and come back.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // ── Weekly breakdown ────────────────────────────────────────
        if (weeklyStats != null && weeklyStats.totalScreenTimePerApp.isNotEmpty()) {
            val weeklyDates = weeklyStats.days.map { it.date }.toSet()

            weeklyStats.totalScreenTimePerApp
                .entries
                .sortedByDescending { it.value }
                .forEach { (app, minutes) ->
                    val appSessions = sessions.filter {
                        it.appLabel == app && it.date in weeklyDates
                    }
                    WeeklyAppCard(
                        app      = app,
                        minutes  = minutes,
                        sessions = appSessions,
                        deltaStats = scrollDeltasByApp[app]  // ← Pass delta stats
                    )
                    Spacer(Modifier.height(12.dp))
                }
        } else {
            Text(
                text  = "No data for this week yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── All-time toggle ─────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text     = "Show all-time stats",
                fontSize = 14.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked         = showAllTime,
                onCheckedChange = { showAllTime = it }
            )
        }

        AnimatedVisibility(
            visible = showAllTime,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    text       = "All Time",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp)
                )
                sessions.groupBy { it.appLabel }.forEach { (app, appSessions) ->
                    AppStatCard(
                        app = app,
                        sessions = appSessions,
                        deltaStats = scrollDeltasByApp[app]  // ← Pass delta stats
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// Data class for scroll delta stats
data class ScrollDeltaStats(
    val avgDelta: Float,
    val microScrolls: Int,
    val normalScrolls: Int,
    val macroScrolls: Int,
    val totalScrolls: Int
)

// ── Weekly app card ──────────────────────────────────────────────────
@Composable
fun WeeklyAppCard(
    app: String,
    minutes: Float,
    sessions: List<SessionEvent>,
    deltaStats: ScrollDeltaStats? = null  // ← Add parameter
) {
    val sessionCount = sessions.size
    val avgSessionMins = if (sessionCount > 0) minutes / sessionCount else 0f

    val avgScroll = sessions
        .filter { it.scrollSpeedEstimate > 0 }
        .map { it.scrollSpeedEstimate }
        .average()
        .takeIf { !it.isNaN() }
        ?.toFloat() ?: 0f

    val totalScrolls = sessions.sumOf { it.scrollCount }
    val totalTaps    = sessions.sumOf { it.tapCount }

    val notificationDrivenOpens = sessions.count { it.triggeredByNotification }

    val maxWeeklyMinutes = 7 * 180f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // App name + total time
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = app,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text       = formatDuration(minutes),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (minutes / maxWeeklyMinutes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color    = when {
                    minutes > 300 -> MaterialTheme.colorScheme.error
                    minutes > 120 -> MaterialTheme.colorScheme.tertiary
                    else          -> MaterialTheme.colorScheme.primary
                }
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text     = when {
                    minutes > 300 -> "High usage this week"
                    minutes > 120 -> "Moderate usage this week"
                    else          -> "Light usage this week"
                },
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Raw stats row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppStatItem(label = "Opened",      value = sessionCount.toString())
                AppStatItem(label = "Avg Session", value = formatDuration(avgSessionMins))
                AppStatItem(label = "Scrolls",     value = totalScrolls.toString())
                AppStatItem(label = "Taps",        value = totalTaps.toString())
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppStatItem(
                    label = "Scroll Speed",
                    value = if (avgScroll > 0) "${avgScroll.toInt()}/min" else "N/A"
                )
                AppStatItem(
                    label = "Notif. Opens",
                    value = notificationDrivenOpens.toString()
                )
            }

            // ── NEW: Scroll Delta Analysis ──────────────────────────
            if (deltaStats != null && deltaStats.totalScrolls > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Scroll Behavior",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Average scroll delta with interpretation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Avg Scroll Size",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${deltaStats.avgDelta.toInt()} px",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            deltaStats.avgDelta > 500 -> MaterialTheme.colorScheme.error
                            deltaStats.avgDelta > 200 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Behavior interpretation
                Text(
                    text = when {
                        deltaStats.avgDelta > 500 -> "🔍 Skipping content rapidly"
                        deltaStats.avgDelta > 200 -> "📱 Normal browsing"
                        else -> "📖 Reading carefully"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Micro/Normal/Macro breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${deltaStats.microScrolls}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Micro\n(<100px)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${deltaStats.normalScrolls}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Normal\n(100-500px)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${deltaStats.macroScrolls}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Macro\n(>500px)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress bar showing distribution
                Spacer(Modifier.height(8.dp))

                val total = deltaStats.totalScrolls.toFloat()
                Row(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    if (total > 0) {
                        Surface(
                            modifier = Modifier
                                .weight((deltaStats.microScrolls / total).coerceIn(0f, 1f))
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                        Surface(
                            modifier = Modifier
                                .weight((deltaStats.normalScrolls / total).coerceIn(0f, 1f))
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {}
                        Surface(
                            modifier = Modifier
                                .weight((deltaStats.macroScrolls / total).coerceIn(0f, 1f))
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.error
                        ) {}
                    }
                }
            }
        }
    }
}

// ── All-time app card ───────────────────────────────────────────────
@Composable
fun AppStatCard(
    app: String,
    sessions: List<SessionEvent>,
    deltaStats: ScrollDeltaStats? = null  // ← Add parameter
) {
    val totalMins    = sessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
    val sessionCount = sessions.size
    val avgMins      = if (sessionCount > 0) totalMins / sessionCount else 0f
    val totalScrolls = sessions.sumOf { it.scrollCount }
    val totalTaps    = sessions.sumOf { it.tapCount }
    val avgScroll    = sessions
        .filter { it.scrollSpeedEstimate > 0 }
        .map { it.scrollSpeedEstimate }
        .average()
        .takeIf { !it.isNaN() }
        ?.toFloat() ?: 0f
    val notificationDrivenOpens = sessions.count { it.triggeredByNotification }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = app,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppStatItem(label = "Total Time",  value = formatDuration(totalMins))
                AppStatItem(label = "Opened",      value = sessionCount.toString())
                AppStatItem(label = "Avg Session", value = formatDuration(avgMins))
                AppStatItem(
                    label = "Scroll Speed",
                    value = if (avgScroll > 0) "${avgScroll.toInt()}/min" else "N/A"
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppStatItem(label = "Total Scrolls", value = totalScrolls.toString())
                AppStatItem(label = "Total Taps",    value = totalTaps.toString())
                AppStatItem(label = "Notif. Opens",  value = notificationDrivenOpens.toString())
            }

            // ── NEW: Scroll Delta Analysis for all-time ─────────────
            if (deltaStats != null && deltaStats.totalScrolls > 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Scroll Behavior",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Avg Scroll: ${deltaStats.avgDelta.toInt()}px",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            deltaStats.avgDelta > 500 -> "Skipping"
                            deltaStats.avgDelta > 200 -> "Browsing"
                            else -> "Reading"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            deltaStats.avgDelta > 500 -> MaterialTheme.colorScheme.error
                            deltaStats.avgDelta > 200 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Micro: ${deltaStats.microScrolls} | Normal: ${deltaStats.normalScrolls} | Macro: ${deltaStats.macroScrolls}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = value,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.primary
        )
        Text(
            text     = label,
            fontSize = 11.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}