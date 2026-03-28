/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2026 The halogenOS Project
 */

package vendor.oplus.hardware.alertslider.controller

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

import vendor.oplus.hardware.alertslider.AlertSliderPosition
import vendor.oplus.hardware.alertslider.IAlertSlider

class AlertSliderService : Service() {

    private lateinit var audioManager: AudioManager
    private lateinit var vibrator: Vibrator
    private var alertSlider: IAlertSlider? = null
    private var dialog: AlertSliderDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var watchThread: Thread? = null
    @Volatile private var running = false
    private var firstRun = true

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AudioManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)

        val binder = waitForService("$DESCRIPTOR/default")
        if (binder == null) {
            Log.e(TAG, "Alert slider HAL not found")
            stopSelf()
            return
        }
        alertSlider = IAlertSlider.Stub.asInterface(binder)

        applyPosition(alertSlider!!.position)
        firstRun = false
        startWatching()
    }

    override fun onDestroy() {
        running = false
        watchThread?.interrupt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun startWatching() {
        running = true
        watchThread = Thread({
            while (running) {
                try {
                    val position = alertSlider!!.waitForChange()
                    handler.post { applyPosition(position) }
                } catch (e: Exception) {
                    Log.e(TAG, "waitForChange failed", e)
                    break
                }
            }
        }, "alertslider-watch").also { it.start() }
    }

    private fun applyPosition(position: Int) {
        val ringerMode = when (position) {
            AlertSliderPosition.TOP -> AudioManager.RINGER_MODE_NORMAL
            AlertSliderPosition.MIDDLE -> AudioManager.RINGER_MODE_VIBRATE
            AlertSliderPosition.BOTTOM -> AudioManager.RINGER_MODE_SILENT
            else -> return
        }

        Log.d(TAG, "Position: $position -> ringer mode: $ringerMode")
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            setZenMode(this, 1)
        } else {
            setZenMode(this, 0)
            audioManager.ringerMode = ringerMode
        }

        if (!firstRun) {
            dialog?.dismiss()
            dialog = AlertSliderDialog(this)
            dialog?.show(position, ringerMode)
        }

        val effect = when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> EFFECT_HEAVY_CLICK
            AudioManager.RINGER_MODE_VIBRATE -> EFFECT_DOUBLE_CLICK
            else -> null
        }
        if (effect != null) {
            vibrator.vibrate(effect, VIBRATION_ATTRS)
        }
    }

    companion object {
        private const val TAG = "AlertSliderService"
        private const val DESCRIPTOR = "vendor.oplus.hardware.alertslider.IAlertSlider"

        private val VIBRATION_ATTRS = VibrationAttributes.createForUsage(
            VibrationAttributes.USAGE_HARDWARE_FEEDBACK
        )
        private val EFFECT_HEAVY_CLICK = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        private val EFFECT_DOUBLE_CLICK = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

        private fun setZenMode(context: Context, mode: Int) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                val filter = when (mode) {
                    0 -> 4 // INTERRUPTION_FILTER_ALL
                    1 -> 2 // INTERRUPTION_FILTER_PRIORITY
                    2 -> 1 // INTERRUPTION_FILTER_NONE
                    else -> 4
                }
                nm::class.java.getMethod("setInterruptionFilter", Int::class.javaPrimitiveType)
                    .invoke(nm, filter)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set zen mode", e)
            }
        }

        private fun waitForService(name: String): IBinder? = try {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("waitForService", String::class.java).invoke(null, name) as? IBinder
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get service $name", e)
            null
        }

        fun start(context: Context) {
            context.startService(Intent(context, AlertSliderService::class.java))
        }
    }
}
