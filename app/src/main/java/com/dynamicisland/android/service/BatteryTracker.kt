package com.dynamicisland.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.IslandEvent

/**
 * Monitors hardware battery percentage, charging state, and power source.
 *
 * Implements:
 * - Neutral, accurate charging speed classification without false "Fast Charging" claims (Finding 29)
 * - Explicit unknown value modeling (-1) without defaulting to 100% (Finding 30)
 * - Deduplication of identical battery broadcasts (Finding 33)
 */
class BatteryTracker(private val context: Context) {

    private var isRegistered = false
    private var lastEmittedBatteryInfo: BatteryInfo? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

                // Accurately compute percentage or assign -1 (unknown) rather than assuming 100%
                val batteryPct = if (level >= 0 && scale > 0) {
                    (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
                } else {
                    -1
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val isPlugged = chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
                        chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                        chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

                // Determine accurate, non-exaggerated charging speed label
                val speedType = determineChargingLabel(chargePlug)

                val batteryInfo = BatteryInfo(
                    level = batteryPct,
                    isCharging = isCharging,
                    isPlugged = isPlugged,
                    chargingSpeedType = speedType
                )

                // Deduplicate broadcasts: avoid emitting if level, charging state, and speed type are unchanged
                val prev = lastEmittedBatteryInfo
                if (prev != null &&
                    prev.level == batteryInfo.level &&
                    prev.isCharging == batteryInfo.isCharging &&
                    prev.isPlugged == batteryInfo.isPlugged &&
                    prev.chargingSpeedType == batteryInfo.chargingSpeedType
                ) {
                    return
                }

                lastEmittedBatteryInfo = batteryInfo
                IslandEventBus.updateBattery(batteryInfo)
            }
        }
    }

    private fun determineChargingLabel(chargePlug: Int): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        // Check if platform exposes high charging current (> 2000 mA / 2,000,000 uA) for verified fast charging
        var isHighPower = false
        if (batteryManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val currentNowMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                // Positive or negative depending on OEM driver convention
                if (Math.abs(currentNowMicroAmps) > 2_000_000) {
                    isHighPower = true
                }
            } catch (e: Exception) {
                // Ignore driver property reading errors
            }
        }

        return when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> {
                if (isHighPower) "Fast Charging (AC)" else "Charging (AC)"
            }
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> {
                if (isHighPower) "Fast Wireless Charging" else "Wireless Charging"
            }
            BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
            else -> "Charging"
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            try {
                context.registerReceiver(receiver, filter)
                isRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register battery receiver", e)
            }
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering battery receiver: ${e.message}")
            }
            isRegistered = false
        }
    }

    companion object {
        private const val TAG = "BatteryTracker"
    }
}
