package com.dynamicisland.android.util

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandSettings

/**
 * WindowManager LayoutParams generator with display bounds clamping to prevent
 * off-screen positioning when FLAG_LAYOUT_NO_LIMITS is active.
 */
object WindowUtils {

    fun createLayoutParams(
        context: Context,
        settings: IslandSettings,
        widthPx: Int,
        heightPx: Int,
        cutoutBounds: Rect? = null
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

        val displaySize = getDisplaySize(context)
        val density = context.resources.displayMetrics.density

        // Incorporate measured hardware cutout offset if available
        val (baseXDp, baseYDp) = DisplayCutoutHelper.getHardwareBaseOffsetsDp(
            cutoutBounds,
            displaySize.x,
            density
        )

        val targetXDp = if (settings.cutoutType == CutoutType.CENTER_PUNCH_HOLE) {
            baseXDp + settings.xOffsetDp
        } else {
            settings.xOffsetDp
        }
        val targetYDp = (baseYDp + settings.yOffsetDp).coerceAtLeast(0)

        val rawXPx = dpToPx(context, targetXDp)
        val rawYPx = dpToPx(context, targetYDp)

        // Clamping coordinates inside visible display bounds
        val (clampedX, clampedY) = clampCoordinates(
            rawXPx,
            rawYPx,
            widthPx,
            heightPx,
            displaySize.x,
            displaySize.y,
            gravity
        )

        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = clampedX
            this.y = clampedY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    /**
     * Clamps window coordinates so the island never gets positioned off-screen.
     */
    fun clampCoordinates(
        xPx: Int,
        yPx: Int,
        widthPx: Int,
        heightPx: Int,
        displayWidth: Int,
        displayHeight: Int,
        gravity: Int
    ): Pair<Int, Int> {
        val clampedY = yPx.coerceIn(0, (displayHeight - heightPx).coerceAtLeast(0))

        val clampedX = when {
            (gravity and Gravity.START) == Gravity.START -> {
                xPx.coerceIn(0, (displayWidth - widthPx).coerceAtLeast(0))
            }
            (gravity and Gravity.END) == Gravity.END -> {
                xPx.coerceIn(0, (displayWidth - widthPx).coerceAtLeast(0))
            }
            else -> {
                // Center horizontal: X offset is relative to center
                val maxOffset = (displayWidth - widthPx) / 2
                xPx.coerceIn(-maxOffset, maxOffset)
            }
        }

        return Pair(clampedX, clampedY)
    }

    fun getDisplaySize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val point = Point()
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                point.x = bounds.width()
                point.y = bounds.height()
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getSize(point)
            }
        } else {
            val dm = context.resources.displayMetrics
            point.x = dm.widthPixels
            point.y = dm.heightPixels
        }
        return point
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
