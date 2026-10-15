package com.dynamicisland.android.service

import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative event bus and state repository connecting notification, media,
 * call, battery, and timer trackers to the island presentation layer.
 *
 * Uses StateFlow for persistent states to guarantee zero event loss regardless
 * of subscriber connection timing, and replay-cached SharedFlow for transient gestures.
 */
object IslandEventBus {

    // Persistent Authoritative StateFlows
    private val _batteryState = MutableStateFlow<BatteryInfo?>(null)
    val batteryState: StateFlow<BatteryInfo?> = _batteryState.asStateFlow()

    private val _mediaState = MutableStateFlow<MediaInfo?>(null)
    val mediaState: StateFlow<MediaInfo?> = _mediaState.asStateFlow()

    private val _callState = MutableStateFlow<CallInfo?>(null)
    val callState: StateFlow<CallInfo?> = _callState.asStateFlow()

    private val _timerState = MutableStateFlow<TimerInfo?>(null)
    val timerState: StateFlow<TimerInfo?> = _timerState.asStateFlow()

    private val _activeNotifications = MutableStateFlow<Map<String, NotificationItem>>(emptyMap())
    val activeNotifications: StateFlow<Map<String, NotificationItem>> = _activeNotifications.asStateFlow()

    // Transient Events (User taps, auto-dismiss, temporary toasts)
    private val _transientEvents = MutableSharedFlow<IslandEvent>(
        replay = 1,
        extraBufferCapacity = 64
    )
    val transientEvents: SharedFlow<IslandEvent> = _transientEvents.asSharedFlow()

    fun updateBattery(battery: BatteryInfo) {
        _batteryState.value = battery
        _transientEvents.tryEmit(IslandEvent.BatteryUpdate(battery))
    }

    fun updateMedia(media: MediaInfo?) {
        _mediaState.value = media
        _transientEvents.tryEmit(IslandEvent.MediaUpdate(media))
    }

    fun updateCall(call: CallInfo?) {
        _callState.value = call
        if (call != null) {
            _transientEvents.tryEmit(IslandEvent.CallUpdate(call))
        }
    }

    fun updateTimer(timer: TimerInfo?) {
        _timerState.value = timer
        _transientEvents.tryEmit(IslandEvent.TimerUpdate(timer))
    }

    fun postNotification(notification: NotificationItem) {
        val updated = _activeNotifications.value.toMutableMap()
        updated[notification.key] = notification
        _activeNotifications.value = updated
        _transientEvents.tryEmit(IslandEvent.NotificationPosted(notification))
    }

    fun removeNotification(key: String) {
        val updated = _activeNotifications.value.toMutableMap()
        updated.remove(key)
        _activeNotifications.value = updated
        _transientEvents.tryEmit(IslandEvent.NotificationRemoved(key))
    }

    fun emitTransient(event: IslandEvent) {
        _transientEvents.tryEmit(event)
    }

    /**
     * Dispatcher method providing backward compatibility and central dispatching.
     */
    fun emit(event: IslandEvent) {
        when (event) {
            is IslandEvent.BatteryUpdate -> updateBattery(event.battery)
            is IslandEvent.MediaUpdate -> updateMedia(event.media)
            is IslandEvent.CallUpdate -> updateCall(event.call)
            is IslandEvent.TimerUpdate -> updateTimer(event.timer)
            is IslandEvent.NotificationPosted -> postNotification(event.notification)
            is IslandEvent.NotificationRemoved -> removeNotification(event.key)
            else -> emitTransient(event)
        }
    }
}
