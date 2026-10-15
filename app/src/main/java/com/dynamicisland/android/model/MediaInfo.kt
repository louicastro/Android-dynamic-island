package com.dynamicisland.android.model

import android.graphics.Bitmap

/**
 * Encapsulates playback metadata and control states from active MediaSessions.
 */
data class MediaInfo(
    val title: String,
    val artist: String,
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val packageName: String = ""
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
