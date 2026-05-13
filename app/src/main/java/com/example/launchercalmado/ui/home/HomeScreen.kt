package com.example.launchercalmado.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.launchercalmado.ui.components.StatusBar
import kotlin.math.roundToInt

/**
 * Pantalla principal del Launcher (el "Escritorio").
 * Gestiona el fondo de pantalla y las interacciones táctiles (pulsación larga para menú).
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPersonalizarClick: () -> Unit
) {
    val context = LocalContext.current
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Detecta gestos en el fondo
                detectTapGestures(
                    onLongPress = { offset ->
                        // Abre el menú contextual en la posición pulsada
                        viewModel.onLongPress(offset)
                    },
                    onTap = { viewModel.cerrarTodo() } // Cierra menús abiertos al tocar el fondo
                )
            },
        color = viewModel.colorSolido ?: Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Renderiza la imagen de fondo personalizada si el usuario ha elegido una
            viewModel.uriImagenFondo?.let { uri ->
                val bitmap = remember(uri) { 
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        BitmapFactory.decodeStream(inputStream)
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

            // Capa para el menú contextual (se muestra al hacer pulsación larga)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Barra de estado personalizada (solo visible aquí)
                StatusBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
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

/**
 * Menú flotante que aparece tras una pulsación larga en la pantalla de inicio.
 */
@Composable
fun BoxWithConstraintsScope.MenuContextual(
    posicion: Offset, 
    onPersonalizarClick: () -> Unit, 
    onDismiss: () -> Unit
) {
    // Fondo invisible para cerrar el menú si se toca fuera de él
    Box(modifier = Modifier.fillMaxSize().clickable { onDismiss() })
    
    Card(
        modifier = Modifier
            .offset { 
                // Calcula la posición del menú asegurando que no se salga de los bordes de la pantalla
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
            // Opción para abrir los ajustes de personalización del sistema
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
