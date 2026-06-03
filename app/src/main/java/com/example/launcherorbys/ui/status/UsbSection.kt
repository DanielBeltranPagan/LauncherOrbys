package com.example.launcherorbys.ui.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun UsbSection(context: Context) {
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    // Función para verificar si hay USB EXTERNO conectado
    fun hasExternalUsb(): Boolean {
        return try {
            val deviceList = usbManager.deviceList.values
            deviceList.any { device ->
                // Filtra solo dispositivos USB tipo almacenamiento (mass storage)
                device.deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE ||
                        device.deviceClass == UsbConstants.USB_CLASS_MISC ||
                        (device.deviceSubclass == 0x06 && device.deviceProtocol == 0x50) // Bulk-only transport
            }
        } catch (_: Exception) {
            false
        }
    }

    var isUsbConnected by remember { mutableStateOf(hasExternalUsb()) }

    // Función para encontrar la ruta del USB
    fun findUsbPath(): String? {
        val possiblePaths = listOf(
            "/storage/usb0",
            "/storage/usb1",
            "/storage/external_sd",
            "/mnt/usb_storage",
            "/mnt/media_rw/usb0",
            "/mnt/media_rw/usb1",
            "/storage/removable/usb0",
            "/storage/removable/usb1",
            "/mnt/usb",
            "/external_sd"
        )

        return possiblePaths.find { path ->
            val file = File(path)
            file.exists() && file.isDirectory && file.canRead()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isUsbConnected = hasExternalUsb()
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
            if (isUsbConnected) {
                val usbPath = findUsbPath()

                if (usbPath != null) {
                    try {
                        val usbFile = File(usbPath)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "com.example.launcherorbys.fileprovider",
                            usbFile
                        )

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "vnd.android.cursor.dir/file")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Abriendo USB: $usbPath", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No se encontró el USB montado", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "No hay USB conectado", Toast.LENGTH_SHORT).show()
            }
        }
    )
}