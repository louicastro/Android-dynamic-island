package com.dynamicisland.android.model

import android.app.PendingIntent

/**
 * Encapsulates an active timer/stopwatch state with actionable intents.
 * Handles known countdowns and open-ended/unknown durations without fabricating times.
 */
data class TimerInfo(
    val label: String = "Timer",
    val totalSeconds: Long = -1L,
    val remainingSeconds: Long = -1L, // -1L if duration could not be parsed from notification
    val isRunning: Boolean = true,
    val packageName: String? = null,
    val rawTimeText: String? = null,
    val pauseIntent: PendingIntent? = null,
    val resumeIntent: PendingIntent? = null,
    val resetIntent: PendingIntent? = null,
    val contentIntent: PendingIntent? = null
) {
    val isUnknownDuration: Boolean
        get() = remainingSeconds < 0L

    val formattedRemaining: String
        get() {
            if (isUnknownDuration) {
                return rawTimeText ?: label
            }
            val hours = remainingSeconds / 3600
            val mins = (remainingSeconds % 3600) / 60
            val secs = remainingSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, mins, secs)
            } else {
                "%d:%02d".format(mins, secs)
            }
        }

    val progressFraction: Float
        get() {
            if (totalSeconds > 0L && remainingSeconds >= 0L) {
                return (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()
            }
            return 0f
        }

    val hasExecutableControls: Boolean
        get() = pauseIntent != null || resumeIntent != null || resetIntent != null || contentIntent != null
}
