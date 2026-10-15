package com.dynamicisland.android.model

enum class CallState {
    IDLE,
    RINGING,
    OFFHOOK // Active in-call
}

/**
 * Encapsulates telephony and incoming/outgoing call details.
 */
data class CallInfo(
    val callerName: String? = null,
    val callerNumber: String? = null,
    val state: CallState = CallState.IDLE,
    val callDurationSeconds: Long = 0L
) {
    val isIncomingRinging: Boolean
        get() = state == CallState.RINGING

    val isInCall: Boolean
        get() = state == CallState.OFFHOOK

    val formattedDuration: String
        get() {
            val minutes = callDurationSeconds / 60
            val seconds = callDurationSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}
