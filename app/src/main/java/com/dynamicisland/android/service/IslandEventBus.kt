package com.dynamicisland.android.service

import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallSource
import com.dynamicisland.android.model.CallState
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
import java.util.UUID

/**
 * Authoritative event bus and state repository connecting notification, media,
 * call, battery, and timer subsystems to the island presentation layer.
 *
 * Implements:
 * - Isolation of caller identity to matching call sources only (Finding 39)
 * - Source-specific call termination preventing unintended global clears (Finding 40)
 * - Complete separation of durable StateFlows from one-time transient events (Finding 37)
 * - Strict replay = 0 for transient events with explicit unique event IDs
 * - Reconciled notification map operations for silent startup synchronization
 */
object IslandEventBus {

    // Persistent Authoritative StateFlows
    private val _batteryState = MutableStateFlow<BatteryInfo?>(null)
    val batteryState: StateFlow<BatteryInfo?> = _batteryState.asStateFlow()

    private val _mediaState = MutableStateFlow<MediaInfo?>(null)
    val mediaState: StateFlow<MediaInfo?> = _mediaState.asStateFlow()

    // Multi-source active calls (CELLULAR, VOIP)
    private val activeCalls = mutableMapOf<CallSource, CallInfo>()
    private val _callState = MutableStateFlow<CallInfo?>(null)
    val callState: StateFlow<CallInfo?> = _callState.asStateFlow()

    private val _timerState = MutableStateFlow<TimerInfo?>(null)
    val timerState: StateFlow<TimerInfo?> = _timerState.asStateFlow()

    private val _activeNotifications = MutableStateFlow<Map<String, NotificationItem>>(emptyMap())
    val activeNotifications: StateFlow<Map<String, NotificationItem>> = _activeNotifications.asStateFlow()

    // Transient Events with replay = 0 to prevent replaying old alerts on new subscribers
    private val _transientEvents = MutableSharedFlow<IslandEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val transientEvents: SharedFlow<IslandEvent> = _transientEvents.asSharedFlow()

    /**
     * Updates persistent battery state.
     * Does NOT duplicate into _transientEvents (Finding 37).
     */
    fun updateBattery(battery: BatteryInfo?) {
        _batteryState.value = battery
    }

    /**
     * Updates persistent media playback state.
     * Does NOT duplicate into _transientEvents (Finding 37).
     */
    fun updateMedia(media: MediaInfo?) {
        _mediaState.value = media
    }

    /**
     * Updates call state for a specific call source (CELLULAR or VOIP).
     * Merges ONLY with previous call record from the exact same source to prevent
     * cross-source identity contamination (Finding 39).
     */
    @Synchronized
    fun updateCall(call: CallInfo) {
        val previous = activeCalls[call.source]
        val merged = if (previous != null) {
            val isIncomingNameGeneric = call.callerName.isNullOrBlank() ||
                    call.callerName == "Cellular Call" ||
                    call.callerName == "Active Call" ||
                    call.callerName == "Phone" ||
                    call.callerName == "Incoming Call"

            val resolvedName = if (isIncomingNameGeneric && !previous.callerName.isNullOrBlank()) {
                previous.callerName
            } else {
                call.callerName
            }

            val resolvedStartTimestamp = if (call.callStartElapsedRealtime > 0L) {
                call.callStartElapsedRealtime
            } else {
                previous.callStartElapsedRealtime
            }

            call.copy(
                callerName = resolvedName,
                callerNumber = call.callerNumber ?: previous.callerNumber,
                avatar = call.avatar ?: previous.avatar,
                callStartElapsedRealtime = resolvedStartTimestamp,
                isVoip = if (call.source == CallSource.VOIP) true else call.isVoip,
                appName = call.appName ?: previous.appName,
                answerIntent = call.answerIntent ?: previous.answerIntent,
                declineIntent = call.declineIntent ?: previous.declineIntent
            )
        } else {
            call
        }

        activeCalls[call.source] = merged
        resolveAuthoritativeCall()
    }

    /**
     * Source-specific call termination (Finding 40).
     * Removes only the specified source, ensuring other active call sources remain intact.
     */
    @Synchronized
    fun removeCallBySource(source: CallSource) {
        activeCalls.remove(source)
        resolveAuthoritativeCall()
    }

    /**
     * Explicit global clear of all calls across all sources.
     * Reserved exclusively for full subsystem reset or test teardown (Finding 40).
     */
    @Synchronized
    fun resetAllCalls() {
        activeCalls.clear()
        _callState.value = null
    }

    @Synchronized
    private fun resolveAuthoritativeCall() {
        // Priority: Ringing call > Active offhook call > null
        val ringing = activeCalls.values.firstOrNull { it.state == CallState.RINGING }
        val offhook = activeCalls.values.firstOrNull { it.state == CallState.OFFHOOK }
        val authoritative = ringing ?: offhook

        _callState.value = authoritative
    }

    /**
     * Updates persistent timer/stopwatch state.
     * Does NOT duplicate into _transientEvents (Finding 37).
     */
    fun updateTimer(timer: TimerInfo?) {
        _timerState.value = timer
    }

    /**
     * Seeds or updates an active notification without triggering a transient alert toast.
     */
    fun upsertActiveNotification(notification: NotificationItem) {
        val updated = _activeNotifications.value.toMutableMap()
        updated[notification.key] = notification
        _activeNotifications.value = updated
    }

    fun seedNotification(notification: NotificationItem) {
        upsertActiveNotification(notification)
    }

    /**
     * Reconciles the active notifications map with the system tray snapshot on listener connect.
     * Prunes stale items and quietly seeds active ones without triggering toast animations.
     */
    fun reconcileActiveNotifications(currentKeys: Set<String>, updatedItems: List<NotificationItem>) {
        val updated = mutableMapOf<String, NotificationItem>()
        for (item in updatedItems) {
            if (currentKeys.contains(item.key)) {
                updated[item.key] = item
            }
        }
        _activeNotifications.value = updated
    }

    fun seedActiveNotifications(notifications: List<NotificationItem>) {
        val updated = _activeNotifications.value.toMutableMap()
        for (item in notifications) {
            updated[item.key] = item
        }
        _activeNotifications.value = updated
    }

    /**
     * Posts a new incoming notification, updating persistent state and emitting
     * a genuine transient toast alert event.
     */
    fun postNotification(notification: NotificationItem, eventId: String = UUID.randomUUID().toString()) {
        val updated = _activeNotifications.value.toMutableMap()
        updated[notification.key] = notification
        _activeNotifications.value = updated
        _transientEvents.tryEmit(IslandEvent.NotificationPosted(notification, eventId))
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

    fun emit(event: IslandEvent) {
        when (event) {
            is IslandEvent.BatteryUpdate -> updateBattery(event.battery)
            is IslandEvent.MediaUpdate -> updateMedia(event.media)
            is IslandEvent.CallUpdate -> updateCall(event.call)
            is IslandEvent.CallRemoved -> removeCallBySource(event.source)
            is IslandEvent.TimerUpdate -> updateTimer(event.timer)
            is IslandEvent.NotificationPosted -> postNotification(event.notification, event.eventId)
            is IslandEvent.NotificationRemoved -> removeNotification(event.key)
            else -> emitTransient(event)
        }
    }
}
