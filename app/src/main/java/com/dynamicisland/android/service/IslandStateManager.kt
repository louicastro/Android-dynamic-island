package com.dynamicisland.android.service

import com.dynamicisland.android.data.SettingsRepository
import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ActiveToastItem {
    val id: String
    val timestamp: Long

    data class Notification(
        override val id: String,
        override val timestamp: Long,
        val item: NotificationItem
    ) : ActiveToastItem

    data class Charging(
        override val id: String,
        override val timestamp: Long,
        val battery: BatteryInfo
    ) : ActiveToastItem

    data class LowBattery(
        override val id: String,
        override val timestamp: Long,
        val battery: BatteryInfo
    ) : ActiveToastItem

    data class Ringer(
        override val id: String,
        override val timestamp: Long,
        val isMuted: Boolean
    ) : ActiveToastItem

    data class Bluetooth(
        override val id: String,
        override val timestamp: Long,
        val deviceName: String,
        val batteryLevel: Int?
    ) : ActiveToastItem
}

enum class ExpandedTarget {
    CALL,
    TIMER,
    NOTIFICATION,
    MEDIA,
    BATTERY,
    RINGER
}

/**
 * Pure state reducer and authoritative coordinator for the Dynamic Island.
 *
 * Implements:
 * - Unified single-reducer architecture eliminating flow race conditions
 * - Strict deterministic state prioritization (Incoming Call > In-Call > User Expansion > Timer > Toast > Media > Idle)
 * - Bounded notification and toast queue with deduplication and targeted timeouts
 * - Low battery alert threshold (<=20%) with hysteresis (>25% reset)
 * - Feature-gated toast eviction on setting toggles
 * - Independent user expansion state preservation
 */
class IslandStateManager(
    private val settingsRepository: SettingsRepository,
    private val eventBus: IslandEventBus = IslandEventBus,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    private val _currentState = MutableStateFlow<IslandState>(IslandState.Idle)
    val currentState: StateFlow<IslandState> = _currentState.asStateFlow()

    // Bounded transient toast queue (max 5 items)
    private val toastQueue = mutableListOf<ActiveToastItem>()
    private val maxQueueSize = 5

    // User-requested expansion target
    private var expandedTarget: ExpandedTarget? = null

    // Battery alert latching with hysteresis
    private var lastChargingState: Boolean = false
    private var hasFiredLowBatteryAlert: Boolean = false
    private val lowBatteryThreshold: Int = 20
    private val lowBatteryResetThreshold: Int = 25

    private var timeoutJob: Job? = null

    init {
        observeSubsystems()
    }

    private fun observeSubsystems() {
        // Collect persistent state flows with distinctUntilChanged to minimize redundant recompositions
        scope.launch {
            combine(
                settingsRepository.settingsFlow.distinctUntilChanged(),
                eventBus.callState.distinctUntilChanged(),
                eventBus.timerState.distinctUntilChanged(),
                eventBus.mediaState.distinctUntilChanged(),
                eventBus.batteryState.distinctUntilChanged()
            ) { settings, call, timer, media, battery ->
                handlePersistentUpdate(settings, call, timer, media, battery)
            }.collect { newState ->
                _currentState.value = newState
            }
        }

        // Collect transient events and dispatch to reducer
        scope.launch {
            eventBus.transientEvents.collect { event ->
                dispatch(event)
            }
        }
    }

    private fun handlePersistentUpdate(
        settings: IslandSettings,
        call: CallInfo?,
        timer: TimerInfo?,
        media: MediaInfo?,
        battery: BatteryInfo?
    ): IslandState {
        // Enforce immediate transient eviction if a feature was disabled in settings
        pruneToastsForDisabledSettings(settings)

        return computeState(
            settings = settings,
            call = call,
            timer = timer,
            media = media,
            battery = battery,
            activeToast = toastQueue.firstOrNull(),
            target = expandedTarget
        )
    }

    @Synchronized
    fun dispatch(event: IslandEvent) {
        val settings = settingsRepository.getSettings()

        when (event) {
            is IslandEvent.NotificationPosted -> {
                if (settings.enableNotifications) {
                    enqueueNotificationToast(event.notification, event.eventId)
                }
            }

            is IslandEvent.NotificationRemoved -> {
                removeNotificationByKey(event.key)
            }

            is IslandEvent.BatteryUpdate -> {
                if (settings.enableBattery) {
                    handleBatteryUpdate(event.battery, event.eventId)
                }
            }

            is IslandEvent.RingerModeToggle -> {
                enqueueToast(
                    ActiveToastItem.Ringer(
                        id = event.eventId,
                        timestamp = System.currentTimeMillis(),
                        isMuted = event.isMuted
                    )
                )
            }

            is IslandEvent.BluetoothConnect -> {
                enqueueToast(
                    ActiveToastItem.Bluetooth(
                        id = event.eventId,
                        timestamp = System.currentTimeMillis(),
                        deviceName = event.deviceName,
                        batteryLevel = event.batteryLevel
                    )
                )
            }

            is IslandEvent.TapPill -> {
                toggleExpansion()
            }

            is IslandEvent.CollapseExpanded, is IslandEvent.DismissExpanded -> {
                expandedTarget = null
            }

            is IslandEvent.DismissCurrentToast -> {
                dismissToastById(event.toastId)
            }

            is IslandEvent.DismissNotificationToast -> {
                removeNotificationByKey(event.key)
            }

            is IslandEvent.ToastTimeout -> {
                handleToastTimeout(event.eventId)
            }

            is IslandEvent.ClearAllTransient -> {
                toastQueue.clear()
                timeoutJob?.cancel()
                expandedTarget = null
            }

            else -> Unit
        }

        recompute()
    }

    private fun handleBatteryUpdate(battery: BatteryInfo, eventId: String) {
        if (battery.isCharging) {
            // Plugged in: show charging toast on plug transition and reset low battery latch
            if (!lastChargingState) {
                enqueueToast(
                    ActiveToastItem.Charging(
                        id = eventId,
                        timestamp = System.currentTimeMillis(),
                        battery = battery
                    )
                )
            }
            hasFiredLowBatteryAlert = false
        } else {
            // Discharging: check threshold (<= 20%) with single-fire latch
            if (battery.level in 0..lowBatteryThreshold && !hasFiredLowBatteryAlert) {
                hasFiredLowBatteryAlert = true
                enqueueToast(
                    ActiveToastItem.LowBattery(
                        id = eventId,
                        timestamp = System.currentTimeMillis(),
                        battery = battery
                    )
                )
            } else if (battery.level > lowBatteryResetThreshold) {
                // Hysteresis: re-arm latch only after battery rises above reset threshold
                hasFiredLowBatteryAlert = false
            }
        }
        lastChargingState = battery.isCharging
    }

    private fun enqueueNotificationToast(notification: NotificationItem, eventId: String) {
        // Deduplicate: replace existing toast with same key or append
        toastQueue.removeAll { it is ActiveToastItem.Notification && it.item.key == notification.key }
        if (toastQueue.size >= maxQueueSize) {
            toastQueue.removeAt(0)
        }
        toastQueue.add(
            ActiveToastItem.Notification(
                id = eventId,
                timestamp = System.currentTimeMillis(),
                item = notification
            )
        )
        checkScheduleTimeout()
    }

    private fun enqueueToast(toast: ActiveToastItem) {
        if (toastQueue.size >= maxQueueSize) {
            toastQueue.removeAt(0)
        }
        toastQueue.add(toast)
        checkScheduleTimeout()
    }

    private fun checkScheduleTimeout() {
        val currentHead = toastQueue.firstOrNull() ?: return
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(TOAST_DURATION_MS)
            dispatch(IslandEvent.ToastTimeout(currentHead.id))
        }
    }

    private fun handleToastTimeout(expiredId: String) {
        val currentHead = toastQueue.firstOrNull()
        if (currentHead != null && currentHead.id == expiredId) {
            toastQueue.removeAt(0)
            checkScheduleTimeout()
        }
    }

    private fun dismissToastById(toastId: String) {
        val removedHead = toastQueue.firstOrNull()?.id == toastId
        toastQueue.removeAll { it.id == toastId }
        if (removedHead) {
            checkScheduleTimeout()
        }
    }

    private fun removeNotificationByKey(key: String) {
        val removedHead = (toastQueue.firstOrNull() as? ActiveToastItem.Notification)?.item?.key == key
        toastQueue.removeAll { it is ActiveToastItem.Notification && it.item.key == key }
        if (removedHead) {
            checkScheduleTimeout()
        }
    }

    private fun pruneToastsForDisabledSettings(settings: IslandSettings) {
        if (!settings.enableNotifications) {
            toastQueue.removeAll { it is ActiveToastItem.Notification }
            if (expandedTarget == ExpandedTarget.NOTIFICATION) expandedTarget = null
        }
        if (!settings.enableBattery) {
            toastQueue.removeAll { it is ActiveToastItem.Charging || it is ActiveToastItem.LowBattery }
            if (expandedTarget == ExpandedTarget.BATTERY) expandedTarget = null
        }
        if (!settings.enableMusic && expandedTarget == ExpandedTarget.MEDIA) {
            expandedTarget = null
        }
        if (!settings.enableCalls && expandedTarget == ExpandedTarget.CALL) {
            expandedTarget = null
        }
    }

    private fun toggleExpansion() {
        if (expandedTarget != null) {
            expandedTarget = null
            return
        }

        // Infer expansion target based on active visible state
        val state = _currentState.value
        expandedTarget = when (state) {
            is IslandState.CompactCall, is IslandState.ExpandedCall -> ExpandedTarget.CALL
            is IslandState.CompactTimer, is IslandState.ExpandedTimer -> ExpandedTarget.TIMER
            is IslandState.NotificationToast, is IslandState.ExpandedNotification -> ExpandedTarget.NOTIFICATION
            is IslandState.CompactMusic, is IslandState.ExpandedMusic -> ExpandedTarget.MEDIA
            is IslandState.ChargingToast, is IslandState.LowBatteryToast, is IslandState.ExpandedBattery, is IslandState.ExpandedLowBattery -> ExpandedTarget.BATTERY
            is IslandState.RingerToast, is IslandState.ExpandedRinger -> ExpandedTarget.RINGER
            is IslandState.SplitIsland -> ExpandedTarget.MEDIA
            else -> null
        }
    }

    private fun recompute() {
        val settings = settingsRepository.getSettings()
        pruneToastsForDisabledSettings(settings)

        val newState = computeState(
            settings = settings,
            call = eventBus.callState.value,
            timer = eventBus.timerState.value,
            media = eventBus.mediaState.value,
            battery = eventBus.batteryState.value,
            activeToast = toastQueue.firstOrNull(),
            target = expandedTarget
        )
        _currentState.value = newState
    }

    /**
     * Authoritative single reducer evaluating IslandState by strict priority:
     * 1. Incoming Call (ringing)
     * 2. Active Call (in-call)
     * 3. User-Expanded Content
     * 4. Active Timer or Alarm
     * 5. Transient Alert Toast (from queue)
     * 6. Multi-tasking Split State
     * 7. Active Media
     * 8. Idle
     */
    private fun computeState(
        settings: IslandSettings,
        call: CallInfo?,
        timer: TimerInfo?,
        media: MediaInfo?,
        battery: BatteryInfo?,
        activeToast: ActiveToastItem?,
        target: ExpandedTarget?
    ): IslandState {
        if (!settings.isEnabled) return IslandState.Idle

        val activeCall = if (settings.enableCalls) call else null
        val activeMedia = if (settings.enableMusic && media?.isPlaying == true) media else null
        val activeBattery = if (settings.enableBattery) battery else null

        // Priority 1: Incoming Ringing Call (highest priority)
        if (activeCall != null && activeCall.state == CallState.RINGING) {
            return if (target == ExpandedTarget.CALL) IslandState.ExpandedCall(activeCall)
            else IslandState.CompactCall(activeCall)
        }

        // Priority 2: Active In-Call Session
        if (activeCall != null && activeCall.state == CallState.OFFHOOK) {
            if (target == ExpandedTarget.CALL) return IslandState.ExpandedCall(activeCall)
            // If user did not expand an alternate item, prioritize call in compact or split
            if (target == null) {
                if (activeMedia != null) {
                    return IslandState.SplitIsland(
                        primaryState = IslandState.CompactCall(activeCall),
                        secondaryState = IslandState.CompactMusic(activeMedia)
                    )
                }
                return IslandState.CompactCall(activeCall)
            }
        }

        // Priority 3: User-Expanded Content
        if (target != null) {
            when (target) {
                ExpandedTarget.CALL -> if (activeCall != null) return IslandState.ExpandedCall(activeCall)
                ExpandedTarget.TIMER -> if (timer != null) return IslandState.ExpandedTimer(timer)
                ExpandedTarget.NOTIFICATION -> {
                    if (activeToast is ActiveToastItem.Notification) {
                        return IslandState.ExpandedNotification(activeToast.item)
                    }
                }
                ExpandedTarget.MEDIA -> if (activeMedia != null) return IslandState.ExpandedMusic(activeMedia)
                ExpandedTarget.BATTERY -> {
                    if (activeToast is ActiveToastItem.LowBattery) return IslandState.ExpandedLowBattery(activeToast.battery)
                    if (activeToast is ActiveToastItem.Charging) return IslandState.ExpandedBattery(activeToast.battery)
                    if (activeBattery != null) return IslandState.ExpandedBattery(activeBattery)
                }
                ExpandedTarget.RINGER -> {
                    if (activeToast is ActiveToastItem.Ringer) return IslandState.ExpandedRinger(activeToast.isMuted)
                }
            }
        }

        // Priority 4: Active Running Timer
        if (timer != null && timer.isRunning && (timer.remainingSeconds > 0L || timer.remainingSeconds < 0L)) {
            if (activeMedia != null) {
                return IslandState.SplitIsland(
                    primaryState = IslandState.CompactTimer(timer),
                    secondaryState = IslandState.CompactMusic(activeMedia)
                )
            }
            return IslandState.CompactTimer(timer)
        }

        // Priority 5: Transient Alert Toasts (from bounded queue)
        if (activeToast != null) {
            when (activeToast) {
                is ActiveToastItem.Notification -> {
                    if (settings.enableNotifications) return IslandState.NotificationToast(activeToast.item)
                }
                is ActiveToastItem.Charging -> {
                    if (settings.enableBattery) return IslandState.ChargingToast(activeToast.battery)
                }
                is ActiveToastItem.LowBattery -> {
                    if (settings.enableBattery) return IslandState.LowBatteryToast(activeToast.battery)
                }
                is ActiveToastItem.Ringer -> {
                    return IslandState.RingerToast(activeToast.isMuted)
                }
                is ActiveToastItem.Bluetooth -> {
                    return IslandState.BluetoothToast(
                        deviceName = activeToast.deviceName,
                        batteryLevel = activeToast.batteryLevel
                    )
                }
            }
        }

        // Priority 6: Multi-tasking Split State (Media + Battery Charging)
        if (activeMedia != null && activeBattery != null && activeBattery.isCharging) {
            return IslandState.SplitIsland(
                primaryState = IslandState.CompactMusic(activeMedia),
                secondaryState = IslandState.CompactBattery(activeBattery)
            )
        }

        // Priority 7: Active Music Playback
        if (activeMedia != null) {
            return IslandState.CompactMusic(activeMedia)
        }

        // Priority 8: Idle Pill
        return IslandState.Idle
    }

    fun handleUserTap() {
        dispatch(IslandEvent.TapPill)
    }

    fun handleDismiss() {
        dispatch(IslandEvent.CollapseExpanded)
    }

    fun handleToastTimeout() {
        val currentHead = toastQueue.firstOrNull() ?: return
        dispatch(IslandEvent.ToastTimeout(currentHead.id))
    }

    fun cleanup() {
        timeoutJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val TOAST_DURATION_MS = 4000L
    }
}
