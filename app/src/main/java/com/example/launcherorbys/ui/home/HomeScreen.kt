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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.launcherorbys.ui.status.StatusBar
import com.example.launcherorbys.utils.Constants

/**
 * Pantalla de inicio principal del Launcher.
 * Gestiona la visualización del fondo, gestos de usuario y estados globales de la UI.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onBluetoothRequest: () -> Unit
) {
    val context = LocalContext.current

    // Sincronización con eventos de la barra de navegación del sistema
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Constants.ACTION_NAVBAR_POSITION_CHANGED) {
                    viewModel.navBarAtTop = intent.getBooleanExtra("atTop", false)
                }
            }
        }
        val filter = IntentFilter(Constants.ACTION_NAVBAR_POSITION_CHANGED)
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
                    onTap = { viewModel.cerrarTodo() }
                )
            },
        color = viewModel.colorSolido ?: Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Renderizado del fondo de pantalla
            WallpaperContainer(viewModel)

            val statusBarPadding = if (viewModel.navBarAtTop) 55.dp else 10.dp

            StatusBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarPadding),
                onBluetoothRequest = onBluetoothRequest
            )
        }
    }
}

@Composable
private fun WallpaperContainer(viewModel: MainViewModel) {
    val context = LocalContext.current
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
}
