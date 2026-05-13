package com.example.launchercalmado.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Barra de navegación personalizada del launcher.
 * Incluye controles de navegación del sistema, accesos rápidos y un reloj.
 */
@Composable
fun NavBar(
    onActionClicked: (String) -> Unit,
    iconColor: Color = Color.White,
    backgroundColor: Color = Color.Black
) {
    // Formateador para la hora forzado a la zona horaria de España (Europe/Madrid)
    val timeFormatter = remember { 
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Madrid")
        }
    }
    
    // Estado reactivo para la hora actual
    var currentTime by remember { 
        mutableStateOf(timeFormatter.format(Date())) 
    }

    // Bucle que actualiza la hora cada segundo mientras el componente esté activo
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormatter.format(Date())
            delay(1000)
        }
    }

    Surface(
        color = backgroundColor,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fila central con los iconos de navegación y accesos rápidos
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NavBarIcon(Icons.AutoMirrored.Filled.ArrowBack, iconColor) { onActionClicked("BACK") }
                Spacer(modifier = Modifier.width(15.dp))
                NavBarIcon(Icons.Default.Home, iconColor) { onActionClicked("HOME") }
                Spacer(modifier = Modifier.width(15.dp))
                NavBarIcon(Icons.Default.CropSquare, iconColor) { onActionClicked("RECENTS") }
                Spacer(modifier = Modifier.width(15.dp))
                NavBarIcon(Icons.Default.Apps, iconColor) { onActionClicked("APPS") }
                Spacer(modifier = Modifier.width(15.dp))
                NavBarIcon(Icons.Default.Search, iconColor) { onActionClicked("GOOGLE") }
                Spacer(modifier = Modifier.width(15.dp))
                NavBarIcon(Icons.Default.Folder, iconColor) { onActionClicked("FILES") }
                Spacer(modifier = Modifier.width(15.dp))
                NavBarIcon(Icons.Default.Tune, iconColor) { onActionClicked("SYSTEM_OPTIONS") }
            }

            // Reloj en el lateral izquierdo
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .clickable { onActionClicked("CLOCK") },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentTime,
                    color = iconColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Componente interno para estandarizar los iconos de la barra de navegación.
 */
@Composable
private fun NavBarIcon(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
