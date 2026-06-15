package com.example.launcherorbys.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.launcherorbys.ui.status.StatusBar
import com.example.launcherorbys.ui.theme.Dimens
import com.example.launcherorbys.utils.Constants

/**
 * Pantalla de inicio principal del Launcher.
 *
 * Esta es la raíz de la interfaz de usuario. Gestiona:
 * - El renderizado del fondo (imagen o color sólido).
 * - La barra de estado superior ([StatusBar]).
 * - Los gestos globales (tap para cerrar menús abiertos).
 * - La escucha de cambios de posición de la barra de navegación mediante [BroadcastReceiver].
 *
 * @param modelo El [MainViewModel] que contiene el estado global de la aplicación.
 * @param alSolicitarBluetooth Callback que se propaga a la [StatusBar] para gestionar permisos de Bluetooth.
 */
@Composable
fun HomeScreen(
    modelo: MainViewModel,
    alSolicitarBluetooth: () -> Unit
) {
    val contexto = LocalContext.current

    DisposableEffect(contexto) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                if (intent?.action == Constants.ACTION_NAVBAR_POSITION_CHANGED) {
                    modelo.navBarEnLaParteSuperior = intent.getBooleanExtra("atTop", false)
                }
            }
        }
        val filtro = IntentFilter(Constants.ACTION_NAVBAR_POSITION_CHANGED)
        ContextCompat.registerReceiver(contexto, receptor, filtro, ContextCompat.RECEIVER_EXPORTED)
        onDispose {
            try { contexto.unregisterReceiver(receptor) } catch (e: Exception) {}
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { modelo.cerrarTodo() }
                )
            },
        color = modelo.colorSolido ?: Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ContenedorFondo(modelo)

            val rellenoBarraEstado = if (modelo.navBarEnLaParteSuperior) Dimens.StatusBarPaddingTopNavAtTop else Dimens.StatusBarPaddingTop

            StatusBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = rellenoBarraEstado),
                alSolicitarBluetooth = alSolicitarBluetooth
            )
        }
    }
}

/**
 * Contenedor encargado de cargar y renderizar la imagen de fondo personalizada.
 */
@Composable
private fun ContenedorFondo(modelo: MainViewModel) {
    modelo.uriImagenFondo?.let { uri ->
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
