package com.example.launcherorbys.ui.components

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
            .fillMaxWidth(0.4f)
            .fillMaxHeight(0.65f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = null, indication = null) { /* Evita que el click cierre el drawer */ }
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            // Buscador mejorado
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                placeholder = { 
                    Text(
                        "Buscar...", 
                        color = Color.White.copy(alpha = 0.5f), 
                        style = MaterialTheme.typography.bodySmall 
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rejilla de aplicaciones
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
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
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                context.startActivity(intent)
                            }
                            onAppLaunched()
                        },
                        onLongPress = {
                            showMenu = true
                        }
                    )
                }
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            app.icon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(), 
                    contentDescription = null, 
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.label, 
                style = MaterialTheme.typography.labelSmall, 
                maxLines = 1, 
                color = Color.White,
                fontSize = 11.sp
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            DropdownMenuItem(
                text = { Text("Información") },
                onClick = {
                    showMenu = false
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        onAppLaunched()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Error al abrir ajustes", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Desinstalar", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    try {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        onAppLaunched() // Solo cerramos si el intent se lanza con éxito
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No se pudo desinstalar", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}
