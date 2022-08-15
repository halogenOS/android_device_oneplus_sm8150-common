/*
 * Copyright (C) 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.halogenos.settings.device.extra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

import java.io.File

const val DEFAULT_FILE = "/sys/class/power_supply/battery/charging_enabled"
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
        val batteryLevel = getBatteryLevel(intent)
        val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            if (getBatteryLevel(intent) >= 80) {
                File(DEFAULT_FILE).writeText("0")
            } else {
                File(DEFAULT_FILE).writeText("1")
            }
        }
    }
}
