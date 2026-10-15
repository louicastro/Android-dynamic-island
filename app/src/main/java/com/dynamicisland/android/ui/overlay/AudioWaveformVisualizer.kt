package com.dynamicisland.android.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated audio visualizer waveform mimicking Apple's dynamic sound bars.
 */
@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = Color(0xFF30D158),
    maxHeight: Dp = 16.dp,
    barWidth: Dp = 2.5.dp,
    spacing: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "waveform")

    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isPlaying) 0.95f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val h2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isPlaying) 0.30f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val h3 by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = if (isPlaying) 1.0f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val h4 by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = if (isPlaying) 0.35f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(490, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    val barHeights = listOf(h1, h2, h3, h4)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val fraction = barHeights.getOrElse(i) { 0.5f }
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * fraction)
                    .background(color, RoundedCornerShape(percent = 50))
            )
        }
    }
}
