// ScrollTrackingService.kt
package com.dreammap.attentionshieldlogger

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ScrollTrackingService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase

    // Deduplication
    private val lastScrollTime = ConcurrentHashMap<String, Long>()
    private val lastTapTime = ConcurrentHashMap<String, Long>()
    private val scrollDebounceMs = 200L
    private val lastScrollPosition = ConcurrentHashMap<String, Int>()
    private val tapDebounceMs = 300L

    private val targetApps = setOf(
        "com.zhiliaoapp.musically",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android"
    )

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        Log.d("SCROLL_DEBUG", "Service onCreate")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            packageNames = null
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        Log.d("SCROLL_DEBUG", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in targetApps) return

        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (shouldCountScroll(pkg, now, event)) {
                    logScrollEvent(pkg, now, event)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (shouldCountTap(pkg, now)) {
                    logTapEvent(pkg, now)
                }
            }
        }
    }

    private val gestureActive = AtomicBoolean(false)
    private val scrollsInCurrentGesture = AtomicInteger(0)

    private fun shouldCountScroll(pkg: String, now: Long, event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        val className = source.className?.toString() ?: return false

        // Check if it's a valid scrollable view
        val isRecyclerView = className.contains("RecyclerView")
        val isListView = className.contains("ListView")
        val isViewPager = className.contains("ViewPager")

        if (!isRecyclerView && !isListView && !isViewPager) return false

        // Size check
        val rect = android.graphics.Rect()
        source.getBoundsInScreen(rect)
        val screenHeight = resources.displayMetrics.heightPixels
        if (rect.height() < screenHeight * 0.4) return false

        // Time debounce
        val lastTime = lastScrollTime[pkg] ?: 0L
        if (now - lastTime < 800) return false

        // Distance check - DIFFERENT for ViewPager
        if (isViewPager) {
            // ViewPager: Use index change instead of scrollY
            val currentIndex = event.toIndex
            val lastIndex = lastScrollPosition[pkg] ?: -1

            if (currentIndex < 0 || currentIndex == lastIndex) {
                Log.d("SCROLL_DEBUG", "📄 SKIP ViewPager: same page ($currentIndex)")
                return false
            }

            lastScrollTime[pkg] = now
            lastScrollPosition[pkg] = currentIndex
            Log.d("SCROLL_DEBUG", "✅ COUNT ViewPager page: $lastIndex → $currentIndex")
            return true

        } else {
            // RecyclerView/ListView: Use scrollY distance
            val currentY = event.scrollY
            val lastY = lastScrollPosition[pkg] ?: 0

            if (Math.abs(currentY - lastY) < 150) return false

            lastScrollTime[pkg] = now
            lastScrollPosition[pkg] = currentY
            Log.d("SCROLL_DEBUG", "✅ COUNT scroll: ${Math.abs(currentY - lastY)}px")
            return true
        }
    }

    private fun shouldCountTap(pkg: String, now: Long): Boolean {
        val last = lastTapTime[pkg] ?: 0L
        return if (now - last >= tapDebounceMs) {
            lastTapTime[pkg] = now
            true
        } else false
    }

    private fun logScrollEvent(pkg: String, timestamp: Long, accessibilityEvent: AccessibilityEvent) {
        scope.launch {
            try {
                val source = accessibilityEvent.source
                val className = source?.className?.toString() ?: ""
                val isViewPager = className.contains("ViewPager")

                val scrollDelta = if (isViewPager) {
                    1.0f  // One page = one content piece
                } else {
                    getScrollDelta(pkg, accessibilityEvent)
                }

                val scrollEvent = ScrollEvent(
                    packageName = pkg,
                    timestamp = timestamp,
                    eventType = "SCROLL",
                    scrollDelta = scrollDelta,
                    confidence = if (isViewPager) 0.95f else calculateConfidence(accessibilityEvent)
                )
                database.scrollEventDao().insert(scrollEvent)
            } catch (e: Exception) {
                Log.e("SCROLL_DEBUG", "Failed to log scroll", e)
            }
        }
    }

    private fun logTapEvent(pkg: String, timestamp: Long) {
        scope.launch {
            try {
                val scrollEvent = ScrollEvent(
                    packageName = pkg,
                    timestamp = timestamp,
                    eventType = "TAP",
                    scrollDelta = 0f,
                    confidence = 0.9f
                )
                database.scrollEventDao().insert(scrollEvent)
            } catch (e: Exception) {
                Log.e("SCROLL_DEBUG", "Failed to log tap", e)
            }
        }
    }

    private fun getScrollDelta(pkg: String, event: AccessibilityEvent): Float {
        val currentY = event.scrollY
        val lastY = lastScrollPosition[pkg] ?: currentY
        return Math.abs(currentY - lastY).toFloat()
    }

    private fun calculateConfidence(event: AccessibilityEvent): Float {
        var confidence = 0.5f

        // Higher confidence for adapter-based views
        if (event.fromIndex >= 0 && event.toIndex >= 0) {
            confidence += 0.3f
        }

        // Use scrollDeltaY on API 28+, fall back to scrollY position diff on API 24-27
        val scrollDelta = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            kotlin.math.abs(event.scrollDeltaY)  // ✅ Safe on API 28+
        } else {
            Math.abs(event.scrollY - (lastScrollPosition[event.packageName?.toString() ?: ""] ?: 0))
        }

        if (scrollDelta > 50) {
            confidence += 0.2f
        }

        return confidence.coerceIn(0f, 1f)
    }

    override fun onInterrupt() {
        Log.d("SCROLL_DEBUG", "Service interrupted")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        Log.d("SCROLL_DEBUG", "Service onDestroy")
    }
}