package com.example.launcherorbys.ui.system

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

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
    onRecordClick: () -> Unit,
    isWifiOn: Boolean,
    isBluetoothOn: Boolean,
    @Suppress("UNUSED_PARAMETER") isMuted: Boolean,
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
            // --- SLIDERS MINIMALISTAS ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Brillo
                CompactSliderRow(
                    icon = if (isAutoBrightness) Icons.Default.BrightnessAuto else Icons.Default.BrightnessLow,
                    value = currentBrightness,
                    onValueChange = onBrightnessChange,
                    enabled = !isAutoBrightness,
                    color = if (isAutoBrightness) themeColor else Color.White,
                    onIconClick = { onAutoBrightnessChange(!isAutoBrightness) }
                )
                
                // Volumen
                CompactSliderRow(
                    icon = if (currentVolume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    value = currentVolume,
                    onValueChange = onVolumeChange,
                    color = if (currentVolume == 0f) themeColor else Color.White,
                    onIconClick = onMuteClick
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

            // --- GRID DE ACCIONES (Iconos únicamente para máxima limpieza) ---
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
                    MiniIconButton(icon = Icons.Default.Videocam, onClick = onRecordClick)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    MiniIconButton(icon = Icons.Default.PowerSettingsNew, isDanger = true, onClick = onPowerClick)
                }
            }
        }
    }
}

@Composable
private fun CompactSliderRow(
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    color: Color = Color.White,
    onIconClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(32.dp)
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = color.copy(alpha = 0.7f), 
            modifier = Modifier
                .size(18.dp)
                .clickable { onIconClick() }
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
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
            isActive -> MaterialTheme.colorScheme.primary
            isDanger -> Color(0xFFEF5350).copy(alpha = 0.2f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = when {
            isActive -> MaterialTheme.colorScheme.onPrimary
            isDanger -> Color(0xFFEF5350)
            else -> Color.White.copy(alpha = 0.8f)
        },
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
