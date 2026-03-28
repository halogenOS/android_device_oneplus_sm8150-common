/*
 * SPDX-License-Identifier: Apache-2.0
 * SPDX-FileCopyrightText: 2026 The halogenOS Project
 */

package vendor.oplus.hardware.alertslider.controller

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import vendor.oplus.hardware.alertslider.AlertSliderPosition

class AlertSliderDialog(private val context: Context) :
    Dialog(context, R.style.alert_slider_theme) {

    private val dialogView by lazy { findViewById<LinearLayout>(R.id.alert_slider_dialog)!! }
    private val frameView by lazy { findViewById<ViewGroup>(R.id.alert_slider_view)!! }
    private val iconView by lazy { findViewById<ImageView>(R.id.alert_slider_icon)!! }
    private val textView by lazy { findViewById<TextView>(R.id.alert_slider_text)!! }

    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    private val systemUiRes = context.createPackageContext(
        SYSTEMUI_PACKAGE, Context.CONTEXT_RESTRICTED
    ).resources

    private val length: Int
    private val yPos: Int

    init {
        window?.let {
            it.requestFeature(Window.FEATURE_NO_TITLE)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            it.attributes = it.attributes.apply {
                format = PixelFormat.TRANSLUCENT
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = TAG
            }
        }

        setCanceledOnTouchOutside(false)
        setContentView(R.layout.alert_slider_dialog)

        val res = context.resources
        val fraction = res.getFraction(R.fraction.alert_slider_dialog_y, 1, 1)
        val heightPixels = res.displayMetrics.heightPixels
        val pads = dialogView.paddingTop * 2
        length = res.getDimension(R.dimen.alert_slider_dialog_height).toInt()
        val hv = (length + pads) * 0.5
        yPos = (heightPixels * fraction - hv).toInt()

        window?.attributes = window?.attributes?.apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = res.displayMetrics.widthPixels / 100
            y = yPos
        }
    }

    private fun systemUiDrawable(name: String): Int =
        systemUiRes.getIdentifier(name, "drawable", SYSTEMUI_PACKAGE)

    fun show(position: Int, ringerMode: Int) {
        window?.attributes = window?.attributes?.apply {
            val delta = length * when (position) {
                AlertSliderPosition.TOP -> -1
                AlertSliderPosition.BOTTOM -> 1
                else -> 0
            }
            y = yPos + delta
        }

        frameView.setBackgroundResource(when (position) {
            AlertSliderPosition.TOP -> R.drawable.alert_slider_top
            AlertSliderPosition.BOTTOM -> R.drawable.alert_slider_bottom
            else -> R.drawable.alert_slider_middle
        })

        val iconName = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "ic_qs_dnd_on"
            AudioManager.RINGER_MODE_VIBRATE -> "ic_volume_ringer_vibrate"
            else -> "ic_speaker_on"
        }
        val iconRes = systemUiDrawable(iconName)
        if (iconRes != 0) {
            iconView.setImageDrawable(systemUiRes.getDrawable(iconRes, context.theme))
        }
        iconView.setColorFilter(context.getColor(R.color.alert_slider_icon_color))

        textView.setText(when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> R.string.alert_slider_mode_silent
            AudioManager.RINGER_MODE_VIBRATE -> R.string.alert_slider_mode_vibration
            else -> R.string.alert_slider_mode_normal
        })

        if (!isShowing) show()

        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, DISMISS_DELAY_MS)
    }

    companion object {
        private const val TAG = "AlertSliderDialog"
        private const val DISMISS_DELAY_MS = 1500L
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }
}
