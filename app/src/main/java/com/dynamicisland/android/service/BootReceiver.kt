package com.dynamicisland.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.dynamicisland.android.data.SettingsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val repository = SettingsRepository.getInstance(context)
            val settings = repository.getSettings()

            if (settings.isEnabled && settings.startOnBoot && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, IslandOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
