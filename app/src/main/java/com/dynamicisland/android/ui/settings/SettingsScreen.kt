package com.dynamicisland.android.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.R
import com.dynamicisland.android.data.SettingsRepository
import com.dynamicisland.android.model.BatteryInfo
import com.dynamicisland.android.model.CallInfo
import com.dynamicisland.android.model.CallSource
import com.dynamicisland.android.model.CallState
import com.dynamicisland.android.model.CutoutType
import com.dynamicisland.android.model.IslandEvent
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.model.IslandState
import com.dynamicisland.android.model.IslandThemeMode
import com.dynamicisland.android.model.MediaInfo
import com.dynamicisland.android.model.NotificationItem
import com.dynamicisland.android.model.TimerInfo
import com.dynamicisland.android.service.IslandEventBus
import com.dynamicisland.android.ui.overlay.IslandOverlayHost
import com.dynamicisland.android.ui.theme.AppleBlack
import com.dynamicisland.android.ui.theme.AppleBlue
import com.dynamicisland.android.ui.theme.AppleDarkCard
import com.dynamicisland.android.ui.theme.AppleDarkCardSecondary
import com.dynamicisland.android.ui.theme.AppleGlassBorder
import com.dynamicisland.android.ui.theme.AppleGreen
import com.dynamicisland.android.ui.theme.AppleOrange
import com.dynamicisland.android.ui.theme.ApplePink
import com.dynamicisland.android.ui.theme.ApplePurple
import com.dynamicisland.android.ui.theme.AppleRed
import com.dynamicisland.android.ui.theme.AppleTextPrimary
import com.dynamicisland.android.ui.theme.AppleTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onRestartService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by repository.settingsFlow.collectAsState()
    var showCalibrator by remember { mutableStateOf(false) }

    // Live permission states
    var hasOverlay by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var hasNotification by remember { mutableStateOf(PermissionHelper.hasNotificationListenerPermission(context)) }
    var hasPhone by remember { mutableStateOf(PermissionHelper.hasPhonePermission(context)) }

    LaunchedEffect(Unit) {
        hasOverlay = PermissionHelper.hasOverlayPermission(context)
        hasNotification = PermissionHelper.hasNotificationListenerPermission(context)
        hasPhone = PermissionHelper.hasPhonePermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleBlack)
                                .border(1.dp, AppleGlassBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Dynamic Island",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "iOS Dynamic Island for Android",
                                fontSize = 11.sp,
                                color = AppleTextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppleBlack
                )
            )
        },
        containerColor = AppleBlack,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Header Card
            item {
                LivePreviewCard(settings = settings)
            }

            // Master Switch & Service Status
            item {
                StatusCard(
                    isEnabled = settings.isEnabled,
                    hasPermissions = hasOverlay && hasNotification,
                    onToggle = { enabled ->
                        repository.updateSettings { it.copy(isEnabled = enabled) }
                        onRestartService()
                    }
                )
            }

            // Permissions Status
            item {
                PermissionsCard(
                    hasOverlay = hasOverlay,
                    hasNotification = hasNotification,
                    hasPhone = hasPhone,
                    onRequestOverlay = onRequestOverlayPermission,
                    onRequestNotification = onRequestNotificationPermission,
                    onRequestPhone = onRequestPhonePermission
                )
            }

            // Cutout & Position Alignment
            item {
                CutoutPositionCard(
                    settings = settings,
                    onCutoutTypeChange = { type ->
                        val updated = when (type) {
                            CutoutType.LEFT_PUNCH_HOLE -> settings.copy(cutoutType = type, xOffsetDp = 24)
                            CutoutType.RIGHT_PUNCH_HOLE -> settings.copy(cutoutType = type, xOffsetDp = -24)
                            CutoutType.WATERDROP_NOTCH -> settings.copy(cutoutType = type, xOffsetDp = 0, yOffsetDp = 0)
                            else -> settings.copy(cutoutType = type, xOffsetDp = 0)
                        }
                        repository.updateSettings { updated }
                    },
                    onOpenCalibrator = { showCalibrator = true }
                )
            }

            // Active Features Section
            item {
                FeaturesCard(
                    settings = settings,
                    onToggleMusic = { v -> repository.updateSettings { it.copy(enableMusic = v) } },
                    onToggleNotifications = { v -> repository.updateSettings { it.copy(enableNotifications = v) } },
                    onToggleCalls = { v -> repository.updateSettings { it.copy(enableCalls = v) } },
                    onToggleBattery = { v -> repository.updateSettings { it.copy(enableBattery = v) } },
                    onToggleTimer = { v -> repository.updateSettings { it.copy(enableTimer = v) } },
                    onToggleRinger = { v -> repository.updateSettings { it.copy(enableRinger = v) } }
                )
            }

            // Appearance & Themes Section
            item {
                AppearanceCard(
                    settings = settings,
                    onThemeChange = { theme -> repository.updateSettings { it.copy(themeMode = theme) } },
                    onToggleVibration = { v -> repository.updateSettings { it.copy(vibrationFeedback = v) } },
                    onToggleStartOnBoot = { v -> repository.updateSettings { it.copy(startOnBoot = v) } }
                )
            }

            // Live Simulator Section
            item {
                SimulatorCard()
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    if (showCalibrator) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCalibrator = false }) {
            CutoutCalibrationView(
                currentSettings = settings,
                onSave = { updated ->
                    repository.updateSettings { updated }
                    showCalibrator = false
                },
                onCancel = { showCalibrator = false }
            )
        }
    }
}

@Composable
private fun LivePreviewCard(settings: IslandSettings) {
    var previewState by remember {
        mutableStateOf<IslandState>(
            IslandState.CompactMusic(
                MediaInfo(
                    title = "Starboy",
                    artist = "The Weeknd",
                    isPlaying = true
                )
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interactive Live Preview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )

                Box(
                    modifier = Modifier
                        .background(AppleDarkCardSecondary, RoundedCornerShape(12.dp))
                        .clickable {
                            previewState = if (previewState.isExpanded) {
                                IslandState.CompactMusic(
                                    MediaInfo(title = "Starboy", artist = "The Weeknd", isPlaying = true)
                                )
                            } else {
                                IslandState.ExpandedMusic(
                                    MediaInfo(title = "Starboy", artist = "The Weeknd", isPlaying = true)
                                )
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (previewState.isExpanded) "Collapse" else "Expand",
                        color = AppleBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Simulated Screen Header with Camera Notch & Live Island
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (previewState.isExpanded) 170.dp else 70.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C0C0E))
                    .border(1.dp, AppleGlassBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                IslandOverlayHost(
                    state = previewState,
                    settings = settings,
                    onTap = {
                        previewState = if (previewState.isExpanded) {
                            IslandState.CompactMusic(MediaInfo(title = "Starboy", artist = "The Weeknd", isPlaying = true))
                        } else {
                            IslandState.ExpandedMusic(MediaInfo(title = "Starboy", artist = "The Weeknd", isPlaying = true))
                        }
                    },
                    onLongPress = {},
                    onDismissRequest = {
                        previewState = IslandState.CompactMusic(MediaInfo(title = "Starboy", artist = "The Weeknd", isPlaying = true))
                    },
                    onAutoDismissToast = {}
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    isEnabled: Boolean,
    hasPermissions: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled && hasPermissions) AppleGreen.copy(alpha = 0.2f) else AppleRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled && hasPermissions) AppleGreen else AppleRed)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isEnabled && hasPermissions) "Overlay Active" else if (!hasPermissions) "Permission Required" else "Service Stopped",
                        color = AppleTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnabled && hasPermissions) "Dynamic Island is running above apps" else "Enable service and grant overlay access",
                        color = AppleTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AppleTextPrimary,
                    checkedTrackColor = AppleGreen
                )
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    hasOverlay: Boolean,
    hasNotification: Boolean,
    hasPhone: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestPhone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "System Permissions",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            PermissionRow(
                title = "Display Over Other Apps",
                description = "Enables floating overlay over apps and lock screen",
                isGranted = hasOverlay,
                onGrant = onRequestOverlay
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionRow(
                title = "Notification Listener",
                description = "Displays live message snippets and music controls",
                isGranted = hasNotification,
                onGrant = onRequestNotification
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionRow(
                title = "Phone State Access",
                description = "Shows caller name and running call duration timer",
                isGranted = hasPhone,
                onGrant = onRequestPhone
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = AppleTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = description, color = AppleTextSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            Box(
                modifier = Modifier
                    .background(AppleGreen.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = "Granted", color = AppleGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Grant", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CutoutPositionCard(
    settings: IslandSettings,
    onCutoutTypeChange: (CutoutType) -> Unit,
    onOpenCalibrator: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Cutout & Screen Alignment",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CutoutChip(
                    label = "Center",
                    isSelected = settings.cutoutType == CutoutType.CENTER_PUNCH_HOLE,
                    modifier = Modifier.weight(1f)
                ) {
                    onCutoutTypeChange(CutoutType.CENTER_PUNCH_HOLE)
                }
                CutoutChip(
                    label = "Left",
                    isSelected = settings.cutoutType == CutoutType.LEFT_PUNCH_HOLE,
                    modifier = Modifier.weight(1f)
                ) {
                    onCutoutTypeChange(CutoutType.LEFT_PUNCH_HOLE)
                }
                CutoutChip(
                    label = "Right",
                    isSelected = settings.cutoutType == CutoutType.RIGHT_PUNCH_HOLE,
                    modifier = Modifier.weight(1f)
                ) {
                    onCutoutTypeChange(CutoutType.RIGHT_PUNCH_HOLE)
                }
                CutoutChip(
                    label = "Notch",
                    isSelected = settings.cutoutType == CutoutType.WATERDROP_NOTCH,
                    modifier = Modifier.weight(1f)
                ) {
                    onCutoutTypeChange(CutoutType.WATERDROP_NOTCH)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onOpenCalibrator,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleDarkCardSecondary),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppleGlassBorder)
            ) {
                Text(text = "Interactive Screen Calibrator", color = AppleTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CutoutChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                color = if (isSelected) AppleBlue.copy(alpha = 0.25f) else AppleDarkCardSecondary
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AppleBlue else AppleGlassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) AppleTextPrimary else AppleTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeaturesCard(
    settings: IslandSettings,
    onToggleMusic: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleCalls: (Boolean) -> Unit,
    onToggleBattery: (Boolean) -> Unit,
    onToggleTimer: (Boolean) -> Unit,
    onToggleRinger: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Active Features",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            FeatureToggleRow("Music Controls & Equalizer", "Controls active media session", settings.enableMusic, ApplePink, onToggleMusic)
            FeatureToggleRow("Live Notification Banners", "Intercepts messages and alerts", settings.enableNotifications, AppleOrange, onToggleNotifications)
            FeatureToggleRow("Incoming & In-Call Alerts", "Displays caller & call duration", settings.enableCalls, AppleGreen, onToggleCalls)
            FeatureToggleRow("Battery & Charging Status", "Animated charge level & alerts", settings.enableBattery, AppleGreen, onToggleBattery)
            FeatureToggleRow("Countdown Timers", "Live countdown timer readout", settings.enableTimer, AppleOrange, onToggleTimer)
            FeatureToggleRow("Silent Mode Switch Banner", "Shows Ring/Silent mode toggle", settings.enableRinger, AppleRed, onToggleRinger)
        }
    }
}

@Composable
private fun FeatureToggleRow(
    title: String,
    description: String,
    isChecked: Boolean,
    iconTint: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(iconTint)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, color = AppleTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = description, color = AppleTextSecondary, fontSize = 11.sp)
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppleTextPrimary,
                checkedTrackColor = AppleGreen
            )
        )
    }
}

@Composable
private fun AppearanceCard(
    settings: IslandSettings,
    onThemeChange: (IslandThemeMode) -> Unit,
    onToggleVibration: (Boolean) -> Unit,
    onToggleStartOnBoot: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Appearance & Physics",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CutoutChip(
                    label = "AMOLED Black",
                    isSelected = settings.themeMode == IslandThemeMode.AMOLED_BLACK,
                    modifier = Modifier.weight(1f)
                ) {
                    onThemeChange(IslandThemeMode.AMOLED_BLACK)
                }
                CutoutChip(
                    label = "Frosted Glass",
                    isSelected = settings.themeMode == IslandThemeMode.FROSTED_GLASS,
                    modifier = Modifier.weight(1f)
                ) {
                    onThemeChange(IslandThemeMode.FROSTED_GLASS)
                }
                CutoutChip(
                    label = "Accent Glow",
                    isSelected = settings.themeMode == IslandThemeMode.AMBIENT_ACCENT,
                    modifier = Modifier.weight(1f)
                ) {
                    onThemeChange(IslandThemeMode.AMBIENT_ACCENT)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FeatureToggleRow("Haptic Feedback", "Tactile tick on tap and expand", settings.vibrationFeedback, ApplePurple, onToggleVibration)
            FeatureToggleRow("Start on Device Boot", "Automatically resumes on restart", settings.startOnBoot, AppleBlue, onToggleStartOnBoot)
        }
    }
}

@Composable
private fun SimulatorCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Interactive Live Simulator",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Text(
                text = "Test island animations and features instantly without external apps",
                fontSize = 12.sp,
                color = AppleTextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Row 1: Music & Notification
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimulatorButton(
                    label = "Music Track",
                    iconRes = R.drawable.ic_music,
                    iconTint = ApplePink,
                    modifier = Modifier.weight(1f)
                ) {
                    val fakeMedia = MediaInfo(
                        title = "Starboy",
                        artist = "The Weeknd",
                        isPlaying = true,
                        durationMs = 230000L,
                        currentPositionMs = 45000L
                    )
                    IslandEventBus.updateMedia(fakeMedia)
                }

                SimulatorButton(
                    label = "Notification",
                    iconRes = R.drawable.ic_bell,
                    iconTint = AppleOrange,
                    modifier = Modifier.weight(1f)
                ) {
                    val fakeNotif = NotificationItem(
                        key = "demo_key_${System.currentTimeMillis()}",
                        packageName = "com.whatsapp",
                        appName = "WhatsApp",
                        title = "Sarah Miller",
                        text = "Hey! Are you ready for our meeting?"
                    )
                    IslandEventBus.emit(IslandEvent.NotificationPosted(fakeNotif))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Battery & Call
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimulatorButton(
                    label = "Battery (88%)",
                    iconRes = R.drawable.ic_battery_charging,
                    iconTint = AppleGreen,
                    modifier = Modifier.weight(1f)
                ) {
                    val fakeBattery = BatteryInfo(
                        level = 88,
                        isCharging = true,
                        isPlugged = true,
                        chargingSpeedType = "Fast Charging (AC)"
                    )
                    IslandEventBus.updateBattery(fakeBattery)
                }

                SimulatorButton(
                    label = "Phone Call",
                    iconRes = R.drawable.ic_call,
                    iconTint = AppleGreen,
                    modifier = Modifier.weight(1f)
                ) {
                    val fakeCall = CallInfo(
                        callerName = "Alex Rivera",
                        callerNumber = "+1 (555) 019-2834",
                        state = CallState.RINGING,
                        source = CallSource.CELLULAR,
                        appName = "Phone"
                    )
                    IslandEventBus.updateCall(fakeCall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Timer & Ringer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimulatorButton(
                    label = "Timer",
                    iconRes = R.drawable.ic_bell,
                    iconTint = AppleBlue,
                    modifier = Modifier.weight(1f)
                ) {
                    val fakeTimer = TimerInfo(
                        label = "Timer",
                        totalSeconds = 300L,
                        remainingSeconds = 245L,
                        isRunning = true
                    )
                    IslandEventBus.updateTimer(fakeTimer)
                }

                SimulatorButton(
                    label = "Silent Mode",
                    iconRes = R.drawable.ic_bell,
                    iconTint = AppleRed,
                    modifier = Modifier.weight(1f)
                ) {
                    IslandEventBus.emit(IslandEvent.RingerModeToggle(isMuted = true))
                }
            }
        }
    }
}

@Composable
private fun SimulatorButton(
    label: String,
    iconRes: Int,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppleDarkCardSecondary,
            contentColor = AppleTextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppleGlassBorder)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
