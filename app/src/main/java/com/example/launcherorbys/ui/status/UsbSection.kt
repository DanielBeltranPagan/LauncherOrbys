package com.example.launcherorbys.ui.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import com.example.launcherorbys.managers.AppLauncher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*

@Composable
fun UsbSection(context: Context) {
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    // Función para verificar si hay un USB de almacenamiento realmente montado y accesible
    fun hasExternalUsb(): Boolean {
        return try {
            // 1. Verificamos si hay algún dispositivo físico de almacenamiento conectado
            val hasPhysicalStorage = usbManager.deviceList.values.any { device ->
                // Un pendrive debe tener la clase Mass Storage (0x08) en el dispositivo o en una interfaz
                device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE ||
                        (0 until device.interfaceCount).any { i ->
                            device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
                        }
            }

            if (!hasPhysicalStorage) return false

            // 2. Si hay hardware, confirmamos que el sistema lo ha montado como volumen removible
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            storageManager.storageVolumes.any { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
        } catch (_: Exception) {
            false
        }
    }

    var isUsbConnected by remember { mutableStateOf(hasExternalUsb()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isUsbConnected = hasExternalUsb()
            }
        }

        // Filtro para conexión física
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        // Filtro para cambios en el sistema de archivos (montaje/desmontaje)
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }

        context.registerReceiver(receiver, usbFilter)
        context.registerReceiver(receiver, mediaFilter)

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
            if (isUsbConnected) {
                AppLauncher(context).abrirAppArchivos()
            } else {
                Toast.makeText(context, "No hay USB conectado", Toast.LENGTH_SHORT).show()
            }
        }
    )
}