package com.dynamicisland.android.ui.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.IslandThemeMode
import com.dynamicisland.android.ui.theme.AppleBlack
import com.dynamicisland.android.ui.theme.AppleBlue
import com.dynamicisland.android.ui.theme.AppleGlassBorder
import com.dynamicisland.android.ui.theme.AppleOrange

/**
 * Spring-animated morphing pill container.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IslandPillContainer(
    state: IslandState,
    settings: IslandSettings,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    content: @Composable (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isExpanded = state.isExpanded
    val isSplit = state is IslandState.SplitIsland

    // Dynamic Island Physics Spring Config
    val springSpec = spring<Dp>(
        dampingRatio = settings.springDampingRatio,
        stiffness = settings.springStiffness
    )

    // Animated dimensions tailored per state
    val targetWidth = when {
        isExpanded -> settings.expandedWidthDp.dp
        isSplit -> (settings.compactWidthDp - 20).dp
        state is IslandState.Idle -> 80.dp
        state is IslandState.CompactTimer -> (settings.compactWidthDp + 10).dp
        state is IslandState.RingerToast -> (settings.compactWidthDp + 60).dp
        state is IslandState.BluetoothToast -> (settings.compactWidthDp + 80).dp
        else -> settings.compactWidthDp.dp
    }

    val targetHeight = when {
        isExpanded -> settings.expandedHeightDp.dp
        else -> settings.compactHeightDp.dp
    }

    val targetCornerRadius = when {
        isExpanded -> 28.dp
        else -> (settings.compactHeightDp / 2).dp
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = springSpec,
        label = "pill_width"
    )

    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = springSpec,
        label = "pill_height"
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = springSpec,
        label = "pill_corner_radius"
    )

    val backgroundColor = when (settings.themeMode) {
        IslandThemeMode.AMOLED_BLACK -> AppleBlack
        IslandThemeMode.FROSTED_GLASS -> Color(0xE6141418)
        IslandThemeMode.AMBIENT_ACCENT -> Color(0xF00A0A0E)
    }

    val borderStroke = when {
        state is IslandState.CompactTimer -> BorderStroke(0.8.dp, AppleOrange.copy(alpha = 0.4f))
        state is IslandState.RingerToast -> BorderStroke(0.8.dp, AppleOrange.copy(alpha = 0.5f))
        state is IslandState.BluetoothToast -> BorderStroke(0.8.dp, AppleBlue.copy(alpha = 0.5f))
        settings.themeMode == IslandThemeMode.FROSTED_GLASS -> BorderStroke(1.dp, Color(0x33FFFFFF))
        settings.themeMode == IslandThemeMode.AMBIENT_ACCENT -> BorderStroke(1.dp, AppleBlue.copy(alpha = 0.3f))
        else -> BorderStroke(0.5.dp, AppleGlassBorder)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Main Island Pill Container
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .shadow(
                    elevation = if (isExpanded) 18.dp else 6.dp,
                    shape = RoundedCornerShape(animatedCornerRadius)
                )
                .clip(RoundedCornerShape(animatedCornerRadius))
                .background(backgroundColor)
                .border(borderStroke, RoundedCornerShape(animatedCornerRadius))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -15 && isExpanded) {
                            if (settings.vibrationFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onSwipeUp()
                        }
                    }
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (settings.vibrationFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onTap()
                    },
                    onLongClick = {
                        if (settings.vibrationFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onLongPress()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            content(isExpanded)
        }

        // Secondary detached bubble in Split state
        if (state is IslandState.SplitIsland) {
            Spacer(modifier = Modifier.width(10.dp))
            SplitBubbleContent(
                state = state.secondaryState,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTap() }
                    )
            )
        }
    }
}

