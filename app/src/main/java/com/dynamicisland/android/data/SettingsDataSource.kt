package com.dynamicisland.android.data

import com.dynamicisland.android.model.IslandSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction for loading, observing, and updating application settings.
 * Enables decoupling of state managers and reducers from Android SharedPreferences for clean unit testing.
 */
interface SettingsDataSource {
    val settingsFlow: StateFlow<IslandSettings>
    fun getSettings(): IslandSettings
    fun updateSettings(update: (IslandSettings) -> IslandSettings)
}
