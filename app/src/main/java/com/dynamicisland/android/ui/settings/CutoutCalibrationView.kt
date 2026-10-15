package com.dynamicisland.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.android.model.IslandSettings
import com.dynamicisland.android.ui.theme.AppleBlue
import com.dynamicisland.android.ui.theme.AppleDarkCard
import com.dynamicisland.android.ui.theme.AppleGlassBorder
import com.dynamicisland.android.ui.theme.AppleGreen
import com.dynamicisland.android.ui.theme.AppleTextPrimary
import com.dynamicisland.android.ui.theme.AppleTextSecondary

@Composable
fun CutoutCalibrationView(
    currentSettings: IslandSettings,
    onSave: (IslandSettings) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var xOffset by remember { mutableIntStateOf(currentSettings.xOffsetDp) }
    var yOffset by remember { mutableIntStateOf(currentSettings.yOffsetDp) }
    var width by remember { mutableIntStateOf(currentSettings.compactWidthDp) }
    var height by remember { mutableIntStateOf(currentSettings.compactHeightDp) }
    var cornerRadius by remember { mutableIntStateOf(currentSettings.cornerRadiusDp) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppleDarkCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Screen Cutout Calibrator",
                style = MaterialTheme.typography.titleLarge,
                color = AppleTextPrimary
            )

            Text(
                text = "Align the pill so it perfectly encapsulates your camera hole punch or notch.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleTextSecondary
            )

            // Live Interactive Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F0F12))
                    .border(1.dp, AppleGlassBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.TopCenter
            ) {
                // Simulated Camera Cutout Dot
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, yOffset + (height / 2) - 6) }
                        .size(12.dp)
                        .background(Color(0xFF2A2A30), CircleShape)
                        .border(1.dp, AppleBlue, CircleShape)
                )

                // Simulated Dynamic Island Pill
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOffset, yOffset) }
                        .width(width.dp)
                        .height(height.dp)
                        .border(1.dp, AppleGreen, RoundedCornerShape(cornerRadius.dp))
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(cornerRadius.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Preview",
                        color = AppleTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Controls
            CalibrationSlider(
                label = "Horizontal Offset (X)",
                value = xOffset.toFloat(),
                range = -150f..150f,
                unit = "dp",
                onValueChange = { xOffset = it.toInt() }
            )

            CalibrationSlider(
                label = "Vertical Offset (Y)",
                value = yOffset.toFloat(),
                range = 0f..80f,
                unit = "dp",
                onValueChange = { yOffset = it.toInt() }
            )

            CalibrationSlider(
                label = "Compact Width",
                value = width.toFloat(),
                range = 70f..220f,
                unit = "dp",
                onValueChange = { width = it.toInt() }
            )

            CalibrationSlider(
                label = "Compact Height",
                value = height.toFloat(),
                range = 24f..56f,
                unit = "dp",
                onValueChange = { height = it.toInt() }
            )

            CalibrationSlider(
                label = "Corner Radius",
                value = cornerRadius.toFloat(),
                range = 10f..28f,
                unit = "dp",
                onValueChange = { cornerRadius = it.toInt() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleTextPrimary)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val updated = currentSettings.copy(
                            xOffsetDp = xOffset,
                            yOffsetDp = yOffset,
                            compactWidthDp = width,
                            compactHeightDp = height,
                            cornerRadiusDp = cornerRadius
                        )
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun CalibrationSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = AppleTextSecondary, fontSize = 13.sp)
            Text(text = "${value.toInt()} $unit", color = AppleBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AppleBlue,
                activeTrackColor = AppleBlue,
                inactiveTrackColor = AppleGlassBorder
            )
        )
    }
}

