/*
 * Copyright (C) 2021 The LineageOS Project
 * Copyright (C) 2023 The halogenOS Project
 * ,,
 * SPDX-License-Identifier: Apache-2.0
 */

package org.halogenos.settings.device.extra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.FileUtils;
import android.os.SystemProperties
import android.util.Log

import java.io.File

const val TAG = "OnePlusBatteryReceiver"
const val BATTERY_CHARGE_PATH = "/sys/class/power_supply/battery/mmi_charging_enable"

fun getBatteryLevel(batteryIntent: Intent): Int {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        return if (level == -1 || scale == -1) {
            50
        } else {
            level * 100 / scale
        }
    }

class BatteryReceiver : BroadcastReceiver() {

    val Context.percentage get() = getSharedPreferences("PREFERENCES",Context.MODE_PRIVATE).getInt("percentage", 0)
    val Context.resumePercentage get() = percentage - 2

    override fun onReceive(context: Context, intent: Intent) {
        if (context.percentage == 0) {
            Log.w(TAG, "Percentage is 0!")
            return
        }
        Log.i(TAG, "Received battery change intent")
        val batteryLevel = getBatteryLevel(intent)
        val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        Log.i(TAG, "Batterylevel: ${batteryLevel}, status: ${currentStatus}")
        if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            Log.i(TAG, "Battery is charging: ${context.percentage}")
            if (getBatteryLevel(intent) >= context.percentage) {
                Log.i(TAG, ">= ${context.percentage}, disabling charge")
                FileUtils.stringToFile(BATTERY_CHARGE_PATH, "0");
            } else {
                Log.i(TAG, "< ${context.percentage}, enabling charge")
                FileUtils.stringToFile(BATTERY_CHARGE_PATH, "1")
            }
        }
        else if (getBatteryLevel(intent) < context.resumePercentage) {
            Log.i(TAG, "<${context.percentage}, enabling charge")
            FileUtils.stringToFile(BATTERY_CHARGE_PATH, "1")
        }
    }
}
