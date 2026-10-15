package com.dynamicisland.android.model

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle

/**
 * Model representing an action on a notification (e.g., Reply, Like, Archive).
 * Supports standard actions and direct RemoteInput inline replies.
 */
data class NotificationActionItem(
    val title: String,
    val intent: PendingIntent?,
    val isDirectReply: Boolean = false,
    val remoteInputResultKey: String? = null,
    val remoteInputLabel: String? = null
) {
    /**
     * Dispatches a typed reply message via the action's RemoteInput PendingIntent.
     */
    fun sendReply(context: Context, replyText: CharSequence): Boolean {
        val pi = intent ?: return false
        val key = remoteInputResultKey ?: return false
        val bundle = Bundle().apply {
            putCharSequence(key, replyText)
        }
        val remoteInput = RemoteInput.Builder(key)
            .setLabel(remoteInputLabel ?: title)
            .build()
        val fillInIntent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillInIntent, bundle)
        return try {
            pi.send(context, 0, fillInIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

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
    val postTime: Long = System.currentTimeMillis(),
    val isOngoing: Boolean = false,
    val isCall: Boolean = false,
    val isTimer: Boolean = false
) {
    val directReplyAction: NotificationActionItem?
        get() = actions.firstOrNull { it.isDirectReply }
}
