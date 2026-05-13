package com.example.launcherorbys.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
fun SideNavBar(
    isLeft: Boolean,
    onAction: (String) -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(true) }
    val width by animateDpAsState(targetValue = if (isExpanded) 36.dp else 20.dp, label = "width")
    
    Box(
        modifier = modifier
            .width(width)
            .heightIn(min = if (isExpanded) 130.dp else 60.dp)
            .onSizeChanged { onHeightChanged(it.height) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            }
            .background(
                color = Color.Black.copy(alpha = 0.9f),
                shape = if (isLeft) {
                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                }
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Botón de Contraer/Expandir
            Icon(
                imageVector = if (isExpanded) {
                    if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft
                },
                contentDescription = "Toggle",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { isExpanded = !isExpanded }
            )

            if (isExpanded) {
                // Botón Home
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onAction("HOME") }
                )

                // Botón Back
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onAction("BACK") }
                )
            }
        }
    }
}
