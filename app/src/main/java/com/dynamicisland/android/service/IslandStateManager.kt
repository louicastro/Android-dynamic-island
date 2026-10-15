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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Dedicated state reducer and state-holder for the Dynamic Island.
 * Decouples state prioritization, transient toast queueing, low battery latching,
 * and multi-tasking logic from the Android Service and WindowManager lifecycle.
 */
class IslandStateManager(
    private val settingsRepository: SettingsRepository,
    private val eventBus: IslandEventBus = IslandEventBus,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {

    private val _currentState = MutableStateFlow<IslandState>(IslandState.Idle)
    val currentState: StateFlow<IslandState> = _currentState.asStateFlow()

    // Transient alert states
    private var activeNotificationToast: NotificationItem? = null
    private var activeChargingToast: BatteryInfo? = null
    private var activeLowBatteryToast: BatteryInfo? = null
    private var activeRingerToast: Boolean? = null
    private var activeBluetoothToast: Pair<String, Int?>? = null
    private var isExpandedManually: Boolean = false

    private var lastChargingState: Boolean = false
    private var hasFiredLowBatteryAlert: Boolean = false
    private val lowBatteryThreshold: Int = 20

    init {
        observeSubsystems()
    }

    private fun observeSubsystems() {
        // Collect persistent state flows
        scope.launch {
            combine(
                settingsRepository.settingsFlow,
                eventBus.callState,
                eventBus.timerState,
                eventBus.mediaState,
                eventBus.batteryState
            ) { settings, call, timer, media, battery ->
                computeState(settings, call, timer, media, battery)
            }.collect { newState ->
                _currentState.value = newState
            }
        }

        // Collect transient gesture events and toast triggers
        scope.launch {
            eventBus.transientEvents.collect { event ->
                handleTransientEvent(event)
            }
        }
    }

    private fun handleTransientEvent(event: IslandEvent) {
        val settings = settingsRepository.getSettings()

        when (event) {
            is IslandEvent.NotificationPosted -> {
                if (settings.enableNotifications) {
                    activeNotificationToast = event.notification
                    recompute()
                }
            }

            is IslandEvent.NotificationRemoved -> {
                if (activeNotificationToast?.key == event.key) {
                    activeNotificationToast = null
                    recompute()
                }
            }

            is IslandEvent.BatteryUpdate -> {
                if (settings.enableBattery) {
                    val battery = event.battery
                    if (battery.isCharging) {
                        // Charger connected: show charging toast and reset low battery alert latch
                        if (!lastChargingState) {
                            activeChargingToast = battery
                            activeLowBatteryToast = null
                        }
                        hasFiredLowBatteryAlert = false
                    } else {
                        // Discharging: check low battery threshold with single-trigger latch
                        if (battery.level <= lowBatteryThreshold && !hasFiredLowBatteryAlert) {
                            activeLowBatteryToast = battery
                            hasFiredLowBatteryAlert = true
                        } else if (battery.level > lowBatteryThreshold) {
                            hasFiredLowBatteryAlert = false
                        }
                    }
                    lastChargingState = battery.isCharging
                    recompute()
                }
            }

            is IslandEvent.RingerModeToggle -> {
                activeRingerToast = event.isMuted
                recompute()
            }

            is IslandEvent.BluetoothConnect -> {
                activeBluetoothToast = Pair(event.deviceName, event.batteryLevel)
                recompute()
            }

            is IslandEvent.TapPill -> {
                isExpandedManually = !isExpandedManually
                recompute()
            }

            is IslandEvent.DismissExpanded -> {
                isExpandedManually = false
                activeNotificationToast = null
                activeChargingToast = null
                activeLowBatteryToast = null
                activeRingerToast = null
                activeBluetoothToast = null
                recompute()
            }

            is IslandEvent.ToastTimeout -> {
                activeNotificationToast = null
                activeChargingToast = null
                activeLowBatteryToast = null
                activeRingerToast = null
                activeBluetoothToast = null
                recompute()
            }

            else -> Unit
        }
    }

    private fun recompute() {
        val settings = settingsRepository.getSettings()
        val newState = computeState(
            settings = settings,
            call = eventBus.callState.value,
            timer = eventBus.timerState.value,
            media = eventBus.mediaState.value,
            battery = eventBus.batteryState.value
        )
        _currentState.value = newState
    }

    private fun computeState(
        settings: IslandSettings,
        call: CallInfo?,
        timer: TimerInfo?,
        media: MediaInfo?,
        battery: BatteryInfo?
    ): IslandState {
        if (!settings.isEnabled) return IslandState.Idle

        val activeCall = if (settings.enableCalls) call else null
        val activeMedia = if (settings.enableMusic && media?.isPlaying == true) media else null
        val activeBattery = if (settings.enableBattery) battery else null

        // Priority 1: Incoming ringing phone / VoIP call
        if (activeCall != null && activeCall.state == CallState.RINGING) {
            return if (isExpandedManually) IslandState.ExpandedCall(activeCall)
            else IslandState.CompactCall(activeCall)
        }

        // Priority 2: Active in-call session
        if (activeCall != null && activeCall.state == CallState.OFFHOOK) {
            return if (isExpandedManually) IslandState.ExpandedCall(activeCall)
            else IslandState.CompactCall(activeCall)
        }

        // Priority 3: Active Timer running
        if (timer != null && timer.isRunning && timer.remainingSeconds > 0) {
            return if (isExpandedManually) IslandState.ExpandedTimer(timer)
            else IslandState.CompactTimer(timer)
        }

        // Priority 4: Transient Toasts
        if (activeNotificationToast != null) {
            return if (isExpandedManually) IslandState.ExpandedNotification(activeNotificationToast!!)
            else IslandState.NotificationToast(activeNotificationToast!!)
        }

        if (activeChargingToast != null) {
            return if (isExpandedManually) IslandState.ExpandedBattery(activeChargingToast!!)
            else IslandState.ChargingToast(activeChargingToast!!)
        }

        if (activeLowBatteryToast != null) {
            return if (isExpandedManually) IslandState.ExpandedLowBattery(activeLowBatteryToast!!)
            else IslandState.LowBatteryToast(activeLowBatteryToast!!)
        }

        if (activeRingerToast != null) {
            return if (isExpandedManually) IslandState.ExpandedRinger(activeRingerToast!!)
            else IslandState.RingerToast(activeRingerToast!!)
        }

        if (activeBluetoothToast != null) {
            return IslandState.BluetoothToast(
                deviceName = activeBluetoothToast!!.first,
                batteryLevel = activeBluetoothToast!!.second
            )
        }

        // Priority 5: Multi-tasking Split State (e.g. Music playing + Charging or Timer)
        if (activeMedia != null && activeBattery != null && activeBattery.isCharging) {
            return if (isExpandedManually) {
                IslandState.ExpandedMusic(activeMedia)
            } else {
                IslandState.SplitIsland(
                    primaryState = IslandState.CompactMusic(activeMedia),
                    secondaryState = IslandState.CompactBattery(activeBattery)
                )
            }
        }

        // Priority 6: Music playing
        if (activeMedia != null) {
            return if (isExpandedManually) IslandState.ExpandedMusic(activeMedia)
            else IslandState.CompactMusic(activeMedia)
        }

        // Priority 7: Idle
        return IslandState.Idle
    }

    fun handleUserTap() {
        handleTransientEvent(IslandEvent.TapPill)
    }

    fun handleDismiss() {
        handleTransientEvent(IslandEvent.DismissExpanded)
    }

    fun handleToastTimeout() {
        handleTransientEvent(IslandEvent.ToastTimeout)
    }

    fun cleanup() {
        scope.cancel()
    }
}
