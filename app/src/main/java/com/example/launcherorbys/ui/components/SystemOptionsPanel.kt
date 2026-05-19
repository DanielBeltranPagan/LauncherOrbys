package com.example.launcherorbys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Panel visual que contiene los controles de brillo, volumen y accesos rápidos
 */
@Composable
fun SystemOptionsPanel(
    onSettingsClick: () -> Unit,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onMuteClick: () -> Unit,
    onPowerClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onRecordClick: () -> Unit,
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

    // Tarjeta principal del panel
    Card(
        modifier = modifier
            .width(320.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.95f),
                            Color.DarkGray.copy(alpha = 0.9f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Panel de Control",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                // --- SECCIÓN DE CONTROLES DESLIZANTES ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Control de Brillo
                    SliderRow(
                        icon = Icons.Default.BrightnessMedium,
                        value = currentBrightness,
                        onValueChange = onBrightnessChange,
                        enabled = !isAutoBrightness,
                        trailingContent = {
                            IconButton(
                                onClick = { onAutoBrightnessChange(!isAutoBrightness) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(
                                    text = "A",
                                    color = if (isAutoBrightness) themeColor else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    )
                    
                    // Control de Volumen
                    SliderRow(
                        icon = if (isMuted || currentVolume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        value = if (isMuted) 0f else currentVolume,
                        onValueChange = onVolumeChange,
                        iconClick = onMuteClick,
                        iconTint = if (isMuted) themeColor else Color.White
                    )
                }

                // --- SECCIÓN DE ACCESOS RÁPIDOS (GRILLA 3xN) ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Fila 1: Conectividad y Audio
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickActionButton(Icons.Default.Wifi, "Wi-Fi", onWifiClick, isWifiOn)
                        QuickActionButton(Icons.Default.Bluetooth, "Bluetooth", onBluetoothClick, isBluetoothOn)
                        QuickActionButton(Icons.AutoMirrored.Filled.VolumeOff, "Silencio", onMuteClick, isMuted)
                    }
                    // Fila 2: Ajustes y Capturas
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickActionButton(Icons.Default.Settings, "Ajustes", onSettingsClick)
                        QuickActionButton(Icons.Default.Screenshot, "Captura", onScreenshotClick)
                        QuickActionButton(Icons.Default.Videocam, "Grabar", onRecordClick)
                    }
                    // Fila 3: Sistema
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        QuickActionButton(Icons.Default.PowerSettingsNew, "Apagar", onPowerClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true,
    iconClick: (() -> Unit)? = null,
    iconTint: Color = Color.White,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = { iconClick?.invoke() },
            enabled = iconClick != null,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.DarkGray
            )
        )
        
        trailingContent?.invoke()
    }
}

/**
 * Componente reutilizable para los botones circulares del panel
 */
@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(70.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            label, 
            color = Color.White.copy(alpha = 0.8f), 
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
