package com.dynamicisland.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.IslandEvent

class BatteryTracker(private val context: Context) {

    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level >= 0 && scale > 0) {
                        (level * 100 / scale.toFloat()).toInt()
                    } else 100

                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    val isPlugged = chargePlug != 0

                    val speedType = when (chargePlug) {
                        BatteryManager.BATTERY_PLUGGED_AC -> "Fast Charging"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging"
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB Charging"
                        else -> "Charging"
                    }

                    val batteryInfo = BatteryInfo(
                        level = batteryPct,
                        isCharging = isCharging,
                        isPlugged = isPlugged,
                        chargingSpeedType = speedType
                    )

                    IslandEventBus.emit(IslandEvent.BatteryUpdate(batteryInfo))
                }
            }
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(receiver, filter)
            isRegistered = true
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore receiver not registered
            }
            isRegistered = false
        }
    }
}
