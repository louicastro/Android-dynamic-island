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
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.NotificationActionItem
import com.dynamicisland.android.model.NotificationItem

class DynamicNotificationListenerService : NotificationListenerService() {

    private lateinit var mediaSessionTracker: MediaSessionTracker

    override fun onCreate() {
        super.onCreate()
        mediaSessionTracker = MediaSessionTracker.getInstance(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, DynamicNotificationListenerService::class.java)
        mediaSessionTracker.start(component)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionTracker.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        // Skip our own service notification
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return

        // Skip non-alerting silent background group summaries or ongoing system status
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return
        }

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }

        val iconBitmap = extractIconBitmap(notification, sbn.packageName)

        val actions = mutableListOf<NotificationActionItem>()
        notification.actions?.forEach { action ->
            val actionTitle = action.title?.toString()
            if (!actionTitle.isNullOrBlank()) {
                actions.add(NotificationActionItem(actionTitle, action.actionIntent))
            }
        }

        val notificationItem = NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            icon = iconBitmap,
            contentIntent = notification.contentIntent,
            actions = actions,
            postTime = sbn.postTime
        )

        IslandEventBus.emit(IslandEvent.NotificationPosted(notificationItem))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        IslandEventBus.emit(IslandEvent.NotificationRemoved(sbn.key))
    }

    private fun extractIconBitmap(notification: Notification, pkgName: String): Bitmap? {
        try {
            // First try large icon
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                val drawable = largeIcon.loadDrawable(this)
                if (drawable != null) return drawableToBitmap(drawable)
            }

            // Fallback to app icon
            val appDrawable = packageManager.getApplicationIcon(pkgName)
            return drawableToBitmap(appDrawable)
        } catch (e: Exception) {
            return null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
