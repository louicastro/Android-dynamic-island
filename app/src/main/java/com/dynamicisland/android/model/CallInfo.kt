package com.dynamicisland.android.model

import android.app.PendingIntent
import android.graphics.Bitmap

enum class CallState {
    IDLE,
    RINGING,
    OFFHOOK // Active in-call
}

/**
 * Encapsulates telephony and incoming/outgoing call details for both
 * cellular calls and VoIP applications (WhatsApp, Telegram, Meet, etc.).
 */
data class CallInfo(
    val callerName: String? = null,
    val callerNumber: String? = null,
    val state: CallState = CallState.IDLE,
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

    val formattedDuration: String
        get() {
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
