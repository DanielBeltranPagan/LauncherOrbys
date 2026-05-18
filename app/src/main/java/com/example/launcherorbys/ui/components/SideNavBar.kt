package com.example.launcherorbys.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/**
 * Barra lateral táctil para navegación rápida.
 * Permite realizar acciones de navegación (Atrás, Inicio, Recientes) y controlar la visibilidad de la NavBar.
 */
@Composable
fun SideNavBar(
    isLeft: Boolean,            // Indica si la barra está a la izquierda o derecha
    onAction: (String) -> Unit, // Callback para ejecutar comandos (BACK, HOME, etc.)
    onDrag: (Float) -> Unit,    // Maneja el desplazamiento vertical de la barra
    isNavBarVisible: Boolean = true,
    isNavBarAtTop: Boolean = false,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(true) }
    // Animación suave del ancho al contraer/expandir
    val width by animateDpAsState(targetValue = if (isExpanded) 44.dp else 24.dp, label = "width")
    
    Box(
        modifier = modifier
            .width(width)
            .heightIn(min = if (isExpanded) 200.dp else 60.dp)
            .onSizeChanged { onHeightChanged(it.height) }
            .pointerInput(Unit) {
                // Permite arrastrar la barra verticalmente por la pantalla
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            }
            .background(
                color = Color.Black.copy(alpha = 0.85f),
                shape = if (isLeft) {
                    RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                } else {
                    RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                }
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón de Contraer/Expandir Sidebar
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
                    } else {
                        if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    },
                    contentDescription = "Toggle Sidebar",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isExpanded) {
                // Botón Back
                IconButton(onClick = { onAction("BACK") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
                }

                // Botón Home
                IconButton(onClick = { onAction("HOME") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Home, "Inicio", tint = Color.White)
                }

                // Botón Recents
                IconButton(onClick = { onAction("RECENTS") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CropSquare, "Recientes", tint = Color.White)
                }

                // Botón NavBar Toggle (Flechas según posición y visibilidad)
                IconButton(onClick = { onAction("TOGGLE_NAVBAR_VISIBILITY") }, modifier = Modifier.size(36.dp)) {
                    val arrowIcon = if (isNavBarAtTop) {
                        if (isNavBarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                    } else {
                        if (isNavBarVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
                    }
                    
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = "Toggle NavBar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
