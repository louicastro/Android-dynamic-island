package com.dynamicisland.android.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallSource
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.NotificationActionItem
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo

/**
 * Intercepts notifications, active media sessions, in-call alerts, and timers.
 *
 * Implements:
 * - Silent startup notification reconciliation without alert toast spam
 * - Safe call-source distinction (Cellular vs VoIP) preserving cellular telephony calls
 * - Semantic action detection (SEMANTIC_ACTION_CALL_ANSWER/DECLINE/REPLY)
 * - Validated multi-format timer parsing (HH:MM:SS, MM:SS) without synthetic 60s fallbacks
 * - Memory-clamped icon extraction (<=128x128)
 */
class DynamicNotificationListenerService : NotificationListenerService() {

    private lateinit var mediaSessionTracker: MediaSessionTracker

    override fun onCreate() {
        super.onCreate()
        mediaSessionTracker = MediaSessionTracker.getInstance(this)
        currentInstance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance == this) {
            currentInstance = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        currentInstance = this
        val component = ComponentName(this, DynamicNotificationListenerService::class.java)
        mediaSessionTracker.start(component)

        // Reconcile existing notifications silently into persistent state WITHOUT triggering alert toasts
        try {
            val active = activeNotifications
            if (active != null) {
                val currentKeys = active.map { it.key }.toSet()
                val seededItems = mutableListOf<NotificationItem>()

                for (sbn in active) {
                    if (sbn.packageName == packageName) continue
                    val notification = sbn.notification ?: continue
                    if (shouldFilterNotification(notification, sbn.packageName)) continue

                    // Parse without triggering transient alert toasts
                    val parsedItem = parseNotificationItem(sbn)
                    if (parsedItem != null) {
                        seededItems.add(parsedItem)
                    }

                    // Also check if an active call or timer was already in the tray
                    checkCallNotification(sbn, notification)
                    checkTimerNotification(sbn, notification)
                }

                IslandEventBus.reconcileActiveNotifications(currentKeys, seededItems)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling active notifications on connect", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionTracker.stop()
        if (currentInstance == this) {
            currentInstance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        if (shouldFilterNotification(notification, sbn.packageName)) return

        // 1. Process Call notifications
        if (checkCallNotification(sbn, notification)) {
            return
        }

        // 2. Process Timer notifications
        if (checkTimerNotification(sbn, notification)) {
            return
        }

        // 3. Process Standard alerting notification
        val item = parseNotificationItem(sbn) ?: return
        IslandEventBus.postNotification(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return

        // When a call notification is removed, only remove the VoIP/notification call source.
        // If a cellular call is still active via CallTracker, it will NOT be cleared!
        val isCall = sbn.notification?.category == Notification.CATEGORY_CALL ||
                isDialerPackage(sbn.packageName)

        if (isCall) {
            val source = if (isDialerPackage(sbn.packageName)) CallSource.CELLULAR else CallSource.VOIP
            IslandEventBus.removeCallBySource(source)
        }

        // If a timer notification was dismissed, reset timer state
        if (isTimerNotification(sbn)) {
            IslandEventBus.updateTimer(null)
        }

        IslandEventBus.removeNotification(sbn.key)
    }

    private fun checkCallNotification(sbn: StatusBarNotification, notification: Notification): Boolean {
        val actions = extractActions(notification)
        val hasCallCategory = notification.category == Notification.CATEGORY_CALL
        val hasCallBtns = hasCallActions(actions)

        if (!hasCallCategory && !hasCallBtns) {
            return false
        }

        val isCellular = isDialerPackage(sbn.packageName)
        val source = if (isCellular) CallSource.CELLULAR else CallSource.VOIP
        val appDisplayName = if (isCellular) "Phone" else getAppLabel(sbn.packageName)

        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val answerAction = findAnswerAction(actions)
        val declineAction = findDeclineAction(actions)

        val callState = if (answerAction != null) CallState.RINGING else CallState.OFFHOOK

        val callInfo = CallInfo(
            callId = sbn.key,
            source = source,
            callerName = title.ifBlank { appDisplayName },
            callerNumber = text.ifBlank { null },
            state = callState,
            isVoip = !isCellular,
            appName = appDisplayName,
            avatar = extractIconBitmap(notification, sbn.packageName),
            answerIntent = answerAction?.intent,
            declineIntent = declineAction?.intent
        )

        IslandEventBus.updateCall(callInfo)
        return true
    }

    private fun checkTimerNotification(sbn: StatusBarNotification, notification: Notification): Boolean {
        if (!isTimerNotification(sbn)) {
            return false
        }

        val actions = extractActions(notification)
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        val pauseAction = actions.firstOrNull {
            it.title.contains("pause", ignoreCase = true) ||
                    it.title.contains("stop", ignoreCase = true)
        }
        val resumeAction = actions.firstOrNull {
            it.title.contains("resume", ignoreCase = true) ||
                    it.title.contains("start", ignoreCase = true)
        }
        val resetAction = actions.firstOrNull {
            it.title.contains("reset", ignoreCase = true) ||
                    it.title.contains("cancel", ignoreCase = true) ||
                    it.title.contains("delete", ignoreCase = true)
        }

        // Determine running state: if resume action is present, timer is paused; if pause is present, it is running
        val isChronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) == true
        val isRunning = when {
            resumeAction != null -> false
            pauseAction != null -> true
            else -> isChronometer
        }

        val parsedSeconds = parseRemainingSeconds(text)

        val timerInfo = TimerInfo(
            label = title.ifBlank { "Timer" },
            remainingSeconds = parsedSeconds,
            isRunning = isRunning,
            packageName = sbn.packageName,
            rawTimeText = text.ifBlank { null },
            pauseIntent = pauseAction?.intent,
            resumeIntent = resumeAction?.intent,
            resetIntent = resetAction?.intent,
            contentIntent = notification.contentIntent
        )

        IslandEventBus.updateTimer(timerInfo)
        return true
    }

    private fun parseNotificationItem(sbn: StatusBarNotification): NotificationItem? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return null

        val appName = getAppLabel(sbn.packageName)
        val iconBitmap = extractIconBitmap(notification, sbn.packageName)
        val actions = extractActions(notification)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0

        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            icon = iconBitmap,
            contentIntent = notification.contentIntent,
            actions = actions,
            postTime = sbn.postTime,
            isOngoing = isOngoing
        )
    }

    private fun extractActions(notification: Notification): List<NotificationActionItem> {
        val list = mutableListOf<NotificationActionItem>()
        notification.actions?.forEach { action ->
            val actionTitle = action.title?.toString()
            if (!actionTitle.isNullOrBlank()) {
                val remoteInput = action.remoteInputs?.firstOrNull()

                // Check semantic actions on Android P+
                var isReply = remoteInput != null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY) {
                        isReply = true
                    }
                }

                list.add(
                    NotificationActionItem(
                        title = actionTitle,
                        intent = action.actionIntent,
                        isDirectReply = isReply,
                        remoteInputResultKey = remoteInput?.resultKey,
                        remoteInputLabel = remoteInput?.label?.toString()
                    )
                )
            }
        }
        return list
    }

    private fun findAnswerAction(actions: List<NotificationActionItem>): NotificationActionItem? {
        return actions.firstOrNull {
            it.title.contains("answer", ignoreCase = true) ||
                    it.title.contains("accept", ignoreCase = true)
        }
    }

    private fun findDeclineAction(actions: List<NotificationActionItem>): NotificationActionItem? {
        return actions.firstOrNull {
            it.title.contains("decline", ignoreCase = true) ||
                    it.title.contains("reject", ignoreCase = true) ||
                    it.title.contains("end", ignoreCase = true) ||
                    it.title.contains("hang up", ignoreCase = true)
        }
    }

    private fun hasCallActions(actions: List<NotificationActionItem>): Boolean {
        return findAnswerAction(actions) != null || findDeclineAction(actions) != null
    }

    private fun isDialerPackage(pkgName: String): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val defaultDialer = telecomManager?.defaultDialerPackage
        val pkg = pkgName.lowercase()
        return (defaultDialer != null && pkgName == defaultDialer) ||
                pkg.contains("dialer") ||
                pkg.contains("incallui") ||
                pkg.contains("telecom") ||
                pkg == "com.android.phone" ||
                pkg == "com.samsung.android.dialer" ||
                pkg == "com.google.android.dialer"
    }

    private fun isTimerNotification(sbn: StatusBarNotification): Boolean {
        val category = sbn.notification?.category
        val pkg = sbn.packageName.lowercase()
        return category == Notification.CATEGORY_ALARM ||
                category == Notification.CATEGORY_STOPWATCH ||
                pkg.contains("clock") ||
                pkg.contains("deskclock")
    }

    /**
     * Parses timer text supporting HH:MM:SS, H:MM:SS, MM:SS, M:SS, and Xs.
     * Returns -1L if parsing fails rather than fabricating a synthetic 60-second timer.
     */
    private fun parseRemainingSeconds(text: String): Long {
        if (text.isBlank()) return -1L

        // Format: HH:MM:SS or H:MM:SS
        val hourRegex = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})")
        val hourMatch = hourRegex.find(text)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toLongOrNull() ?: 0L
            val mins = hourMatch.groupValues[2].toLongOrNull() ?: 0L
            val secs = hourMatch.groupValues[3].toLongOrNull() ?: 0L
            return hours * 3600 + mins * 60 + secs
        }

        // Format: MM:SS or M:SS
        val minRegex = Regex("(\\d{1,2}):(\\d{2})")
        val minMatch = minRegex.find(text)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toLongOrNull() ?: 0L
            val secs = minMatch.groupValues[2].toLongOrNull() ?: 0L
            return mins * 60 + secs
        }

        // Format: Xs (e.g. "45s")
        val secRegex = Regex("(\\d+)\\s*s", RegexOption.IGNORE_CASE)
        val secMatch = secRegex.find(text)
        if (secMatch != null) {
            return secMatch.groupValues[1].toLongOrNull() ?: -1L
        }

        // Could not reliably parse; return -1L to represent unknown duration
        return -1L
    }

    @Suppress("DEPRECATION")
    private fun shouldFilterNotification(notification: Notification, packageName: String): Boolean {
        // Skip group summaries that do not have actual alert content
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return true

        // Do NOT filter call or timer categories even if marked ongoing
        if (notification.category == Notification.CATEGORY_CALL ||
            notification.category == Notification.CATEGORY_ALARM ||
            notification.category == Notification.CATEGORY_STOPWATCH ||
            notification.category == Notification.CATEGORY_TRANSPORT ||
            notification.category == Notification.CATEGORY_NAVIGATION
        ) {
            return false
        }

        // Filter out background system noise with lowest priority
        if (notification.priority <= NotificationCompat.PRIORITY_MIN &&
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        ) {
            return true
        }

        return false
    }

    private fun getAppLabel(pkgName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkgName
        }
    }

    private fun extractIconBitmap(notification: Notification, pkgName: String): Bitmap? {
        try {
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                val drawable = largeIcon.loadDrawable(this)
                if (drawable != null) return drawableToSafeBitmap(drawable)
            }
            val appDrawable = packageManager.getApplicationIcon(pkgName)
            return drawableToSafeBitmap(appDrawable)
        } catch (e: Exception) {
            return null
        }
    }

    private fun drawableToSafeBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val orig = drawable.bitmap
            if (orig.width <= 128 && orig.height <= 128) {
                return orig
            }
            return Bitmap.createScaledBitmap(orig, 128, 128, true)
        }
        val targetWidth = if (drawable.intrinsicWidth in 1..128) drawable.intrinsicWidth else 96
        val targetHeight = if (drawable.intrinsicHeight in 1..128) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val TAG = "DynamicNotifListener"

        @Volatile
        private var currentInstance: DynamicNotificationListenerService? = null

        fun cancelNotification(key: String): Boolean {
            val service = currentInstance ?: return false
            return try {
                service.cancelNotification(key)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel notification $key", e)
                false
            }
        }
    }
}
