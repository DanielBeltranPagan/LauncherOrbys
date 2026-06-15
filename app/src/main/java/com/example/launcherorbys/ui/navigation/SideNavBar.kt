package com.example.launcherorbys.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
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
 */
@Composable
fun SideNavBar(
    isLeft: Boolean,
    onAction: (String) -> Unit,
    onDrag: (Float) -> Unit,
    isNavBarVisible: Boolean = true,
    isNavBarAtTop: Boolean = false,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {}
) {
    var estaExpandida by remember { mutableStateOf(true) }
    val ancho by animateDpAsState(targetValue = if (estaExpandida) 36.dp else 12.dp, label = "anchoNav")
    val rellenoLateral by animateDpAsState(targetValue = if (estaExpandida) 4.dp else 0.dp, label = "rellenoNav")
    
    Box(
        modifier = modifier
            .padding(
                start = if (isLeft) rellenoLateral else 0.dp,
                end = if (!isLeft) rellenoLateral else 0.dp
            )
            .width(ancho)
            .heightIn(min = if (estaExpandida) 160.dp else 40.dp)
            .systemGestureExclusion()
            .onSizeChanged { onHeightChanged(it.height) }
            .pointerInput(Unit) {
                detectDragGestures { cambio, cantidadArrastre ->
                    cambio.consume()
                    onDrag(cantidadArrastre.y)
                }
            }
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = if (estaExpandida) {
                    RoundedCornerShape(20.dp)
                } else {
                    if (isLeft) RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                    else RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                }
            )
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { estaExpandida = !estaExpandida },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (estaExpandida) {
                        if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
                    } else {
                        if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    },
                    contentDescription = "Alternar Sidebar",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (estaExpandida) {
                IconButton(onClick = { onAction("BACK") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(onClick = { onAction("HOME") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Home, "Inicio", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(onClick = { onAction("RECENTS") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.CropSquare, "Recientes", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(onClick = { onAction("TOGGLE_NAVBAR_VISIBILITY") }, modifier = Modifier.size(28.dp)) {
                    val iconoFlecha = if (isNavBarAtTop) {
                        if (isNavBarVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                    } else {
                        if (isNavBarVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
                    }
                    
                    Icon(
                        imageVector = iconoFlecha,
                        contentDescription = "Alternar NavBar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
