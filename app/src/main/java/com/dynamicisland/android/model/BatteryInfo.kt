package com.dynamicisland.android.model

/**
 * Encapsulates battery level, charging status, and power connection type.
 * Accurately models unknown levels without assuming a fully charged state.
 */
data class BatteryInfo(
    val level: Int = -1, // -1 represents unknown/unresolved battery level
    val isCharging: Boolean = false,
    val isPlugged: Boolean = false,
    val chargingSpeedType: String = "Charging"
) {
    val isUnknown: Boolean
        get() = level !in 0..100

    val isLow: Boolean
        get() = level in 0..20 && !isCharging

    val isCritical: Boolean
        get() = level in 0..10 && !isCharging

    val displayPercentage: String
        get() = if (isUnknown) "--%" else "$level%"
}
