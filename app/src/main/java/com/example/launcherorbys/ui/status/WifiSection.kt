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
    
    // Función para verificar la conexión WiFi actual
    fun isWifiConnectedNow(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    var isWifiConnected by remember { mutableStateOf(isWifiConnectedNow()) }

    DisposableEffect(context) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Al conectarse a una red WiFi, actualizamos el estado
                isWifiConnected = true
            }

            override fun onLost(network: Network) {
                // Al perder la conexión WiFi, verificamos si queda alguna otra activa
                isWifiConnected = isWifiConnectedNow()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // Si cambian las capacidades, verificamos si sigue siendo WiFi funcional
                isWifiConnected = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }

        // Filtramos para recibir SOLO eventos relacionados con WiFi
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            isWifiConnected = isWifiConnectedNow()
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
