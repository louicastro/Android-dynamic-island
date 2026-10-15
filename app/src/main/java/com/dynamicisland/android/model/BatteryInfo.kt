package com.dynamicisland.android.model

/**
 * Encapsulates battery level, charging status, and power connection type.
 */
data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isPlugged: Boolean = false,
    val chargingSpeedType: String = "Normal" // "Fast", "Super Fast", "Wireless", "USB"
) {
    val isLow: Boolean
        get() = level <= 20 && !isCharging

    val isCritical: Boolean
        get() = level <= 10 && !isCharging
}
