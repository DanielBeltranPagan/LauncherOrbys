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
 *
 * Se suscribe a los cambios de red para detectar si hay una conexión WiFi activa.
 * Al pulsar sobre el icono, se abren los ajustes de WiFi del sistema.
 *
 * @param contexto El contexto de la aplicación.
 */
@Composable
fun WifiSection(contexto: Context) {
    val gestorConectividad = remember { 
        contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager 
    }

    val redesWifiActivas = remember { mutableStateListOf<Network>() }
    val estaWifiConectado = redesWifiActivas.isNotEmpty()

    DisposableEffect(contexto) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(red: Network) {
                if (!redesWifiActivas.contains(red)) {
                    redesWifiActivas.add(red)
                }
            }

            override fun onLost(red: Network) {
                redesWifiActivas.remove(red)
            }

            override fun onUnavailable() {
                redesWifiActivas.clear()
            }
        }

        val solicitud = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        try {
            gestorConectividad.activeNetwork?.let { red ->
                val caps = gestorConectividad.getNetworkCapabilities(red)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    redesWifiActivas.add(red)
                }
            }
            
            gestorConectividad.registerNetworkCallback(solicitud, callback)
        } catch (e: Exception) {}

        onDispose {
            try { 
                gestorConectividad.unregisterNetworkCallback(callback) 
            } catch (e: Exception) {}
        }
    }

    StatusIcon(
        imageVector = Icons.Default.Wifi,
        contentDescription = "WiFi",
        isVisible = estaWifiConectado,
        onClick = {
            val gestorSistema = SystemControlManager(contexto)
            gestorSistema.abrirAjustesWifi()
        }
    )
}
