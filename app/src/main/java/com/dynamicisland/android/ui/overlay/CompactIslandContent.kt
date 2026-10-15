package com.dynamicisland.android.ui.overlay

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.R
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.ui.theme.AppleBlue
import com.dynamicisland.android.ui.theme.AppleGreen
import com.dynamicisland.android.ui.theme.AppleOrange
import com.dynamicisland.android.ui.theme.ApplePink
import com.dynamicisland.android.ui.theme.AppleRed

@Composable
fun CompactIslandContent(
    state: IslandState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = state, label = "compact_content") { currentState ->
            when (currentState) {
                is IslandState.CompactMusic -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentState.media.albumArt != null) {
                            Image(
                                bitmap = currentState.media.albumArt.asImageBitmap(),
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF2C2C2E), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_music),
                                    contentDescription = "Music",
                                    tint = ApplePink,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        AudioWaveformVisualizer(
                            isPlaying = currentState.media.isPlaying,
                            color = AppleGreen,
                            maxHeight = 15.dp,
                            barWidth = 2.5.dp
                        )
                    }
                }

                is IslandState.CompactCall -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_call),
                            contentDescription = "Call",
                            tint = AppleGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (currentState.call.isInCall) {
                                currentState.call.formattedDuration
                            } else "Call",
                            color = AppleGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                is IslandState.CompactBattery -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_battery_charging),
                            contentDescription = "Charging",
                            tint = AppleGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentState.battery.displayPercentage,
                            color = AppleGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is IslandState.CompactTimer -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bell),
                            contentDescription = "Timer",
                            tint = AppleOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = currentState.timer.formattedRemaining,
                            color = AppleOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is IslandState.RingerToast -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bell),
                            contentDescription = "Ringer",
                            tint = if (currentState.isMuted) AppleRed else AppleGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentState.isMuted) "Silent" else "Ring",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is IslandState.BluetoothToast -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music),
                            contentDescription = "Bluetooth",
                            tint = AppleBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentState.deviceName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                is IslandState.NotificationToast -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentState.notification.icon != null) {
                            Image(
                                bitmap = currentState.notification.icon.asImageBitmap(),
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bell),
                                contentDescription = "Notification",
                                tint = AppleOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentState.notification.title.ifBlank { currentState.notification.appName },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is IslandState.ChargingToast -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_battery_charging),
                            contentDescription = "Charging",
                            tint = AppleGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentState.battery.displayPercentage,
                            color = AppleGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is IslandState.LowBatteryToast -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_battery_charging),
                            contentDescription = "Low Battery",
                            tint = AppleRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${currentState.battery.displayPercentage} Low",
                            color = AppleRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

