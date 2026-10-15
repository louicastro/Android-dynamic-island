package com.dynamicisland.android.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.SystemClock
import java.util.UUID

enum class CallState {
    IDLE,
    RINGING,
    OFFHOOK // Active in-call
}

enum class CallSource {
    CELLULAR,
    VOIP
}

/**
 * Encapsulates telephony and incoming/outgoing call details for both
 * cellular calls and VoIP applications (WhatsApp, Telegram, Meet, etc.).
 *
 * Tracks call origin (source), monotonic start timestamp for drift-free
 * duration calculation, and executable Telecom/PendingIntent actions.
 */
data class CallInfo(
    val callId: String = UUID.randomUUID().toString(),
    val source: CallSource = CallSource.CELLULAR,
    val callerName: String? = null,
    val callerNumber: String? = null,
    val state: CallState = CallState.IDLE,
    val callStartElapsedRealtime: Long = 0L,
    val callDurationSeconds: Long = 0L,
    val isVoip: Boolean = false,
    val appName: String? = null,
    val avatar: Bitmap? = null,
    val answerIntent: PendingIntent? = null,
    val declineIntent: PendingIntent? = null
) {
    val isIncomingRinging: Boolean
        get() = state == CallState.RINGING

    val isInCall: Boolean
        get() = state == CallState.OFFHOOK

    val hasExecutableActions: Boolean
        get() = answerIntent != null || declineIntent != null

    /**
     * Derives elapsed call duration using the monotonic start timestamp if available,
     * preventing resets from state recomputation or system clock changes.
     */
    val currentDurationSeconds: Long
        get() {
            if (callStartElapsedRealtime > 0L && state == CallState.OFFHOOK) {
                val elapsedMs = System.currentTimeMillis() - callStartElapsedRealtime
                return (elapsedMs / 1000L).coerceAtLeast(0L)
            }
            return callDurationSeconds
        }

    val formattedDuration: String
        get() {
            val secondsTotal = currentDurationSeconds
            val minutes = secondsTotal / 60
            val seconds = secondsTotal % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
