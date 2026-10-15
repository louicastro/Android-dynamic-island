package com.dynamicisland.android.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
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
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.ui.overlay.IslandOverlayHost
import com.dynamicisland.android.ui.settings.MainActivity
import com.dynamicisland.android.util.DisplayCutoutHelper
import com.dynamicisland.android.util.WindowUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service responsible for WindowManager lifecycle, system overlay view attachment,
 * dynamic layout sizing, coordinate clamping, and hardware window insets.
 *
 * Implements:
 * - Idempotent overlay attachment preventing duplicate addView() calls (Finding 21)
 * - Dynamic permission rechecking and automatic recovery (Finding 20)
 * - Subsystem pausing and tracker resource release when disabled (Finding 22)
 * - Synchronized lifecycle owner transitions matching view visibility (Finding 23)
 * - Android 14/15 ForegroundServiceStartNotAllowedException protection (Finding 25)
 * - Measured hardware cutout insets and display bounds clamping (Findings 26, 27)
 */
class IslandOverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var stateManager: IslandStateManager

    private var composeView: ComposeView? = null
    private val overlayLifecycleOwner = OverlayLifecycleOwner()

    private var batteryTracker: BatteryTracker? = null
    private var callTracker: CallTracker? = null

    private var currentLayoutParams: WindowManager.LayoutParams? = null
    private var isViewAttached: Boolean = false
    private var attachRetryCount = 0
    private var detectedCutoutBounds: Rect? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository.getInstance(this)
        stateManager = IslandStateManager(settingsRepository, IslandEventBus, serviceScope)

        startForegroundSafely()

        overlayLifecycleOwner.onCreate()

        // 1. Subscribe to state and settings
        observeStateAndSettings()

        // 2. Start hardware trackers if enabled
        val settings = settingsRepository.getSettings()
        if (settings.isEnabled) {
            setupTrackers()
            overlayLifecycleOwner.onResume()
            ensureOverlayAttached()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Dynamic permission recovery: recheck if overlay permission was granted after service launch
        if (settingsRepository.getSettings().isEnabled && !isViewAttached) {
            ensureOverlayAttached()
        }
        return START_STICKY
    }

    private fun startForegroundSafely() {
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

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "ForegroundServiceStartNotAllowedException during startForeground", e)
            } else if (e is SecurityException) {
                Log.e(TAG, "SecurityException during startForeground", e)
            } else {
                Log.e(TAG, "Unexpected error in startForeground", e)
            }
        }
    }

    private fun setupTrackers() {
        if (batteryTracker == null) {
            batteryTracker = BatteryTracker(this).apply { start() }
        }
        if (callTracker == null) {
            callTracker = CallTracker.getInstance(this).apply { start() }
        }
    }

    private fun stopTrackers() {
        batteryTracker?.stop()
        batteryTracker = null
        callTracker?.stop()
        callTracker = null
    }

    private fun observeStateAndSettings() {
        serviceScope.launch {
            stateManager.currentState.collect { state ->
                val settings = settingsRepository.getSettings()
                if (settings.isEnabled && isViewAttached) {
                    updateWindowDimensions(state, settings)
                }
            }
        }

        serviceScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                if (!settings.isEnabled) {
                    // Feature disabled: stop hardware trackers and pause Compose lifecycle
                    stopTrackers()
                    composeView?.visibility = View.GONE
                    overlayLifecycleOwner.onPause()
                } else {
                    // Feature enabled: restart trackers, resume lifecycle, and attach view
                    setupTrackers()
                    overlayLifecycleOwner.onResume()
                    composeView?.visibility = View.VISIBLE
                    if (!isViewAttached) {
                        ensureOverlayAttached()
                    } else {
                        updateWindowDimensions(stateManager.currentState.value, settings)
                    }
                }
            }
        }
    }

    @Synchronized
    private fun ensureOverlayAttached() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot attach overlay: SYSTEM_ALERT_WINDOW permission missing")
            return
        }

        if (isViewAttached && composeView != null) {
            // Already attached: update layout instead of duplicate addView()
            val settings = settingsRepository.getSettings()
            updateWindowDimensions(stateManager.currentState.value, settings)
            return
        }

        val settings = settingsRepository.getSettings()
        val widthPx = WindowUtils.dpToPx(this, settings.compactWidthDp + 24)
        val heightPx = WindowUtils.dpToPx(this, settings.compactHeightDp + 16)

        val params = WindowUtils.createLayoutParams(
            this,
            settings,
            widthPx,
            heightPx,
            detectedCutoutBounds
        )
        currentLayoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

            // Listen for hardware window insets to measure camera punch-hole cutout
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setOnApplyWindowInsetsListener { _, insets ->
                    val bounds = DisplayCutoutHelper.extractCutoutBounds(insets)
                    if (bounds != null && bounds != detectedCutoutBounds) {
                        detectedCutoutBounds = bounds
                        updateWindowDimensions(stateManager.currentState.value, settingsRepository.getSettings())
                    }
                    insets
                }
            }

            setContent {
                val state by stateManager.currentState.collectAsState()
                val currentSettings by settingsRepository.settingsFlow.collectAsState()

                IslandOverlayHost(
                    state = state,
                    settings = currentSettings,
                    onTap = { stateManager.handleUserTap() },
                    onLongPress = { handleLongPress() },
                    onDismissRequest = { stateManager.handleDismiss() },
                    onAutoDismissToast = { stateManager.handleToastTimeout() }
                )
            }
        }

        composeView = view

        try {
            windowManager.addView(view, params)
            isViewAttached = true
            attachRetryCount = 0
            overlayLifecycleOwner.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "WindowManager addView failed", e)
            isViewAttached = false
            if (attachRetryCount < 3) {
                attachRetryCount++
                mainHandler.postDelayed({ ensureOverlayAttached() }, (attachRetryCount * 1000).toLong())
            }
        }
    }

    private fun handleLongPress() {
        val state = stateManager.currentState.value
        when (state) {
            is IslandState.ExpandedNotification -> {
                state.notification.contentIntent?.let {
                    try { it.send() } catch (e: Exception) { Log.e(TAG, "Error sending contentIntent", e) }
                }
            }
            is IslandState.NotificationToast -> {
                state.notification.contentIntent?.let {
                    try { it.send() } catch (e: Exception) { Log.e(TAG, "Error sending contentIntent", e) }
                }
            }
            is IslandState.ExpandedMusic -> {
                val launchIntent = packageManager.getLaunchIntentForPackage(state.media.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                }
            }
            else -> Unit
        }
    }

    private fun updateWindowDimensions(state: IslandState, settings: IslandSettings) {
        val view = composeView ?: return
        val params = currentLayoutParams ?: return
        if (!isViewAttached) return

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

        params.gravity = when (settings.cutoutType) {
            CutoutType.LEFT_PUNCH_HOLE -> Gravity.TOP or Gravity.START
            CutoutType.RIGHT_PUNCH_HOLE -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        val displaySize = WindowUtils.getDisplaySize(this)
        val density = resources.displayMetrics.density

        // Incorporate measured hardware cutout offset if available
        val (baseXDp, baseYDp) = DisplayCutoutHelper.getHardwareBaseOffsetsDp(
            detectedCutoutBounds,
            displaySize.x,
            density
        )

        val targetXDp = if (settings.cutoutType == CutoutType.CENTER_PUNCH_HOLE) {
            baseXDp + settings.xOffsetDp
        } else {
            settings.xOffsetDp
        }
        val targetYDp = (baseYDp + settings.yOffsetDp).coerceAtLeast(0)

        val rawXPx = WindowUtils.dpToPx(this, targetXDp)
        val rawYPx = WindowUtils.dpToPx(this, targetYDp)

        val (clampedX, clampedY) = WindowUtils.clampCoordinates(
            rawXPx,
            rawYPx,
            widthPx,
            heightPx,
            displaySize.x,
            displaySize.y,
            params.gravity
        )

        params.x = clampedX
        params.y = clampedY

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout failed (view might be unattached): ${e.message}")
            isViewAttached = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTrackers()
        stateManager.cleanup()

        composeView?.let {
            try {
                if (isViewAttached) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        composeView = null
        isViewAttached = false

        overlayLifecycleOwner.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "IslandOverlayService"
        private const val NOTIFICATION_ID = 1001
    }
}
