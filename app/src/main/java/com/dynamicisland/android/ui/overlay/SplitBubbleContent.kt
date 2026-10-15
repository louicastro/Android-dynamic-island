package com.dynamicisland.android.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.R
import com.dynamicisland.android.model.IslandState

/**
 * Renders the detached secondary bubble in multi-tasking split island state.
 */
@Composable
fun SplitBubbleContent(
    state: IslandState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is IslandState.CompactTimer -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bell),
                    contentDescription = "Timer",
                    tint = Color(0xFFFF9F0A),
                    modifier = Modifier.size(16.dp)
                )
            }
            is IslandState.CompactBattery, is IslandState.ChargingToast -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_battery_charging),
                    contentDescription = "Battery",
                    tint = Color(0xFF30D158),
                    modifier = Modifier.size(18.dp)
                )
            }
            is IslandState.CompactCall -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call),
                    contentDescription = "Call",
                    tint = Color(0xFF30D158),
                    modifier = Modifier.size(16.dp)
                )
            }
            is IslandState.CompactMusic -> {
                if (state.media.albumArt != null) {
                    Image(
                        bitmap = state.media.albumArt.asImageBitmap(),
                        contentDescription = "Art",
                        modifier = Modifier
                            .fillMaxSize(0.8f)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_music),
                        contentDescription = "Music",
                        tint = Color(0xFFFF2D55),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            is IslandState.NotificationToast -> {
                if (state.notification.icon != null) {
                    Image(
                        bitmap = state.notification.icon.asImageBitmap(),
                        contentDescription = "App",
                        modifier = Modifier
                            .fillMaxSize(0.75f)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bell),
                        contentDescription = "Notification",
                        tint = Color(0xFFFF9F0A),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF0A84FF), CircleShape)
                )
            }
        }
    }
}
