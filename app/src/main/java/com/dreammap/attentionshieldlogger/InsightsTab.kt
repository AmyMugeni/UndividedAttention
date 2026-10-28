package com.dreammap.attentionshieldlogger

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun InsightsTab(todayStats: TodayStats?, weeklyStats: WeeklyStats?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Insights",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = "What your usage means for your wellbeing",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (todayStats == null && weeklyStats == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No data yet to generate insights.",
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Today's insights
            if (todayStats != null) {
                Text(
                    text = "Today",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                InsightCard(
                    title = "Screen Time",
                    insight = generateScreenTimeInsight(todayStats.totalScreenTimeMinutes)
                )
                Spacer(modifier = Modifier.height(10.dp))

                InsightCard(
                    title = "Notification Load",
                    insight = generateNotificationInsight(todayStats.notificationCount)
                )
                Spacer(modifier = Modifier.height(10.dp))

                InsightCard(
                    title = "Content Consumption",
                    insight = generateVideoInsight(
                        todayStats.estimatedVideosWatched.values.sum()
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Weekly insights
            if (weeklyStats != null && weeklyStats.days.any { it.sessionCount > 0 }) {
                Text(
                    text = "This Week",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val avgWellness = weeklyStats.days
                    .filter { it.sessionCount > 0 }
                    .map { it.wellnessScore }
                    .average().toInt()

                InsightCard(
                    title = "Weekly Wellness Score",
                    insight = generateWeeklyWellnessInsight(avgWellness)
                )
                Spacer(modifier = Modifier.height(10.dp))

                val totalPassive = weeklyStats.days.sumOf { it.passiveSessions }
                val totalAutoplay = weeklyStats.days.sumOf { it.autoplaySessions }
                if (totalPassive > 0 || totalAutoplay > 0) {
                    InsightCard(
                        title = "Passive Consumption",
                        insight = generatePassiveInsight(totalPassive, totalAutoplay)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                val totalLateNight = weeklyStats.days.sumOf { it.lateNightSessions }
                if (totalLateNight > 0) {
                    InsightCard(
                        title = "Late Night Usage",
                        insight = generateLateNightInsight(totalLateNight)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (weeklyStats.bestDay != null && weeklyStats.worstDay != null) {
                    InsightCard(
                        title = "Your Pattern This Week",
                        insight = "Your healthiest day was ${weeklyStats.bestDay.dayLabel} " +
                                "(score: ${weeklyStats.bestDay.wellnessScore}/100) and your " +
                                "most intensive day was ${weeklyStats.worstDay.dayLabel} " +
                                "(score: ${weeklyStats.worstDay.wellnessScore}/100). " +
                                generatePatternInsight(
                                    weeklyStats.bestDay,
                                    weeklyStats.worstDay
                                )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // AI Insights — coming soon
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Personalised AI Insights",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Coming soon — AI-powered recommendations " +
                                "based on your usage patterns and digital " +
                                "wellbeing research.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Export data section — always visible
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))



        ExportDataButton()
    }
}

@Composable
fun ExportDataButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Export Your Data",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Export your usage sessions as a CSV file. " +
                        "Your data is identified only by an anonymous ID — " +
                        "no personal information is included.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "This data is used to train personalised machine learning " +
                        "models that improve your insights over time.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            exportError?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // DEBUG — remove before release
            OutlinedButton(
                onClick = {
                    scope.launch {
                        UsageLogger(context).logRecentSessions()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Debug: Force Sync")
            }

            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        exportError = null
                        try {
                            val intent = CsvExporter.exportToCsv(context)
                            launcher.launch(
                                Intent.createChooser(intent, "Export data via...")
                            )
                        } catch (e: Exception) {
                            exportError = "Export failed: ${e.message}"
                        } finally {
                            isExporting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                Text(if (isExporting) "Exporting..." else "Export to CSV")
            }
        }
    }
}

@Composable
fun InsightCard(title: String, insight: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = insight,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

fun generateScreenTimeInsight(minutes: Float): String {
    return when {
        minutes < 30 -> "Under 30 minutes today — a light day. Research links low social " +
                "media use with higher life satisfaction and better focus."
        minutes < 60 -> "About ${minutes.toInt()} minutes today. Moderate usage. " +
                "Consider short breaks between sessions to reset your attention."
        minutes < 120 -> "${formatDuration(minutes)} today. Studies link usage above " +
                "1 hour with increased anxiety. Consider setting a daily limit."
        else -> "${formatDuration(minutes)} today — a high usage day. Consider a " +
                "short digital detox this evening to help your brain recover."
    }
}
fun generateNotificationInsight(count: Int): String {
    if (count < 0) return "No notification data yet — this will populate after your next session."
    return when {
        count == 0   -> "No notifications received today. Either the apps were quiet or notification access was just granted."
        count <= 5   -> "Light notification day with $count pings. Your apps aren't demanding much attention today."
        count <= 15  -> "$count notifications today. Moderate pull — consider checking apps on your own schedule instead."
        count <= 30  -> "$count notifications today. These apps are actively competing for your attention."
        else         -> "$count notifications today. High interruption load — your focus is being fragmented frequently."
    }
}
fun generateScrollInsight(scrollSpeed: Float): String {
    return when {
        scrollSpeed <= 0 -> "No scroll data recorded yet. Make sure the " +
                "Accessibility Service is enabled."
        scrollSpeed < 10 -> "Low scroll speed — you're engaging deliberately " +
                "with content rather than mindlessly scrolling. Good sign."
        scrollSpeed < 30 -> "Moderate scroll speed. You're browsing actively " +
                "but not in a compulsive pattern."
        else -> "High scroll speed at ${scrollSpeed.toInt()} scrolls per minute. " +
                "Fast scrolling is a key sign of doomscrolling driven by " +
                "persuasive design. Try pausing intentionally between posts."
    }
}

fun generateVideoInsight(videoCount: Int): String {
    return when {
        videoCount == 0 -> "No video consumption recorded today."
        videoCount < 20 -> "An estimated $videoCount videos today — " +
                "a relatively intentional consumption day."
        videoCount < 60 -> "An estimated $videoCount videos today. That's roughly " +
                "one every few minutes. Consider whether each one was intentional."
        else -> "An estimated $videoCount videos today. High volume video " +
                "consumption is strongly linked to reduced attention span over time."
    }
}

fun generateWeeklyWellnessInsight(avgScore: Int): String {
    return when {
        avgScore >= 75 -> "Your average wellness score this week is $avgScore/100 — " +
                "excellent. You're maintaining healthy digital habits."
        avgScore >= 50 -> "Your average wellness score is $avgScore/100 — moderate. " +
                "There are some patterns worth addressing, particularly around " +
                "session length and passive consumption."
        else -> "Your average wellness score is $avgScore/100 — this suggests some " +
                "intensive usage patterns this week. Focus on shorter, more " +
                "intentional sessions."
    }
}

fun generatePassiveInsight(passive: Int, autoplay: Int): String {
    val total = passive + autoplay
    return "You had $total passive or autoplay sessions this week. " +
            "These are sessions where content plays without active engagement — " +
            "a key mechanism of persuasive design. Try opening apps with a " +
            "specific intention rather than out of habit."
}

fun generateLateNightInsight(lateNight: Int): String {
    return "You had $lateNight late night social media sessions this week. " +
            "Blue light exposure and stimulating content before sleep is linked " +
            "to reduced sleep quality and next-day focus. Consider a screen " +
            "cutoff time of 10pm."
}

fun generatePatternInsight(best: DayStats, worst: DayStats): String {
    val diff = worst.totalMinutes - best.totalMinutes
    return if (diff > 60) {
        "There's a ${formatDuration(diff)} difference between your best and worst " +
                "day — suggesting your usage varies significantly. " +
                "Try to bring your high days closer to your best day pattern."
    } else {
        "Your usage is fairly consistent across the week. " +
                "Small daily improvements will compound over time."
    }
}