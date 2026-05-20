package com.example.launcherorbys.ui.components.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*
import com.example.launcherorbys.ui.components.StatusIcon

/**
 * Gestiona el estado y la visualización del icono de USB en la barra de estado.
 * Detecta cuando se conectan o desconectan dispositivos USB.
 */
@Composable
fun UsbSection(context: Context) {
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    
    // Estado inicial: comprobamos si ya hay algún dispositivo conectado
    var isUsbConnected by remember { mutableStateOf(usbManager.deviceList.isNotEmpty()) }

    // Escuchamos eventos de conexión/desconexión de USB
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Actualizamos el estado comprobando la lista de dispositivos
                isUsbConnected = usbManager.deviceList.isNotEmpty()
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        context.registerReceiver(receiver, filter)
        
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    StatusIcon(
        imageVector = Icons.Default.Usb,
        contentDescription = "USB",
        isVisible = isUsbConnected,
        onClick = {
            // Intentamos abrir los ajustes de almacenamiento para gestionar el USB
            try {
                context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                // Fallback: Si no existe la actividad de almacenamiento, no hacemos nada
            }
        }
    )
}
