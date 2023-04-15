// Copyright (C) 2022 The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package org.halogenos.settings.device.extra

import android.service.quicksettings.TileService
import android.content.Context
import android.service.quicksettings.Tile
import android.content.Intent
import android.os.BatteryManager
import android.os.SystemProperties
import android.util.Log
import android.content.IntentFilter

class BatteryChargeLimitTileService : TileService() {
    override fun onTileAdded() {
        when(percentage) {
            80 -> {
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.subtitle = "80%"
            }
            90 -> {
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.subtitle = "90%"
            }
            0 -> {
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.subtitle = null
            }
        }

        // Update looks
        qsTile.updateTile()
    }

    override fun onStartListening() {
        onTileAdded()
    }

    override fun onClick() {
        super.onClick() 
        when(percentage) {
            0 -> {
                setPercentage(80)
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.subtitle = "80%"
            }
            80 -> {
                setPercentage(90)
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.subtitle = "90%"
            }
            90 -> {
                setPercentage(0)
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.subtitle = null
            }
        }
        val resumePercentage = percentage - 2

        if (percentage != 0) {
            val intent: Intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                registerReceiver(null, ifilter)
            }!!

            val batteryLevel = getBatteryLevel(intent)
            val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            Log.i(TAG, "Batterylevel: ${batteryLevel}, status: ${currentStatus}")
            if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
                Log.i(TAG, "Battery is charging")
                if (getBatteryLevel(intent) >= percentage) {
                    Log.i(TAG, ">= ${percentage}, disabling charge")
                    SystemProperties.set(SYSPROP_CHARGE, "0")
                } else {
                    Log.i(TAG, "< ${percentage}, enabling charge")
                    SystemProperties.set(SYSPROP_CHARGE, "1")
                }
            }
            else if (getBatteryLevel(intent) < resumePercentage) {
                Log.i(TAG, "<${percentage}, enabling charge")
                SystemProperties.set(SYSPROP_CHARGE, "1")
            }
        }

        // Update looks
        qsTile.updateTile()
    }

    fun setPercentage(percentage: Int) {
        val sharedPreference =  getSharedPreferences("PREFERENCES",Context.MODE_PRIVATE)
        var editor = sharedPreference.edit()
        editor.putInt("percentage", percentage)
        editor.commit()
    }

    val percentage get()=getSharedPreferences("PREFERENCES",Context.MODE_PRIVATE).getInt("percentage", 0)
}
