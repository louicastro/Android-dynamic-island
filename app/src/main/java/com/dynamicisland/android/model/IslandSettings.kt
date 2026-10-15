package com.dynamicisland.android.model

enum class CutoutType {
    CENTER_PUNCH_HOLE,
    LEFT_PUNCH_HOLE,
    RIGHT_PUNCH_HOLE,
    WATERDROP_NOTCH,
    CUSTOM
}

enum class IslandThemeMode {
    AMOLED_BLACK,
    FROSTED_GLASS,
    AMBIENT_ACCENT
}

/**
 * User customizable settings for the island overlay position, size, and features.
 */
data class IslandSettings(
    val isEnabled: Boolean = true,
    val cutoutType: CutoutType = CutoutType.CENTER_PUNCH_HOLE,
    val xOffsetDp: Int = 0,
    val yOffsetDp: Int = 12,
    val compactWidthDp: Int = 120,
    val compactHeightDp: Int = 36,
    val expandedWidthDp: Int = 340,
    val expandedHeightDp: Int = 160,
    val cornerRadiusDp: Int = 24,
    val enableMusic: Boolean = true,
    val enableNotifications: Boolean = true,
    val enableCalls: Boolean = true,
    val enableBattery: Boolean = true,
    val enableTimer: Boolean = true,
    val enableRinger: Boolean = true,
    val autoDismissSeconds: Int = 5,
    val springStiffness: Float = 600f,
    val springDampingRatio: Float = 0.78f,
    val themeMode: IslandThemeMode = IslandThemeMode.AMOLED_BLACK,
    val startOnBoot: Boolean = true,
    val vibrationFeedback: Boolean = true
)
