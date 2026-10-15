package com.dynamicisland.android.ui.overlay

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.R
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.service.MediaSessionTracker

@Composable
fun ExpandedIslandContent(
    state: IslandState,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaTracker = remember { MediaSessionTracker.getInstance(context) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when (state) {
            is IslandState.ExpandedMusic -> {
                ExpandedMusicView(
                    media = state.media,
                    onPlayPause = { mediaTracker.togglePlayPause() },
                    onNext = { mediaTracker.skipNext() },
                    onPrev = { mediaTracker.skipPrevious() },
                    onSeek = { mediaTracker.seekTo((it * state.media.durationMs).toLong()) }
                )
            }

            is IslandState.ExpandedNotification -> {
                ExpandedNotificationView(
                    notification = state.notification,
                    onDismiss = onDismissRequest
                )
            }

            is IslandState.ExpandedCall -> {
                ExpandedCallView(
                    call = state.call,
                    onDismiss = onDismissRequest
                )
            }

            is IslandState.ExpandedBattery -> {
                ExpandedBatteryView(battery = state.battery)
            }

            is IslandState.ExpandedTimer -> {
                ExpandedTimerView(
                    timer = state.timer,
                    onDismiss = onDismissRequest
                )
            }

            is IslandState.ExpandedRinger -> {
                ExpandedRingerView(
                    isMuted = state.isMuted,
                    onDismiss = onDismissRequest
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ExpandedMusicView(
    media: com.dynamicisland.android.model.MediaInfo,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Album art + Track Details + Waveform
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (media.albumArt != null) {
                Image(
                    bitmap = media.albumArt.asImageBitmap(),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_music),
                        contentDescription = "Music",
                        tint = Color(0xFFFF2D55),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = media.artist,
                    color = Color(0xFF98989D),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            AudioWaveformVisualizer(
                isPlaying = media.isPlaying,
                color = Color(0xFF30D158),
                maxHeight = 22.dp,
                barWidth = 3.dp,
                spacing = 3.dp
            )
        }

        // Middle section: Scrubber / Progress slider
        var sliderPosition by remember(media.progress) { mutableFloatStateOf(media.progress) }
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition) },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(media.currentPositionMs),
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp
                )
                Text(
                    text = if (media.durationMs > 0) formatMs(media.durationMs) else "--:--",
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp
                )
            }
        }

        // Bottom section: Playback control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_prev),
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (media.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (media.isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            IconButton(onClick = onNext) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedNotificationView(
    notification: com.dynamicisland.android.model.NotificationItem,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (notification.icon != null) {
                Image(
                    bitmap = notification.icon.asImageBitmap(),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2C2C2E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bell),
                        contentDescription = "Notification",
                        tint = Color(0xFFFF9F0A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.appName,
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = notification.title.ifBlank { notification.appName },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = notification.text,
            color = Color(0xCCEBEBF5),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Notification Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            notification.actions.take(2).forEach { action ->
                Button(
                    onClick = {
                        try {
                            action.intent?.send()
                        } catch (e: Exception) {
                            // Ignored
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C2E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(text = action.title, fontSize = 12.sp)
                }
            }

            if (notification.contentIntent != null && notification.actions.isEmpty()) {
                Button(
                    onClick = {
                        try {
                            notification.contentIntent.send()
                        } catch (e: Exception) {
                            // Ignored
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A84FF),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "Open", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ExpandedCallView(
    call: com.dynamicisland.android.model.CallInfo,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF30D158)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call),
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.callerName ?: "Unknown Caller",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (call.isInCall) "Call in progress • ${call.formattedDuration}" else "Incoming Call...",
                    color = Color(0xFF30D158),
                    fontSize = 12.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline / End Call Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF453A))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_end),
                    contentDescription = "Decline",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (call.state == CallState.RINGING) {
                // Answer Call Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30D158))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_call),
                        contentDescription = "Answer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedBatteryView(
    battery: com.dynamicisland.android.model.BatteryInfo
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_battery_charging),
                contentDescription = "Battery",
                tint = Color(0xFF30D158),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "${battery.level}% Charged",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = battery.chargingSpeedType,
                color = Color(0xFF30D158),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ExpandedTimerView(
    timer: com.dynamicisland.android.model.TimerInfo,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF9F0A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bell),
                    contentDescription = "Timer",
                    tint = Color(0xFFFF9F0A),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = timer.label,
                    color = Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = timer.formattedRemaining,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9F0A))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pause),
                contentDescription = "Pause",
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ExpandedRingerView(
    isMuted: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) Color(0x33FF453A) else Color(0x3330D158)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bell),
                    contentDescription = "Ringer",
                    tint = if (isMuted) Color(0xFFFF453A) else Color(0xFF30D158),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "Silent Mode",
                    color = Color(0x99FFFFFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (isMuted) "On" else "Off",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2C2C2E),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(text = "Done", fontSize = 12.sp)
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
