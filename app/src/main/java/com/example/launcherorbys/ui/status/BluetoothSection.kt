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
 *
 * Muestra el icono de Bluetooth dependiendo de si está habilitado o si hay dispositivos conectados.
 * Si no se tienen permisos de Bluetooth, el icono permite solicitar dichos permisos.
 *
 * @param contexto El contexto de la aplicación.
 * @param alSolicitarPermiso Callback para solicitar los permisos de Bluetooth necesarios.
 */
@Composable
fun BluetoothSection(
    contexto: Context,
    alSolicitarPermiso: () -> Unit
) {
    val gestorSistema = remember { SystemControlManager(contexto) }
    val adaptadorBluetooth = remember { 
        try {
            val manager = contexto.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter
        } catch (e: Exception) { null }
    }

    var tienePermiso by remember { mutableStateOf(true) }
    var estaBluetoothHabilitado by remember { mutableStateOf(false) }
    var estaConectado by remember { mutableStateOf(false) }

    val actualizarEstado = {
        if (adaptadorBluetooth != null) {
            val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else true
            
            tienePermiso = p

            if (p) {
                try {
                    estaBluetoothHabilitado = adaptadorBluetooth.isEnabled
                    estaConectado = adaptadorBluetooth.getProfileConnectionState(BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED ||
                                  adaptadorBluetooth.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                } catch (e: SecurityException) {
                    tienePermiso = false
                }
            } else {
                estaBluetoothHabilitado = false
                estaConectado = false
            }
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            actualizarEstado()
            delay(3000) 
        }
    }

    DisposableEffect(contexto) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                actualizarEstado()
            }
        }
        contexto.registerReceiver(receptor, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { try { contexto.unregisterReceiver(receptor) } catch (e: Exception) {} }
    }

    val deberiaMostrar = adaptadorBluetooth != null && (!tienePermiso || estaBluetoothHabilitado)

    if (deberiaMostrar) {
        StatusIcon(
            imageVector = if (estaConectado) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
            contentDescription = "Bluetooth",
            isVisible = true,
            onClick = {
                if (!tienePermiso) {
                    alSolicitarPermiso()
                } else {
                    gestorSistema.abrirAjustesBluetooth()
                }
            }
        )
    }
}
