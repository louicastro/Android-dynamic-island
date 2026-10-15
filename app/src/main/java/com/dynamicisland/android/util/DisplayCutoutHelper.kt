package com.dynamicisland.android.util

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.DisplayCutout
import android.view.WindowInsets
import android.view.WindowManager
import com.dynamicisland.android.model.CutoutType

/**
 * Utility for querying physical display cutout bounds and computing
 * relative alignment offsets based on measured hardware geometry.
 */
object DisplayCutoutHelper {

    /**
     * Extracts physical camera cutout bounds from WindowInsets when available.
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
     * Detects cutout position preset based on hardware cutout bounds.
     */
    fun detectCutoutTypeFromBounds(cutoutBounds: Rect?, screenWidthPx: Int): CutoutType {
        if (cutoutBounds == null) return CutoutType.CENTER_PUNCH_HOLE
        val centerX = cutoutBounds.centerX()
        val oneThirdWidth = screenWidthPx / 3
        val twoThirdsWidth = (screenWidthPx * 2) / 3

        return when {
            centerX < oneThirdWidth -> CutoutType.LEFT_PUNCH_HOLE
            centerX > twoThirdsWidth -> CutoutType.RIGHT_PUNCH_HOLE
            else -> CutoutType.CENTER_PUNCH_HOLE
        }
    }

    /**
     * Calculates hardware-measured base offsets (X, Y) in DP so user calibration
     * acts as micro-adjustments relative to the real camera punch-hole.
     */
    fun getHardwareBaseOffsetsDp(
        cutoutBounds: Rect?,
        screenWidthPx: Int,
        density: Float
    ): Pair<Int, Int> {
        if (cutoutBounds == null || density <= 0f) return Pair(0, 8)

        val cutoutCenterX = cutoutBounds.centerX()
        val screenCenterX = screenWidthPx / 2
        val diffXPx = cutoutCenterX - screenCenterX
        val diffXDp = (diffXPx / density).toInt()

        // Position pill vertically centered around cutout
        val cutoutCenterY = cutoutBounds.centerY()
        val baseYDp = (cutoutCenterY / density).toInt().coerceAtLeast(4)

        return Pair(diffXDp, baseYDp)
    }
}
