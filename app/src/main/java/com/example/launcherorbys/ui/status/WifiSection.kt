package com.example.launcherorbys.ui.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import com.example.launcherorbys.managers.SystemControlManager

/**
 * Gestiona el estado y la visualización del icono de WiFi en la barra de estado.
 * El icono solo es visible si hay una conexión WiFi activa y funcional.
 */
@Composable
fun WifiSection(context: Context) {
    val connectivityManager = remember { 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager 
    }

    // Usamos una lista de redes WiFi activas para mayor precisión
    val activeWifiNetworks = remember { mutableStateListOf<Network>() }
    val isWifiConnected = activeWifiNetworks.isNotEmpty()

    DisposableEffect(context) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!activeWifiNetworks.contains(network)) {
                    activeWifiNetworks.add(network)
                }
            }

            override fun onLost(network: Network) {
                activeWifiNetworks.remove(network)
            }

            override fun onUnavailable() {
                activeWifiNetworks.clear()
            }
        }

        // Filtramos para recibir SOLO eventos relacionados con WiFi
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            // Comprobación inicial: si ya hay una red WiFi activa al empezar
            connectivityManager.activeNetwork?.let { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    activeWifiNetworks.add(network)
                }
            }
            
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            // Fallback silencioso
        }

        onDispose {
            try { 
                connectivityManager.unregisterNetworkCallback(callback) 
            } catch (e: Exception) {}
        }
    }

    StatusIcon(
        imageVector = Icons.Default.Wifi,
        contentDescription = "WiFi",
        isVisible = isWifiConnected,
        onClick = {
            val systemManager = SystemControlManager(context)
            systemManager.abrirAjustesWifi()
        }
    )
}
