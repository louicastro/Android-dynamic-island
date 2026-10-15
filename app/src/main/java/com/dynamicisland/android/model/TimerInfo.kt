package com.dynamicisland.android.model

data class TimerInfo(
    val label: String = "Timer",
    val totalSeconds: Long = 300L,
    val remainingSeconds: Long = 245L,
    val isRunning: Boolean = true
) {
    val formattedRemaining: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            return "%d:%02d".format(mins, secs)
        }

    val progressFraction: Float
        get() = if (totalSeconds > 0) (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat() else 0f
}
