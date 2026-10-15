package com.dynamicisland.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
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
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.ui.overlay.IslandOverlayHost
import com.dynamicisland.android.ui.settings.MainActivity
import com.dynamicisland.android.util.WindowUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service responsible for WindowManager lifecycle, system overlay view attachment,
 * dynamic layout sizing, and hardware window insets.
 *
 * State prioritization and event reduction are delegated to [IslandStateManager].
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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository.getInstance(this)
        stateManager = IslandStateManager(settingsRepository, IslandEventBus, serviceScope)

        startForegroundNotification()

        overlayLifecycleOwner.onCreate()
        overlayLifecycleOwner.onResume()

        // 1. Subscribe to state and settings BEFORE starting event-producing trackers
        observeStateAndSettings()

        // 2. Start hardware trackers now that collectors are active
        setupTrackers()

        // 3. Attach overlay window to WindowManager
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
        callTracker = CallTracker.getInstance(this).apply { start() }
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
                    composeView?.visibility = android.view.View.GONE
                } else {
                    composeView?.visibility = android.view.View.VISIBLE
                    if (!isViewAttached) {
                        attachOverlayView()
                    } else {
                        updateWindowDimensions(stateManager.currentState.value, settings)
                    }
                }
            }
        }
    }

    private fun attachOverlayView() {
        if (!Settings.canDrawOverlays(this)) {
            // Cannot attach if overlay permission is missing
            return
        }

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
        } catch (e: Exception) {
            e.printStackTrace()
            isViewAttached = false
            // Exponential backoff recovery for window manager failures (up to 3 retries)
            if (attachRetryCount < 3) {
                attachRetryCount++
                mainHandler.postDelayed({ attachOverlayView() }, (attachRetryCount * 1000).toLong())
            }
        }
    }

    private fun handleLongPress() {
        // Open the active content source if available
        val state = stateManager.currentState.value
        when (state) {
            is IslandState.ExpandedNotification -> {
                state.notification.contentIntent?.let {
                    try { it.send() } catch (e: Exception) { e.printStackTrace() }
                }
            }
            is IslandState.NotificationToast -> {
                state.notification.contentIntent?.let {
                    try { it.send() } catch (e: Exception) { e.printStackTrace() }
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

        params.width = WindowUtils.dpToPx(this, widthDp)
        params.height = WindowUtils.dpToPx(this, heightDp)
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
            // View may have been detached or window token changed
            isViewAttached = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryTracker?.stop()
        callTracker?.stop()
        stateManager.cleanup()

        composeView?.let {
            try {
                if (isViewAttached) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
        composeView = null
        isViewAttached = false

        overlayLifecycleOwner.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
