package com.dynamicisland.android.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import kotlinx.coroutines.delay

@Composable
fun IslandOverlayHost(
    state: IslandState,
    settings: IslandSettings,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissRequest: () -> Unit,
    onAutoDismissToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss transient toasts/banners after user configured seconds
    LaunchedEffect(state) {
        if (state.isToast) {
            delay(settings.autoDismissSeconds * 1000L)
            onAutoDismissToast()
        }
    }

    Box(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        IslandPillContainer(
            state = state,
            settings = settings,
            onTap = onTap,
            onLongPress = onLongPress,
            onSwipeUp = onDismissRequest
        ) { isExpanded ->
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    fadeIn(animationSpec = tween(160)) togetherWith
                            fadeOut(animationSpec = tween(120))
                },
                label = "island_content_transition"
            ) { expanded ->
                if (expanded) {
                    ExpandedIslandContent(
                        state = state,
                        onDismissRequest = onDismissRequest
                    )
                } else {
                    val compactState = when (state) {
                        is IslandState.SplitIsland -> state.primaryState
                        else -> state
                    }
                    CompactIslandContent(state = compactState)
                }
            }
        }
    }
}
