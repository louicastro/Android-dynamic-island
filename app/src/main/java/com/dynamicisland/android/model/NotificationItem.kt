package com.dynamicisland.android.model

import android.app.PendingIntent
import android.graphics.Bitmap

/**
 * Model representing an action on a notification (e.g., Reply, Like, Archive).
 */
data class NotificationActionItem(
    val title: String,
    val intent: PendingIntent?
)

/**
 * Encapsulates an intercepted status bar notification for dynamic display.
 */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val icon: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val actions: List<NotificationActionItem> = emptyList(),
    val postTime: Long = System.currentTimeMillis()
)
