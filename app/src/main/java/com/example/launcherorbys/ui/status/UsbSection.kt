package com.example.launcherorbys.ui.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*

/**
 * Gestiona el estado y la visualización del icono de USB en la barra de estado.
 * Detecta cuando se conectan memorias USB (Mass Storage).
 */
@Composable
fun UsbSection(context: Context) {
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    // Paso 1: Función para detectar qué app de archivos está instalada
    fun getFileManagerPackage(context: Context): String? {
        val packages = listOf(
            "com.google.android.documentsui",    // Google Files
            "com.android.documentsui",           // Android Files
            "com.sec.android.app.myfiles",       // Samsung Files
            "com.motorola.filemanager",          // Motorola Files
            "com.htc.sense.filemanager",         // HTC Files
            "com.xiaomi.filemanager",            // Xiaomi Files
            "com.oppo.filemanager",              // OPPO Files
            "com.realme.filemanager"             // Realme Files
        )
        
        val pm = context.packageManager
        return packages.find { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    // Función para verificar si hay algún almacenamiento masivo USB conectado
    fun checkUsbStorage(): Boolean {
        return usbManager.deviceList.values.any { device ->
            (0 until device.interfaceCount).any { i ->
                device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
            }
        }
    }

    // Función para obtener el dispositivo USB conectado
    fun getConnectedUsbDevice(): UsbDevice? {
        return usbManager.deviceList.values.find { device ->
            (0 until device.interfaceCount).any { i ->
                device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
            }
        }
    }

    var isUsbConnected by remember { mutableStateOf(checkUsbStorage()) }

    // Escuchamos eventos de conexión/desconexión de USB
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                isUsbConnected = checkUsbStorage()
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
            val usbDevice = getConnectedUsbDevice()
            
            if (usbDevice != null) {
                val fileManagerPkg = getFileManagerPackage(context)
                
                if (fileManagerPkg != null) {
                    try {
                        var intent = context.packageManager.getLaunchIntentForPackage(fileManagerPkg)
                        
                        // Si no tiene intent de lanzamiento directo, probamos con uno de visualización genérico
                        if (intent == null) {
                            intent = Intent(Intent.ACTION_VIEW).apply {
                                setPackage(fileManagerPkg)
                                setDataAndType(android.net.Uri.parse("content://com.android.externalstorage.documents/root/"), "vnd.android.cursor.dir/file")
                            }
                        }

                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            Toast.makeText(context, "Abriendo: $fileManagerPkg", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error: No se pudo crear el acceso a $fileManagerPkg", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al abrir: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "No se encontró app de archivos compatible", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "USB no detectado como almacenamiento", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
