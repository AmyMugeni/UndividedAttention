package com.dreammap.attentionshieldlogger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OverviewTab(todayStats: TodayStats?, weeklyStats: WeeklyStats?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Today's Overview",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = java.text.SimpleDateFormat(
                "EEEE, MMM d",
                java.util.Locale.getDefault()
            ).format(java.util.Date()),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ASI Card — shown even when todayStats is null
        todayStats?.asiResult?.let { asi ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (asi.level) {
                        ASILevel.HEALTHY -> MaterialTheme.colorScheme.primaryContainer
                        ASILevel.MODERATE -> MaterialTheme.colorScheme.secondaryContainer
                        ASILevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                        ASILevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Attention Saturation Index",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${asi.score} / 100",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = asi.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when (asi.level) {
                                    ASILevel.HEALTHY -> MaterialTheme.colorScheme.primary
                                    ASILevel.MODERATE -> MaterialTheme.colorScheme.secondary
                                    ASILevel.HIGH -> MaterialTheme.colorScheme.error
                                    ASILevel.CRITICAL -> MaterialTheme.colorScheme.error
                                }
                            )
                        }

                        // Circular score indicator
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(80.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { asi.score / 100f },
                                modifier = Modifier.size(80.dp),
                                strokeWidth = 8.dp,
                                color = when (asi.level) {
                                    ASILevel.HEALTHY -> MaterialTheme.colorScheme.primary
                                    ASILevel.MODERATE -> MaterialTheme.colorScheme.secondary
                                    ASILevel.HIGH -> MaterialTheme.colorScheme.error
                                    ASILevel.CRITICAL -> MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                text = "${asi.score}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = asi.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    // Dimension breakdown
                    Text(
                        text = "Dimension breakdown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ASIComponentRow(
                        label = "Intensity (α=0.30)",
                        score = asi.intensityScore,
                        weight = 0.30f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ASIComponentRow(
                        label = "Fragmentation (β=0.25)",
                        score = asi.fragmentationScore,
                        weight = 0.25f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ASIComponentRow(
                        label = "Compulsion (γ=0.25)",
                        score = asi.compulsionScore,
                        weight = 0.25f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ASIComponentRow(
                        label = "Disruption (δ=0.20)",
                        score = asi.disruptionScore,
                        weight = 0.20f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Dominant dimension: ${asi.dominantDimension}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (todayStats == null) {
            // No data yet
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
                        text = "No sessions recorded today yet.",
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Open TikTok, Instagram, or YouTube and come back.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Screen Time",
                    value = formatDuration(todayStats.totalScreenTimeMinutes),
                    subtitle = "${todayStats.sessionCount} sessions"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Most Used",
                    value = todayStats.mostUsedApp,
                    subtitle = "today"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Notifications",
                    value = if (todayStats.notificationCount >= 0)
                        todayStats.notificationCount.toString()
                    else "—",
                    subtitle = "received today"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Videos Watched",
                    value = todayStats.estimatedVideosWatched.values.sum().toString(),
                    subtitle = "estimated total"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Estimated videos per platform
            if (todayStats.estimatedVideosWatched.isNotEmpty()) {
                Text(
                    text = "Estimated Videos per Platform",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                todayStats.estimatedVideosWatched.forEach { (app, count) ->
                    VideoEstimateRow(app = app, count = count)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

// Weekly wellness scores
            if (weeklyStats != null) {
                Text(
                    text = "This Week",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Best and worst day
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    weeklyStats.bestDay?.let { best ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Best Day",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = best.dayLabel,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Score: ${best.wellnessScore}/100",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatDuration(best.totalMinutes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    weeklyStats.worstDay?.let { worst ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Worst Day",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = worst.dayLabel,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Score: ${worst.wellnessScore}/100",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = formatDuration(worst.totalMinutes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Daily wellness scores for the week
                Text(
                    text = "Daily Wellness Scores",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                weeklyStats.days.forEach { day ->
                    DayWellnessRow(day = day)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VideoEstimateRow(app: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = app,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "~$count videos",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "estimated",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatDuration(minutes: Float): String {
    return if (minutes < 60) {
        "${minutes.toInt()}m"
    } else {
        val hours = (minutes / 60).toInt()
        val mins = (minutes % 60).toInt()
        "${hours}h ${mins}m"
    }
}

@Composable
fun DayWellnessRow(day: DayStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.dayLabel,
            fontSize = 13.sp,
            modifier = Modifier.width(40.dp)
        )
        LinearProgressIndicator(
            progress = { day.wellnessScore / 100f },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = when {
                day.wellnessScore >= 70 -> MaterialTheme.colorScheme.primary
                day.wellnessScore >= 40 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
        )
        Text(
            text = if (day.sessionCount > 0) "${day.wellnessScore}" else "-",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.End
        )
    }
}
@Composable
fun ASIComponentRow(label: String, score: Int, weight: Float){
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(160.dp)
        )
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = when {
                score <= 30 -> MaterialTheme.colorScheme.primary
                score <= 60 -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.error
            }
        )
        Text(
            text = "$score",
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = "×${weight}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}