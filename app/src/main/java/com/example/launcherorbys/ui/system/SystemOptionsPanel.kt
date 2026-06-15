package com.example.launcherorbys.ui.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Panel visual ultra-compacto y minimalista.
 */
@Composable
fun SystemOptionsPanel(
    onSettingsClick: () -> Unit,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onWallpaperClick: () -> Unit,
    onMuteClick: () -> Unit,
    onPowerClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    isWifiOn: Boolean,
    isBluetoothOn: Boolean,
    isMuted: Boolean,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isAutoBrightness: Boolean,
    onAutoBrightnessChange: (Boolean) -> Unit,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .width(220.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF0A0A0A).copy(alpha = 0.9f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SLIDERS ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Brillo
                CompactSliderRow(
                    icon = if (isAutoBrightness) Icons.Default.BrightnessAuto else if (currentBrightness > 0.5f) Icons.Default.BrightnessHigh else Icons.Default.BrightnessLow,
                    value = currentBrightness,
                    onValueChange = onBrightnessChange,
                    enabled = !isAutoBrightness,
                    iconColor = if (isAutoBrightness) themeColor else Color.White.copy(alpha = 0.8f),
                    onIconClick = { onAutoBrightnessChange(!isAutoBrightness) }
                )
                
                // Volumen con Mute integrado
                CompactSliderRow(
                    icon = if (isMuted || currentVolume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    value = if (isMuted) 0f else currentVolume,
                    onValueChange = onVolumeChange,
                    iconColor = if (isMuted) Color(0xFFEF5350) else Color.White.copy(alpha = 0.8f),
                    onIconClick = onMuteClick
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            // --- GRID DE ACCIONES ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniIconButton(icon = Icons.Default.Wifi, isActive = isWifiOn, onClick = onWifiClick)
                    MiniIconButton(icon = Icons.Default.Bluetooth, isActive = isBluetoothOn, onClick = onBluetoothClick)
                    MiniIconButton(icon = Icons.Default.Wallpaper, onClick = onWallpaperClick)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniIconButton(icon = Icons.Default.Settings, onClick = onSettingsClick)
                    MiniIconButton(icon = Icons.Default.Screenshot, onClick = onScreenshotClick)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    MiniIconButton(icon = Icons.Default.PowerSettingsNew, isDanger = true, onClick = onPowerClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSliderRow(
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    iconColor: Color = Color.White.copy(alpha = 0.8f),
    onIconClick: () -> Unit
) {
    val trackColor = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
    val inactiveTrackColor = Color.White.copy(alpha = 0.1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(32.dp)
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = iconColor, 
            modifier = Modifier
                .size(18.dp)
                .clickable { onIconClick() }
        )
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = if (enabled) Color.White else Color.Transparent,
                activeTrackColor = trackColor,
                inactiveTrackColor = inactiveTrackColor,
                disabledActiveTrackColor = trackColor,
                disabledInactiveTrackColor = inactiveTrackColor,
                disabledThumbColor = Color.Transparent
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        activeTrackColor = trackColor,
                        inactiveTrackColor = inactiveTrackColor,
                        disabledActiveTrackColor = trackColor,
                        disabledInactiveTrackColor = inactiveTrackColor
                    ),
                    modifier = Modifier.height(6.dp).clip(CircleShape),
                    thumbTrackGapSize = 0.dp
                )
            },
            thumb = {
                if (enabled) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        )
    }
}

@Composable
private fun MiniIconButton(
    icon: ImageVector,
    isActive: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            isActive -> Color(0xFF2196F3) // Azul vibrante
            isDanger -> Color(0xFFEF5350).copy(alpha = 0.2f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        contentColor = Color.White,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(20.dp),
                tint = if (isActive) Color.White else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
