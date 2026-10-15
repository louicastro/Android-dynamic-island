package com.dynamicisland.android

import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelTest {

    @Test
    fun testDefaultSettings() {
        val settings = IslandSettings()
        assertTrue(settings.isEnabled)
        assertEquals(CutoutType.CENTER_PUNCH_HOLE, settings.cutoutType)
        assertEquals(0, settings.xOffsetDp)
        assertEquals(12, settings.yOffsetDp)
        assertEquals(120, settings.compactWidthDp)
        assertEquals(36, settings.compactHeightDp)
        assertEquals(340, settings.expandedWidthDp)
        assertEquals(160, settings.expandedHeightDp)
        assertEquals(IslandThemeMode.AMOLED_BLACK, settings.themeMode)
        assertTrue(settings.enableMusic)
        assertTrue(settings.enableNotifications)
        assertTrue(settings.enableCalls)
        assertTrue(settings.enableBattery)
    }

    @Test
    fun testCustomCutoutSettings() {
        val settings = IslandSettings().copy(
            cutoutType = CutoutType.LEFT_PUNCH_HOLE,
            xOffsetDp = 30,
            yOffsetDp = 15
        )
        assertEquals(CutoutType.LEFT_PUNCH_HOLE, settings.cutoutType)
        assertEquals(30, settings.xOffsetDp)
        assertEquals(15, settings.yOffsetDp)
    }
}
