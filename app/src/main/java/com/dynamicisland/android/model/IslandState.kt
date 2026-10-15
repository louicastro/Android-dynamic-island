package com.dynamicisland.android.model

/**
 * Visual display states of the Dynamic Island overlay.
 */
sealed class IslandState {
    // Hidden or minimal camera cutout dot
    data object Idle : IslandState()

    // Compact states (pill sitting around camera cutout)
    data class CompactMusic(val media: MediaInfo) : IslandState()
    data class CompactCall(val call: CallInfo) : IslandState()
    data class CompactBattery(val battery: BatteryInfo) : IslandState()
    data class CompactTimer(val timer: TimerInfo) : IslandState()

    // Multi-tasking split island: Main pill + auxiliary circular bubble
    data class SplitIsland(
        val primaryState: IslandState,
        val secondaryState: IslandState
    ) : IslandState()

    // Temporary floating toast / alert that auto-dismisses
    data class NotificationToast(val notification: NotificationItem) : IslandState()
    data class ChargingToast(val battery: BatteryInfo) : IslandState()
    data class LowBatteryToast(val battery: BatteryInfo) : IslandState()
    data class RingerToast(val isMuted: Boolean) : IslandState()
    data class BluetoothToast(val deviceName: String, val batteryLevel: Int? = null) : IslandState()

    // Expanded interactive cards (triggered by tap / long press)
    data class ExpandedMusic(val media: MediaInfo) : IslandState()
    data class ExpandedCall(val call: CallInfo) : IslandState()
    data class ExpandedNotification(val notification: NotificationItem) : IslandState()
    data class ExpandedBattery(val battery: BatteryInfo) : IslandState()
    data class ExpandedLowBattery(val battery: BatteryInfo) : IslandState()
    data class ExpandedTimer(val timer: TimerInfo) : IslandState()
    data class ExpandedRinger(val isMuted: Boolean) : IslandState()

    val isExpanded: Boolean
        get() = this is ExpandedMusic || this is ExpandedCall ||
                this is ExpandedNotification || this is ExpandedBattery ||
                this is ExpandedLowBattery || this is ExpandedTimer ||
                this is ExpandedRinger

    val isToast: Boolean
        get() = this is NotificationToast || this is ChargingToast ||
                this is LowBatteryToast || this is RingerToast ||
                this is BluetoothToast

    val isSplit: Boolean
        get() = this is SplitIsland
}
