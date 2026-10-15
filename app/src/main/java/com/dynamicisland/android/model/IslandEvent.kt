package com.dynamicisland.android.model

import java.util.UUID

/**
 * Events passed into the Dynamic Island state coordinator and unified reducer.
 *
 * Transient events carry a unique [eventId] to enable targeted timeouts and prevent
 * race conditions where a timeout for one alert removes another.
 */
sealed class IslandEvent {
    // Persistent subsystem updates
    data class MediaUpdate(val media: MediaInfo?) : IslandEvent()
    data class CallUpdate(val call: CallInfo) : IslandEvent()
    data class CallRemoved(val source: CallSource) : IslandEvent()
    data class TimerUpdate(val timer: TimerInfo?) : IslandEvent()

    // Transient alerts with explicit tracking IDs
    data class NotificationPosted(
        val notification: NotificationItem,
        val eventId: String = UUID.randomUUID().toString()
    ) : IslandEvent()

    data class NotificationRemoved(val key: String) : IslandEvent()

    data class BatteryUpdate(
        val battery: BatteryInfo,
        val eventId: String = UUID.randomUUID().toString()
    ) : IslandEvent()

    data class RingerModeToggle(
        val isMuted: Boolean,
        val eventId: String = UUID.randomUUID().toString()
    ) : IslandEvent()

    data class BluetoothConnect(
        val deviceName: String,
        val batteryLevel: Int? = null,
        val eventId: String = UUID.randomUUID().toString()
    ) : IslandEvent()

    // User interactions and discrete dismissal actions
    data object TapPill : IslandEvent()
    data object LongPressPill : IslandEvent()
    data object CollapseExpanded : IslandEvent()
    data class DismissCurrentToast(val toastId: String) : IslandEvent()
    data class DismissNotificationToast(val key: String) : IslandEvent()
    data class ToastTimeout(val eventId: String) : IslandEvent()
    data object ClearAllTransient : IslandEvent()

    // Backward-compatibility aliases
    data object DismissExpanded : IslandEvent()
}
