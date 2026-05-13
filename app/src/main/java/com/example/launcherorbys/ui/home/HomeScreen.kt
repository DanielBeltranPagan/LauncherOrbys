package com.example.launcherorbys.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.launcherorbys.ui.components.StatusBar
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPersonalizarClick: () -> Unit
) {
    val context = LocalContext.current

    // Escuchar el cambio de posición de la NavBar para adaptar la UI
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "NAVBAR_POSITION_CHANGED") {
                    viewModel.navBarAtTop = intent.getBooleanExtra("atTop", false)
                }
            }
        }
        val filter = IntentFilter("NAVBAR_POSITION_CHANGED")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { viewModel.onLongPress(it) },
                    onTap = { viewModel.cerrarTodo() }
                )
            },
        color = viewModel.colorSolido ?: Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            viewModel.uriImagenFondo?.let { uri ->
                val bitmap = remember(uri) { 
                    try {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) { null }
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val statusBarPadding = if (viewModel.navBarAtTop) 55.dp else 10.dp

                StatusBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusBarPadding)
                )

                if (viewModel.mostrarMenuContextual) {
                    MenuContextual(
                        posicion = viewModel.posicionToque,
                        onPersonalizarClick = {
                            onPersonalizarClick()
                            viewModel.cerrarTodo()
                        },
                        onDismiss = { viewModel.cerrarTodo() }
                    )
                }
            }
        }
    }
}

@Composable
fun BoxWithConstraintsScope.MenuContextual(
    posicion: Offset, 
    onPersonalizarClick: () -> Unit, 
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() })
    
    Card(
        modifier = Modifier
            .offset { 
                IntOffset(
                    posicion.x.roundToInt().coerceIn(0, (constraints.maxWidth - 220.dp.toPx().toInt()).coerceAtLeast(0)), 
                    posicion.y.roundToInt().coerceIn(0, (constraints.maxHeight - 80.dp.toPx().toInt()).coerceAtLeast(0))
                ) 
            }
            .width(220.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            TextButton(
                onClick = onPersonalizarClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Fondo y estilo",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
