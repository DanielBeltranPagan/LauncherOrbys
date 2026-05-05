package com.example.launchercalmado.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickMenu() {
    var isExpanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(targetValue = if (isExpanded) 100.dp else 40.dp, label = "quickAnim")

    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(0.4f)
            .clickable { isExpanded = !isExpanded },
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        if (!isExpanded) {
            Box(contentAlignment = Alignment.Center) {
                Text("⋮", color = Color.White, fontSize = 20.sp)
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("Accesos Rápidos", color = Color.Cyan, fontSize = 14.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color.White)
                    Text(
                        text = " Navegador",
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    Text(
                        text = " Ajustes",
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}