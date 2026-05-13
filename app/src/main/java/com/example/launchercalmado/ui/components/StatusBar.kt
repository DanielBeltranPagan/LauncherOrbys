package com.example.launchercalmado.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Componente que muestra la barra de estado superior personalizada.
 * Actualmente incluye indicadores de conectividad (Bluetooth y WiFi).
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Secciones de iconos de conectividad con fondo circular
        BluetoothSection(context = context)
        WifiSection(context = context)
    }
}

/**
 * Componente base para los iconos de la barra de estado con fondo circular y efecto de click.
 */
@Composable
fun StatusIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isVisible: Boolean = true
) {
    if (isVisible) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White, shape = CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Gestiona el estado y la visualización del icono de Bluetooth.
 */
@Composable
fun BluetoothSection(context: Context) {
    val bluetoothAdapter = remember { 
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }
    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    // Escucha cambios en el estado del Bluetooth mediante un BroadcastReceiver
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    isBluetoothEnabled = state == BluetoothAdapter.STATE_ON
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }

    if (bluetoothAdapter != null) {
        StatusIcon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = "Bluetooth",
            isVisible = isBluetoothEnabled,
            onClick = {
                // Abre los ajustes de Bluetooth al pulsar
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )
    }
}

/**
 * Gestiona el estado y la visualización del icono de WiFi.
 */
@Composable
fun WifiSection(context: Context) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var isWifiEnabled by remember { mutableStateOf(false) }

    // Utiliza NetworkCallbacks para detectar cambios en la conectividad en tiempo real
    DisposableEffect(context) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isWifiEnabled = true
            }

            override fun onLost(network: Network) {
                isWifiEnabled = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isWifiEnabled = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Fallback para versiones donde registerDefaultNetworkCallback pueda fallar o no ser adecuado
        }

        onDispose {
            try { connectivityManager.unregisterNetworkCallback(callback) } catch (e: Exception) {}
        }
    }

    StatusIcon(
        imageVector = Icons.Default.Wifi,
        contentDescription = "WiFi",
        isVisible = isWifiEnabled,
        onClick = {
            // Abre los ajustes de WiFi al pulsar
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    )
}
