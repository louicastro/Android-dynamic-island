package com.dynamicisland.android.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallSource
import com.dynamicisland.android.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CallActionResult(
    val success: Boolean,
    val errorReason: String? = null
)

/**
 * Tracks cellular telephony call states and interfaces with TelecomManager
 * to answer and end calls when permitted.
 *
 * Implements:
 * - Monotonic timestamp-based call duration using SystemClock.elapsedRealtime()
 * - Preservation of known contact names and avatars across Android 12+ null number callbacks
 * - Explicit permission checks and actionable error reporting for call control APIs
 */
class CallTracker(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val scope = CoroutineScope(Dispatchers.Main)
    private var durationJob: Job? = null
    private var callStartMonotonicMs: Long = 0L

    @Suppress("DEPRECATION")
    private var legacyListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null

    init {
        instance = this
    }

    fun start() {
        val tm = telephonyManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerModernCallback(tm)
        } else {
            registerLegacyListener(tm)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(tm: TelephonyManager) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleStateChange(state, null)
            }
        }
        telephonyCallback = callback
        try {
            tm.registerTelephonyCallback(context.mainExecutor, callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted for TelephonyCallback: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyListener(tm: TelephonyManager) {
        legacyListener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleStateChange(state, phoneNumber)
            }
        }
        try {
            tm.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted for PhoneStateListener: ${e.message}")
        }
    }

    private fun handleStateChange(rawState: Int, phoneNumber: String?) {
        val callState = when (rawState) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
            else -> CallState.IDLE
        }

        if (callState == CallState.OFFHOOK) {
            if (callStartMonotonicMs == 0L) {
                callStartMonotonicMs = System.currentTimeMillis()
            }
            startDurationCounter(phoneNumber)
        } else {
            stopDurationCounter()
            callStartMonotonicMs = 0L

            if (callState == CallState.IDLE) {
                IslandEventBus.removeCallBySource(CallSource.CELLULAR)
            } else {
                // Ringing state
                val existingCall = IslandEventBus.callState.value
                val resolvedName = resolveContactName(phoneNumber)
                    ?: phoneNumber
                    ?: if (existingCall?.source == CallSource.CELLULAR && !existingCall.callerName.isNullOrBlank()) existingCall.callerName
                    else "Incoming Call"

                val callInfo = CallInfo(
                    source = CallSource.CELLULAR,
                    callerName = resolvedName,
                    callerNumber = phoneNumber ?: existingCall?.callerNumber,
                    state = CallState.RINGING,
                    callStartElapsedRealtime = 0L,
                    callDurationSeconds = 0L,
                    isVoip = false,
                    appName = "Phone",
                    avatar = existingCall?.avatar
                )
                IslandEventBus.updateCall(callInfo)
            }
        }
    }

    private fun resolveContactName(phoneNumber: String?): String? {
        phoneNumber ?: return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) it.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name for $phoneNumber", e)
            null
        }
    }

    private fun startDurationCounter(number: String?) {
        durationJob?.cancel()
        val existing = IslandEventBus.callState.value
        val resolvedName = resolveContactName(number)
            ?: number
            ?: if (existing != null && !existing.callerName.isNullOrBlank()) existing.callerName
            else "Active Call"

        val avatar = existing?.avatar

        durationJob = scope.launch {
            while (isActive) {
                val elapsedSeconds = if (callStartMonotonicMs > 0L) {
                    (SystemClock.elapsedRealtime() - callStartMonotonicMs) / 1000L
                } else 0L

                val callInfo = CallInfo(
                    source = CallSource.CELLULAR,
                    callerName = resolvedName,
                    callerNumber = number ?: existing?.callerNumber,
                    state = CallState.OFFHOOK,
                    callStartElapsedRealtime = callStartMonotonicMs,
                    callDurationSeconds = elapsedSeconds,
                    isVoip = false,
                    appName = "Phone",
                    avatar = avatar
                )
                IslandEventBus.updateCall(callInfo)
                delay(1000)
            }
        }
    }

    private fun stopDurationCounter() {
        durationJob?.cancel()
        durationJob = null
    }

    /**
     * Answers an incoming ringing cellular call via TelecomManager.
     * Returns an explicit [CallActionResult] with failure details if permissions are missing.
     */
    @Suppress("DEPRECATION")
    fun answerCall(): CallActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return CallActionResult(false, "Answering calls is unsupported on Android versions below 8.0")
        }

        val tm = telecomManager
            ?: return CallActionResult(false, "TelecomManager service is unavailable")

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return CallActionResult(false, "ANSWER_PHONE_CALLS permission not granted in system settings")
        }

        return try {
            tm.acceptRingingCall()
            CallActionResult(true)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException accepting call", e)
            CallActionResult(false, "Security restriction prevented call answer: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call", e)
            CallActionResult(false, "Failed to answer call: ${e.message}")
        }
    }

    /**
     * Ends or declines an active or ringing cellular call via TelecomManager.
     */
    @Suppress("DEPRECATION")
    fun endCall(): CallActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return CallActionResult(false, "Ending calls via Telecom requires Android 9.0 (API 28)+")
        }

        val tm = telecomManager
            ?: return CallActionResult(false, "TelecomManager service is unavailable")

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return CallActionResult(false, "ANSWER_PHONE_CALLS permission not granted in system settings")
        }

        return try {
            val ended = tm.endCall()
            if (ended) {
                stopDurationCounter()
                callStartMonotonicMs = 0L
                IslandEventBus.removeCallBySource(CallSource.CELLULAR)
                CallActionResult(true)
            } else {
                CallActionResult(false, "Telecom service could not end the active call")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException ending call", e)
            CallActionResult(false, "Security restriction prevented call termination: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call", e)
            CallActionResult(false, "Failed to end call: ${e.message}")
        }
    }

    fun stop() {
        stopDurationCounter()
        callStartMonotonicMs = 0L
        val tm = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    tm.unregisterTelephonyCallback(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering telephony callback: ${e.message}")
                }
            }
        } else {
            legacyListener?.let {
                @Suppress("DEPRECATION")
                tm.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    companion object {
        private const val TAG = "CallTracker"

        @Volatile
        private var instance: CallTracker? = null

        fun getInstance(context: Context): CallTracker {
            return instance ?: synchronized(this) {
                instance ?: CallTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
