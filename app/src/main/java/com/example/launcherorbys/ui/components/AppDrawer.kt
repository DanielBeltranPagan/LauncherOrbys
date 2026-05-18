package com.example.launcherorbys.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.launcherorbys.data.model.AppInfo
import com.example.launcherorbys.data.repository.AppRepository
import com.example.launcherorbys.receivers.PackageReceiver

/**
 * Componente que muestra el cajón de aplicaciones en un panel flotante.
 */
@Composable
fun AppDrawer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Usamos el Repositorio para gestionar la lógica de datos
    val repository = remember { AppRepository(context) }
    // Observamos el flujo de aplicaciones del repositorio
    val apps by repository.apps.collectAsState()

    DisposableEffect(Unit) {
        // Escuchamos cambios en el sistema (apps instaladas/desinstaladas) para refrescar el repositorio
        val receiver = PackageReceiver { 
            repository.refreshApps()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { 
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // Filtrado de aplicaciones
    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isEmpty()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.65f)
            .fillMaxHeight(0.55f)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(interactionSource = null, indication = null) { /* Evita que el click cierre el drawer */ }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Buscador mejorado
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { 
                    Text(
                        "Buscar aplicaciones...", 
                        color = Color.White.copy(alpha = 0.5f), 
                        style = MaterialTheme.typography.bodyMedium 
                    ) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White
                )
            )
            
            // Rejilla de aplicaciones
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps) { app ->
                    AppItem(
                        app = app,
                        onAppLaunched = onClose
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItem(
    app: AppInfo,
    onAppLaunched: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    // METODO 1: Abrir por nombre de paquete (Usado para apps instaladas)
                    // Buscamos la "puerta de entrada" principal de la aplicación
                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                        // Añadimos banderas para que se abra como una nueva tarea y sin animaciones bruscas
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        context.startActivity(intent)
                    }
                    onAppLaunched()
                }
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            app.icon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(), 
                    contentDescription = null, 
                    modifier = Modifier.size(35.dp)
                )
            }
            Text(
                text = app.label, 
                style = MaterialTheme.typography.labelSmall, 
                maxLines = 1, 
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}
