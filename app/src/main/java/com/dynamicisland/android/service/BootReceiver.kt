package com.dynamicisland.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.dynamicisland.android.data.SettingsRepository

/**
 * Handles device boot completion and package replacement to restart the overlay service.
 *
 * Implements:
 * - Independent validation of configuration, overlay permissions, and listener status (Finding 24)
 * - Structured logging and startup exception resilience
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val repository = SettingsRepository.getInstance(context)
            val settings = repository.getSettings()

            if (!settings.isEnabled) {
                Log.i(TAG, "Dynamic Island is disabled in settings; skipping boot startup")
                return
            }

            if (!settings.startOnBoot) {
                Log.i(TAG, "Start on boot is disabled; skipping boot startup")
                return
            }

            val hasOverlayPermission = Settings.canDrawOverlays(context)
            if (!hasOverlayPermission) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW permission missing; cannot start overlay service on boot")
                return
            }

            // Check notification listener status for diagnostic logging
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val isListenerGranted = enabledListeners.contains(context.packageName)
            if (!isListenerGranted) {
                Log.w(TAG, "Notification listener permission is not enabled; media and notification features will be restricted until granted")
            }

            val serviceIntent = Intent(context, IslandOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "Successfully triggered IslandOverlayService launch on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start IslandOverlayService on boot", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
