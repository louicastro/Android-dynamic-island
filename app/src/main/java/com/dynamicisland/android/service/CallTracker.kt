package com.dynamicisland.android.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tracks cellular telephony call states and interfaces with TelecomManager
 * to answer and end calls when permitted.
 */
class CallTracker(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val scope = CoroutineScope(Dispatchers.Main)
    private var durationJob: Job? = null
    private var currentDuration = 0L

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
            val callerDisplayName = resolveContactName(phoneNumber) ?: phoneNumber ?: "Cellular Call"
            val callInfo = CallInfo(
                callerName = callerDisplayName,
                callerNumber = phoneNumber,
                state = callState,
                callDurationSeconds = 0L,
                isVoip = false,
                appName = "Phone"
            )
            IslandEventBus.updateCall(if (callState == CallState.IDLE) null else callInfo)
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
            null
        }
    }

    private fun startDurationCounter(number: String?) {
        durationJob?.cancel()
        currentDuration = 0L
        val displayName = resolveContactName(number) ?: number ?: "Active Call"
        durationJob = scope.launch {
            while (isActive) {
                val callInfo = CallInfo(
                    callerName = displayName,
                    callerNumber = number,
                    state = CallState.OFFHOOK,
                    callDurationSeconds = currentDuration,
                    isVoip = false,
                    appName = "Phone"
                )
                IslandEventBus.updateCall(callInfo)
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

    /**
     * Answers an incoming ringing cellular call via TelecomManager.
     */
    fun answerCall(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tm = telecomManager ?: return false
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    tm.acceptRingingCall()
                    return true
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
        return false
    }

    /**
     * Ends or declines an active or ringing cellular call via TelecomManager.
     */
    fun endCall(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val tm = telecomManager ?: return false
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    return tm.endCall()
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
        return false
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

    companion object {
        @Volatile
        private var instance: CallTracker? = null

        fun getInstance(context: Context): CallTracker {
            return instance ?: synchronized(this) {
                instance ?: CallTracker(context.applicationContext).also { instance = it }
            }
        }
    }
}
