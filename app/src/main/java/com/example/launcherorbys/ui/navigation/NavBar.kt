package com.example.launcherorbys.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Barra de navegación personalizada del launcher.
 * Incluye controles de navegación del sistema, accesos rápidos y un reloj.
 * Puede contraerse a un tirador central de mayor tamaño.
 */
@Composable
fun NavBar(
    onActionClicked: (String) -> Unit,
    iconColor: Color = Color.White,
    backgroundColor: Color = Color.Black,
    isAtTop: Boolean = false,
    isExpanded: Boolean = true,
    clockAtLeft: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    val height by animateDpAsState(targetValue = if (isExpanded) 48.dp else 0.dp, label = "navHeight")
    val contentAlpha by animateFloatAsState(targetValue = if (isExpanded) 1f else 0f, label = "contentAlpha")
    
    var controlsExpanded by remember { mutableStateOf(false) }

    // Auto-cierre del menú de controles tras 5 segundos o si la navbar se oculta
    LaunchedEffect(controlsExpanded, isExpanded) {
        if (!isExpanded) {
            controlsExpanded = false
        } else if (controlsExpanded) {
            delay(5000)
            controlsExpanded = false
        }
    }

    var currentTime by remember { 
        mutableStateOf("") 
    }

    // Función para obtener la hora formateada respetando los ajustes del sistema (12h/24h)
    val updateTime = {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        val sdf = SimpleDateFormat(pattern, configuration.locales[0])
        // Forzamos al calendario a obtener la zona horaria actual del sistema
        sdf.calendar = Calendar.getInstance()
        currentTime = sdf.format(Date())
    }

    // Sincronización con la hora del sistema y cambios de zona horaria/región
    DisposableEffect(context, configuration.locales[0]) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateTime()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        updateTime()
        
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // Loop de respaldo para asegurar que la hora se actualice incluso si los eventos fallan
    LaunchedEffect(configuration.locales[0]) {
        while (true) {
            updateTime()
            delay(10000) // Actualizar cada 10 segundos es suficiente para HH:mm
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .systemGestureExclusion(),
        contentAlignment = if (isAtTop) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        // Fondo principal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .background(color = backgroundColor.copy(alpha = 0.95f))
        )

        if (isExpanded) {
            // Capa para los Iconos Centrales (Perfectamente centrados en pantalla)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    NavBarIcon(Icons.AutoMirrored.Filled.ArrowBack, iconColor) { onActionClicked("BACK") }
                    Spacer(modifier = Modifier.width(16.dp))
                    NavBarIcon(Icons.Default.Home, iconColor) { onActionClicked("HOME") }
                    Spacer(modifier = Modifier.width(16.dp))
                    NavBarIcon(Icons.Default.CropSquare, iconColor) { onActionClicked("RECENTS") }
                    Spacer(modifier = Modifier.width(16.dp))
                    NavBarIcon(Icons.Default.Apps, iconColor) { onActionClicked("APPS") }
                    Spacer(modifier = Modifier.width(16.dp))
                    NavBarIcon(Icons.Default.Language, iconColor) { onActionClicked("GOOGLE") }
                    Spacer(modifier = Modifier.width(16.dp))
                    NavBarIcon(Icons.Default.Folder, iconColor) { onActionClicked("FILES") }
                    Spacer(modifier = Modifier.width(16.dp))
                    NavBarIcon(Icons.Default.Tune, iconColor) { onActionClicked("SYSTEM_OPTIONS") }
                }
            }

            // Capa para los componentes laterales (Reloj y Controles)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Definimos los componentes
                val clockComponent = @Composable {
                    Text(
                        text = currentTime,
                        color = iconColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onActionClicked("CLOCK") }
                    )
                }

                val controlsComponent = @Composable {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (controlsExpanded) {
                            NavBarIcon(Icons.Default.SwapVert, iconColor) { onActionClicked("TOGGLE_NAVBAR_POSITION") }
                            Spacer(modifier = Modifier.width(8.dp))
                            NavBarIcon(Icons.Default.SwapHoriz, iconColor) { onActionClicked("TOGGLE_CLOCK_SIDE") }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        NavBarIcon(
                            icon = if (controlsExpanded) Icons.Default.Close else Icons.Default.OpenWith,
                            color = iconColor.copy(alpha = if (controlsExpanded) 0.5f else 1f)
                        ) { controlsExpanded = !controlsExpanded }
                    }
                }

                // Posicionamiento absoluto a los lados
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    if (clockAtLeft) clockComponent() else controlsComponent()
                }

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (clockAtLeft) controlsComponent() else clockComponent()
                }
            }
        }
    }
}

@Composable
private fun NavBarIcon(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
