package com.dynamicisland.android

import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandStateTest {

    @Test
    fun testMediaProgressCalculation() {
        val media = MediaInfo(
            title = "Song",
            artist = "Artist",
            durationMs = 200000L,
            currentPositionMs = 50000L,
            isPlaying = true
        )
        assertEquals(0.25f, media.progress, 0.001f)
    }

    @Test
    fun testCallFormattedDuration() {
        val call = CallInfo(
            callerName = "John Doe",
            state = CallState.OFFHOOK,
            callDurationSeconds = 125L
        )
        assertEquals("02:05", call.formattedDuration)
        assertTrue(call.isInCall)
        assertFalse(call.isIncomingRinging)
    }

    @Test
    fun testBatteryThresholds() {
        val normalBattery = BatteryInfo(level = 80, isCharging = false)
        assertFalse(normalBattery.isLow)
        assertFalse(normalBattery.isCritical)

        val lowBattery = BatteryInfo(level = 18, isCharging = false)
        assertTrue(lowBattery.isLow)
        assertFalse(lowBattery.isCritical)

        val criticalBattery = BatteryInfo(level = 5, isCharging = false)
        assertTrue(criticalBattery.isLow)
        assertTrue(criticalBattery.isCritical)

        val chargingLowBattery = BatteryInfo(level = 5, isCharging = true)
        assertFalse(chargingLowBattery.isLow)
    }

    @Test
    fun testIslandStateProperties() {
        val idle = IslandState.Idle
        assertFalse(idle.isExpanded)
        assertFalse(idle.isToast)
        assertFalse(idle.isSplit)

        val expandedMusic = IslandState.ExpandedMusic(
            MediaInfo(title = "T", artist = "A")
        )
        assertTrue(expandedMusic.isExpanded)
        assertFalse(expandedMusic.isToast)

        val toast = IslandState.NotificationToast(
            NotificationItem(
                key = "1",
                packageName = "com.test",
                appName = "Test",
                title = "Hello",
                text = "World"
            )
        )
        assertTrue(toast.isToast)
        assertFalse(toast.isExpanded)

        val split = IslandState.SplitIsland(
            primaryState = IslandState.CompactMusic(MediaInfo(title = "T", artist = "A")),
            secondaryState = IslandState.CompactBattery(BatteryInfo(level = 90, isCharging = true))
        )
        assertTrue(split.isSplit)
    }

    @Test
    fun testTimerInfoAndStateProperties() {
        val timer = com.dynamicisland.android.model.TimerInfo(
            label = "Timer",
            totalSeconds = 300L,
            remainingSeconds = 125L
        )
        assertEquals("2:05", timer.formattedRemaining)

        val compactTimer = IslandState.CompactTimer(timer)
        assertFalse(compactTimer.isExpanded)

        val expandedTimer = IslandState.ExpandedTimer(timer)
        assertTrue(expandedTimer.isExpanded)

        val ringerToast = IslandState.RingerToast(isMuted = true)
        assertTrue(ringerToast.isToast)

        val bluetoothToast = IslandState.BluetoothToast("AirPods Pro")
        assertTrue(bluetoothToast.isToast)
    }
}
