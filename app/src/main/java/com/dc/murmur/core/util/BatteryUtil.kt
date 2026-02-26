package com.dc.murmur.core.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryUtil(private val context: Context) {

    private fun getBatteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun getBatteryLevel(): Int {
        val intent = getBatteryIntent() ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return (level * 100 / scale)
    }

    fun isCharging(): Boolean {
        val intent = getBatteryIntent() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getTemperatureCelsius(): Float {
        val intent = getBatteryIntent() ?: return 0f
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        return temp / 10f  // tenths of a degree Celsius
    }
}
