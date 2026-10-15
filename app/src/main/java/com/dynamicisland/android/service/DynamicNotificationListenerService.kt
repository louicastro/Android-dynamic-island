package com.dynamicisland.android.service

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.NotificationActionItem
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo

/**
 * Intercepts notifications, active media sessions, in-call alerts, and timers.
 * Employs targeted ongoing notification filtering, RemoteInput detection,
 * and memory-safe bitmap downsampling.
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

        // Reconcile and seed existing active notifications on connection
        try {
            val active = activeNotifications
            active?.forEach { sbn ->
                handleNotificationPosted(sbn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        handleNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return

        // If a call notification was dismissed, reset call state
        if (sbn.notification?.category == Notification.CATEGORY_CALL) {
            IslandEventBus.updateCall(null)
        }

        // If a timer notification was dismissed, reset timer state
        if (isTimerNotification(sbn)) {
            IslandEventBus.updateTimer(null)
        }

        IslandEventBus.removeNotification(sbn.key)
    }

    private fun handleNotificationPosted(sbn: StatusBarNotification) {
        // Skip our own overlay service notification
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return

        // Filter out purely silent background group summaries or non-alerting system noise
        if (shouldFilterNotification(notification, sbn.packageName)) {
            return
        }

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        val iconBitmap = extractIconBitmap(notification, sbn.packageName)

        // Parse actions including Direct Reply (RemoteInput)
        val actions = mutableListOf<NotificationActionItem>()
        notification.actions?.forEach { action ->
            val actionTitle = action.title?.toString()
            if (!actionTitle.isNullOrBlank()) {
                val remoteInput = action.remoteInputs?.firstOrNull()
                actions.add(
                    NotificationActionItem(
                        title = actionTitle,
                        intent = action.actionIntent,
                        isDirectReply = remoteInput != null,
                        remoteInputResultKey = remoteInput?.resultKey,
                        remoteInputLabel = remoteInput?.label?.toString()
                    )
                )
            }
        }

        // 1. Check if this is an incoming or active Call (VoIP or system dialer)
        if (notification.category == Notification.CATEGORY_CALL || hasCallActions(actions)) {
            val answerAction = actions.firstOrNull {
                it.title.contains("answer", ignoreCase = true) ||
                        it.title.contains("accept", ignoreCase = true)
            }
            val declineAction = actions.firstOrNull {
                it.title.contains("decline", ignoreCase = true) ||
                        it.title.contains("reject", ignoreCase = true) ||
                        it.title.contains("end", ignoreCase = true) ||
                        it.title.contains("hang up", ignoreCase = true)
            }

            val callState = if (answerAction != null) CallState.RINGING else CallState.OFFHOOK

            val callInfo = CallInfo(
                callerName = title.ifBlank { appName },
                callerNumber = text.ifBlank { null },
                state = callState,
                isVoip = true,
                appName = appName,
                avatar = iconBitmap,
                answerIntent = answerAction?.intent,
                declineIntent = declineAction?.intent
            )
            IslandEventBus.updateCall(callInfo)
            return
        }

        // 2. Check if this is a Timer/Stopwatch notification (Clock apps)
        if (isTimerNotification(sbn)) {
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
                        it.title.contains("cancel", ignoreCase = true)
            }

            val timerInfo = TimerInfo(
                label = title.ifBlank { "Timer" },
                remainingSeconds = parseRemainingSeconds(text),
                isRunning = pauseAction != null,
                packageName = sbn.packageName,
                pauseIntent = pauseAction?.intent,
                resumeIntent = resumeAction?.intent,
                resetIntent = resetAction?.intent,
                contentIntent = notification.contentIntent
            )
            IslandEventBus.updateTimer(timerInfo)
            return
        }

        // 3. Standard Alerting Notification
        if (title.isNotBlank() || text.isNotBlank()) {
            val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val item = NotificationItem(
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
            IslandEventBus.postNotification(item)
        }
    }

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

    private fun hasCallActions(actions: List<NotificationActionItem>): Boolean {
        return actions.any {
            it.title.contains("answer", ignoreCase = true) ||
                    it.title.contains("decline", ignoreCase = true) ||
                    it.title.contains("reject", ignoreCase = true)
        }
    }

    private fun isTimerNotification(sbn: StatusBarNotification): Boolean {
        val category = sbn.notification?.category
        val pkg = sbn.packageName.lowercase()
        return category == Notification.CATEGORY_ALARM ||
                category == Notification.CATEGORY_STOPWATCH ||
                pkg.contains("clock") ||
                pkg.contains("deskclock")
    }

    private fun parseRemainingSeconds(text: String): Long {
        // Match formats like "04:32" or "12:05"
        val regex = Regex("(\\d{1,2}):(\\d{2})")
        val match = regex.find(text)
        if (match != null) {
            val mins = match.groupValues[1].toLongOrNull() ?: 0L
            val secs = match.groupValues[2].toLongOrNull() ?: 0L
            return mins * 60 + secs
        }
        return 60L
    }

    private fun extractIconBitmap(notification: Notification, pkgName: String): Bitmap? {
        try {
            // First attempt to load large icon
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                val drawable = largeIcon.loadDrawable(this)
                if (drawable != null) return drawableToSafeBitmap(drawable)
            }

            // Fallback to application icon
            val appDrawable = packageManager.getApplicationIcon(pkgName)
            return drawableToSafeBitmap(appDrawable)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Converts a drawable to a memory-safe bitmap clamped to max 128x128 pixels.
     * Prevents high memory allocation on low-RAM devices like Galaxy A35.
     */
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
        @Volatile
        private var currentInstance: DynamicNotificationListenerService? = null

        /**
         * Cancels an active status bar notification by key.
         */
        fun cancelNotification(key: String): Boolean {
            val service = currentInstance ?: return false
            return try {
                service.cancelNotification(key)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
