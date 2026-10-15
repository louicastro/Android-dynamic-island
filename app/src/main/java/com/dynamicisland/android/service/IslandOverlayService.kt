package com.dynamicisland.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynamicisland.android.DynamicIslandApp
import com.dynamicisland.android.R
import com.dynamicisland.android.data.SettingsRepository
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.ui.overlay.IslandOverlayHost
import com.dynamicisland.android.ui.settings.MainActivity
import com.dynamicisland.android.util.WindowUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IslandOverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository

    private var composeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private var batteryTracker: BatteryTracker? = null
    private var callTracker: CallTracker? = null

    // Subsystem States
    private var currentMedia: MediaInfo? = null
    private var currentCall: CallInfo? = null
    private var currentBattery: BatteryInfo? = null
    private var currentNotificationToast: NotificationItem? = null
    private var currentChargingToast: BatteryInfo? = null
    private var currentTimer: com.dynamicisland.android.model.TimerInfo? = null
    private var currentRingerToast: Boolean? = null
    private var currentBluetoothDevice: Pair<String, Int?>? = null
    private var isExpandedManually: Boolean = false

    private val _currentState = MutableStateFlow<IslandState>(IslandState.Idle)
    val currentState = _currentState.asStateFlow()

    private var currentLayoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository.getInstance(this)

        startForegroundNotification()

        overlayLifecycleOwner.onCreate()
        overlayLifecycleOwner.onResume()

        setupTrackers()
        observeEvents()
        observeSettings()

        attachOverlayView()
    }

    private fun startForegroundNotification() {
        val channelId = DynamicIslandApp.NOTIFICATION_CHANNEL_ID
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupTrackers() {
        batteryTracker = BatteryTracker(this).apply { start() }
        callTracker = CallTracker(this).apply { start() }
    }

    private fun observeEvents() {
        serviceScope.launch {
            IslandEventBus.events.collect { event ->
                handleIslandEvent(event)
            }
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                if (!settings.isEnabled) {
                    composeView?.visibility = android.view.View.GONE
                } else {
                    composeView?.visibility = android.view.View.VISIBLE
                    updateWindowDimensions(_currentState.value, settings)
                }
            }
        }
    }

    private fun handleIslandEvent(event: IslandEvent) {
        val settings = settingsRepository.getSettings()

        when (event) {
            is IslandEvent.MediaUpdate -> {
                currentMedia = if (settings.enableMusic) event.media else null
                recomputeState()
            }

            is IslandEvent.NotificationPosted -> {
                if (settings.enableNotifications) {
                    currentNotificationToast = event.notification
                    recomputeState()
                }
            }

            is IslandEvent.NotificationRemoved -> {
                if (currentNotificationToast?.key == event.key) {
                    currentNotificationToast = null
                    recomputeState()
                }
            }

            is IslandEvent.CallUpdate -> {
                currentCall = if (settings.enableCalls) event.call else null
                recomputeState()
            }

            is IslandEvent.BatteryUpdate -> {
                if (settings.enableBattery) {
                    val wasCharging = currentBattery?.isCharging == true
                    currentBattery = event.battery
                    if (event.battery.isCharging && !wasCharging) {
                        currentChargingToast = event.battery
                    }
                    recomputeState()
                }
            }

            is IslandEvent.TimerUpdate -> {
                currentTimer = if (settings.enableTimer) event.timer else null
                recomputeState()
            }

            is IslandEvent.RingerModeToggle -> {
                if (settings.enableRinger) {
                    currentRingerToast = event.isMuted
                    recomputeState()
                }
            }

            is IslandEvent.BluetoothConnect -> {
                currentBluetoothDevice = Pair(event.deviceName, event.batteryLevel)
                recomputeState()
            }

            is IslandEvent.TapPill -> {
                isExpandedManually = !isExpandedManually
                recomputeState()
            }

            is IslandEvent.LongPressPill -> {
                // Open source app if available
                currentNotificationToast?.contentIntent?.let {
                    try {
                        it.send()
                    } catch (e: Exception) {
                        // Ignored
                    }
                }
            }

            is IslandEvent.DismissExpanded -> {
                isExpandedManually = false
                currentNotificationToast = null
                currentChargingToast = null
                currentRingerToast = null
                currentBluetoothDevice = null
                recomputeState()
            }

            is IslandEvent.ToastTimeout -> {
                currentNotificationToast = null
                currentChargingToast = null
                currentRingerToast = null
                currentBluetoothDevice = null
                recomputeState()
            }
        }
    }

    private fun recomputeState() {
        val settings = settingsRepository.getSettings()

        val newState: IslandState = when {
            // High priority: Incoming call
            currentCall?.state == CallState.RINGING -> {
                if (isExpandedManually) IslandState.ExpandedCall(currentCall!!)
                else IslandState.CompactCall(currentCall!!)
            }

            // In-call active
            currentCall?.state == CallState.OFFHOOK -> {
                if (isExpandedManually) IslandState.ExpandedCall(currentCall!!)
                else IslandState.CompactCall(currentCall!!)
            }

            // Charging toast alert
            currentRingerToast != null -> {
                if (isExpandedManually) IslandState.ExpandedRinger(currentRingerToast!!)
                else IslandState.RingerToast(currentRingerToast!!)
            }

            currentBluetoothDevice != null -> {
                IslandState.BluetoothToast(currentBluetoothDevice!!.first, currentBluetoothDevice!!.second)
            }

            currentChargingToast != null -> {
                if (isExpandedManually) IslandState.ExpandedBattery(currentChargingToast!!)
                else IslandState.ChargingToast(currentChargingToast!!)
            }

            // Notification alert
            currentNotificationToast != null -> {
                if (isExpandedManually) IslandState.ExpandedNotification(currentNotificationToast!!)
                else IslandState.NotificationToast(currentNotificationToast!!)
            }

            // Active Timer
            currentTimer != null -> {
                if (isExpandedManually) IslandState.ExpandedTimer(currentTimer!!)
                else IslandState.CompactTimer(currentTimer!!)
            }

            // Split island: Music playing + battery plugged in
            currentMedia != null && currentMedia!!.isPlaying && currentBattery != null && currentBattery!!.isCharging -> {
                if (isExpandedManually) {
                    IslandState.ExpandedMusic(currentMedia!!)
                } else {
                    IslandState.SplitIsland(
                        primaryState = IslandState.CompactMusic(currentMedia!!),
                        secondaryState = IslandState.CompactBattery(currentBattery!!)
                    )
                }
            }

            // Music playing
            currentMedia != null && currentMedia!!.isPlaying -> {
                if (isExpandedManually) IslandState.ExpandedMusic(currentMedia!!)
                else IslandState.CompactMusic(currentMedia!!)
            }

            // Idle or minimal cutout
            else -> IslandState.Idle
        }

        _currentState.value = newState
        updateWindowDimensions(newState, settings)
    }

    private fun attachOverlayView() {
        val settings = settingsRepository.getSettings()

        val widthPx = WindowUtils.dpToPx(this, settings.compactWidthDp + 24)
        val heightPx = WindowUtils.dpToPx(this, settings.compactHeightDp + 16)

        val params = WindowUtils.createLayoutParams(this, settings, widthPx, heightPx)
        currentLayoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

            setContent {
                val state by _currentState.collectAsState()
                val currentSettings by settingsRepository.settingsFlow.collectAsState()

                IslandOverlayHost(
                    state = state,
                    settings = currentSettings,
                    onTap = { handleIslandEvent(IslandEvent.TapPill) },
                    onLongPress = { handleIslandEvent(IslandEvent.LongPressPill) },
                    onDismissRequest = { handleIslandEvent(IslandEvent.DismissExpanded) },
                    onAutoDismissToast = { handleIslandEvent(IslandEvent.ToastTimeout) }
                )
            }
        }

        composeView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWindowDimensions(state: IslandState, settings: IslandSettings) {
        val view = composeView ?: return
        val params = currentLayoutParams ?: return

        val widthDp = when {
            state.isExpanded -> settings.expandedWidthDp + 24
            state is IslandState.SplitIsland -> settings.compactWidthDp + 64
            state is IslandState.Idle -> 90
            else -> settings.compactWidthDp + 24
        }

        val heightDp = when {
            state.isExpanded -> settings.expandedHeightDp + 24
            else -> settings.compactHeightDp + 16
        }

        val widthPx = WindowUtils.dpToPx(this, widthDp)
        val heightPx = WindowUtils.dpToPx(this, heightDp)

        params.width = widthPx
        params.height = heightPx
        params.x = WindowUtils.dpToPx(this, settings.xOffsetDp)
        params.y = WindowUtils.dpToPx(this, settings.yOffsetDp)

        params.gravity = when (settings.cutoutType) {
            CutoutType.LEFT_PUNCH_HOLE -> Gravity.TOP or Gravity.START
            CutoutType.RIGHT_PUNCH_HOLE -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            // View may not be attached yet
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryTracker?.stop()
        callTracker?.stop()

        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        composeView = null

        overlayLifecycleOwner.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
