package com.dynamicisland.android.service

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.IslandEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallTracker(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.WINDOW_SERVICE).run {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var durationJob: Job? = null
    private var currentDuration = 0L

    private var legacyListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null

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
            // Permission not yet granted
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
            // Permission not granted
        }
    }

    private fun handleStateChange(rawState: Int, phoneNumber: String?) {
        val callState = when (rawState) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
            else -> CallState.IDLE
        }

        if (callState == CallState.OFFHOOK) {
            startDurationCounter(phoneNumber)
        } else {
            stopDurationCounter()
            val callInfo = CallInfo(
                callerName = phoneNumber ?: "Incoming Call",
                callerNumber = phoneNumber,
                state = callState,
                callDurationSeconds = 0L
            )
            IslandEventBus.emit(IslandEvent.CallUpdate(callInfo))
        }
    }

    private fun startDurationCounter(number: String?) {
        durationJob?.cancel()
        currentDuration = 0L
        durationJob = scope.launch {
            while (isActive) {
                val callInfo = CallInfo(
                    callerName = number ?: "Active Call",
                    callerNumber = number,
                    state = CallState.OFFHOOK,
                    callDurationSeconds = currentDuration
                )
                IslandEventBus.emit(IslandEvent.CallUpdate(callInfo))
                delay(1000)
                currentDuration++
            }
        }
    }

    private fun stopDurationCounter() {
        durationJob?.cancel()
        durationJob = null
        currentDuration = 0L
    }

    fun stop() {
        stopDurationCounter()
        val tm = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    tm.unregisterTelephonyCallback(it)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } else {
            legacyListener?.let {
                @Suppress("DEPRECATION")
                tm.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
}
