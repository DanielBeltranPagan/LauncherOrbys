package com.example.launcherorbys.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Barra de navegación personalizada del launcher.
 */
@Composable
fun NavBar(
    onActionClicked: (String) -> Unit,
    iconColor: Color = Color.White,
    backgroundColor: Color = Color.Black,
    isAtTop: Boolean = false,
    isExpanded: Boolean = true,
    clockAtLeft: Boolean = true
) {
    val contexto = LocalContext.current
    val configuracion = LocalConfiguration.current
    
    val altura by animateDpAsState(targetValue = if (isExpanded) 48.dp else 0.dp, label = "alturaNav")
    val alfaContenido by animateFloatAsState(targetValue = if (isExpanded) 1f else 0f, label = "alfaContenido")
    
    var controlesExpandidos by remember { mutableStateOf(false) }

    var ultimoTiempoClic by remember { mutableLongStateOf(0L) }
    val accionConDebounce: (String) -> Unit = { accion ->
        val ahora = System.currentTimeMillis()
        // El delay de 0.5s solo se aplica al botón de Apps para evitar rebotes
        if (accion == "APPS") {
            Log.d("click","CLICK X A")
            if (ahora - ultimoTiempoClic >= 2000L) {
                Log.d("click","TIEMPO VALIDO: $ultimoTiempoClic")
                ultimoTiempoClic = ahora
                onActionClicked(accion)
            }
        } else {
            // Los demás botones son instantáneos
            onActionClicked(accion)
        }
    }

    val alternarControles = {
        controlesExpandidos = !controlesExpandidos
    }

    LaunchedEffect(controlesExpandidos, isExpanded) {
        if (!isExpanded) {
            controlesExpandidos = false
        } else if (controlesExpandidos) {
            delay(5000)
            controlesExpandidos = false
        }
    }

    var horaActual by remember { mutableStateOf("") }

    val actualizarHora = {
        val formato24Horas = android.text.format.DateFormat.is24HourFormat(contexto)
        val patron = if (formato24Horas) "HH:mm" else "h:mm a"
        val formatoFecha = SimpleDateFormat(patron, configuracion.locales[0])
        formatoFecha.calendar = Calendar.getInstance()
        horaActual = formatoFecha.format(Date())
    }

    DisposableEffect(contexto, configuracion.locales[0]) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                actualizarHora()
            }
        }
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        
        ContextCompat.registerReceiver(contexto, receptor, filtro, ContextCompat.RECEIVER_EXPORTED)
        actualizarHora()
        
        onDispose {
            try { contexto.unregisterReceiver(receptor) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(configuracion.locales[0]) {
        while (true) {
            actualizarHora()
            delay(10000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            .systemGestureExclusion(),
        contentAlignment = if (isAtTop) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alfaContenido)
                .background(color = backgroundColor.copy(alpha = 0.95f))
        )

        if (isExpanded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconoNavBar(Icons.AutoMirrored.Filled.ArrowBack, iconColor) { accionConDebounce("BACK") }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconoNavBar(Icons.Default.Home, iconColor) { accionConDebounce("HOME") }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconoNavBar(Icons.Default.CropSquare, iconColor) { accionConDebounce("RECENTS") }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconoNavBar(Icons.Default.Apps, iconColor) { accionConDebounce("APPS") }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconoNavBar(Icons.Default.Language, iconColor) { accionConDebounce("GOOGLE") }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconoNavBar(Icons.Default.Folder, iconColor) { accionConDebounce("FILES") }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconoNavBar(Icons.Default.Tune, iconColor) { accionConDebounce("SYSTEM_OPTIONS") }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                val componenteReloj = @Composable {
                    Text(
                        text = horaActual,
                        color = iconColor,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.clickable { accionConDebounce("CLOCK") }
                    )
                }

                val componenteControles = @Composable {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (controlesExpandidos) {
                            IconoNavBar(Icons.Default.SwapVert, iconColor) { accionConDebounce("TOGGLE_NAVBAR_POSITION") }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconoNavBar(Icons.Default.SwapHoriz, iconColor) { accionConDebounce("TOGGLE_CLOCK_SIDE") }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconoNavBar(
                            icon = if (controlesExpandidos) Icons.Default.Close else Icons.Default.OpenWith,
                            color = iconColor.copy(alpha = if (controlesExpandidos) 0.5f else 1f)
                        ) { alternarControles() }
                    }
                }

                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    if (clockAtLeft) componenteReloj() else componenteControles()
                }

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    if (clockAtLeft) componenteControles() else componenteReloj()
                }
            }
        }
    }
}

@Composable
private fun IconoNavBar(
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
