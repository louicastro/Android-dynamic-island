package com.dynamicisland.android

import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo
import com.dynamicisland.android.service.IslandEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IslandStateManagerTest {

    @Before
    fun setUp() {
        // Reset EventBus states before each test
        IslandEventBus.updateCall(null)
        IslandEventBus.updateMedia(null)
        IslandEventBus.updateTimer(null)
    }

    @Test
    fun testCallStatePrioritization() {
        val ringingCall = CallInfo(
            callerName = "Alice",
            state = CallState.RINGING,
            isVoip = true
        )
        IslandEventBus.updateCall(ringingCall)
        assertEquals(ringingCall, IslandEventBus.callState.value)

        val activeCall = CallInfo(
            callerName = "Bob",
            state = CallState.OFFHOOK,
            callDurationSeconds = 42L
        )
        IslandEventBus.updateCall(activeCall)
        assertEquals("00:42", IslandEventBus.callState.value?.formattedDuration)
    }

    @Test
    fun testAuthoritativeStatePersistence() {
        // Ensure state is retained in StateFlow regardless of subscriber timing
        val media = MediaInfo(
            title = "Blinding Lights",
            artist = "The Weeknd",
            isPlaying = true
        )
        IslandEventBus.updateMedia(media)
        assertEquals(media, IslandEventBus.mediaState.value)

        val battery = BatteryInfo(
            level = 85,
            isCharging = true,
            chargingSpeedType = "Super Fast Charging"
        )
        IslandEventBus.updateBattery(battery)
        assertEquals(85, IslandEventBus.batteryState.value?.level)
    }

    @Test
    fun testNotificationDirectReplyModel() {
        val replyAction = com.dynamicisland.android.model.NotificationActionItem(
            title = "Reply",
            intent = null,
            isDirectReply = true,
            remoteInputResultKey = "reply_key",
            remoteInputLabel = "Type a message"
        )
        val notif = NotificationItem(
            key = "key_1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Sarah",
            text = "Are you on your way?",
            actions = listOf(replyAction)
        )
        assertTrue(notif.directReplyAction?.isDirectReply == true)
        assertEquals("reply_key", notif.directReplyAction?.remoteInputResultKey)
    }

    @Test
    fun testTimerControlsModel() {
        val timer = TimerInfo(
            label = "Eggs",
            totalSeconds = 180L,
            remainingSeconds = 60L,
            isRunning = true
        )
        assertEquals("1:00", timer.formattedRemaining)
        assertEquals(0.666f, timer.progressFraction, 0.01f)
    }
}
