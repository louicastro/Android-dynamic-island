package com.dynamicisland.android

import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallSource
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo
import com.dynamicisland.android.service.IslandEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IslandStateManagerTest {

    @Before
    fun setUp() {
        // Reset EventBus states before each test using explicit resetAllCalls
        IslandEventBus.resetAllCalls()
        IslandEventBus.updateMedia(null)
        IslandEventBus.updateTimer(null)
    }

    @Test
    fun testCallStatePrioritization() {
        val ringingCall = CallInfo(
            callerName = "Alice",
            state = CallState.RINGING,
            source = CallSource.VOIP,
            isVoip = true
        )
        IslandEventBus.updateCall(ringingCall)
        assertEquals(ringingCall, IslandEventBus.callState.value)

        val activeCall = CallInfo(
            callerName = "Bob",
            state = CallState.OFFHOOK,
            source = CallSource.CELLULAR,
            callDurationSeconds = 42L
        )
        IslandEventBus.updateCall(activeCall)
        assertEquals("00:42", IslandEventBus.callState.value?.formattedDuration)
    }

    @Test
    fun testCrossSourceCallIdentityIsolation() {
        // Finding 39: Ensure a new call from a different source NEVER inherits identity from current call
        val cellularCall = CallInfo(
            callerName = "Alice",
            callerNumber = "+1 (555) 012-3456",
            state = CallState.OFFHOOK,
            source = CallSource.CELLULAR,
            appName = "Phone"
        )
        IslandEventBus.updateCall(cellularCall)
        assertEquals("Alice", IslandEventBus.callState.value?.callerName)

        // An incoming VoIP call arrives without a caller name
        val voipCall = CallInfo(
            callerName = null,
            callerNumber = null,
            state = CallState.RINGING,
            source = CallSource.VOIP,
            appName = "WhatsApp"
        )
        IslandEventBus.updateCall(voipCall)

        // The VoIP call is authoritative (RINGING > OFFHOOK), but must NEVER inherit "Alice" from Cellular!
        val authoritative = IslandEventBus.callState.value
        assertNotNull(authoritative)
        assertEquals(CallSource.VOIP, authoritative?.source)
        assertNull(authoritative?.callerName)
        assertNull(authoritative?.callerNumber)
    }

    @Test
    fun testMultiSourceCallRemovalDoesNotClearCellularCall() {
        // 1. Cellular call is active
        val cellularCall = CallInfo(
            callerName = "David",
            state = CallState.OFFHOOK,
            source = CallSource.CELLULAR,
            appName = "Phone",
            callDurationSeconds = 10L
        )
        IslandEventBus.updateCall(cellularCall)
        assertEquals(CallSource.CELLULAR, IslandEventBus.callState.value?.source)

        // 2. VoIP incoming ringing call notification arrives
        val voipCall = CallInfo(
            callerName = "WhatsApp Group",
            state = CallState.RINGING,
            source = CallSource.VOIP,
            isVoip = true,
            appName = "WhatsApp"
        )
        IslandEventBus.updateCall(voipCall)
        assertEquals(CallSource.VOIP, IslandEventBus.callState.value?.source)

        // 3. VoIP notification dismissed/removed
        IslandEventBus.removeCallBySource(CallSource.VOIP)

        // 4. Assert that cellular call is STILL active and authoritative
        val remaining = IslandEventBus.callState.value
        assertNotNull(remaining)
        assertEquals(CallSource.CELLULAR, remaining?.source)
        assertEquals("David", remaining?.callerName)
        assertEquals(CallState.OFFHOOK, remaining?.state)
    }

    @Test
    fun testExplicitResetAllCalls() {
        // Finding 40: Calling resetAllCalls() clears all sources explicitly
        IslandEventBus.updateCall(CallInfo(callerName = "A", source = CallSource.CELLULAR))
        IslandEventBus.updateCall(CallInfo(callerName = "B", source = CallSource.VOIP))
        assertNotNull(IslandEventBus.callState.value)

        IslandEventBus.resetAllCalls()
        assertNull(IslandEventBus.callState.value)
    }

    @Test
    fun testAuthoritativeStatePersistence() {
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
            chargingSpeedType = "Fast Charging (AC)"
        )
        IslandEventBus.updateBattery(battery)
        assertEquals(85, IslandEventBus.batteryState.value?.level)
    }

    @Test
    fun testPersistentUpdatesDoNotDuplicateTransientEvents() {
        // Finding 37: Verify updateBattery, updateMedia, updateTimer, updateCall do NOT emit into transientEvents
        IslandEventBus.updateBattery(BatteryInfo(level = 95, isCharging = true))
        IslandEventBus.updateMedia(MediaInfo(title = "Starboy", artist = "The Weeknd"))
        IslandEventBus.updateTimer(TimerInfo(label = "Cooking", remainingSeconds = 300L))
        IslandEventBus.updateCall(CallInfo(callerName = "Alex", state = CallState.OFFHOOK))

        // Persistent flows are updated authoritatively
        assertEquals(95, IslandEventBus.batteryState.value?.level)
        assertEquals("Starboy", IslandEventBus.mediaState.value?.title)
        assertEquals("Cooking", IslandEventBus.timerState.value?.label)
        assertEquals("Alex", IslandEventBus.callState.value?.callerName)

        // Transient flow replay cache remains completely empty
        assertTrue(IslandEventBus.transientEvents.replayCache.isEmpty())
    }

    @Test
    fun testUnknownBatteryLevelHandling() {
        val unknownBattery = BatteryInfo(level = -1, isCharging = false)
        assertTrue(unknownBattery.isUnknown)
        assertEquals("--%", unknownBattery.displayPercentage)

        val validBattery = BatteryInfo(level = 68, isCharging = true)
        assertFalse(validBattery.isUnknown)
        assertEquals("68%", validBattery.displayPercentage)
    }

    @Test
    fun testTimerMultiFormatAndUnknownDuration() {
        // Unknown duration (parsing failed or stopwatch)
        val unknownTimer = TimerInfo(
            label = "Baking",
            remainingSeconds = -1L,
            rawTimeText = "In Progress"
        )
        assertTrue(unknownTimer.isUnknownDuration)
        assertEquals("In Progress", unknownTimer.formattedRemaining)

        // HH:MM:SS format
        val longTimer = TimerInfo(
            label = "Marathon",
            totalSeconds = 7200L,
            remainingSeconds = 3665L // 1 hour, 1 minute, 5 seconds
        )
        assertFalse(longTimer.isUnknownDuration)
        assertEquals("1:01:05", longTimer.formattedRemaining)
    }

    @Test
    fun testReconcileActiveNotificationsPruning() {
        val notif1 = NotificationItem(key = "k1", packageName = "p1", appName = "App 1", title = "T1", text = "X1")
        val notif2 = NotificationItem(key = "k2", packageName = "p2", appName = "App 2", title = "T2", text = "X2")
        val notif3 = NotificationItem(key = "k3", packageName = "p3", appName = "App 3", title = "T3", text = "X3")

        IslandEventBus.seedActiveNotifications(listOf(notif1, notif2, notif3))
        assertEquals(3, IslandEventBus.activeNotifications.value.size)

        // Reconcile with active system snapshot: only k2 is currently in system tray
        IslandEventBus.reconcileActiveNotifications(setOf("k2"), listOf(notif2))
        assertEquals(1, IslandEventBus.activeNotifications.value.size)
        assertTrue(IslandEventBus.activeNotifications.value.containsKey("k2"))
        assertFalse(IslandEventBus.activeNotifications.value.containsKey("k1"))
        assertFalse(IslandEventBus.activeNotifications.value.containsKey("k3"))
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
    fun testCallMergingPreservesCallerName() {
        val specificCall = CallInfo(
            callerName = "Sarah Miller",
            callerNumber = "+1 (555) 019-2834",
            state = CallState.RINGING,
            source = CallSource.VOIP,
            isVoip = true,
            appName = "WhatsApp"
        )
        IslandEventBus.updateCall(specificCall)
        assertEquals("Sarah Miller", IslandEventBus.callState.value?.callerName)

        val genericCall = CallInfo(
            callerName = "Active Call",
            callerNumber = null,
            state = CallState.OFFHOOK,
            source = CallSource.VOIP,
            callDurationSeconds = 15L,
            isVoip = true,
            appName = "WhatsApp"
        )
        IslandEventBus.updateCall(genericCall)

        // Specific caller name and number are preserved, while state and duration are updated
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
