package com.example.launcherorbys.ui.status

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*

/**
 * Gestiona el estado y la visualización del icono de WiFi en la barra de estado.
 * Escucha cambios de red en tiempo real.
 */
@Composable
fun WifiSection(context: Context) {
    val connectivityManager = remember { 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager 
    }
    
    val checkWifi = {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    var isWifiEnabled by remember { mutableStateOf(checkWifi()) }

    // Registra un callback para detectar cambios en la conectividad
    DisposableEffect(context) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isWifiEnabled = true
            }

            override fun onLost(network: Network) {
                isWifiEnabled = checkWifi()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isWifiEnabled = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // Fallback en caso de error en el registro
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
            // Abrir ajustes de WiFi
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    )
}
