package com.example.launchercalmado.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NavBar(onActionClicked: (String) -> Unit) {
    Surface(
        color = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center // Botones juntos en el medio
        ) {
            IconButton(onClick = { onActionClicked("BACK") }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(15.dp))
            IconButton(onClick = { onActionClicked("HOME") }) {
                Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(15.dp))
            IconButton(onClick = { onActionClicked("RECENTS") }) {
                Icon(Icons.Default.CropSquare, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(15.dp))
            IconButton(onClick = { onActionClicked("APPS") }) {
                Icon(Icons.Default.Apps, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(15.dp))
            IconButton(onClick = { onActionClicked("GOOGLE") }) {
                Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(15.dp))
            IconButton(onClick = { onActionClicked("FILES") }) {
                Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}