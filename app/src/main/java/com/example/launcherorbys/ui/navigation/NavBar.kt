package com.example.launcherorbys.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val timeFormatter = remember { 
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }
    
    var currentTime by remember { 
        mutableStateOf(timeFormatter.format(Date())) 
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormatter.format(Date())
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
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
