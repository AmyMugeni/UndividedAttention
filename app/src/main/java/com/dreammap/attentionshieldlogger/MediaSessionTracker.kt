package com.dreammap.attentionshieldlogger

import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

class MediaSessionTracker(private val context: Context) {

    private var mediaSessionManager: MediaSessionManager? = null
    private var currentPackage: String = ""

    // Per session tracking
    private var sessionVideoCount: Int = 0
    private var autoplayVideoCount: Int = 0
    private var totalPlaybackMs: Long = 0L
    private var playbackStartTime: Long = 0L
    private var isPlaying: Boolean = false
    private var lastInteractionTime: Long = 0L
    private var wasMediaDetected: Boolean = false

    // Threshold — if no user interaction within this window
    // before a content switch, it counts as autoplay
    private val autoplayThresholdMs = 3000L

    private val handler = Handler(Looper.getMainLooper())

    // Active session callback
    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            controllers?.forEach { controller ->
                val pkg = controller.packageName
                if (pkg !in getTargetPackages()) return@forEach

                wasMediaDetected = true

                controller.registerCallback(object :
                    android.media.session.MediaController.Callback() {

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        state ?: return
                        val now = System.currentTimeMillis()

                        when (state.state) {
                            PlaybackState.STATE_PLAYING -> {
                                if (!isPlaying) {
                                    // New content started
                                    sessionVideoCount++

                                    // Check if this was autoplay
                                    val timeSinceInteraction = now - lastInteractionTime
                                    if (timeSinceInteraction > autoplayThresholdMs &&
                                        lastInteractionTime > 0
                                    ) {
                                        autoplayVideoCount++
                                    }

                                    playbackStartTime = now
                                    isPlaying = true
                                }
                            }

                            PlaybackState.STATE_PAUSED,
                            PlaybackState.STATE_STOPPED -> {
                                if (isPlaying && playbackStartTime > 0) {
                                    totalPlaybackMs += now - playbackStartTime
                                    isPlaying = false
                                }
                            }
                        }
                    }
                }, handler)
            }
        }

    fun startTracking(packageName: String) {
        currentPackage = packageName
        resetCounters()

        try {
            mediaSessionManager = context.getSystemService(
                Context.MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            // Register the listener so we actually receive media events
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                null
            )
        } catch (e: Exception) {
            // MediaSession not available on this device
        }
    }

    fun notifyUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun getSessionData(): MediaSessionData {
        // If media is still playing when session ends, count remaining time
        if (isPlaying && playbackStartTime > 0) {
            totalPlaybackMs += System.currentTimeMillis() - playbackStartTime
        }

        return MediaSessionData(
            actualVideoCount = sessionVideoCount,
            autoplayVideoCount = autoplayVideoCount,
            totalPlaybackMinutes = totalPlaybackMs / 60000f,
            wasMediaDetected = wasMediaDetected
        )
    }

    fun resetCounters() {
        sessionVideoCount = 0
        autoplayVideoCount = 0
        totalPlaybackMs = 0L
        playbackStartTime = 0L
        isPlaying = false
        lastInteractionTime = 0L
        wasMediaDetected = false
    }

    fun stopTracking() {
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (e: Exception) { }
        resetCounters()
        currentPackage = ""
    }

    private fun getTargetPackages() = listOf(
        "com.zhiliaoapp.musically",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android"
    )

    companion object {
        var instance: MediaSessionTracker? = null
    }
}

data class MediaSessionData(
    val actualVideoCount: Int,
    val autoplayVideoCount: Int,
    val totalPlaybackMinutes: Float,
    val wasMediaDetected: Boolean
)