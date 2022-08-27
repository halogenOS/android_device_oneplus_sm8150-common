/*
 * Copyright (C) 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.halogenos.settings.device.extra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.SystemProperties
import android.util.Log

import java.io.File

private const val TAG = "OnePlusBatteryReceiver"
private const val SYSPROP_CHARGE = "vendor.battery.charge_enable"

class BatteryReceiver : BroadcastReceiver() {

    fun getBatteryLevel(batteryIntent: Intent): Int {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        return if (level == -1 || scale == -1) {
            50
        } else {
            level * 100 / scale
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received battery change intent")
        val batteryLevel = getBatteryLevel(intent)
        val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        Log.i(TAG, "Batterylevel: ${batteryLevel}, status: ${currentStatus}")
        if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            Log.i(TAG, "Battery is charging")
            if (getBatteryLevel(intent) >= 80) {
                Log.i(TAG, ">= 80, disabling charge")
                SystemProperties.set(SYSPROP_CHARGE, "0")
            } else {
                Log.i(TAG, "< 80, enabling charge")
                SystemProperties.set(SYSPROP_CHARGE, "1")
            }
        }
    }
}
