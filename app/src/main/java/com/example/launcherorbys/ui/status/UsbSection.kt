package com.example.launcherorbys.ui.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File

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
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val volumes = storageManager.storageVolumes
                val usbVolume = volumes.find { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }

                var opened = false

                // 1. Intentar abrir el volumen USB específico directamente (Android 7+)
                if (usbVolume != null) {
                    val uuid = usbVolume.uuid
                    if (uuid != null) {
                        try {
                            val uri = Uri.parse("content://com.android.externalstorage.documents/root/$uuid")
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "vnd.android.cursor.dir/file")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            opened = true
                        } catch (_: Exception) {}
                    }
                }

                // 2. Usar el selector estándar de archivos (API 29+)
                if (!opened && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        opened = true
                    } catch (_: Exception) {}
                }

                // 3. Buscar paquetes conocidos de administradores de archivos (Especialmente para < API 29)
                if (!opened) {
                    val knownPackages = listOf(
                        "com.google.android.documentsui",
                        "com.android.documentsui",
                        "com.sec.android.app.myfiles",
                        "com.mi.android.globalFileexplorer",
                        "com.huawei.hidisk",
                        "com.android.fileexplorer"
                    )
                    for (pkg in knownPackages) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            try {
                                context.startActivity(launchIntent)
                                opened = true
                                break
                            } catch (_: Exception) {}
                        }
                    }
                }

                // 4. Intentar abrir la raíz primaria como último recurso
                if (!opened) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "vnd.android.cursor.dir/file")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        opened = true
                    } catch (_: Exception) {}
                }

                if (opened) {
                    Toast.makeText(context, "Abriendo archivos", Toast.LENGTH_SHORT).show()
                } else {
                    // Fallback físico
                    val usbPath = findUsbPath()
                    if (usbPath != null) {
                        try {
                            val usbFile = File(usbPath)
                            val uri = FileProvider.getUriForFile(context, "com.example.launcherorbys.fileprovider", usbFile)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "vnd.android.cursor.dir/file")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No se pudo abrir el administrador de archivos", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No se encontró una aplicación de archivos", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "No hay USB conectado", Toast.LENGTH_SHORT).show()
            }
        }
    )
}