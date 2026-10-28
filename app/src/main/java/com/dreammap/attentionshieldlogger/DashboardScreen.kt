// DashboardScreen.kt
package com.dreammap.attentionshieldlogger

import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.unit.dp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var todayStats by remember { mutableStateOf<TodayStats?>(null) }
    var weeklyStats by remember { mutableStateOf<WeeklyStats?>(null) }
    var allSessions by remember { mutableStateOf<List<SessionEvent>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(context)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val calendar = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val weekStart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(calendar.time)

                val weekEndCalendar = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                }
                val weekEnd = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(weekEndCalendar.time)

                val todaySessions = db.sessionDao().getByDate(today)
                val weeklySessions = db.sessionDao().getWeekSessions(weekStart, weekEnd)
                val all = db.sessionDao().getAll()

                withContext(Dispatchers.Main) {
                    allSessions = all

                    if (todaySessions.isNotEmpty()) {
                        val totalTime = todaySessions.sumOf { it.durationMinutes.toDouble() }.toFloat()

                        val mostUsed = todaySessions
                            .groupBy { it.appLabel }
                            .maxByOrNull { it.value.sumOf { s -> s.durationMinutes.toDouble() } }
                            ?.key ?: "None"

                        val totalNotifications = todaySessions.filter { it.notificationCount >= 0 }.sumOf { it.notificationCount.toLong() }.toInt()

                        val estimatedVideos = todaySessions
                            .groupBy { it.appLabel }
                            .mapValues { (_, sessions) ->
                                sessions.sumOf { session ->
                                    estimateVideoCount(session).first.toLong()  // Explicit Long
                                }.toInt()
                            }
                            .filter { it.value > 0 }

                        val asiResult = ASICalculator.calculate(todaySessions)

                        todayStats = TodayStats(
                            totalScreenTimeMinutes = totalTime,
                            estimatedVideosWatched = estimatedVideos,
                            mostUsedApp = mostUsed,
                            notificationCount = totalNotifications,
                            sessionCount = todaySessions.size,
                            asiResult = asiResult
                        )
                    } else {
                        todayStats = null
                    }

                    weeklyStats = computeWeeklyStats(weeklySessions)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                refreshData()
                isRefreshing = false
            }
        }
    )

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("UndividedAttention") },
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        scope.launch {
                            refreshData()
                            isRefreshing = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync now")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Overview") },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "Per App"
                        )
                    },
                    label = { Text("Per App") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Insights") },
                    label = { Text("Insights") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            when (selectedTab) {
                0 -> OverviewTab(todayStats, weeklyStats)
                1 -> PerAppTab(allSessions, weeklyStats)
                2 -> InsightsTab(todayStats, weeklyStats)
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

