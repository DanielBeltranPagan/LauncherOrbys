package com.example.launcherorbys.ui.status

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.launcherorbys.managers.SystemControlManager
import kotlinx.coroutines.delay

/**
 * Gestiona el estado y la visualización del icono de Bluetooth en la barra de estado.
 * Verifica permisos y estado de conexión de forma periódica.
 */
@Composable
fun BluetoothSection(
    context: Context,
    onRequestPermission: () -> Unit
) {
    val systemManager = remember { SystemControlManager(context) }
    val bluetoothAdapter = remember { 
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter
        } catch (e: Exception) { null }
    }

    var hasPermission by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    // Función interna para refrescar los estados
    val updateState = {
        if (bluetoothAdapter != null) {
            hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true

            try {
                if (hasPermission) {
                    @Suppress("MissingPermission")
                    isBluetoothEnabled = bluetoothAdapter.isEnabled
                    @Suppress("MissingPermission")
                    isConnected = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED ||
                                  bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                } else {
                    isBluetoothEnabled = false
                    isConnected = false
                }
            } catch (e: Exception) {
                // Si hay un error al acceder a las propiedades (ej. permisos), asumimos desactivado
                isBluetoothEnabled = false
                isConnected = false
            }
        }
    }

    // Actualización periódica cada 2 segundos para detectar cambios de conexión
    LaunchedEffect(Unit) {
        while(true) {
            updateState()
            delay(2000) 
        }
    }

    // Escucha cambios de hardware (activado/desactivado)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateState()
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { try { context.unregisterReceiver(receiver) } catch (e: Exception) {} }
    }

    StatusIcon(
        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
        contentDescription = "Bluetooth",
        isVisible = bluetoothAdapter != null && isBluetoothEnabled && hasPermission,
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission) {
                // Pedir permiso si falta
                onRequestPermission()
            }
            // SIEMPRE abrir ajustes de Bluetooth
            systemManager.abrirAjustesBT()
        }
    )
}
