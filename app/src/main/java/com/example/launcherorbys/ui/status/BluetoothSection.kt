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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.launcherorbys.managers.SystemControlManager
import kotlinx.coroutines.delay

/**
 * Gestiona el estado y la visualización del icono de Bluetooth en la barra de estado.
 * Se muestra si está conectado a un dispositivo o si el usuario necesita gestionar permisos.
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

    var hasPermission by remember { mutableStateOf(true) }
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    // Función para actualizar los estados reales
    val updateState = {
        if (bluetoothAdapter != null) {
            val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true
            
            hasPermission = p

            if (p) {
                try {
                    isBluetoothEnabled = bluetoothAdapter.isEnabled
                    isConnected = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED ||
                                  bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                } catch (e: SecurityException) {
                    hasPermission = false
                }
            } else {
                isBluetoothEnabled = false
                isConnected = false
            }
        }
    }

    // Refresco periódico y al montar
    LaunchedEffect(Unit) {
        while(true) {
            updateState()
            delay(3000) 
        }
    }

    // Escucha cambios de estado del adaptador (ON/OFF)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateState()
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { try { context.unregisterReceiver(receiver) } catch (e: Exception) {} }
    }

    // Decidimos si mostrar el icono:
    // 1. Si NO hay permiso, lo mostramos para que el usuario pueda pulsar y arreglarlo.
    // 2. Si hay permiso, solo lo mostramos si está activado (similar al WiFi).
    val shouldShow = bluetoothAdapter != null && (!hasPermission || isBluetoothEnabled)

    if (shouldShow) {
        StatusIcon(
            imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
            contentDescription = "Bluetooth",
            isVisible = true,
            onClick = {
                if (!hasPermission) {
                    onRequestPermission()
                } else {
                    systemManager.abrirAjustesBT()
                }
            }
        )
    }
}
