// Copyright (C) 2022 The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package org.halogenos.device.oplus.sm8150.batterychargelimiter

import android.service.quicksettings.TileService
import android.content.Context
import android.service.quicksettings.Tile
import android.content.Intent
import android.os.BatteryManager
import android.os.FileUtils
import android.os.ServiceManager
import android.util.Log
import android.content.IntentFilter

import vendor.lineage.health.IChargingControl

val levels = listOf(80, 90)

class BatteryChargeLimitTileService : TileService() {

    override fun onTileAdded() {
        if (percentage == 0) {
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.subtitle = null
        } else if (percentage in levels) {
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.subtitle = "$percentage%"
        }

        // Update looks
        qsTile.updateTile()
    }

    override fun onStartListening() {
        onTileAdded()
    }

    override fun onClick() {
        super.onClick()
        if (percentage == 0) {
            val firstPercentage = levels.first()
            setPercentage(firstPercentage)
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.subtitle = "$firstPercentage%"
        } else if (percentage in levels.dropLast(1)) {
            val nextPercentage = levels[levels.indexOf(percentage) + 1]
	    setPercentage(nextPercentage)
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.subtitle = "$nextPercentage%"
        } else if (percentage == levels.last()) {
            setPercentage(0)
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.subtitle = null
        }

        val resumePercentage = percentage - 2

        if (percentage != 0) {
            val intent: Intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                registerReceiver(null, ifilter)
            }!!

            handleBatteryStateChange(intent, percentage, resumePercentage)
        }

        // Update looks
        qsTile.updateTile()
    }

    private fun setPercentage(percentage: Int) {
        val sharedPreference =  getSharedPreferences("PREFERENCES",Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putInt("percentage", percentage)
        editor.commit()
    }

    val percentage get()=getSharedPreferences("PREFERENCES",Context.MODE_PRIVATE).getInt("percentage", 0)
}

fun handleBatteryStateChange(intent: Intent, percentage: Int, resumePercentage: Int) {
    val chargingControl = IChargingControl.Stub.asInterface(
                ServiceManager.waitForDeclaredService(
                        IChargingControl.DESCRIPTOR + "/default"));
    val batteryLevel = getBatteryLevel(intent)
    val currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    Log.i(TAG, "Batterylevel: $batteryLevel, status: $currentStatus")
    if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
        Log.i(TAG, "Battery is charging: $percentage")
        if (getBatteryLevel(intent) >= percentage) {
            Log.i(TAG, ">= $percentage, disabling charge")
            chargingControl.setChargingEnabled(false)
        } else {
            Log.i(TAG, "< $percentage, enabling charge")
            chargingControl.setChargingEnabled(true)
        }
    } else if (getBatteryLevel(intent) < resumePercentage) {
        Log.i(TAG, "<$percentage, enabling charge")
        chargingControl.setChargingEnabled(true)
    }
}
