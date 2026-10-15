package com.dynamicisland.android.data

import android.content.Context
import android.content.SharedPreferences
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<IslandSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): IslandSettings = _settingsFlow.value

    fun updateSettings(update: (IslandSettings) -> IslandSettings) {
        val updated = update(_settingsFlow.value)
        _settingsFlow.value = updated
        saveSettings(updated)
    }

    private fun loadSettings(): IslandSettings {
        val isEnabled = prefs.getBoolean(KEY_IS_ENABLED, true)
        val cutoutTypeOrdinal = prefs.getInt(KEY_CUTOUT_TYPE, CutoutType.CENTER_PUNCH_HOLE.ordinal)
        val cutoutType = CutoutType.entries.getOrElse(cutoutTypeOrdinal) { CutoutType.CENTER_PUNCH_HOLE }
        val xOffset = prefs.getInt(KEY_X_OFFSET, 0)
        val yOffset = prefs.getInt(KEY_Y_OFFSET, 12)
        val compactWidth = prefs.getInt(KEY_COMPACT_WIDTH, 120)
        val compactHeight = prefs.getInt(KEY_COMPACT_HEIGHT, 36)
        val expandedWidth = prefs.getInt(KEY_EXPANDED_WIDTH, 340)
        val expandedHeight = prefs.getInt(KEY_EXPANDED_HEIGHT, 160)
        val cornerRadius = prefs.getInt(KEY_CORNER_RADIUS, 24)

        val enableMusic = prefs.getBoolean(KEY_ENABLE_MUSIC, true)
        val enableNotifications = prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, true)
        val enableCalls = prefs.getBoolean(KEY_ENABLE_CALLS, true)
        val enableBattery = prefs.getBoolean(KEY_ENABLE_BATTERY, true)
        val autoDismissSeconds = prefs.getInt(KEY_AUTO_DISMISS, 5)

        val springStiffness = prefs.getFloat(KEY_SPRING_STIFFNESS, 300f)
        val springDampingRatio = prefs.getFloat(KEY_SPRING_DAMPING, 0.75f)

        val themeOrdinal = prefs.getInt(KEY_THEME_MODE, IslandThemeMode.AMOLED_BLACK.ordinal)
        val themeMode = IslandThemeMode.entries.getOrElse(themeOrdinal) { IslandThemeMode.AMOLED_BLACK }

        val startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, true)
        val vibrationFeedback = prefs.getBoolean(KEY_VIBRATION, true)

        return IslandSettings(
            isEnabled = isEnabled,
            cutoutType = cutoutType,
            xOffsetDp = xOffset,
            yOffsetDp = yOffset,
            compactWidthDp = compactWidth,
            compactHeightDp = compactHeight,
            expandedWidthDp = expandedWidth,
            expandedHeightDp = expandedHeight,
            cornerRadiusDp = cornerRadius,
            enableMusic = enableMusic,
            enableNotifications = enableNotifications,
            enableCalls = enableCalls,
            enableBattery = enableBattery,
            autoDismissSeconds = autoDismissSeconds,
            springStiffness = springStiffness,
            springDampingRatio = springDampingRatio,
            themeMode = themeMode,
            startOnBoot = startOnBoot,
            vibrationFeedback = vibrationFeedback
        )
    }

    private fun saveSettings(settings: IslandSettings) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ENABLED, settings.isEnabled)
            putInt(KEY_CUTOUT_TYPE, settings.cutoutType.ordinal)
            putInt(KEY_X_OFFSET, settings.xOffsetDp)
            putInt(KEY_Y_OFFSET, settings.yOffsetDp)
            putInt(KEY_COMPACT_WIDTH, settings.compactWidthDp)
            putInt(KEY_COMPACT_HEIGHT, settings.compactHeightDp)
            putInt(KEY_EXPANDED_WIDTH, settings.expandedWidthDp)
            putInt(KEY_EXPANDED_HEIGHT, settings.expandedHeightDp)
            putInt(KEY_CORNER_RADIUS, settings.cornerRadiusDp)
            putBoolean(KEY_ENABLE_MUSIC, settings.enableMusic)
            putBoolean(KEY_ENABLE_NOTIFICATIONS, settings.enableNotifications)
            putBoolean(KEY_ENABLE_CALLS, settings.enableCalls)
            putBoolean(KEY_ENABLE_BATTERY, settings.enableBattery)
            putInt(KEY_AUTO_DISMISS, settings.autoDismissSeconds)
            putFloat(KEY_SPRING_STIFFNESS, settings.springStiffness)
            putFloat(KEY_SPRING_DAMPING, settings.springDampingRatio)
            putInt(KEY_THEME_MODE, settings.themeMode.ordinal)
            putBoolean(KEY_START_ON_BOOT, settings.startOnBoot)
            putBoolean(KEY_VIBRATION, settings.vibrationFeedback)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "dynamic_island_prefs"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_CUTOUT_TYPE = "cutout_type"
        private const val KEY_X_OFFSET = "x_offset"
        private const val KEY_Y_OFFSET = "y_offset"
        private const val KEY_COMPACT_WIDTH = "compact_width"
        private const val KEY_COMPACT_HEIGHT = "compact_height"
        private const val KEY_EXPANDED_WIDTH = "expanded_width"
        private const val KEY_EXPANDED_HEIGHT = "expanded_height"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_ENABLE_MUSIC = "enable_music"
        private const val KEY_ENABLE_NOTIFICATIONS = "enable_notifications"
        private const val KEY_ENABLE_CALLS = "enable_calls"
        private const val KEY_ENABLE_BATTERY = "enable_battery"
        private const val KEY_AUTO_DISMISS = "auto_dismiss"
        private const val KEY_SPRING_STIFFNESS = "spring_stiffness"
        private const val KEY_SPRING_DAMPING = "spring_damping"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_VIBRATION = "vibration"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
