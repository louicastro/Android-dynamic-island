package com.dynamicisland.android.model

import android.app.PendingIntent

/**
 * Encapsulates an active timer/stopwatch state with actionable intents.
 */
data class TimerInfo(
    val label: String = "Timer",
    val totalSeconds: Long = 300L,
    val remainingSeconds: Long = 245L,
    val isRunning: Boolean = true,
    val packageName: String? = null,
    val pauseIntent: PendingIntent? = null,
    val resumeIntent: PendingIntent? = null,
    val resetIntent: PendingIntent? = null,
    val contentIntent: PendingIntent? = null
) {
    val formattedRemaining: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return "%d:%02d".format(mins, secs)
        }

    val progressFraction: Float
        get() = if (totalSeconds > 0) (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat() else 0f

    val hasExecutableControls: Boolean
        get() = pauseIntent != null || resumeIntent != null || contentIntent != null
}
