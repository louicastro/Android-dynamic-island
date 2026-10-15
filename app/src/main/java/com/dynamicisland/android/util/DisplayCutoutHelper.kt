package com.dynamicisland.android.util

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.WindowInsets
import android.view.WindowManager
import com.dynamicisland.android.model.CutoutType

object DisplayCutoutHelper {

    /**
     * Attempts to detect the physical camera cutout position on the device screen.
     */
    fun detectCutoutType(context: Context): CutoutType {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return CutoutType.CENTER_PUNCH_HOLE
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return CutoutType.CENTER_PUNCH_HOLE

        return CutoutType.CENTER_PUNCH_HOLE
    }

    /**
     * Extracts cutout rect from WindowInsets when available.
     */
    fun extractCutoutBounds(windowInsets: WindowInsets?): Rect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && windowInsets != null) {
            val cutout: DisplayCutout? = windowInsets.displayCutout
            if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                return cutout.boundingRects.first()
            }
        }
        return null
    }

    /**
     * Calculates the horizontal offset in DP based on cutout position relative to screen center.
     */
    fun calculateHorizontalOffsetDp(cutoutBounds: Rect, screenWidthPx: Int, density: Float): Int {
        val cutoutCenterX = cutoutBounds.centerX()
        val screenCenterX = screenWidthPx / 2
        val diffPx = cutoutCenterX - screenCenterX
        return (diffPx / density).toInt()
    }
}

