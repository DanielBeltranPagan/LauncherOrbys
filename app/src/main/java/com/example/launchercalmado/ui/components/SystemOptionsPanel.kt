package com.example.launchercalmado.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onAirplaneModeClick: () -> Unit,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isAutoBrightness: Boolean,
    onAutoBrightnessChange: (Boolean) -> Unit,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Tarjeta principal del panel
    Card(
        modifier = modifier
            .width(280.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.9f) // Fondo negro semi-transparente
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Text(
                "Opciones",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // --- SECCIÓN DE CONTROLES DESLIZANTES ---
            // Aquí dibujamos las barras de Brillo y Volumen
            Column(modifier = Modifier.fillMaxWidth()) {
                // Control de Brillo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Slider(
                        value = currentBrightness,
                        onValueChange = onBrightnessChange,
                        enabled = !isAutoBrightness, // Desactiva la barra si el brillo automático está encendido
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            disabledThumbColor = Color.Gray,
                            disabledActiveTrackColor = Color.Gray
                        )
                    )
                    // Botón para activar/desactivar el Brillo Automático
                    IconButton(
                        onClick = { onAutoBrightnessChange(!isAutoBrightness) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "A",
                            color = if (isAutoBrightness) Color.Cyan else Color.White, // Cian si está activo
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                // Control de Volumen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Slider(
                        value = currentVolume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // --- SECCIÓN DE ACCESOS RÁPIDOS (BOTONES) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(Icons.Default.Wifi, "Wi-Fi", onWifiClick)
                QuickActionButton(Icons.Default.Bluetooth, "Bluetooth", onBluetoothClick)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(Icons.Default.AirplanemodeActive, "Avión", onAirplaneModeClick)
                QuickActionButton(Icons.Default.Settings, "Ajustes", onSettingsClick)
            }
        }
    }
}

/**
 * Componente reutilizable para los botones circulares del panel
 */
@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(4.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
