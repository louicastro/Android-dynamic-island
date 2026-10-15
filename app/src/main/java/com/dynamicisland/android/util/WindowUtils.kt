package com.dynamicisland.android.util

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandSettings

object WindowUtils {

    fun createLayoutParams(
        context: Context,
        settings: IslandSettings,
        widthPx: Int,
        heightPx: Int
    ): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val gravity = when (settings.cutoutType) {
            CutoutType.LEFT_PUNCH_HOLE -> Gravity.TOP or Gravity.START
            CutoutType.RIGHT_PUNCH_HOLE -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = dpToPx(context, settings.xOffsetDp)
            this.y = dpToPx(context, settings.yOffsetDp)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
