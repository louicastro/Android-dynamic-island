package com.dynamicisland.android.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp

object AnimationSpecs {

    // Authentic Apple Dynamic Island Spring Parameters
    const val APPLE_SPRING_STIFFNESS = 600f
    const val APPLE_SPRING_DAMPING = 0.78f

    /**
     * Organic, squishy spring animation mirroring Apple's Dynamic Island fluid physics.
     */
    fun <T> islandSpring(
        stiffness: Float = APPLE_SPRING_STIFFNESS,
        dampingRatio: Float = APPLE_SPRING_DAMPING
    ): AnimationSpec<T> {
        return spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        )
    }

    /**
     * Smooth collapse spring when returning to compact pill.
     */
    val pillCollapseSpring: AnimationSpec<Dp> = spring(
        dampingRatio = 0.85f,
        stiffness = 650f
    )

    /**
     * Content crossfade transition during size expansion.
     */
    fun <T> contentCrossfade(durationMs: Int = 180): AnimationSpec<T> {
        return tween(
            durationMillis = durationMs,
            easing = FastOutSlowInEasing
        )
    }
}

