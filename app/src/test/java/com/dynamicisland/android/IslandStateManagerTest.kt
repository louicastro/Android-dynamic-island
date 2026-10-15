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
    fun testTimerControlsModelWithReset() {
        val timer = TimerInfo(
            label = "Eggs",
            totalSeconds = 180L,
            remainingSeconds = 60L,
            isRunning = true,
            resetIntent = null
        )
        assertEquals("1:00", timer.formattedRemaining)
        assertEquals(0.666f, timer.progressFraction, 0.01f)
        assertFalse(timer.hasExecutableControls)
    }

    @Test
    fun testNoReplayOfTransientEvents() {
        // Verify that replay = 0 prevents caching old transient events
        assertTrue(IslandEventBus.transientEvents.replayCache.isEmpty())
    }

    @Test
    fun testSeedingNotificationWithoutToast() {
        val notif = NotificationItem(
            key = "seed_key",
            packageName = "com.slack",
            appName = "Slack",
            title = "Team",
            text = "Deployment successful"
        )
        // Seeding updates active notifications without emitting to transientEvents
        IslandEventBus.seedNotification(notif)
        assertTrue(IslandEventBus.activeNotifications.value.containsKey("seed_key"))
        assertTrue(IslandEventBus.transientEvents.replayCache.isEmpty())
    }

    @Test
    fun testCallMergingPreservesCallerName() {
        // 1. Initial VoIP/Notification call arrives with specific caller name
        val specificCall = CallInfo(
            callerName = "Sarah Miller",
            callerNumber = "+1 (555) 019-2834",
            state = CallState.RINGING,
            isVoip = true,
            appName = "WhatsApp"
        )
        IslandEventBus.updateCall(specificCall)
        assertEquals("Sarah Miller", IslandEventBus.callState.value?.callerName)

        // 2. Cellular duration counter emits a generic label ("Active Call")
        val genericCall = CallInfo(
            callerName = "Active Call",
            callerNumber = null,
            state = CallState.OFFHOOK,
            callDurationSeconds = 15L,
            isVoip = false,
            appName = "Phone"
        )
        IslandEventBus.updateCall(genericCall)

        // The specific caller name and number are preserved, while state and duration are updated!
        assertEquals("Sarah Miller", IslandEventBus.callState.value?.callerName)
        assertEquals("+1 (555) 019-2834", IslandEventBus.callState.value?.callerNumber)
        assertEquals(15L, IslandEventBus.callState.value?.callDurationSeconds)
    }

    @Test
    fun testLowBatteryThresholds() {
        val normal = BatteryInfo(level = 45, isCharging = false)
        assertFalse(normal.isLow)
        assertFalse(normal.isCritical)

        val low = BatteryInfo(level = 18, isCharging = false)
        assertTrue(low.isLow)
        assertFalse(low.isCritical)

        val critical = BatteryInfo(level = 8, isCharging = false)
        assertTrue(critical.isLow)
        assertTrue(critical.isCritical)

        // When charging, low battery warnings are suppressed
        val chargingLow = BatteryInfo(level = 15, isCharging = true)
        assertFalse(chargingLow.isLow)
    }
}
