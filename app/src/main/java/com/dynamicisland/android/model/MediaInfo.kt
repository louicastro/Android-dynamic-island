package com.dynamicisland.android.model

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Encapsulates playback metadata and control states from active MediaSessions.
 * Supports timestamped position estimation for smooth progress scrubbing.
 */
data class MediaInfo(
    val title: String,
    val artist: String,
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val packageName: String = "",
    val playbackSpeed: Float = 1.0f,
    val lastPositionUpdateTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Estimates current playback position based on elapsed time since the
     * last controller update. Prevents the progress bar from freezing between callbacks.
     */
    fun estimatedPositionMs(): Long {
        if (!isPlaying || durationMs <= 0L) return currentPositionMs
        val elapsed = (System.currentTimeMillis() - lastPositionUpdateTimestamp).coerceAtLeast(0L)
        val estimated = currentPositionMs + (elapsed * playbackSpeed).toLong()
        return estimated.coerceIn(0L, durationMs)
    }

    val progress: Float
        get() {
            val pos = estimatedPositionMs()
            return if (durationMs > 0L) (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        }
}
