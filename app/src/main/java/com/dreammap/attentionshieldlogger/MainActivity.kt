package com.dreammap.attentionshieldlogger

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schedule logging at startup if permissions are already granted
        if (hasUsagePermission(this) && hasAccessibilityPermission(this) && hasNotificationPermission(this)) {
            scheduleLogging(this)
        }

        // Run all one-time data fixes on startup
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getInstance(this@MainActivity)
                    val userId = UserPreferences.getUserId(this@MainActivity)

                    // 1 — Remove duplicate sessions
                    db.sessionDao().deleteDuplicates()

                    // 2 — Backfill sessionHour for old sessions
                    val unknownHourSessions = db.sessionDao().getSessionsWithUnknownHour()
                    unknownHourSessions.forEach { session ->
                        val hour = Calendar.getInstance().apply {
                            timeInMillis = session.sessionStart
                        }.get(Calendar.HOUR_OF_DAY)
                        db.sessionDao().updateSessionHour(session.id, hour)
                    }

                    // 3 — Backfill userId for old sessions
                    val emptyUserIdSessions = db.sessionDao().getSessionsWithEmptyUserId()
                    emptyUserIdSessions.forEach { id ->
                        db.sessionDao().updateUserId(id, userId)
                    }

                } catch (e: Exception) {
                    // Best-effort — never crash on startup
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    var bothGranted by remember {
                        mutableStateOf(
                            hasUsagePermission(context) && hasAccessibilityPermission(context) && hasNotificationPermission(this)
                        )
                    }
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                bothGranted = hasUsagePermission(context) &&
                                        hasAccessibilityPermission(context)
                                // Also schedule here in case permissions were just granted
                                if (bothGranted) scheduleLogging(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (bothGranted) {
                        DashboardScreen()
                    } else {
                        PermissionScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(hasUsagePermission(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(hasAccessibilityPermission(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = hasUsagePermission(context)
                hasAccessibilityPermission = hasAccessibilityPermission(context)
                hasNotificationPermission = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(
            text = "AttentionShield",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Understand your digital habits",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        PermissionCard(
            title = "Usage Access",
            description = "Tracks which apps you use and for how long.",
            granted = hasUsagePermission,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Notification Access",
            description = "Detects notifications that may trigger compulsive app checking.",
            granted = hasNotificationPermission,
            onGrant = {
                context.startActivity(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Accessibility Service",
            description = "Tracks scroll and tap interactions within social media apps.",
            granted = hasAccessibilityPermission,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Please grant all permissions to start logging.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Device-specific battery optimisation guidance
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isOppo = manufacturer.contains("oppo") || manufacturer.contains("realme")
        val isSamsung = manufacturer.contains("samsung")

        if (isOppo || isSamsung) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isOppo) "OPPO/ColorOS detected" else "Samsung OneUI detected",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = if (isOppo) {
                            "To ensure accurate tracking:\n" +
                                    "1. Go to Settings → Battery → Battery Optimisation\n" +
                                    "2. Find AttentionShield → set to 'Don't optimise'\n" +
                                    "3. Settings → Privacy → Special App Access\n" +
                                    "4. Enable 'Run in background' for AttentionShield"
                        } else {
                            "To ensure accurate tracking:\n" +
                                    "1. Go to Settings → Apps → AttentionShield\n" +
                                    "2. Tap Battery → set to 'Unrestricted'\n" +
                                    "3. Settings → Device Care → Battery\n" +
                                    "4. Add AttentionShield to 'Never sleeping apps'"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Debug button — remove once testing is complete
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                                as android.app.usage.UsageStatsManager
                        val end = System.currentTimeMillis()
                        val start = end - (7 * 24 * 60 * 60 * 1000L)
                        val events = usm.queryEvents(start, end)
                        val event = android.app.usage.UsageEvents.Event()
                        var totalEvents = 0
                        var targetEvents = 0
                        val foundPackages = mutableSetOf<String>()

                        val targetApps = listOf(
                            "com.zhiliaoapp.musically",
                            "com.instagram.android",
                            "com.google.android.youtube",
                            "com.twitter.android",
                            "com.facebook.katana",
                            "com.snapchat.android"
                        )

                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            totalEvents++
                            foundPackages.add(event.packageName)
                            if (event.packageName in targetApps) {
                                targetEvents++
                            }
                        }

                        foundPackages.forEach { pkg ->
                            android.util.Log.d("AttentionShield", "Package: $pkg")
                        }

                        UsageLogger(context).logRecentSessions()
                        val count = AppDatabase.getInstance(context)
                            .sessionDao().getCount()

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Total events: $totalEvents | " +
                                        "Target hits: $targetEvents | " +
                                        "DB sessions: $count",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Debug: Force Sync")
        }
    }
}
fun hasNotificationPermission(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    val componentName = ComponentName(context, NotificationTracker::class.java)
    return enabledListeners.contains(componentName.flattenToString())
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (granted) {
                    Text(
                        text = "✓",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Button(onClick = onGrant) {
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

fun hasUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasAccessibilityPermission(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    // Use splitter for robust parsing across all OEM implementations
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServices)
    while (splitter.hasNext()) {
        val component = splitter.next()
        if (component.contains(context.packageName, ignoreCase = true)) {
            return true
        }
    }
    return false
}