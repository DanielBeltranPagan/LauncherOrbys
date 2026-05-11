package com.example.launchercalmado.ui.components

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.launchercalmado.logic.AppLoader
import com.example.launchercalmado.receivers.PackageReceiver

/**
 * Componente que muestra el cajón de aplicaciones en una cuadrícula.
 */
@Composable
fun AppDrawer() {
    val context = LocalContext.current
    // Cargamos la lista de apps instaladas
    var apps by remember { mutableStateOf(AppLoader(context).loadInstalledApps()) }

    // Registramos un receptor para actualizar la lista si se instalan/desinstalan apps
    DisposableEffect(Unit) {
        val receiver = PackageReceiver { 
            apps = AppLoader(context).loadInstalledApps() 
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Aplicaciones",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(75.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps) { app ->
                    AppItem(app)
                }
            }
        }
    }
}

/**
 * Representación individual de una aplicación en el cajón.
 */
@Composable
fun AppItem(app: com.example.launchercalmado.data.AppInfo) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .clickable {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.let { context.startActivity(it) }
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        app.icon?.let {
            Image(
                bitmap = it.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
