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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

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
    
    // Estado inicial
    var currentTime by remember { 
        mutableStateOf(timeFormatter.format(Date())) 
    }

    // Efecto para actualizar la hora cada segundo
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
            // Fila central con los iconos de navegación
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { onActionClicked("BACK") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(15.dp))
                IconButton(onClick = { onActionClicked("HOME") }) {
                    Icon(Icons.Default.Home, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(15.dp))
                IconButton(onClick = { onActionClicked("RECENTS") }) {
                    Icon(Icons.Default.CropSquare, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(15.dp))
                IconButton(onClick = { onActionClicked("APPS") }) {
                    Icon(Icons.Default.Apps, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(15.dp))
                IconButton(onClick = { onActionClicked("GOOGLE") }) {
                    Icon(Icons.Default.Search, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(15.dp))
                IconButton(onClick = { onActionClicked("FILES") }) {
                    Icon(Icons.Default.Folder, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(15.dp))
                IconButton(onClick = { onActionClicked("SYSTEM_OPTIONS") }) {
                    Icon(Icons.Default.Tune, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }
            }

            // Reloj en el lateral izquierdo
            // Lo ponemos en un Box alineado para que no interfiera con el centrado del Row
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
