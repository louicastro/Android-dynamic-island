package com.dynamicisland.android.model

/**
 * Events passed into the Dynamic Island state coordinator.
 */
sealed class IslandEvent {
    data class MediaUpdate(val media: MediaInfo?) : IslandEvent()
    data class NotificationPosted(val notification: NotificationItem) : IslandEvent()
    data class NotificationRemoved(val key: String) : IslandEvent()
    data class CallUpdate(val call: CallInfo) : IslandEvent()
    data class BatteryUpdate(val battery: BatteryInfo) : IslandEvent()
    data class TimerUpdate(val timer: TimerInfo?) : IslandEvent()
    data class RingerModeToggle(val isMuted: Boolean) : IslandEvent()
    data class BluetoothConnect(val deviceName: String, val batteryLevel: Int? = null) : IslandEvent()
    data object TapPill : IslandEvent()
    data object LongPressPill : IslandEvent()
    data object DismissExpanded : IslandEvent()
    data object ToastTimeout : IslandEvent()
}

